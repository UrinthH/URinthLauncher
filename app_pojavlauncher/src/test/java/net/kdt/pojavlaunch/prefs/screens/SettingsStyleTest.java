package net.kdt.pojavlaunch.prefs.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.kdt.pojavlaunch.prefs.screens.SettingsStyle.RowPosition;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class SettingsStyleTest {
    @Test
    public void everyAccentHasAnIdAndAColourAndTheListsLineUp() {
        assertEquals(SettingsStyle.ACCENT_IDS.length, SettingsStyle.ACCENT_COLORS.length);
        Set<String> ids = new HashSet<>();
        Set<Integer> colours = new HashSet<>();
        for (int i = 0; i < SettingsStyle.ACCENT_IDS.length; i++) {
            ids.add(SettingsStyle.ACCENT_IDS[i]);
            colours.add(SettingsStyle.ACCENT_COLORS[i]);
            assertEquals(SettingsStyle.ACCENT_COLORS[i], SettingsStyle.accentColor(SettingsStyle.ACCENT_IDS[i]));
        }
        assertEquals(SettingsStyle.ACCENT_IDS.length, ids.size());
        assertEquals(SettingsStyle.ACCENT_COLORS.length, colours.size());
        assertTrue(ids.contains(SettingsStyle.DEFAULT_ACCENT));
    }

    @Test
    public void anUnknownOrMissingAccentGivesTheDefault() {
        assertEquals(SettingsStyle.DEFAULT_ACCENT, SettingsStyle.accentIdOrDefault(null));
        assertEquals(SettingsStyle.DEFAULT_ACCENT, SettingsStyle.accentIdOrDefault("hotpink"));
        assertEquals("ocean", SettingsStyle.accentIdOrDefault("ocean"));
        assertEquals(SettingsStyle.accentColor(SettingsStyle.DEFAULT_ACCENT), SettingsStyle.accentColor("nope"));
    }

    @Test
    public void everyAccentIsReadableOnTheWhiteCards() {
        // WCAG asks 3:1 for the parts of a control that must be seen (switch, slider, icon).
        for (int color : SettingsStyle.ACCENT_COLORS) {
            assertTrue(SettingsStyle.contrast(color, SettingsStyle.CARD) >= 3.0);
        }
        assertTrue(SettingsStyle.contrast(SettingsStyle.TEXT, SettingsStyle.CARD) >= 7.0);
        assertTrue(SettingsStyle.contrast(SettingsStyle.MUTED, SettingsStyle.CARD) >= 4.5);
        assertTrue(SettingsStyle.contrast(SettingsStyle.HEADING, SettingsStyle.PAGE) >= 4.5);
        assertTrue(SettingsStyle.contrast(SettingsStyle.DANGER, SettingsStyle.CARD) >= 4.5);
    }

    @Test
    public void rowsKnowTheirPlaceInAGroup() {
        assertEquals(RowPosition.ALONE, SettingsStyle.rowPosition(false, false));
        assertEquals(RowPosition.FIRST, SettingsStyle.rowPosition(false, true));
        assertEquals(RowPosition.MIDDLE, SettingsStyle.rowPosition(true, true));
        assertEquals(RowPosition.LAST, SettingsStyle.rowPosition(true, false));
        assertTrue(SettingsStyle.hasTopEdge(RowPosition.FIRST));
        assertTrue(!SettingsStyle.hasTopEdge(RowPosition.MIDDLE));
        assertTrue(SettingsStyle.hasBottomEdge(RowPosition.LAST));
        assertTrue(!SettingsStyle.hasBottomEdge(RowPosition.FIRST));
    }

    @Test
    public void onlyTheOuterCornersOfAGroupAreRounded() {
        float[] first = SettingsStyle.corners(RowPosition.FIRST, 16f);
        assertEquals(16f, first[0], 0f);
        assertEquals(0f, first[4], 0f);
        float[] last = SettingsStyle.corners(RowPosition.LAST, 16f);
        assertEquals(0f, last[0], 0f);
        assertEquals(16f, last[6], 0f);
        float[] alone = SettingsStyle.corners(RowPosition.ALONE, 16f);
        for (float v : alone) assertEquals(16f, v, 0f);
        float[] middle = SettingsStyle.corners(RowPosition.MIDDLE, 16f);
        for (float v : middle) assertEquals(0f, v, 0f);
        assertEquals(8, first.length);
    }

    @Test
    public void colourHelpersKeepTheirContracts() {
        assertEquals(0x80123456, SettingsStyle.withAlpha(0xFF123456, 0x80));
        assertEquals(0x00123456, SettingsStyle.withAlpha(0xFF123456, -5));
        assertEquals(0xFF123456, SettingsStyle.withAlpha(0x00123456, 999));
        assertEquals(0xFFFFFFFF, SettingsStyle.mix(0xFFFFFFFF, 0xFF000000, 0f));
        assertEquals(0xFF000000, SettingsStyle.mix(0xFFFFFFFF, 0xFF000000, 1f));
        assertEquals(0xFF808080, SettingsStyle.mix(0xFFFFFFFF, 0xFF000000, 0.5f));
        assertEquals(0xFF000000, SettingsStyle.mix(0xFFFFFFFF, 0xFF000000, 7f));
    }

    @Test
    public void contrastOfBlackAndWhiteIsTwentyOne() {
        assertEquals(21.0, SettingsStyle.contrast(0xFF000000, 0xFFFFFFFF), 0.01);
        assertEquals(1.0, SettingsStyle.contrast(0xFF777777, 0xFF777777), 0.0001);
    }
}
