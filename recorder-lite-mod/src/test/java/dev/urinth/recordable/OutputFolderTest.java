package dev.urinth.recordable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class OutputFolderTest {
    @Test
    void theFirstFolderThatAcceptsFilesWinsAndLeavesNoTestFileBehind() throws Exception {
        Path root = TestFiles.tempDir();
        try {
            Path game = root.resolve("game");
            Path blocked = root.resolve("blocked");
            Files.write(blocked, new byte[]{1});                         // a file where a folder is needed: cannot be used
            Path good = root.resolve("Movies").resolve("Record-able");
            List<String> notes = new ArrayList<>();
            Path chosen = OutputFolder.choose(true, game, List.of(blocked.resolve("Record-able"), good), notes);
            assertEquals(good, chosen);
            assertTrue(Files.isDirectory(good));
            try (var files = Files.list(good)) {
                assertEquals(0L, files.count(), "the test file is removed again");
            }
            assertEquals(1, notes.size(), "one note for the folder that could not be used");
            assertTrue(notes.get(0).contains("cannot save to"), notes.get(0));
        } finally {
            TestFiles.deleteTree(root);
        }
    }

    @Test
    void theGameFolderIsTheLastResort() throws Exception {
        Path root = TestFiles.tempDir();
        try {
            Path game = root.resolve("game");
            Path blocked = root.resolve("blocked");
            Files.write(blocked, new byte[]{1});
            List<String> notes = new ArrayList<>();
            Path chosen = OutputFolder.choose(true, game, List.of(blocked.resolve("a"), blocked.resolve("b")), notes);
            assertEquals(game.resolve("recordings"), chosen);
            assertEquals(2, notes.size());
            // switched off: the shared storage is not even looked at
            List<String> quiet = new ArrayList<>();
            assertEquals(game.resolve("recordings"), OutputFolder.choose(false, game, List.of(root.resolve("Movies")), quiet));
            assertTrue(quiet.isEmpty());
            assertFalse(Files.exists(root.resolve("Movies")), "nothing is created in a folder that was not asked for");
        } finally {
            TestFiles.deleteTree(root);
        }
    }

    @Test
    void whenNothingWorksThereIsNoFolder() throws Exception {
        Path root = TestFiles.tempDir();
        try {
            Path blocked = root.resolve("blocked");
            Files.write(blocked, new byte[]{1});
            List<String> notes = new ArrayList<>();
            assertNull(OutputFolder.choose(false, blocked.resolve("game"), List.of(), notes));
            assertEquals(1, notes.size());
            assertNotNull(OutputFolder.tryUse(blocked.resolve("x")), "the reason is given");
            assertNull(OutputFolder.tryUse(root.resolve("fine")));
        } finally {
            TestFiles.deleteTree(root);
        }
    }

    @Test
    void phoneStorageIsRecognisedByItsPath() {
        assertTrue(OutputFolder.isShared(Path.of("/storage/emulated/0/Movies/Record-able")));
        assertTrue(OutputFolder.isShared(Path.of("/sdcard/Movies/Record-able")));
        assertFalse(OutputFolder.isShared(Path.of("/data/data/app/files/recordings")));
        assertFalse(OutputFolder.isShared(Path.of("/storage/emulated/0")));
    }
}
