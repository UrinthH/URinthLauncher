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
    private static String excerpt(String log, int head, int tail) throws IOException {
        return GameLogExcerpt.fromReader(new BufferedReader(new StringReader(log)), head, tail);
    }

    private static String lines(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= count; i++) text.append("line ").append(i).append('\n');
        return text.toString();
    }

    @Test
    public void aShortLogIsKeptWhole() throws IOException {
        assertEquals("line 1\nline 2\nline 3\n", excerpt(lines(3), 5, 5));
    }

    @Test
    public void aLogExactlyAsLongAsTheStartAndTheEndIsKeptWhole() throws IOException {
        assertEquals(lines(10), excerpt(lines(10), 5, 5));
    }

    @Test
    public void aLongLogKeepsTheStartAndTheEndAndSaysHowManyLinesWereLeftOut() throws IOException {
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
    public void aVeryLongLineIsCut() throws IOException {
        StringBuilder longLine = new StringBuilder("start ");
        for (int i = 0; i < 1000; i++) longLine.append('x');
        String text = excerpt(longLine + "\n", 5, 5);
        assertTrue(text.startsWith("start xxxx"));
        assertTrue(text.endsWith(" ... [cut]\n"));
        assertTrue(text.length() < GameLogExcerpt.MAX_LINE_CHARS + 30);
    }

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

    @Test
    public void readsALogFileAndGivesNothingForAMissingOrEmptyOne() throws IOException {
        File dir = File.createTempFile("excerpt", "dir");
        assertTrue(dir.delete());
        assertTrue(dir.mkdirs());
        try {
            File log = new File(dir, "latestlog.txt");
            try (FileWriter writer = new FileWriter(log)) {
                writer.write(lines(100));
            }
            assertEquals("line 1\n... 98 lines left out ...\nline 100\n", GameLogExcerpt.fromFile(log, 1, 1));

            File empty = new File(dir, "empty.txt");
            assertTrue(empty.createNewFile());
            assertNull(GameLogExcerpt.fromFile(empty, 5, 5));
            assertNull(GameLogExcerpt.fromFile(new File(dir, "missing.txt"), 5, 5));
            assertNull(GameLogExcerpt.fromFile(null, 5, 5));
        } finally {
            File[] left = dir.listFiles();
            if (left != null) for (File file : left) file.delete();
            dir.delete();
        }
    }
}
