package dev.urinth.recordable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ReportAndSessionHelpersTest {
    @Test
    void aReportIsWrittenReadBackAndSummarisedWithoutTheLongTechnicalLines() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Path file = Report.fileFor(dir);
            assertTrue(file.startsWith(dir.resolve("logs")), "next to the game's log");
            Report r = new Report(file);
            r.addAll(Report.environment(dir));
            r.add("settings: { \"a\": 1 }");
            r.add("output folder: /storage/emulated/0/Movies/Record-able (phone storage)");
            r.add("video encoder search: default/NV12@90: c2.mtk.avc.encoder works");
            r.add("result: Saved: x.mp4 (0:10, 30 MB)");
            r.write();
            assertTrue(Files.isRegularFile(file));
            String text = Files.readString(file);
            assertTrue(text.contains("output folder: /storage/emulated/0/Movies/Record-able"));
            List<String> chat = Report.summary(file);
            assertTrue(chat.stream().anyMatch(l -> l.startsWith("video encoder search")), chat.toString());
            assertTrue(chat.stream().anyMatch(l -> l.startsWith("result:")), chat.toString());
            assertFalse(chat.stream().anyMatch(l -> l.startsWith("settings:") || l.startsWith("java.home:")), chat.toString());
            assertTrue(chat.get(chat.size() - 1).startsWith("Full report: "));
            assertTrue(Report.summary(dir.resolve("none.txt")).get(0).contains("No recording"));
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    @Test
    void sizesToTryStartAtTheWantedOneAndGoDown() {
        assertEquals(List.of(720, 480, 360), list(RecorderSession.heightsFrom(720)));
        assertEquals(List.of(1080, 720, 480, 360), list(RecorderSession.heightsFrom(1080)));
        assertEquals(List.of(360), list(RecorderSession.heightsFrom(360)));
        assertEquals(List.of(540, 480, 360), list(RecorderSession.heightsFrom(540)));
    }

    @Test
    void moreFramesInFlightAndMoreBuffersAreOnlyUsedAtHighRates() {
        assertEquals(3, RecorderSession.ringFor(30));
        assertEquals(3, RecorderSession.ringFor(45));
        assertEquals(6, RecorderSession.ringFor(60));
        assertEquals(6, RecorderSession.ringFor(90));
        int small = 848 * 480 * 3 / 2;
        assertEquals(6, RecorderSession.poolFrames(30, small));
        assertEquals(10, RecorderSession.poolFrames(90, small));
        int fullHd = 1920 * 1088 * 3 / 2;
        assertEquals(10, RecorderSession.poolFrames(90, fullHd), "a full HD picture is small enough for the full pool");
        int huge = 3840 * 2160 * 3 / 2;
        int pool = RecorderSession.poolFrames(90, huge);
        assertEquals(3, pool, "the memory budget limits the pool for huge pictures, but never below 3");
    }

    @Test
    void longTextIsCutForTheChat() {
        assertEquals("short", RecorderSession.shorten("short", 20));
        assertEquals("a b c", RecorderSession.shorten("a\n  b \t c", 20));
        String cut = RecorderSession.shorten("x".repeat(300), 50);
        assertEquals(50, cut.length());
        assertTrue(cut.endsWith("..."));
        assertEquals("", RecorderSession.shorten(null, 10));
    }

    @Test
    void theHelperFoldersIncludeTheGameFolderAndNeverFail() {
        Path[] folders = RecorderSession.nativeFolders(Path.of("game"));
        assertTrue(folders.length >= 2);
        assertTrue(Settings.configDir(Path.of("game")).resolve("native").equals(folders[folders.length - 1]));
    }

    private static List<Integer> list(int[] a) {
        java.util.ArrayList<Integer> l = new java.util.ArrayList<>();
        for (int v : a) {
            l.add(v);
        }
        return l;
    }
}
