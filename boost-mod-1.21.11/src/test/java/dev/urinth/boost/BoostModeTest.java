package dev.urinth.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BoostModeTest {
    @Test
    public void parsesEveryWordInAnyCaseWithSpaces() {
        assertEquals(BoostMode.AUTO, BoostMode.parse("auto"));
        assertEquals(BoostMode.OFF, BoostMode.parse("OFF"));
        assertEquals(BoostMode.LIGHT, BoostMode.parse("  Light "));
        assertEquals(BoostMode.STRONG, BoostMode.parse("strong"));
        assertEquals(BoostMode.MAX, BoostMode.parse("MaX"));
    }

    @Test
    public void unknownWordsAndNullGiveNothing() {
        assertNull(BoostMode.parse(null));
        assertNull(BoostMode.parse(""));
        assertNull(BoostMode.parse("turbo"));
        assertEquals(BoostMode.AUTO, BoostMode.parseOr("turbo", BoostMode.AUTO));
        assertEquals(BoostMode.MAX, BoostMode.parseOr("max", BoostMode.AUTO));
    }

    @Test
    public void onlyAutoDecidesByItself() {
        assertTrue(BoostMode.AUTO.isAuto());
        assertFalse(BoostMode.OFF.isAuto());
        assertEquals(0, BoostMode.OFF.fixedLevel());
        assertEquals(1, BoostMode.LIGHT.fixedLevel());
        assertEquals(2, BoostMode.STRONG.fixedLevel());
        assertEquals(3, BoostMode.MAX.fixedLevel());
    }

    @Test
    public void levelNamesMatchTheModeWords() {
        for (BoostMode mode : BoostMode.values()) {
            if (!mode.isAuto()) assertEquals(mode.word(), BoostLevel.name(mode.fixedLevel()));
        }
        assertEquals("off", BoostLevel.name(-3));
        assertEquals("max", BoostLevel.name(42));
    }
}
