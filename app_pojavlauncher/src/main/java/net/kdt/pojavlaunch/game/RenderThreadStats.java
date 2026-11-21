package net.kdt.pojavlaunch.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The numbers behind the once-a-minute "[RenderThread]" line in the game log, with no Android code
 * in it so it can be tested on a plain JVM.
 *
 * The frame rate of a game is set by one thread, the render thread, so the two questions that matter
 * when the FPS is lower than hoped are: is that thread always busy (then the CPU is the limit and a
 * faster core or less work per frame is the way up) or does it spend part of each frame waiting (then
 * the graphics chip or the screen is the limit and a faster CPU would change nothing), and does it run
 * on the phone's fast cores or on the slow ones. The kernel answers both: /proc/self/task/TID/stat
 * holds the CPU time the thread used and the core it last ran on. One reading a second is enough to
 * tell, and costs a file read.
 */
final class RenderThreadStats {
    /** One reading of a thread's stat file. */
    static final class Reading {
        final long userTicks;
        final long systemTicks;
        final int cpu;

        Reading(long userTicks, long systemTicks, int cpu) {
            this.userTicks = userTicks;
            this.systemTicks = systemTicks;
            this.cpu = cpu;
        }
    }

    /** The CPU numbers kept apart when counting where the thread ran. */
    private static final int MAX_CPUS = FastCores.MAX_CPUS;

    private final long fastMask;
    private final long ticksPerSecond;
    private final int[] ranOn = new int[MAX_CPUS];

    private boolean havePrevious;
    private long previousUserTicks;
    private long previousSystemTicks;
    private long previousAtNs;

    private int busySamples;
    private double userSum;
    private double systemSum;
    private double busyPeak;
    private int cpuReadings;
    private int onFastCores;

    /**
     * @param fastMask       the phone's fast cores (see {@link FastCores}), 0 when they cannot be told apart
     * @param ticksPerSecond the kernel's clock tick rate, usually 100
     */
    RenderThreadStats(long fastMask, long ticksPerSecond) {
        this.fastMask = fastMask;
        this.ticksPerSecond = ticksPerSecond > 0 ? ticksPerSecond : 100;
    }

    /**
     * Reads the CPU times and the core out of a /proc/self/task/TID/stat line, or null when the line
     * is not one. The thread name (field 2) sits in brackets and may hold spaces and brackets
     * ("Render thread"), so the fields after it are found from the last closing bracket.
     */
    static Reading parseStat(String line) {
        if (line == null) return null;
        int close = line.lastIndexOf(')');
        if (close < 0 || close + 2 > line.length()) return null;
        String[] rest = line.substring(close + 2).trim().split("\\s+");
        // rest[0] is field 3 (the state), so field N is rest[N - 3]: utime 14, stime 15, processor 39.
        if (rest.length < 37) return null;
        try {
            long user = Long.parseLong(rest[11]);
            long system = Long.parseLong(rest[12]);
            int cpu = Integer.parseInt(rest[36]);
            if (user < 0 || system < 0) return null;
            return new Reading(user, system, cpu);
        } catch (NumberFormatException notNumbers) {
            return null;
        }
    }

    /** Adds one reading taken at {@code nowNs} (any steady clock, in nanoseconds). */
    void add(Reading reading, long nowNs) {
        if (reading == null) return;
        if (havePrevious) {
            long wallNs = nowNs - previousAtNs;
            long user = reading.userTicks - previousUserTicks;
            long system = reading.systemTicks - previousSystemTicks;
            if (wallNs > 0 && user >= 0 && system >= 0) {
                double wallTicks = wallNs * (double) ticksPerSecond / 1_000_000_000d;
                double userShare = Math.min(1d, user / wallTicks);
                double systemShare = Math.min(1d - userShare, system / wallTicks);
                busySamples++;
                userSum += userShare;
                systemSum += systemShare;
                busyPeak = Math.max(busyPeak, userShare + systemShare);
            }
        }
        havePrevious = true;
        previousUserTicks = reading.userTicks;
        previousSystemTicks = reading.systemTicks;
        previousAtNs = nowNs;
        if (reading.cpu >= 0 && reading.cpu < MAX_CPUS) {
            ranOn[reading.cpu]++;
            cpuReadings++;
            if (FastCores.contains(fastMask, reading.cpu)) onFastCores++;
        }
    }

    /** True once there is something to report. */
    boolean hasData() {
        return busySamples > 0 || cpuReadings > 0;
    }

    /** Share of the time on the fast cores, 0..1, or -1 when the fast cores are not known or nothing was read. */
    double fastShare() {
        return fastMask == 0 || cpuReadings == 0 ? -1 : onFastCores / (double) cpuReadings;
    }

    /**
     * The line for the game log, then the counters start again.
     *
     * @param placement what the launcher did with the thread ("pinned to cpu6-7", "not pinned"), or empty
     */
    String summarise(String placement) {
        StringBuilder text = new StringBuilder("Last minute: ");
        if (busySamples > 0) {
            double user = 100 * userSum / busySamples;
            double system = 100 * systemSum / busySamples;
            text.append(String.format(Locale.US,
                    "the render thread was busy %.0f%% of the time (game code %.0f%%, system and graphics driver %.0f%%), "
                            + "never above %.0f%%",
                    user + system, user, system, 100 * busyPeak));
        } else {
            text.append("the render thread's CPU use could not be read");
        }
        if (cpuReadings > 0) {
            if (fastMask != 0) {
                text.append(String.format(Locale.US, "; it ran on the fast cores (%s) %.0f%% of the time",
                        FastCores.describe(fastMask), 100 * fastShare()));
            } else {
                text.append("; this phone does not report which of its cores are fast");
            }
            text.append("; seen on ").append(cores());
        }
        if (placement != null && !placement.isEmpty()) text.append("; ").append(placement);
        reset();
        return text.toString();
    }

    /** "cpu7 41%, cpu6 33%, cpu2 17%": the cores it was seen on, most often first, at most four. */
    private String cores() {
        List<Integer> seen = new ArrayList<>();
        for (int cpu = 0; cpu < MAX_CPUS; cpu++) if (ranOn[cpu] > 0) seen.add(cpu);
        Collections.sort(seen, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                if (ranOn[a] != ranOn[b]) return ranOn[b] - ranOn[a];
                return a - b;
            }
        });
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < seen.size() && i < 4; i++) {
            int cpu = seen.get(i);
            if (i > 0) text.append(", ");
            text.append("cpu").append(cpu).append(' ').append(Math.round(100d * ranOn[cpu] / cpuReadings)).append('%');
        }
        return text.toString();
    }

    private void reset() {
        java.util.Arrays.fill(ranOn, 0);
        busySamples = 0;
        userSum = 0;
        systemSum = 0;
        busyPeak = 0;
        cpuReadings = 0;
        onFastCores = 0;
    }
}
