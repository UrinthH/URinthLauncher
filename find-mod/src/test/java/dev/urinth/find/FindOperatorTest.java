package dev.urinth.find;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The operator mode with the real Minecraft message classes: what a server's /data answer looks like. */
class FindOperatorTest {
    private static final String POS = "[128.3d, 63.0d, -1073.0d]";
    private static final String DIM = "\"minecraft:overworld\"";
    /** Far in the past or the future of any real clock reading, so no test waits for time to pass. */
    private static final long LONG_AGO = Long.MIN_VALUE / 4;
    private static final long MUCH_LATER = Long.MAX_VALUE / 4;

    private static Component answer(String who, String value) {
        return Component.translatable("commands.data.entity.query", Component.literal(who), Component.literal(value));
    }

    @AfterEach
    void stopWaiting() {
        FindOperator.cancel();
    }

    @Test
    void aDataAnswerIsTakenApart() {
        DataReply.Seen seen = FindOperator.read(answer("Notkiahh", POS));
        assertNotNull(seen);
        assertEquals("Notkiahh", seen.who());
        assertEquals(POS, seen.value());
    }

    @Test
    void valuesThatArriveAsPlainTextArriveTheSame() {
        Component line = Component.translatable("commands.data.entity.query", "Notkiahh", DIM);
        DataReply.Seen seen = FindOperator.read(line);
        assertNotNull(seen);
        assertEquals("Notkiahh", seen.who());
        assertEquals(DIM, seen.value());
    }

    @Test
    void theSameWordsTypedAsPlainTextAreNotAnAnswer() {
        assertNull(FindOperator.read(Component.literal("Notkiahh has the following entity data: " + POS)));
    }

    @Test
    void otherMessagesAreNotAnAnswer() {
        assertNull(FindOperator.read(null));
        assertNull(FindOperator.read(Component.literal("hello")));
        assertNull(FindOperator.read(Component.translatable("commands.data.entity.modified", Component.literal("Notkiahh"))));
        assertNull(FindOperator.read(Component.translatable("commands.data.entity.query", Component.literal("Notkiahh"))));
        assertNull(FindOperator.read(Component.translatable("commands.data.entity.query",
                Component.literal("Notkiahh"), Component.literal(POS), Component.literal("extra"))));
    }

    @Test
    void theTwoAnswersAreHiddenAndTurnIntoTheUsualResult() {
        FindOperator.begin("Notkiahh");
        assertFalse(FindOperator.allow(answer("Notkiahh", POS), false));
        assertNull(FindOperator.result(LONG_AGO));
        assertFalse(FindOperator.allow(answer("Notkiahh", DIM), false));
        FindReply reply = FindOperator.result(LONG_AGO);
        assertNotNull(reply);
        assertEquals("XYZ: x128.30 y63 z-1073\nOverworld < Notkiahh", reply.plainText());
        assertNull(FindOperator.result(MUCH_LATER));
    }

    @Test
    void aFarAwayAnswerIsShownLikeAnyOther() {
        FindOperator.begin("Notkiahh");
        FindOperator.allow(answer("Notkiahh", "[100000.0d, 70.0d, -100000.0d]"), false);
        FindOperator.allow(answer("Notkiahh", "\"minecraft:the_end\""), false);
        assertEquals("XYZ: x100000 y70 z-100000\nEnd < Notkiahh", FindOperator.result(LONG_AGO).plainText());
    }

    @Test
    void aTeamPrefixOnTheNameIsNoProblem() {
        FindOperator.begin("Notkiahh");
        assertFalse(FindOperator.allow(answer("[VIP] Notkiahh", POS), false));
        assertFalse(FindOperator.allow(answer("[VIP] Notkiahh", DIM), false));
        assertEquals("XYZ: x128.30 y63 z-1073\nOverworld < Notkiahh", FindOperator.result(LONG_AGO).plainText());
    }

    @Test
    void withoutADimensionThePositionStillComes() {
        FindOperator.begin("Notkiahh");
        FindOperator.allow(answer("Notkiahh", POS), false);
        assertNull(FindOperator.result(LONG_AGO));
        assertEquals("XYZ: x128.30 y63 z-1073\nUnknown < Notkiahh", FindOperator.result(MUCH_LATER).plainText());
    }

    @Test
    void withNoAnswerTheResultSaysSo() {
        FindOperator.begin("Notkiahh");
        assertNull(FindOperator.result(LONG_AGO));
        FindReply reply = FindOperator.result(MUCH_LATER);
        assertNotNull(reply);
        assertEquals(FindReply.NO_ANSWER, reply.status());
        assertNull(FindOperator.result(MUCH_LATER));
    }

    @Test
    void aLineAboutAnotherPlayerIsShownNormally() {
        FindOperator.begin("Notkiahh");
        assertTrue(FindOperator.allow(answer("Someone", POS), false));
        assertTrue(FindOperator.allow(answer("Notkiahhh", POS), false));
        assertNull(FindOperator.result(LONG_AGO));
    }

    @Test
    void otherDataAboutThePlayerIsShownNormally() {
        FindOperator.begin("Notkiahh");
        assertTrue(FindOperator.allow(answer("Notkiahh", "20.0f"), false));
        assertTrue(FindOperator.allow(answer("Notkiahh", "\"Hello\""), false));
        assertNull(FindOperator.result(LONG_AGO));
    }

    @Test
    void ordinaryMessagesAreShownNormally() {
        FindOperator.begin("Notkiahh");
        assertTrue(FindOperator.allow(Component.literal("hello"), false));
        assertTrue(FindOperator.allow(Component.literal("Notkiahh has the following entity data: " + POS), false));
        assertNull(FindOperator.result(LONG_AGO));
    }

    @Test
    void theActionBarIsNeverTaken() {
        FindOperator.begin("Notkiahh");
        assertTrue(FindOperator.allow(answer("Notkiahh", POS), true));
        assertNull(FindOperator.result(LONG_AGO));
    }

    @Test
    void whenNothingIsWaitingEverythingIsShownNormally() {
        assertTrue(FindOperator.allow(answer("Notkiahh", POS), false));
        assertTrue(FindOperator.allow(answer("Notkiahh", DIM), false));
        assertNull(FindOperator.result(MUCH_LATER));
    }

    @Test
    void aNewQuestionDropsTheOldOne() {
        FindOperator.begin("Notkiahh");
        FindOperator.allow(answer("Notkiahh", POS), false);
        FindOperator.begin("Nora");
        assertTrue(FindOperator.allow(answer("Notkiahh", DIM), false));
        assertNull(FindOperator.result(LONG_AGO));
        assertFalse(FindOperator.allow(answer("Nora", POS), false));
        assertFalse(FindOperator.allow(answer("Nora", "\"minecraft:the_nether\""), false));
        assertEquals("XYZ: x128.30 y63 z-1073\nNether < Nora", FindOperator.result(LONG_AGO).plainText());
    }

    @Test
    void cancellingStopsTheWait() {
        FindOperator.begin("Notkiahh");
        FindOperator.cancel();
        assertTrue(FindOperator.allow(answer("Notkiahh", POS), false));
        assertNull(FindOperator.result(MUCH_LATER));
    }
}
