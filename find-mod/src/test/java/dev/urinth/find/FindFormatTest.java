package dev.urinth.find;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FindFormatTest {
    @Test
    void keepsTwoDecimalsWhenTheyMatter() {
        assertEquals("128.30", FindFormat.coordinate(128.3));
        assertEquals("-1073.50", FindFormat.coordinate(-1073.5));
        assertEquals("0.07", FindFormat.coordinate(0.07));
        assertEquals("64.25", FindFormat.coordinate(64.25));
    }

    @Test
    void dropsAWholeNumbersDecimals() {
        assertEquals("63", FindFormat.coordinate(63.0));
        assertEquals("-1073", FindFormat.coordinate(-1073.0));
        assertEquals("63", FindFormat.coordinate(63.004));
        assertEquals("1074", FindFormat.coordinate(1073.996));
        assertEquals("-64", FindFormat.coordinate(-63.996));
    }

    @Test
    void neverShowsMinusZero() {
        assertEquals("0", FindFormat.coordinate(0.0));
        assertEquals("0", FindFormat.coordinate(-0.0));
        assertEquals("0", FindFormat.coordinate(-0.004));
        assertEquals("0", FindFormat.coordinate(0.004));
        assertEquals("-0.01", FindFormat.coordinate(-0.005));
    }

    @Test
    void bigNumbersStayPlainDigits() {
        assertEquals("30000000", FindFormat.coordinate(3.0E7));
        assertEquals("-29999999.50", FindFormat.coordinate(-29999999.5));
    }

    @Test
    void numbersThatAreNotNumbersGiveAQuestionMark() {
        assertEquals("?", FindFormat.coordinate(Double.NaN));
        assertEquals("?", FindFormat.coordinate(Double.POSITIVE_INFINITY));
        assertEquals("?", FindFormat.coordinate(Double.NEGATIVE_INFINITY));
    }

    @Test
    void theExampleFromTheRequest() {
        assertEquals("XYZ: x128.30 y63 z-1073", FindFormat.xyz(128.3, 63.0, -1073.0));
        assertEquals("x128.30 y63 z-1073", FindFormat.position(128.3, 63.0, -1073.0));
    }

    @Test
    void normalDimensionsHaveShortNames() {
        assertEquals("Overworld", FindFormat.dimensionName("minecraft:overworld"));
        assertEquals("Nether", FindFormat.dimensionName("minecraft:the_nether"));
        assertEquals("End", FindFormat.dimensionName("minecraft:the_end"));
        assertEquals("Overworld", FindFormat.dimensionName("  Minecraft:Overworld "));
        assertEquals("Nether", FindFormat.dimensionName("the_nether"));
    }

    @Test
    void otherDimensionsGetANameFromTheirId() {
        assertEquals("Deep Dark Plane", FindFormat.dimensionName("mymod:deep_dark_plane"));
        assertEquals("Twilight Forest", FindFormat.dimensionName("twilightforest:twilight_forest"));
        assertEquals("Mining", FindFormat.dimensionName("rftools:mining"));
        assertEquals("Moon", FindFormat.dimensionName("space/moon"));
        assertEquals("Sky Island", FindFormat.dimensionName("pack:sky-island"));
    }

    @Test
    void unknownIdsAreNamedUnknown() {
        assertEquals("Unknown", FindFormat.dimensionName(null));
        assertEquals("Unknown", FindFormat.dimensionName(""));
        assertEquals("Unknown", FindFormat.dimensionName("minecraft:"));
        assertEquals("Unknown", FindFormat.dimensionName("mod:___"));
    }

    @Test
    void onlyTheRealDimensionsCountAsNormal() {
        assertTrue(FindFormat.isOverworld("minecraft:overworld"));
        assertTrue(FindFormat.isNether("minecraft:the_nether"));
        assertTrue(FindFormat.isEnd("minecraft:the_end"));
        assertFalse(FindFormat.isOverworld("mymod:overworld"));
        assertFalse(FindFormat.isNether("mymod:the_nether"));
        assertFalse(FindFormat.isEnd(null));
    }

    @Test
    void cutNeverMakesATextLongerOrNull() {
        assertEquals("", FindFormat.cut(null, 5));
        assertEquals("abc", FindFormat.cut("abc", 5));
        assertEquals("abcde", FindFormat.cut("abcdefgh", 5));
        assertEquals("", FindFormat.cut("abc", 0));
    }
}
