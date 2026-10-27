package net.kdt.pojavlaunch.game;

import java.util.Locale;

/**
 * Which part of start-up the game is in, judged from the end of its own log.
 *
 * <p>The loading screen used to say the same thing for the whole wait. The stage is read from
 * lines the game and the launcher already write, so it follows what is really happening: the
 * Java VM coming up, Minecraft setting itself up, the window being created, resources loading.
 * It only ever reads the log; it changes nothing.
 *
 * <p>Plain Java on purpose, so it is covered by JVM unit tests.
 */
public final class LaunchStage {
    public static final int PREPARING = 0;
    public static final int STARTING_JAVA = 1;
    public static final int LOADING_GAME = 2;
    public static final int CREATING_WINDOW = 3;
    public static final int LOADING_RESOURCES = 4;

    private LaunchStage() {}

    /**
     * The furthest stage the log tail shows. Checked from the last stage backwards, so a log that
     * has reached resources is not reported as only starting Java.
     */
    public static int fromLog(String logTail) {
        if (logTail == null || logTail.isEmpty()) return PREPARING;
        if (containsAny(logTail,
                "Reloading ResourceManager", "atlas", "Loading resources", "Reloading resources")) {
            return LOADING_RESOURCES;
        }
        if (containsAny(logTail, "Backend library", "LWJGL Version", "LWJGL version")) {
            return CREATING_WINDOW;
        }
        if (containsAny(logTail, "Setting user", "Datafixer optimizations", "Environment:",
                "Loading Minecraft", "Loading for game", "ModLauncher", "Fabric Loader",
                "Quilt Loader", "NeoForge", "/INFO]")) {
            return LOADING_GAME;
        }
        if (containsAny(logTail, "[Options]", "[info][gc", "[info][")) {
            return STARTING_JAVA;
        }
        return PREPARING;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    /** 83000 becomes "1:23". */
    public static String elapsed(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
