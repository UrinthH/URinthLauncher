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
    public static final int STRONG = 3;

    public static final String[] NAMES = {"Off", "Subtle", "Full", "Strong"};

    /** The pack's LENS_POWER for each mode: 20%, 35% (the default) and 50% (the pack's maximum). */
    private static final double[] POWER = {0.0, 0.20, 0.35, 0.50};

    /** Sun beams: screen-space god rays. Each level has a brightness and a number of layers (more layers = smoother, costs more). */
    public static final int BEAMS_OFF = 0;
    public static final int BEAMS_DEFAULT = 2;
    public static final String[] BEAM_NAMES = {"Off", "Low", "Medium", "High"};
    private static final double[] GOD_DENSITY = {0.0, 0.55, 0.85, 1.20};
    private static final int[] GOD_LAYERS = {0, 10, 16, 22};
    /** The frame rate the effects try to keep (0 = no limit on what they may cost). */
    public static final int DEFAULT_TARGET_FPS = 90;

    private final Path file;
    private int mode = FULL;
    private int beams = BEAMS_DEFAULT;
    private int targetFps = DEFAULT_TARGET_FPS;
    /** The photographic "Real" look (default) or the pack's own colourful flare. */
    private boolean real = true;

    public FlareConfig(Path file) {
        this.file = file;
    }

    public int mode() {
        return mode;
    }

    public double power() {
        return POWER[mode];
    }

    public int cycle() {
        mode = (mode + 1) % POWER.length;
        return mode;
    }

    /** Sets a mode directly (used by the /sunflare command). Out-of-range values are ignored. Returns the active mode. */
    public int setMode(int value) {
        if (value >= OFF && value <= STRONG) mode = value;
        return mode;
    }

    /** "off", "subtle", "full" or "strong" (any case) to a mode, or -1. */
    public static int modeFromName(String name) {
        if (name == null) return -1;
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(name.trim())) return i;
        }
        return -1;
    }

    public boolean realStyle() {
        return real;
    }

    public boolean setRealStyle(boolean value) {
        real = value;
        return real;
    }

    public boolean toggleStyle() {
        real = !real;
        return real;
    }

    public int beams() {
        return beams;
    }

    public double godRayDensity() {
        return GOD_DENSITY[beams];
    }

    public int godRayLayers() {
        return GOD_LAYERS[beams];
    }

    public int targetFps() {
        return targetFps;
    }

    /** 0 turns the frame-rate guard off; otherwise 30..240. */
    public int setTargetFps(int fps) {
        targetFps = fps <= 0 ? 0 : Math.max(30, Math.min(240, fps));
        return targetFps;
    }

    public int cycleBeams() {
        beams = (beams + 1) % BEAM_NAMES.length;
        return beams;
    }

    public int setBeams(int value) {
        if (value >= 0 && value < BEAM_NAMES.length) beams = value;
        return beams;
    }

    /** "off", "low", "medium" or "high" (any case) to a beams level, or -1. */
    public static int beamsFromName(String name) {
        if (name == null) return -1;
        for (int i = 0; i < BEAM_NAMES.length; i++) {
            if (BEAM_NAMES[i].equalsIgnoreCase(name.trim())) return i;
        }
        return -1;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int parseMode(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            int v = Integer.parseInt(raw.trim());
            return v >= OFF && v <= STRONG ? v : fallback;
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
        int b = parseMode(p.getProperty("beams"), beams);
        beams = Math.min(b, BEAM_NAMES.length - 1);
        real = !"pack".equalsIgnoreCase(p.getProperty("style", "real").trim());
        targetFps = setTargetFps(parseInt(p.getProperty("targetFps"), DEFAULT_TARGET_FPS));
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("mode", Integer.toString(mode));
        p.setProperty("beams", Integer.toString(beams));
        p.setProperty("style", real ? "real" : "pack");
        p.setProperty("targetFps", Integer.toString(targetFps));
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "URinth sun lens flare. mode: 0 = off, 1 = subtle, 2 = full, 3 = strong. beams (light shafts): 0 = off, 1 = low, 2 = medium, 3 = high. style: real or pack");
            }
        } catch (IOException ignored) {
            // the toggle still works for this session
        }
    }
}
