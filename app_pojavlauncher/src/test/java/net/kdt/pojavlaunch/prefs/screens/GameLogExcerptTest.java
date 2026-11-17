package net.kdt.pojavlaunch.prefs.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;

public class GameLogExcerptTest {
    private static final int BIG = 1_000_000;

    private static String excerpt(String log, int head, int keys, int tail, int budget) throws IOException {
        return GameLogExcerpt.fromReader(new BufferedReader(new StringReader(log)), head, keys, tail, budget);
    }

    private static String excerpt(String log, int head, int tail) throws IOException {
        return excerpt(log, head, 10, tail, BIG);
    }

    private static String lines(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= count; i++) text.append("line ").append(i).append('\n');
        return text.toString();
    }

    // ---- what is kept ------------------------------------------------------------------------

    @Test
    public void aShortLogIsKeptWhole() throws IOException {
        assertEquals("line 1\nline 2\nline 3\n", excerpt(lines(3), 5, 5));
    }

    @Test
    public void aLogExactlyAsLongAsTheStartAndTheEndIsKeptWhole() throws IOException {
        assertEquals(lines(10), excerpt(lines(10), 5, 5));
    }

    @Test
    public void aLongLogWithNothingTellingKeepsTheStartAndTheEndAndSaysHowManyLinesWereLeftOut() throws IOException {
        assertEquals("line 1\nline 2\nline 3\n... 95 lines left out ...\nline 99\nline 100\n",
                excerpt(lines(100), 3, 2));
    }

    @Test
    public void oneLeftOutLineIsNotCalledLines() throws IOException {
        assertEquals("line 1\n... 1 line left out ...\nline 3\n", excerpt(lines(3), 1, 1));
    }

    @Test
    public void anEmptyLogGivesNothing() throws IOException {
        assertNull(excerpt("", 5, 5));
    }

    @Test
    public void tellingLinesFromTheMiddleAreKeptWithTheirLineNumbers() throws IOException {
        StringBuilder log = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            if (i == 20) log.append("WARNING: Some incorrect rendering might occur (Mali-G57)\n");
            else if (i == 30) log.append("[10:35:03] [Render thread/INFO]: OpenGL Renderer: Mali-G57 (Vulkan 1.3)\n");
            else log.append("line ").append(i).append('\n');
        }
        String text = excerpt(log.toString(), 3, 10, 3, BIG);
        assertTrue(text, text.startsWith("line 1\nline 2\nline 3\n--- lines from the middle"));
        assertTrue(text, text.contains("#20 WARNING: Some incorrect rendering might occur (Mali-G57)\n"));
        assertTrue(text, text.contains("#30 [10:35:03] [Render thread/INFO]: OpenGL Renderer: Mali-G57 (Vulkan 1.3)\n"));
        assertTrue(text, text.contains("--- end of the log (60 lines in all) ---\nline 58\nline 59\nline 60\n"));
        assertTrue(text, text.indexOf("#20 ") < text.indexOf("#30 "));
    }

    @Test
    public void theBigCoreLinesFromTheLauncherAndTheNativeLibraryAreKept() throws IOException {
        StringBuilder log = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            if (i == 15) log.append("[BigCore] core speeds: cpu0-7 1612 MHz; capacities: cpu0-5 485, cpu6-7 1024; the game library picks cpu7\n");
            else if (i == 25) log.append("bigcore: big CPU number is 7, frequency 1612000 Hz\n");
            else if (i == 26) log.append("bigcore: forced current thread onto big core\n");
            else log.append("line ").append(i).append('\n');
        }
        String text = excerpt(log.toString(), 3, 10, 3, BIG);
        assertTrue(text, text.contains("#15 [BigCore] core speeds: cpu0-7 1612 MHz"));
        assertTrue(text, text.contains("#25 bigcore: big CPU number is 7"));
        assertTrue(text, text.contains("#26 bigcore: forced current thread onto big core"));
    }

    @Test
    public void theMobileGluesSettingsAndRendererChoiceLinesAreKept() throws IOException {
        StringBuilder log = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            if (i == 19) log.append("[PerformanceMode] renderer: MobileGlues (plugin found), instead of LTW\n");
            else if (i == 21) log.append("[MGConfig] OpenGL 4.6, ANGLE off if possible, errors auto, compute off, timer query on, DSA on, shader cache 32 MB, FSR1 off, hide env off, multi-draw automatic\n");
            else if (i == 22) log.append("[PerformanceMode] the game ended with error code -1 soon after it started with MobileGlues, so Performance Mode uses LTW\n");
            else log.append("line ").append(i).append('\n');
        }
        String text = excerpt(log.toString(), 3, 10, 3, BIG);
        assertTrue(text, text.contains("#19 [PerformanceMode] renderer: MobileGlues (plugin found)"));
        assertTrue(text, text.contains("#21 [MGConfig] OpenGL 4.6"));
        assertTrue(text, text.contains("#22 [PerformanceMode] the game ended with error code -1"));
    }

    // ---- the summary counted over the whole log ----------------------------------------------

    private static final String GC_YOUNG = "[71.235s][info   ][gc         ] GC(18) Pause Young (Mixed) (G1 Evacuation Pause) 424M->215M(512M) 94.223ms";
    private static final String GC_REMARK = "[74.663s][info   ][gc         ] GC(20) Pause Remark 279M->279M(512M) 234.224ms";
    private static final String GC_CLEANUP = "[75.081s][info   ][gc         ] GC(20) Pause Cleanup 318M->318M(512M) 1.936ms";
    private static final String GC_CYCLE_START = "[73.661s][info   ][gc         ] GC(20) Concurrent Mark Cycle";
    private static final String GC_CYCLE_END = "[75.092s][info   ][gc         ] GC(20) Concurrent Mark Cycle 1431.426ms";
    private static final String REFRESH = "[RefreshRate] the system lowered the screen to 60.0 Hz, asked for 90.0 Hz again (attempt 1)";

    @Test
    public void gcPausesAreCountedOverTheWholeLogEvenWhenTheirLinesAreCutOut() throws IOException {
        StringBuilder log = new StringBuilder();
        for (int i = 1; i <= 80; i++) {
            if (i == 20) log.append(GC_YOUNG).append('\n');
            else if (i == 25) log.append(GC_CYCLE_START).append('\n');
            else if (i == 30) log.append(GC_REMARK).append('\n');
            else if (i == 31) log.append(GC_CLEANUP).append('\n');
            else if (i == 32) log.append(GC_CYCLE_END).append('\n');
            else log.append("line ").append(i).append('\n');
        }
        String text = excerpt(log.toString(), 3, 0, 3, BIG);
        assertTrue(text, text.startsWith("--- summary counted over the whole log ---\nMemory (GC) pauses: 3 in 75 s, 0.3 s paused in total; "
                + "worst 234 ms (Remark); over 50 ms: 2, over 100 ms: 1, over 200 ms: 1; full collections: 0; "
                + "background cycles: 1 (longest 1431 ms)\n--- the log itself ---\nline 1\n"));
        assertFalse(text, text.contains("Pause Young")); // the lines themselves were left out of the middle
    }

    @Test
    public void aFullCollectionIsCountedAndItsLineIsKept() throws IOException {
        String full = "[90.100s][info   ][gc         ] GC(31) Pause Full (Metadata GC Threshold) 400M->300M(512M) 812.500ms";
        StringBuilder log = new StringBuilder();
        for (int i = 1; i <= 60; i++) log.append(i == 25 ? full : "line " + i).append('\n');
        String text = excerpt(log.toString(), 3, 10, 3, BIG);
        assertTrue(text, text.contains("worst 813 ms (Full)"));
        assertTrue(text, text.contains("full collections: 1"));
        assertTrue(text, text.contains("#25 " + full));
    }

    @Test
    public void refreshRateDropsAreCounted() throws IOException {
        StringBuilder log = new StringBuilder();
        for (int i = 1; i <= 50; i++) log.append(i % 10 == 0 ? REFRESH : "line " + i).append('\n');
        String text = excerpt(log.toString(), 3, 0, 3, BIG);
        assertTrue(text, text.contains("Screen refresh rate: the system lowered it 5 times (the launcher asked for it back each time)\n"));
        assertFalse(text, text.contains("Memory (GC) pauses"));
    }

    @Test
    public void aLogWithNothingToCountGetsNoSummary() throws IOException {
        String text = excerpt(lines(5), 10, 10);
        assertFalse(text, text.contains("summary counted"));
    }

    @Test
    public void aShortLogWithGcLinesIsKeptWholeUnderItsSummary() throws IOException {
        String log = "line 1\n" + GC_YOUNG + "\nline 3\n";
        String text = excerpt(log, 10, 10);
        assertEquals("--- summary counted over the whole log ---\n"
                + "Memory (GC) pauses: 1 in 71 s, 0.1 s paused in total; worst 94 ms (Young); over 50 ms: 1, over 100 ms: 0, over 200 ms: 0; full collections: 0\n"
                + "--- the log itself ---\n" + log, text);
    }

    @Test
    public void theSummaryDoesNotPushTheExcerptOverTheBudget() throws IOException {
        StringBuilder log = new StringBuilder();
        StringBuilder wide = new StringBuilder();
        for (int i = 0; i < 160; i++) wide.append('w');
        for (int i = 1; i <= 400; i++) log.append(i % 7 == 0 ? GC_YOUNG : "info " + wide + i).append('\n');
        String text = excerpt(log.toString(), 40, 40, 70, 8_000);
        assertTrue("length " + text.length(), text.length() <= 8_000);
        assertTrue(text, text.startsWith("--- summary counted over the whole log ---\nMemory (GC) pauses: 57"));
    }

    @Test
    public void aTellingLineThatIsAlreadyInTheEndIsNotShownTwice() throws IOException {
        String text = excerpt(lines(10) + "ERROR: boom\nline 12\n", 2, 10, 3, BIG);
        assertFalse(text, text.contains("#11"));
        assertEquals(1, text.split("ERROR: boom", -1).length - 1);
    }

    @Test
    public void linesThatOnlyDifferInNumbersAreShownOnce() throws IOException {
        StringBuilder log = new StringBuilder(lines(5));
        for (int i = 1; i <= 40; i++) {
            log.append("[RefreshRate] the system lowered the screen to 60.0 Hz, asked for 90.0 Hz again (attempt ")
                    .append(i % 3 + 1).append(")\n");
        }
        log.append(lines(20));
        String text = excerpt(log.toString(), 2, 10, 5, BIG);
        assertEquals(1, text.split("\\[RefreshRate\\]", -1).length - 1);
    }

    @Test
    public void onlyTheFirstFewTellingLinesAreKept() throws IOException {
        StringBuilder log = new StringBuilder(lines(3));
        for (int i = 1; i <= 50; i++) log.append("ERROR: problem number-").append((char) ('a' + i % 26)).append(i % 7 == 0 ? "x" : "y").append("-").append((char) ('A' + i % 20)).append('\n');
        log.append(lines(20));
        String text = excerpt(log.toString(), 2, 3, 4, BIG);
        long keyLines = 0;
        for (String line : text.split("\n")) if (line.startsWith("#")) keyLines++;
        assertTrue(text, keyLines <= 3);
    }

    // ---- stack traces and long lines ---------------------------------------------------------

    @Test
    public void aLongStackTraceInTheEndIsFolded() throws IOException {
        StringBuilder log = new StringBuilder(lines(10));
        log.append("[Render thread/ERROR]: Error while loading the narrator\n");
        log.append("com.mojang.text2speech.Narrator$InitializeException: Failed to load library flite\n");
        for (int i = 1; i <= 30; i++) log.append("\tat knot/com.example.Frame").append(i).append("(Frame.java:1)\n");
        log.append("Caused by: java.lang.UnsatisfiedLinkError: Unable to load library 'flite'\n");
        log.append("last line\n");
        String text = excerpt(log.toString(), 2, 0, 40, BIG);
        assertTrue(text, text.contains("\tat knot/com.example.Frame1(Frame.java:1)\n\tat knot/com.example.Frame2(Frame.java:1)\n\t... 28 more stack lines\nCaused by:"));
        assertFalse(text, text.contains("Frame3("));
        assertTrue(text, text.endsWith("last line\n"));
    }

    @Test
    public void aVeryLongLineIsCut() throws IOException {
        StringBuilder longLine = new StringBuilder("start ");
        for (int i = 0; i < 1000; i++) longLine.append('x');
        String text = excerpt(longLine + "\n", 5, 5);
        assertTrue(text.startsWith("start xxxx"));
        assertTrue(text.endsWith(" ... [cut]\n"));
        assertTrue(text.length() < GameLogExcerpt.MAX_LINE_CHARS + 30);
    }

    // ---- the size limit ----------------------------------------------------------------------

    @Test
    public void theExcerptStaysUnderTheBudgetAndKeepsTheEnd() throws IOException {
        StringBuilder log = new StringBuilder();
        StringBuilder wide = new StringBuilder();
        for (int i = 0; i < 160; i++) wide.append('w');
        for (int i = 1; i <= 400; i++) log.append(i % 5 == 0 ? "ERROR: " + wide + i : "info " + wide + i).append('\n');
        log.append("THE LAST LINE\n");
        String text = excerpt(log.toString(), 40, 40, 70, 6_000);
        assertTrue("length " + text.length(), text.length() <= 6_000);
        assertTrue(text, text.endsWith("THE LAST LINE\n"));
        assertTrue(text, text.startsWith("info www"));
    }

    @Test
    public void theDefaultSizeFitsInAChatMessage() throws IOException {
        StringBuilder log = new StringBuilder();
        StringBuilder wide = new StringBuilder();
        for (int i = 0; i < 400; i++) wide.append('w');
        for (int i = 1; i <= 3000; i++) log.append(i % 3 == 0 ? "ERROR: " + wide + i : "info " + wide + i).append('\n');
        String text = excerpt(log.toString(), GameLogExcerpt.HEAD_LINES, GameLogExcerpt.KEY_LINES,
                GameLogExcerpt.TAIL_LINES, GameLogExcerpt.BUDGET_CHARS);
        assertTrue("length " + text.length(), text.length() <= GameLogExcerpt.BUDGET_CHARS);
        assertTrue(text, text.endsWith("info " + wide.substring(0, GameLogExcerpt.MAX_LINE_CHARS - 5) + " ... [cut]\n")
                || text.endsWith("ERROR: " + wide.substring(0, GameLogExcerpt.MAX_LINE_CHARS - 7) + " ... [cut]\n"));
    }

    // ---- login tokens ------------------------------------------------------------------------

    @Test
    public void launchArgumentsWithATokenAreHidden() throws IOException {
        String text = excerpt("java --username Steve --accessToken abc.def_123 --uuid 1234 --version 1.21.4\n", 5, 5);
        assertFalse(text.contains("abc.def_123"));
        assertTrue(text.contains("--accessToken [hidden] --uuid 1234"));
    }

    @Test
    public void bearerHeadersAndWebTokensAreHidden() throws IOException {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N";
        String text = excerpt("auth Authorization: Bearer abcdefghijklmnopqrstuvwxyz0123\nid " + jwt + " done\n", 5, 5);
        assertFalse(text.contains("abcdefghijklmnopqrstuvwxyz0123"));
        assertFalse(text.contains(jwt));
        assertTrue(text.contains("Bearer [hidden]"));
        assertTrue(text.contains("id [hidden] done"));
    }

    @Test
    public void namedTokensAreHidden() throws IOException {
        String text = excerpt("accessToken=secret123 next\n{\"refresh_token\": \"zzz999\"}\nsessionId: s3cr3t\n", 5, 5);
        assertFalse(text.contains("secret123"));
        assertFalse(text.contains("zzz999"));
        assertFalse(text.contains("s3cr3t"));
        assertTrue(text.contains("accessToken=[hidden] next"));
    }

    @Test
    public void aTokenOnAKeptLineAtTheEndOfALongLogIsHiddenToo() throws IOException {
        String log = lines(50) + "last --accessToken endsecret\n";
        String text = excerpt(log, 2, 3);
        assertFalse(text.contains("endsecret"));
        assertTrue(text.endsWith("last --accessToken [hidden]\n"));
    }

    @Test
    public void aTokenOnATellingLineInTheMiddleIsHiddenToo() throws IOException {
        String text = excerpt(lines(10) + "ERROR: login failed --accessToken midsecret\n" + lines(30), 2, 10, 3, BIG);
        assertFalse(text.contains("midsecret"));
        assertTrue(text, text.contains("#11 ERROR: login failed --accessToken [hidden]"));
    }

    @Test
    public void aTokenThatStartsBeforeTheCutIsNeverHalfShown() throws IOException {
        StringBuilder padding = new StringBuilder();
        for (int i = 0; i < GameLogExcerpt.MAX_LINE_CHARS - 20; i++) padding.append('p');
        String text = excerpt(padding + " --accessToken SECRETSECRETSECRETSECRETSECRET\n", 5, 5);
        assertFalse(text.contains("SECRET"));
    }

    @Test
    public void textWithoutTokensIsLeftAlone() throws IOException {
        String line = "[Render thread/INFO]: Reloading ResourceManager: vanilla, fabric, urinth-boost";
        assertEquals(line + "\n", excerpt(line + "\n", 5, 5));
    }

    // ---- the file ----------------------------------------------------------------------------

    @Test
    public void readsALogFileAndGivesNothingForAMissingOrEmptyOne() throws IOException {
        File dir = File.createTempFile("excerpt", "dir");
        assertTrue(dir.delete());
        assertTrue(dir.mkdirs());
        try {
            File log = new File(dir, "latestlog.txt");
            try (FileWriter writer = new FileWriter(log)) {
                writer.write(lines(30));
            }
            assertEquals(lines(30), GameLogExcerpt.fromFile(log));

            File empty = new File(dir, "empty.txt");
            assertTrue(empty.createNewFile());
            assertNull(GameLogExcerpt.fromFile(empty));
            assertNull(GameLogExcerpt.fromFile(new File(dir, "missing.txt")));
            assertNull(GameLogExcerpt.fromFile(null));
        } finally {
            File[] left = dir.listFiles();
            if (left != null) for (File file : left) file.delete();
            dir.delete();
        }
    }
}
