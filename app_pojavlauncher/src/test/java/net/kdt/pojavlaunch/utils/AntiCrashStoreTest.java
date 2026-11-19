package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AntiCrashStoreTest {
    private static File freshDir() throws IOException {
        File dir = Files.createTempDirectory("anticrash").toFile();
        dir.deleteOnExit();
        return dir;
    }

    @Test
    public void nothingStoredMeansNothingRemembered() throws IOException {
        AntiCrashPolicy.State state = AntiCrashStore.read(freshDir());
        assertEquals(0, state.lowMemorySteps);
        assertFalse(state.jitFallback);
        assertEquals(0L, state.handledAtMs);
        assertEquals(0, state.allocationMb);
    }

    @Test
    public void whatIsWrittenIsReadBack() throws IOException {
        File dir = freshDir();
        AntiCrashStore.write(dir, new AntiCrashPolicy.State(2, true, 1_731_900_000_123L, 1024));
        AntiCrashPolicy.State state = AntiCrashStore.read(dir);
        assertEquals(2, state.lowMemorySteps);
        assertTrue(state.jitFallback);
        assertEquals(1_731_900_000_123L, state.handledAtMs);
        assertEquals(1024, state.allocationMb);
        // A second write replaces the first.
        AntiCrashStore.write(dir, new AntiCrashPolicy.State(0, false, 5L, 768));
        assertEquals(768, AntiCrashStore.read(dir).allocationMb);
        assertFalse(new File(dir, AntiCrashStore.FILE_NAME + ".tmp").exists());
    }

    @Test
    public void damagedTextIsReadAsFarAsItMakesSense() {
        AntiCrashPolicy.State state = AntiCrashStore.parse("steps=abc\njit=1\nhandled=77\nalloc=\ngarbage\nsteps=2\n");
        assertEquals(2, state.lowMemorySteps);
        assertTrue(state.jitFallback);
        assertEquals(77L, state.handledAtMs);
        assertEquals(0, state.allocationMb);
        assertEquals(0, AntiCrashStore.parse(null).lowMemorySteps);
        assertEquals(0, AntiCrashStore.parse("").lowMemorySteps);
    }

    @Test
    public void aHugeFileIsNotReadAtAll() throws IOException {
        File dir = freshDir();
        StringBuilder big = new StringBuilder("steps=3\n");
        for (int i = 0; i < 200; i++) big.append("padding=padding padding padding padding\n");
        Files.write(new File(dir, AntiCrashStore.FILE_NAME).toPath(), big.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(0, AntiCrashStore.read(dir).lowMemorySteps);
    }

    @Test
    public void aMissingFolderNeverThrows() {
        File missing = new File("/this/folder/does/not/exist");
        assertEquals(0, AntiCrashStore.read(missing).lowMemorySteps);
        AntiCrashStore.write(missing, new AntiCrashPolicy.State(1, false, 1L, 1024));
    }

    @Test
    public void theFileHoldsOnlyTheFourFieldsAsPlainText() {
        String text = AntiCrashStore.format(new AntiCrashPolicy.State(1, true, 42L, 1024));
        assertEquals("steps=1\njit=1\nhandled=42\nalloc=1024\n", text);
    }
}
