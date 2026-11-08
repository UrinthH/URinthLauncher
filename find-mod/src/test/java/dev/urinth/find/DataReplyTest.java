package dev.urinth.find;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataReplyTest {
    private static void assertPosition(double x, double y, double z, String printed) {
        double[] spot = DataReply.position(printed);
        assertNotNull(spot, printed);
        assertEquals(x, spot[0], 0.0);
        assertEquals(y, spot[1], 0.0);
        assertEquals(z, spot[2], 0.0);
    }

    @Test
    void aPositionIsReadAsTheServerPrintsIt() {
        assertPosition(128.3, 63.0, -1073.0, "[128.3d, 63.0d, -1073.0d]");
    }

    @Test
    void spacesDoNotMatter() {
        assertPosition(1.5, 2.0, -3.25, "[1.5d,2.0d,-3.25d]");
        assertPosition(1.5, 2.0, -3.25, "  [ 1.5d , 2.0d , -3.25d ]  ");
    }

    @Test
    void thereIsNoLimitOnHowFarAway() {
        // a hundred thousand blocks, in every direction
        assertPosition(100000.0, 70.0, -100000.0, "[100000.0d, 70.0d, -100000.0d]");
        assertPosition(-100000.5, -60.0, 100000.25, "[-100000.5d, -60.0d, 100000.25d]");
        // the game prints big numbers in scientific form: this is 12,345,678.5 and the edge of the world
        assertPosition(12345678.5, 64.0, -29999984.0, "[1.23456785E7d, 64.0d, -2.9999984E7d]");
        assertPosition(29999984.5, 319.0, 29999984.5, "[2.99999845E7d, 319.0d, 2.99999845E7d]");
    }

    @Test
    void whatIsNotAPositionIsNotRead() {
        assertNull(DataReply.position(null));
        assertNull(DataReply.position(""));
        assertNull(DataReply.position("[1.0d, 2.0d]"));
        assertNull(DataReply.position("[1.0d, 2.0d, 3.0d, 4.0d]"));
        assertNull(DataReply.position("[a, b, c]"));
        assertNull(DataReply.position("[1.0d, 2.0d, NaN]"));
        assertNull(DataReply.position("[1.0d, 2.0d, Infinity]"));
        assertNull(DataReply.position("20.0f"));
        assertNull(DataReply.position("\"minecraft:overworld\""));
    }

    @Test
    void aDimensionIsReadWithoutItsQuotes() {
        assertEquals("minecraft:overworld", DataReply.dimension("\"minecraft:overworld\""));
        assertEquals("minecraft:the_nether", DataReply.dimension("'minecraft:the_nether'"));
        assertEquals("minecraft:the_end", DataReply.dimension("  \"minecraft:the_end\"  "));
        assertEquals("some_pack:deep_space", DataReply.dimension("\"some_pack:deep_space\""));
        assertEquals("some_pack:worlds/moon", DataReply.dimension("\"some_pack:worlds/moon\""));
    }

    @Test
    void whatIsNotADimensionIsNotRead() {
        assertNull(DataReply.dimension(null));
        assertNull(DataReply.dimension(""));
        assertNull(DataReply.dimension("\"\""));
        assertNull(DataReply.dimension("\"Overworld\""));
        assertNull(DataReply.dimension("[128.3d, 63.0d, -1073.0d]"));
        assertNull(DataReply.dimension("20.0f"));
        assertNull(DataReply.dimension("\"two words:here\""));
    }

    @Test
    void aPositionIsNeverTakenForADimensionAndTheOtherWayRound() {
        assertNull(DataReply.dimension("[1.0d, 2.0d, 3.0d]"));
        assertNull(DataReply.position("\"minecraft:the_end\""));
    }

    @Test
    void thePrintedNameIsMatchedWholeNotByPieces() {
        assertTrue(DataReply.isPlayer("Notkiahh", "Notkiahh"));
        assertTrue(DataReply.isPlayer("notkiahh", "Notkiahh"));
        assertTrue(DataReply.isPlayer("Notkiahh", "NOTKIAHH"));
        assertFalse(DataReply.isPlayer("Bobby", "Bob"));
        assertFalse(DataReply.isPlayer("Bob", "Bobby"));
        assertFalse(DataReply.isPlayer("Bob_2", "Bob"));
        assertFalse(DataReply.isPlayer("Notkiahh", "Notkia"));
    }

    @Test
    void aTeamPrefixOrSuffixDoesNotHideThePlayer() {
        assertTrue(DataReply.isPlayer("[VIP] Notkiahh", "Notkiahh"));
        assertTrue(DataReply.isPlayer("[VIP] Notkiahh [Lv5]", "Notkiahh"));
        assertTrue(DataReply.isPlayer("Red | Notkiahh", "Notkiahh"));
    }

    @Test
    void nothingMatchesNothing() {
        assertFalse(DataReply.isPlayer(null, "Bob"));
        assertFalse(DataReply.isPlayer("Bob", null));
        assertFalse(DataReply.isPlayer("", "Bob"));
        assertFalse(DataReply.isPlayer("Bob", ""));
        assertFalse(DataReply.isPlayer("[]", ""));
    }
}
