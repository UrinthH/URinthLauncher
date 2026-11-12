package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OptimizerScreensTest {
    @Test
    void findsTheVideoSettingsScreens() {
        assertEquals(OptimizerScreens.Kind.VANILLA, OptimizerScreens.classify("net.minecraft.client.gui.screens.options.VideoSettingsScreen"));
        assertEquals(OptimizerScreens.Kind.SODIUM, OptimizerScreens.classify("me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI"));
        assertEquals(OptimizerScreens.Kind.SODIUM, OptimizerScreens.classify("net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"));
        assertEquals(OptimizerScreens.Kind.NONE, OptimizerScreens.classify("net.minecraft.client.gui.screens.options.OptionsScreen"));
        assertEquals(OptimizerScreens.Kind.NONE, OptimizerScreens.classify("net.caffeinemc.mods.sodium.client.gui.options.OptionPage"));
        assertEquals(OptimizerScreens.Kind.NONE, OptimizerScreens.classify(null));
    }

    @Test
    void theVanillaButtonIsInTheBottomLeftCornerAndNeverUnderTheDoneButton() {
        for (int width = 230; width <= 1200; width += 10) {
            int[] b = OptimizerScreens.bounds(OptimizerScreens.Kind.VANILLA, width, 240);
            if (b == null) continue;
            int doneLeft = (width - 200) / 2;
            assertTrue(b[0] + b[2] <= doneLeft, "width " + width + ": button ends at " + (b[0] + b[2]) + ", Done starts at " + doneLeft);
            assertEquals(240 - 26, b[1]);
            assertTrue(b[2] >= 54 && b[2] <= 118);
        }
    }

    @Test
    void aNarrowVanillaScreenHasNoRoomSoNoButton() {
        assertNull(OptimizerScreens.bounds(OptimizerScreens.Kind.VANILLA, 260, 200));
        assertNotNull(OptimizerScreens.bounds(OptimizerScreens.Kind.VANILLA, 480, 270));
    }

    @Test
    void theSodiumButtonIsTheSixthRowOfTheRightColumn() {
        int[] b = OptimizerScreens.bounds(OptimizerScreens.Kind.SODIUM, 640, 360);
        assertEquals(640 - 112 - 6, b[0]);
        assertEquals(30 + 22 * 5, b[1]);
        assertNull(OptimizerScreens.bounds(OptimizerScreens.Kind.SODIUM, 640, 120));
    }

    @Test
    void otherScreensGetNothing() {
        assertNull(OptimizerScreens.bounds(OptimizerScreens.Kind.NONE, 640, 360));
    }

    @Test
    void theLabelIsShortWhenTheButtonIsNarrow() {
        assertEquals("URinth Optimizer", OptimizerScreens.buttonLabel(118));
        assertEquals("Optimizer", OptimizerScreens.buttonLabel(86));
    }

    @Test
    void theSettingsScreenFitsEveryButtonInsideTheScreenAboveTheDoneButton() {
        int[][] sizes = {{320, 200}, {400, 225}, {480, 270}, {640, 360}, {854, 480}, {1280, 720}, {220, 260}};
        for (int[] size : sizes) {
            int[][] cells = OptimizerScreens.grid(size[0], size[1], 10);
            assertEquals(10, cells.length);
            for (int i = 0; i < cells.length; i++) {
                int[] c = cells[i];
                assertTrue(c[0] >= 0 && c[0] + c[2] <= size[0], size[0] + "x" + size[1] + " cell " + i + " is inside sideways");
                assertTrue(c[1] >= 40, "below the title and the status lines");
                if (size[0] >= 320) assertTrue(c[1] + c[3] <= size[1] - 30, size[0] + "x" + size[1] + " cell " + i + " ends at " + (c[1] + c[3]));
                for (int j = 0; j < i; j++) {
                    int[] d = cells[j];
                    boolean apart = c[0] + c[2] <= d[0] || d[0] + d[2] <= c[0] || c[1] + c[3] <= d[1] || d[1] + d[3] <= c[1];
                    assertTrue(apart, "cells " + i + " and " + j + " overlap at " + size[0] + "x" + size[1]);
                }
            }
        }
    }

    @Test
    void aLongTextIsWrappedAtSpacesToTheWidth() {
        assertEquals(java.util.List.of("one two", "three", "four"), OptimizerScreens.wrap("one two three four", 9, String::length));
        assertEquals(java.util.List.of("one two three four"), OptimizerScreens.wrap("one two three four", 100, String::length));
        assertEquals(java.util.List.of(), OptimizerScreens.wrap("", 20, String::length));
        assertEquals(java.util.List.of(), OptimizerScreens.wrap("   ", 20, String::length));
        assertEquals(java.util.List.of("a b", "c"), OptimizerScreens.wrap("a   b  c", 4, String::length));
    }

    @Test
    void aWordWiderThanTheLineStaysWhole() {
        assertEquals(java.util.List.of("supercalifragilistic", "is", "long"),
            OptimizerScreens.wrap("supercalifragilistic is long", 5, String::length));
    }

    @Test
    void wrappedLinesNeverGoOverTheWidthExceptSingleWords() {
        String text = "Your render distance in Video Settings is never changed. Only a memory emergency (Memory guard: Full)"
            + " shortens the world around you for a while.";
        for (int width : new int[] {40, 80, 150, 300, 600}) {
            for (String line : OptimizerScreens.wrap(text, width, s -> s.length() * 6)) {
                assertTrue(line.length() * 6 <= width || !line.contains(" "), "'" + line + "' at " + width);
            }
            assertEquals(text, String.join(" ", OptimizerScreens.wrap(text, width, s -> s.length() * 6)));
        }
    }

    @Test
    void widerScreensUseMoreColumns() {
        int[][] narrow = OptimizerScreens.grid(240, 400, 9);
        int[][] middle = OptimizerScreens.grid(320, 240, 9);
        int[][] wide = OptimizerScreens.grid(1000, 240, 9);
        assertTrue(narrow[1][0] == narrow[0][0], "one column when narrow: the second button is below the first");
        assertTrue(middle[1][0] > middle[0][0] && middle[2][0] == middle[0][0], "two columns");
        assertTrue(wide[2][0] > wide[1][0] && wide[3][0] == wide[0][0], "three columns when wide");
    }
}
