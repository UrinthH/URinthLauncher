package net.kdt.pojavlaunch.prefs;

/**
 * Java arguments that earlier builds of this launcher seeded into Settings (3ad3d01) and merged
 * into existing installs (5c427e7), and the logic that takes them out again.
 *
 * They are tuned for multi-gigabyte server heaps, not the roughly 1 GB heap a phone gets here.
 * AlwaysPreTouch in particular commits and writes to the whole heap while the JVM starts (-Xms
 * equals -Xmx in this launcher), and on a 3-4 GB phone that is enough for Android to kill the
 * game process within seconds, with no Java error and no log. They are listed only so that they
 * can be removed.
 *
 * Pure string work with no Android calls, so it can be unit tested on a plain JVM.
 */
final class EarlierTuningArgs {
    /** Seeded into an empty Java arguments field by 3ad3d01. */
    private static final String[] SEEDED = {
            "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=130",
            "-XX:+UnlockExperimentalVMOptions", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch",
            "-XX:G1NewSizePercent=28", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=8M",
            "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
            "-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90",
            "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=32",
            "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1"
    };

    /** Appended to whatever the user already had, by 5c427e7. */
    private static final String[] MERGED = {
            "-XX:SoftRefLRUPolicyMSPerMB=0", "-XX:+UseStringDeduplication"
    };

    /**
     * The seeded set counts as written by the launcher when at most this many of its flags are
     * missing, so a user who trimmed one or two out of it afterwards is still recognised.
     */
    private static final int SEEDED_TOLERANCE = 3;

    private EarlierTuningArgs() {}

    /**
     * Take the tokens that earlier builds of this launcher wrote out of a Java arguments string.
     * The two merged flags always go. The seeded set goes only when nearly all of it is present,
     * which means the launcher wrote the whole thing; a few of the same flags inside arguments the
     * user wrote themselves are left alone. Everything else is kept, in order.
     *
     * @param args Java arguments separated by whitespace, or null
     * @return the arguments without the launcher-written tokens, separated by single spaces
     */
    static String removeFrom(String args) {
        if (args == null || args.trim().isEmpty()) return "";
        String[] tokens = args.trim().split("\\s+");
        int seededTokens = 0;
        for (String token : tokens) {
            if (isOneOf(token, SEEDED)) seededTokens++;
        }
        // Nearly the whole seeded set is present, so the launcher wrote it, not the user.
        boolean launcherSeeded = seededTokens >= SEEDED.length - SEEDED_TOLERANCE;
        StringBuilder kept = new StringBuilder();
        for (String token : tokens) {
            if (isOneOf(token, MERGED)) continue;
            if (launcherSeeded && isOneOf(token, SEEDED)) continue;
            if (kept.length() > 0) kept.append(' ');
            kept.append(token);
        }
        return kept.toString();
    }

    private static boolean isOneOf(String token, String[] candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(token)) return true;
        }
        return false;
    }
}
