package dev.urinth.recordable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Small helpers for tests that write files. */
public final class TestFiles {
    private TestFiles() {
    }

    public static Path tempDir() throws IOException {
        return Files.createTempDirectory("recordable-test");
    }

    public static void deleteTree(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // a leftover temp file does not matter
                }
            });
        } catch (IOException ignored) {
            // nothing to clean up
        }
    }
}
