package net.kdt.pojavlaunch.downloads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FormattersTest {
    @Test
    public void numbersReadLikeModrinths() {
        assertEquals("0", Formatters.compactNumber(0));
        assertEquals("999", Formatters.compactNumber(999));
        assertEquals("1K", Formatters.compactNumber(1000));
        assertEquals("37.3K", Formatters.compactNumber(37_300));
        assertEquals("292.39M", Formatters.compactNumber(292_390_000L));
        assertEquals("1.5M", Formatters.compactNumber(1_500_000L));
        assertEquals("2B", Formatters.compactNumber(2_000_000_000L));
        assertEquals("0", Formatters.compactNumber(-5));
    }

    @Test
    public void numbersJustBelowAStepDoNotShowAThousandK() {
        assertEquals("1M", Formatters.compactNumber(999_999));
        assertEquals("1B", Formatters.compactNumber(999_999_999));
    }

    @Test
    public void isoTimesAreParsedToUtcMillis() {
        assertEquals(0L, Formatters.parseIsoMillis("1970-01-01T00:00:00Z"));
        assertEquals(86_400_000L, Formatters.parseIsoMillis("1970-01-02T00:00:00.123456Z"));
        // 2026-10-28T10:12:45Z
        assertEquals(1_793_182_365_000L, Formatters.parseIsoMillis("2026-10-28T10:12:45.5Z"));
        assertEquals(951_782_400_000L, Formatters.parseIsoMillis("2000-02-29"));
        assertEquals(-1L, Formatters.parseIsoMillis(""));
        assertEquals(-1L, Formatters.parseIsoMillis(null));
        assertEquals(-1L, Formatters.parseIsoMillis("not a date"));
        assertEquals(-1L, Formatters.parseIsoMillis("2026-13-01T00:00:00Z"));
    }

    @Test
    public void relativeTimeUsesModrinthsWords() {
        long now = Formatters.parseIsoMillis("2026-10-29T12:00:00Z");
        assertEquals("Just now", Formatters.relativeTime("2026-10-29T11:59:30Z", now));
        assertEquals("5 minutes ago", Formatters.relativeTime("2026-10-29T11:55:00Z", now));
        assertEquals("1 minute ago", Formatters.relativeTime("2026-10-29T11:59:00Z", now));
        assertEquals("3 hours ago", Formatters.relativeTime("2026-10-29T09:00:00Z", now));
        assertEquals("Yesterday", Formatters.relativeTime("2026-10-28T08:00:00Z", now));
        assertEquals("4 days ago", Formatters.relativeTime("2026-10-25T12:00:00Z", now));
        assertEquals("2 weeks ago", Formatters.relativeTime("2026-10-15T12:00:00Z", now));
        assertEquals("3 months ago", Formatters.relativeTime("2026-07-20T12:00:00Z", now));
        assertEquals("1 year ago", Formatters.relativeTime("2025-09-01T12:00:00Z", now));
        assertEquals("2 years ago", Formatters.relativeTime("2024-06-01T12:00:00Z", now));
        assertEquals("", Formatters.relativeTime("garbage", now));
    }

    @Test
    public void tagLabelsAreReadable() {
        assertEquals("NeoForge", Formatters.tagLabel("neoforge"));
        assertEquals("Fabric", Formatters.tagLabel("fabric"));
        assertEquals("Library", Formatters.tagLabel("library"));
        assertEquals("Worldgen", Formatters.tagLabel("worldgen"));
        assertEquals("Game mechanics", Formatters.tagLabel("game_mechanics"));
        assertEquals("GUI", Formatters.tagLabel("gui"));
        assertEquals("Data Pack", Formatters.tagLabel("datapack"));
        assertEquals("", Formatters.tagLabel(null));
        assertEquals("", Formatters.tagLabel(""));
    }

    @Test
    public void loadersAreTold_apart_fromCategories() {
        assertTrue(Formatters.isLoader("fabric"));
        assertTrue(Formatters.isLoader("Paper"));
        assertTrue(Formatters.isLoader("iris"));
        assertFalse(Formatters.isLoader("library"));
        assertFalse(Formatters.isLoader(null));
    }

    @Test
    public void environmentWording() {
        assertEquals("Client or server", Formatters.environmentLabel("required", "optional"));
        assertEquals("Client", Formatters.environmentLabel("required", "unsupported"));
        assertEquals("Server", Formatters.environmentLabel("unsupported", "required"));
        assertEquals("", Formatters.environmentLabel("unsupported", "unsupported"));
        assertEquals("", Formatters.environmentLabel(null, null));
        assertEquals("", Formatters.environmentLabel("", ""));
    }
}
