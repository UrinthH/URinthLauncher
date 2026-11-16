package net.kdt.pojavlaunch.prefs;

/**
 * The Java arguments the launcher puts into Settings once, on a new install with an empty Java
 * arguments field. Plain Java with no Android code in it, so Performance Mode (and its tests) can
 * tell "the launcher's own default text" apart from arguments the player wrote.
 */
public final class DefaultJavaArgs {
    /**
     * A modest G1 setup for phones. -Xms and -Xmx are not part of it because the launcher sets them
     * itself (Xmx from the RAM setting, default 1024 MB).
     */
    public static final String TEXT =
            "-XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ParallelRefProcEnabled "
            + "-XX:+DisableExplicitGC -XX:G1ReservePercent=15 "
            + "-XX:InitiatingHeapOccupancyPercent=25 -XX:+UseStringDeduplication";

    private DefaultJavaArgs() {}

    /** Every argument of {@link #TEXT}, one per entry. */
    public static String[] tokens() {
        return TEXT.split("\\s+");
    }
}
