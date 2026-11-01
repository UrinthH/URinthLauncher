package dev.urinth.sunflare;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The mod's own settings file (config/urinth-sunflare.properties). It never reads or writes options.txt.
 */
public final class FlareConfig {
    public static final int OFF = 0;
    public static final int SUBTLE = 1;
    public static final int FULL = 2;

    private static final double[] STRENGTH = {0.0, 0.5, 1.0};

    private final Path file;
    private int mode = FULL;

    public FlareConfig(Path file) {
        this.file = file;
    }

    public int mode() {
        return mode;
    }

    public double strength() {
        return STRENGTH[mode];
    }

    public int cycle() {
        mode = (mode + 1) % STRENGTH.length;
        return mode;
    }

    public static int parseMode(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            int v = Integer.parseInt(raw.trim());
            return v >= OFF && v <= FULL ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void load() {
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        mode = parseMode(p.getProperty("mode"), mode);
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("mode", Integer.toString(mode));
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "URinth sun lens flare. mode: 0 = off, 1 = subtle, 2 = full");
            }
        } catch (IOException ignored) {
            // the toggle still works for this session
        }
    }
}
