package net.kdt.pojavlaunch.prefs.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SystemInfoTextTest {
    @Test
    public void everyFactIsOnItsOwnLine() {
        String text = SystemInfoText.build("1.2.3 (build 45)", "Infinix Smart 8", "14 (API 34)", "arm64-v8a",
                2700, 1200, "Mali-G57 MC2 (ARM)", 3, "Vulkan 1.1", "Zink [vulkan_zink]", "Vulkan", true);
        String[] lines = text.split("\n");
        assertEquals(11, lines.length);
        assertEquals("Launcher: 1.2.3 (build 45)", lines[0]);
        assertEquals("Device: Infinix Smart 8", lines[1]);
        assertEquals("RAM: 2700 MB in the phone, 1200 MB set for the game", lines[4]);
        assertEquals("Graphics chip: Mali-G57 MC2 (ARM)", lines[5]);
        assertEquals("OpenGL ES: 3", lines[6]);
        assertEquals("Vulkan: Vulkan 1.1", lines[7]);
        assertEquals("Renderer: Zink [vulkan_zink]", lines[8]);
        assertEquals("Graphics API: Vulkan", lines[9]);
        assertEquals("Performance Mode: on", lines[10]);
    }

    @Test
    public void missingFactsReadAsUnknownInsteadOfNull() {
        String text = SystemInfoText.build(null, "  ", "14", null, 0, 0, "", 0, null, null, "OpenGL", false);
        assertFalse(text.contains("null"));
        assertTrue(text.contains("Launcher: unknown\n"));
        assertTrue(text.contains("Device: unknown\n"));
        assertTrue(text.contains("CPU ABI: unknown\n"));
        assertTrue(text.contains("Graphics chip: unknown\n"));
        assertTrue(text.contains("OpenGL ES: unknown\n"));
        assertTrue(text.contains("Vulkan: not reported\n"));
        assertTrue(text.contains("Performance Mode: off\n"));
    }

    @Test
    public void lineBreaksInsideAValueCannotSplitTheReport() {
        String text = SystemInfoText.build("a\nb", "d", "x", "y", 1, 1, "g\r\nh", 2, "", "r", "OpenGL", false);
        assertEquals(11, text.split("\n").length);
        assertTrue(text.contains("Launcher: a b\n"));
        assertTrue(text.contains("Graphics chip: g  h\n"));
        assertTrue(text.contains("Vulkan: not reported\n"));
    }
}
