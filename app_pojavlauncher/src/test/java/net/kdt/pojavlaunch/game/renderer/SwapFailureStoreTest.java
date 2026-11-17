package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class SwapFailureStoreTest {
    private static final String BUILD = "justicia-20261117-[abc1234]-claude/fix-mesa-renderers";
    private static final long START = 5_000_000L;

    private static File freshDir() throws IOException {
        File dir = Files.createTempDirectory("swapstore").toFile();
        dir.deleteOnExit();
        return dir;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void aLaunchWithTheSwapIsRememberedAndOneWithoutForgetsIt() throws IOException {
        File dir = freshDir();
        SwapFailureStore.markLaunch(dir, true, START);
        File started = new File(dir, SwapFailureStore.STARTED_NAME);
        assertTrue(started.isFile());
        assertEquals(Long.toString(START), read(started));
        SwapFailureStore.markLaunch(dir, false, START + 1L);
        assertFalse(started.exists());
    }

    @Test
    public void anErrorExitSoonAfterTheSwapIsRecordedForThisBuildOnly() throws IOException {
        File dir = freshDir();
        SwapFailureStore.markLaunch(dir, true, START);
        assertTrue(SwapFailureStore.onGameExit(dir, BUILD, START + 58_000L, -1, false));
        assertTrue(SwapFailureStore.lastSwapFailed(dir, BUILD));
        // A newer launcher build is not held back by what an older one saw.
        assertFalse(SwapFailureStore.lastSwapFailed(dir, BUILD + "-newer"));
        assertFalse(SwapFailureStore.lastSwapFailed(dir, null));
        // The start time is used up.
        assertFalse(new File(dir, SwapFailureStore.STARTED_NAME).exists());
    }

    @Test
    public void aNormalExitASignalAndALateErrorAreNotRecorded() throws IOException {
        File dir = freshDir();
        SwapFailureStore.markLaunch(dir, true, START);
        assertFalse(SwapFailureStore.onGameExit(dir, BUILD, START + 40_000L, 0, false));
        SwapFailureStore.markLaunch(dir, true, START);
        assertFalse(SwapFailureStore.onGameExit(dir, BUILD, START + 40_000L, 9, true));
        SwapFailureStore.markLaunch(dir, true, START);
        assertFalse(SwapFailureStore.onGameExit(dir, BUILD,
                START + PerformanceMobileGlues.FAILURE_WINDOW_MS + 1L, 1, false));
        assertFalse(SwapFailureStore.lastSwapFailed(dir, BUILD));
    }

    @Test
    public void anErrorExitOfALaunchWithoutTheSwapIsNotBlamedOnIt() throws IOException {
        File dir = freshDir();
        SwapFailureStore.markLaunch(dir, true, START);
        // The next launch did not use the swap: the old start time must not be blamed for its crash.
        SwapFailureStore.markLaunch(dir, false, START + 10_000L);
        assertFalse(SwapFailureStore.onGameExit(dir, BUILD, START + 20_000L, 1, false));
        assertFalse(SwapFailureStore.lastSwapFailed(dir, BUILD));
        // And an exit with nothing recorded at all is no failure either.
        assertFalse(SwapFailureStore.onGameExit(freshDir(), BUILD, START, 1, false));
    }

    @Test
    public void aRecordedFailureStaysUntilClearedAndSurvivesALaterGoodExit() throws IOException {
        File dir = freshDir();
        SwapFailureStore.markLaunch(dir, true, START);
        assertTrue(SwapFailureStore.onGameExit(dir, BUILD, START + 5_000L, 1, false));
        SwapFailureStore.markLaunch(dir, false, START + 100_000L);
        assertFalse(SwapFailureStore.onGameExit(dir, BUILD, START + 130_000L, 0, false));
        assertTrue(SwapFailureStore.lastSwapFailed(dir, BUILD));
        SwapFailureStore.clearFailure(dir);
        assertFalse(SwapFailureStore.lastSwapFailed(dir, BUILD));
        // Clearing again, with nothing there, is fine.
        SwapFailureStore.clearFailure(dir);
    }

    @Test
    public void garbageInTheStartedFileCountsAsNothingRecorded() throws IOException {
        File dir = freshDir();
        try (FileOutputStream out = new FileOutputStream(new File(dir, SwapFailureStore.STARTED_NAME))) {
            out.write("not a number".getBytes(StandardCharsets.UTF_8));
        }
        assertFalse(SwapFailureStore.onGameExit(dir, BUILD, START, 1, false));
        assertFalse(new File(dir, SwapFailureStore.STARTED_NAME).exists());
    }

    @Test
    public void aFolderThatDoesNotExistNeverThrows() throws IOException {
        File missing = new File(freshDir(), "nope/deeper");
        SwapFailureStore.markLaunch(missing, true, START);
        assertFalse(SwapFailureStore.onGameExit(missing, BUILD, START + 1L, 1, false));
        assertFalse(SwapFailureStore.lastSwapFailed(missing, BUILD));
        SwapFailureStore.clearFailure(missing);
    }
}
