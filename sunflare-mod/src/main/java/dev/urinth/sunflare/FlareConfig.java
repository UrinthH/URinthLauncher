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

    /**
     * Sun beams: screen-space god rays. Each level has a brightness, a number of samples along each ray (more = smoother, costs
     * more) and a divisor for the size of the small picture the rays are made in (smaller = sharper rays, costs more).
     */
    public static final int BEAMS_OFF = 0;
    public static final int BEAMS_EXTREME = 4;
    public static final int BEAMS_DEFAULT = BEAMS_EXTREME;
    public static final String[] BEAM_NAMES = {"Off", "Low", "Medium", "High", "Extreme"};
    private static final double[] GOD_DENSITY = {0.0, 0.60, 0.90, 1.15, 1.35};
    private static final int[] GOD_LAYERS = {0, 16, 32, 32, 64};
    private static final int[] GOD_DIVISOR = {12, 10, 8, 7, 6};
    /**
     * The frame rate the effects try to keep (0 = no limit on what they may cost). 80 is the lowest frame rate the effects may
     * cause on a phone that runs 90 without them.
     */
    public static final int DEFAULT_TARGET_FPS = 80;
    /**
     * Cheaper Fancy leaves (see LeafCull): how many layers of leaves stay visible on the outside of a tree. Faces buried under
     * more leaves than that are not drawn. 0 = off.
     */
    public static final int LEAVES_MAX = 3;
    public static final int LEAVES_DEFAULT = 2;
    public static final String[] LEAVES_NAMES = {"Off", "Light", "Balanced", "Safe"};

    /** The frame rates the Perf Guard button cycles through (0 = the guard is off). */
    public static final int[] FPS_CHOICES = {0, 60, 72, 80, 90};

    /** "Off" or "80 FPS", for the button and the commands. */
    public static String fpsName(int fps) {
        return fps <= 0 ? "Off" : fps + " FPS";
    }

    /** Version of the settings file. Files of an older version get the new beam level and frame guard defaults once. */
    private static final int FILE_VERSION = 2;

    private final Path file;
    private int mode = FULL;
    private int beams = BEAMS_DEFAULT;
    private int targetFps = DEFAULT_TARGET_FPS;
    private int leaves = LEAVES_DEFAULT;
    /** Write the stutter log (logs/urinth-perf.log) when the game hitches or freezes. */
    private boolean perfLog = true;
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

    /** The small picture the rays are made in is the window size divided by this. */
    public int godRayDivisor() {
        return GOD_DIVISOR[beams];
    }

    public int targetFps() {
        return targetFps;
    }

    /** 0 turns the frame-rate guard off; otherwise 30..240. */
    public int setTargetFps(int fps) {
        targetFps = fps <= 0 ? 0 : Math.max(30, Math.min(240, fps));
        return targetFps;
    }

    /** The next frame rate in {@link #FPS_CHOICES} after the current one (the first one after the last). Returns the new target. */
    public int cycleTargetFps() {
        int next = FPS_CHOICES[0];
        for (int i = 0; i < FPS_CHOICES.length; i++) {
            if (FPS_CHOICES[i] > targetFps) {
                next = FPS_CHOICES[i];
                break;
            }
        }
        return setTargetFps(next);
    }

    public boolean perfLog() {
        return perfLog;
    }

    public boolean setPerfLog(boolean on) {
        perfLog = on;
        return perfLog;
    }

    public int leaves() {
        return leaves;
    }

    public int setLeaves(int value) {
        if (value >= 0 && value <= LEAVES_MAX) leaves = value;
        return leaves;
    }

    public int cycleLeaves() {
        leaves = (leaves + 1) % (LEAVES_MAX + 1);
        return leaves;
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
        real = !"pack".equalsIgnoreCase(p.getProperty("style", "real").trim());
        leaves = Math.max(0, Math.min(LEAVES_MAX, parseInt(p.getProperty("leaves"), LEAVES_DEFAULT)));
        perfLog = !"off".equalsIgnoreCase(p.getProperty("perfLog", "on").trim());
        if (parseInt(p.getProperty("version"), 1) < FILE_VERSION) {
            // saved by an older build: the beams are different now (Extreme level, frame guard at 80), so start from the new defaults
            beams = BEAMS_DEFAULT;
            targetFps = DEFAULT_TARGET_FPS;
            return;
        }
        int b = parseInt(p.getProperty("beams"), beams);
        beams = Math.max(0, Math.min(b, BEAM_NAMES.length - 1));
        targetFps = setTargetFps(parseInt(p.getProperty("targetFps"), DEFAULT_TARGET_FPS));
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("version", Integer.toString(FILE_VERSION));
        p.setProperty("mode", Integer.toString(mode));
        p.setProperty("beams", Integer.toString(beams));
        p.setProperty("style", real ? "real" : "pack");
        p.setProperty("targetFps", Integer.toString(targetFps));
        p.setProperty("leaves", Integer.toString(leaves));
        p.setProperty("perfLog", perfLog ? "on" : "off");
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "URinth sun lens flare. mode: 0 = off, 1 = subtle, 2 = full, 3 = strong. beams (light shafts): 0 = off, 1 = low, 2 = medium, 3 = high, 4 = extreme. style: real or pack. targetFps: frame rate the effects try to keep (0 = no limit). leaves: cheaper Fancy leaves, 0 = off, 1 = light, 2 = balanced, 3 = safe. perfLog: on/off, writes logs/urinth-perf.log when the game hitches");
            }
        } catch (IOException ignored) {
            // the toggle still works for this session
        }
    }
}
