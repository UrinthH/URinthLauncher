package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigStoreTest {
    private static Path dir() throws IOException {
        return Files.createTempDirectory("urinth-optimizer-test");
    }

    @Test
    void aMissingFileGivesTheDefaultsAndIsWritten() throws IOException {
        Path file = dir().resolve("config").resolve(OptimizerConfig.FILE_NAME);
        OptimizerConfig c = ConfigStore.load(file, false);
        assertEquals(OptimizerConfig.defaults(false).toText(), c.toText());
        assertTrue(Files.isRegularFile(file), "the defaults are written so the player can find the file");
        assertEquals(OptimizerConfig.defaults(false).toText(), Files.readString(file));
    }

    @Test
    void savedSettingsComeBack() throws IOException {
        Path file = dir().resolve(OptimizerConfig.FILE_NAME);
        OptimizerConfig c = OptimizerConfig.defaults(false);
        c.setAdaptive(false);
        c.setTargetFps(120);
        ConfigStore.save(file, c);
        OptimizerConfig back = ConfigStore.load(file, false);
        assertFalse(back.adaptive());
        assertEquals(120, back.targetFps());
        assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".tmp")), "no temporary file is left");
    }

    @Test
    void aDamagedFileGivesTheDefaultsAndIsLeftForThePlayerToFix() throws IOException {
        Path file = dir().resolve(OptimizerConfig.FILE_NAME);
        Files.writeString(file, "target-fps=oops\nadaptive-distance=\n\u0000\u0001garbage");
        OptimizerConfig c = ConfigStore.load(file, false);
        assertEquals(60, c.targetFps());
        assertTrue(Files.readString(file).contains("garbage"), "an existing file is not overwritten on load");
    }

    @Test
    void aFileThatCannotBeWrittenIsNotAnError() throws IOException {
        Path blocker = dir().resolve("blocker");
        Files.writeString(blocker, "a file where a folder should be");
        // the parent of this path is a file, so it can never be created: this must not throw
        ConfigStore.save(blocker.resolve("sub").resolve(OptimizerConfig.FILE_NAME), OptimizerConfig.defaults(false));
        OptimizerConfig c = ConfigStore.load(blocker.resolve("sub").resolve(OptimizerConfig.FILE_NAME), false);
        assertEquals(60, c.targetFps());
        ConfigStore.save(null, OptimizerConfig.defaults(false));
        ConfigStore.save(blocker, null);
    }
}
