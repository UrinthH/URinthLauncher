package dev.urinth.optimizer;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Properties;

/**
 * The settings of the mod, kept in config/urinth-optimizer.properties. options.txt is never touched: the render distance the player
 * chose stays exactly as it is, the mod only draws less of it while the frame rate is too low. Plain Java, unit tested.
 *
 * <p>Every value is clamped to what makes sense, and anything missing or unreadable falls back to its default, so a damaged file can
 * never stop the game from starting. The fields are volatile because they are read by the render thread, the server thread and the
 * chunk workers.
 */
public final class OptimizerConfig {
    public static final String FILE_NAME = "urinth-optimizer.properties";

    /** The frame rates offered in the settings screen. */
    public static final int[] TARGET_FPS = {30, 45, 60, 75, 90, 120};
    /** The shortest distance (in chunks) the mod may shrink to. */
    public static final int[] MIN_DISTANCE = {3, 4, 5, 6, 7, 8};
    /** The longest simulation distance a world may have; 0 = no limit. */
    public static final int[] SIM_CAP = {0, 4, 5, 6, 8, 10};

    public static final int AUTO_THREADS = -1;
    public static final int VANILLA_THREADS = 0;
    /** The worker thread counts offered: automatic, the game's own choice, or a number. */
    public static final int[] THREADS = {AUTO_THREADS, VANILLA_THREADS, 2, 3, 4, 6};

    public static final int PARTICLES_OFF = 0;
    public static final int PARTICLES_AUTO = 1;
    public static final int PARTICLES_ALWAYS = 2;
    private static final String[] PARTICLE_NAMES = {"off", "auto", "always"};

    /** The memory guard: nothing | only invisible savings (particles, simulation distance) | and, as the last resort, a shorter world. */
    public static final int MEMORY_OFF = 0;
    public static final int MEMORY_SOFT = 1;
    public static final int MEMORY_FULL = 2;
    private static final String[] MEMORY_NAMES = {"off", "soft", "full"};

    private static volatile OptimizerConfig current = defaults(false);

    private volatile boolean adaptive = true;
    private volatile int targetFps = 60;
    private volatile int minDistance = 5;
    private volatile boolean cull = true;
    private volatile int particles = PARTICLES_AUTO;
    private volatile boolean uploads = true;
    private volatile int simulationCap = 8;
    private volatile boolean serverGovernor = true;
    private volatile int workerThreads = AUTO_THREADS;
    private volatile int memoryGuard = MEMORY_FULL;
    private volatile boolean diagnostics = true;

    private OptimizerConfig() {}

    /** The settings of the running game. Never null. */
    public static OptimizerConfig current() {
        return current;
    }

    static void setCurrent(OptimizerConfig config) {
        current = config == null ? defaults(false) : config;
    }

    /**
     * The defaults. A dedicated server is somebody else's machine, so there the mod changes nothing unless it is asked to: no
     * simulation limit, no governor, the game's own thread count.
     */
    public static OptimizerConfig defaults(boolean dedicatedServer) {
        OptimizerConfig c = new OptimizerConfig();
        if (dedicatedServer) {
            c.simulationCap = 0;
            c.serverGovernor = false;
            c.workerThreads = VANILLA_THREADS;
            c.memoryGuard = MEMORY_OFF;
            c.diagnostics = false;
        }
        return c;
    }

    // ---- reading and writing the file --------------------------------------------------------------------------------------

    /** The settings from the text of the file; anything missing or unreadable gives the default of that setting. */
    public static OptimizerConfig fromText(String text, boolean dedicatedServer) {
        OptimizerConfig c = defaults(dedicatedServer);
        if (text == null) return c;
        Properties p = new Properties();
        try {
            p.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            return c;
        }
        c.adaptive = bool(p.getProperty("adaptive-distance"), c.adaptive);
        c.targetFps = integer(p.getProperty("target-fps"), c.targetFps, 20, 240);
        c.minDistance = integer(p.getProperty("min-distance"), c.minDistance, 2, 16);
        c.cull = bool(p.getProperty("far-chunk-culling"), c.cull);
        c.particles = particles(p.getProperty("particles"), c.particles);
        c.uploads = bool(p.getProperty("upload-smoothing"), c.uploads);
        c.simulationCap = simulation(p.getProperty("simulation-cap"), c.simulationCap);
        c.serverGovernor = bool(p.getProperty("server-governor"), c.serverGovernor);
        c.workerThreads = threads(p.getProperty("worker-threads"), c.workerThreads);
        c.memoryGuard = named(p.getProperty("memory-guard"), MEMORY_NAMES, c.memoryGuard);
        c.diagnostics = bool(p.getProperty("freeze-log"), c.diagnostics);
        return c;
    }

    /** The text of the settings file, with a short explanation of every line. */
    public String toText() {
        return "# URinth Optimizer\n"
            + "# The render distance you chose in Video Settings is never changed (options.txt is not touched).\n"
            + "# While the frame rate is below the target, the mod draws fewer chunks (fog and culling move in, nothing reloads)\n"
            + "# and brings them back when the game has room again. (The one exception is memory-guard=full, below.)\n"
            + "adaptive-distance=" + adaptive + "\n"
            + "# The frame rate to keep (20-240). Capped by the frame limit and v-sync of the game.\n"
            + "target-fps=" + targetFps + "\n"
            + "# The shortest distance, in chunks, it may shrink to (2-16).\n"
            + "min-distance=" + minDistance + "\n"
            + "# Stop building and drawing the chunks beyond the current distance (the big saving). false = only move the fog.\n"
            + "far-chunk-culling=" + cull + "\n"
            + "# Fewer particles while the frame rate is low: off | auto | always\n"
            + "particles=" + PARTICLE_NAMES[particles] + "\n"
            + "# Upload new chunk meshes over a few frames instead of all at once (smoother while chunks load).\n"
            + "upload-smoothing=" + uploads + "\n"
            + "# The longest simulation distance a world may have, in chunks (what is ticked: mobs, crops). 0 = no limit.\n"
            + "simulation-cap=" + simulationCap + "\n"
            + "# Lower that distance by itself while the server is too slow to keep up, and raise it again later.\n"
            + "server-governor=" + serverGovernor + "\n"
            + "# Background threads for chunk generation and meshing: auto | vanilla | a number. Needs a restart.\n"
            + "worker-threads=" + threadsName(workerThreads) + "\n"
            + "# When the game is running out of memory (a phone with little of it): off | soft | full.\n"
            + "# soft = fewer particles and a shorter simulation distance until there is room again.\n"
            + "# full = and, as the last resort before a crash, the world around you is shortened by two chunks for a while (your setting\n"
            + "#        in Video Settings stays as it is) and given back one chunk at a time when memory is fine again.\n"
            + "memory-guard=" + memoryGuardName(memoryGuard) + "\n"
            + "# Write freezes (what the game was doing, with the garbage collector and memory numbers) and a summary a minute to the game log.\n"
            + "freeze-log=" + diagnostics + "\n";
    }

    private static boolean bool(String raw, boolean fallback) {
        if (raw == null) return fallback;
        switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true": case "on": case "yes": case "1": return true;
            case "false": case "off": case "no": case "0": return false;
            default: return fallback;
        }
    }

    private static int integer(String raw, int fallback, int min, int max) {
        if (raw == null) return fallback;
        try {
            int v = Integer.parseInt(raw.trim());
            return v < min ? min : Math.min(v, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int particles(String raw, int fallback) {
        return named(raw, PARTICLE_NAMES, fallback);
    }

    /** The index of {@code raw} in {@code names} (ignoring case and spaces), or the fallback. */
    private static int named(String raw, String[] names, int fallback) {
        if (raw == null) return fallback;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < names.length; i++) if (names[i].equals(v)) return i;
        return fallback;
    }

    private static int simulation(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            int v = Integer.parseInt(raw.trim());
            if (v <= 0) return 0;
            return Math.max(2, Math.min(v, 32));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int threads(String raw, int fallback) {
        if (raw == null) return fallback;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.equals("auto")) return AUTO_THREADS;
        if (v.equals("vanilla") || v.equals("off")) return VANILLA_THREADS;
        try {
            int n = Integer.parseInt(v);
            return n < 1 ? VANILLA_THREADS : Math.min(n, 32);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String threadsName(int threads) {
        return threads == AUTO_THREADS ? "auto" : threads == VANILLA_THREADS ? "vanilla" : Integer.toString(threads);
    }

    public static String particlesName(int mode) {
        return PARTICLE_NAMES[Math.max(0, Math.min(mode, PARTICLE_NAMES.length - 1))];
    }

    public static String memoryGuardName(int mode) {
        return MEMORY_NAMES[Math.max(0, Math.min(mode, MEMORY_NAMES.length - 1))];
    }

    // ---- values ------------------------------------------------------------------------------------------------------------

    public boolean adaptive() { return adaptive; }
    public int targetFps() { return targetFps; }
    public int minDistance() { return minDistance; }
    public boolean cull() { return cull; }
    public int particles() { return particles; }
    public boolean uploads() { return uploads; }
    public int simulationCap() { return simulationCap; }
    public boolean serverGovernor() { return serverGovernor; }
    public int workerThreads() { return workerThreads; }
    public int memoryGuard() { return memoryGuard; }
    public boolean diagnostics() { return diagnostics; }

    public void setAdaptive(boolean on) { adaptive = on; }
    public void setCull(boolean on) { cull = on; }
    public void setUploads(boolean on) { uploads = on; }
    public void setServerGovernor(boolean on) { serverGovernor = on; }

    public int setTargetFps(int fps) {
        targetFps = Math.max(20, Math.min(fps, 240));
        return targetFps;
    }

    public int setMinDistance(int chunks) {
        minDistance = Math.max(2, Math.min(chunks, 16));
        return minDistance;
    }

    // ---- cycling through the offered values (the buttons) ------------------------------------------------------------------

    public void cycleTargetFps() { targetFps = next(TARGET_FPS, targetFps); }
    public void cycleMinDistance() { minDistance = next(MIN_DISTANCE, minDistance); }
    public void cycleSimulationCap() { simulationCap = next(SIM_CAP, simulationCap); }
    public void cycleParticles() { particles = (particles + 1) % PARTICLE_NAMES.length; }
    public void cycleThreads() { workerThreads = next(THREADS, workerThreads); }
    /** The button goes full -> soft -> off -> full. */
    public void cycleMemoryGuard() { memoryGuard = (memoryGuard + MEMORY_NAMES.length - 1) % MEMORY_NAMES.length; }
    public void setMemoryGuard(int mode) { memoryGuard = Math.max(MEMORY_OFF, Math.min(mode, MEMORY_FULL)); }

    /** The value after {@code current} in the list (the first when current is the last or is not in the list). */
    static int next(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return values[(i + 1) % values.length];
        }
        // not in the list (a hand-edited file): continue with the first value above it, else wrap
        for (int v : values) if (v > current) return v;
        return values[0];
    }
}
