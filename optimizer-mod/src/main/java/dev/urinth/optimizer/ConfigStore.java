package dev.urinth.optimizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads and writes the settings file. Never throws: a file that cannot be read or written gives the defaults. */
final class ConfigStore {
    private ConfigStore() {}

    private static final Logger LOG = LoggerFactory.getLogger("urinth_optimizer");

    /** The settings from the file; if there is none yet, the defaults are written so the player can find and edit it. */
    static OptimizerConfig load(Path file, boolean dedicatedServer) {
        String text = null;
        try {
            if (file != null && Files.isRegularFile(file)) text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOG.warn("[Optimizer] could not read {}, using the defaults", file, e);
        }
        OptimizerConfig config = OptimizerConfig.fromText(text, dedicatedServer);
        if (text == null) save(file, config);
        return config;
    }

    /** Writes the file (a temporary file first, so a crash cannot leave half a file). */
    static void save(Path file, OptimizerConfig config) {
        if (file == null || config == null) return;
        try {
            Path dir = file.toAbsolutePath().getParent();
            if (dir != null) Files.createDirectories(dir);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, config.toText(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("[Optimizer] could not write {}", file, e);
        }
    }
}
