package dev.urinth.find;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataQueryTest {
    private static final String POS = "[128.3d, 63.0d, -1073.0d]";
    private static final String DIM = "\"minecraft:overworld\"";

    @Test
    void bothAnswersMakeTheResultDueAtOnce() {
        DataQuery query = new DataQuery("Notkiahh", 1000);
        assertFalse(query.isDue(1000));
        assertTrue(query.accept(POS, 1100));
        assertFalse(query.isDue(1100));
        assertTrue(query.accept(DIM, 1150));
        assertTrue(query.isDue(1150));
        assertEquals("XYZ: x128.30 y63 z-1073\nOverworld < Notkiahh", query.reply().plainText());
    }

    @Test
    void theOrderOfTheAnswersDoesNotMatter() {
        DataQuery query = new DataQuery("Notkiahh", 0);
        assertTrue(query.accept(DIM, 10));
        assertFalse(query.isDue(10));
        assertTrue(query.accept(POS, 20));
        assertTrue(query.isDue(20));
        assertEquals("XYZ: x128.30 y63 z-1073\nOverworld < Notkiahh", query.reply().plainText());
    }

    @Test
    void farAwayAnswersAreShownLikeAnyOther() {
        DataQuery query = new DataQuery("Notkiahh", 0);
        query.accept("[100000.0d, 70.0d, -100000.0d]", 10);
        query.accept("\"minecraft:the_nether\"", 11);
        assertEquals("XYZ: x100000 y70 z-100000\nNether < Notkiahh", query.reply().plainText());
    }

    @Test
    void withoutADimensionTheWaitIsShortAndThePositionStillShows() {
        DataQuery query = new DataQuery("Notkiahh", 0);
        query.accept(POS, 100);
        assertFalse(query.isDue(100));
        assertFalse(query.isDue(100 + DataQuery.GRACE_MS - 1));
        assertTrue(query.isDue(100 + DataQuery.GRACE_MS));
        assertEquals("XYZ: x128.30 y63 z-1073\nUnknown < Notkiahh", query.reply().plainText());
    }

    @Test
    void withNoAnswerAtAllTheWaitEndsAndTheReplyIsHonest() {
        DataQuery query = new DataQuery("Notkiahh", 5000);
        assertFalse(query.isDue(5000 + DataQuery.TIMEOUT_MS - 1));
        assertTrue(query.isDue(5000 + DataQuery.TIMEOUT_MS));
        assertEquals(FindReply.NO_ANSWER, query.reply().status());
        assertEquals("Notkiahh", query.reply().name());
    }

    @Test
    void aDimensionAloneIsNotAnAnswer() {
        DataQuery query = new DataQuery("Notkiahh", 0);
        query.accept(DIM, 10);
        assertFalse(query.isDue(10 + DataQuery.GRACE_MS));
        assertTrue(query.isDue(DataQuery.TIMEOUT_MS));
        assertEquals(FindReply.NO_ANSWER, query.reply().status());
    }

    @Test
    void otherPrintedValuesAreLeftForTheChat() {
        DataQuery query = new DataQuery("Notkiahh", 0);
        assertFalse(query.accept("20.0f", 1));
        assertFalse(query.accept("[1.0d, 2.0d]", 2));
        assertFalse(query.accept("\"Hello\"", 3));
        assertFalse(query.accept(null, 4));
        assertFalse(query.hasPosition());
        assertFalse(query.isDue(5));
    }

    @Test
    void theFirstAnswerOfEachKindWinsButTheLaterOnesAreStillHidden() {
        DataQuery query = new DataQuery("Notkiahh", 0);
        assertTrue(query.accept(POS, 10));
        assertTrue(query.accept("[1.0d, 2.0d, 3.0d]", 20));
        assertTrue(query.accept(DIM, 30));
        assertTrue(query.accept("\"minecraft:the_end\"", 40));
        assertEquals("XYZ: x128.30 y63 z-1073\nOverworld < Notkiahh", query.reply().plainText());
    }

    @Test
    void theNameIsKeptAsGiven() {
        assertEquals("Notkiahh", new DataQuery("Notkiahh", 0).name());
    }
}
