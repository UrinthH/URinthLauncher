package net.kdt.pojavlaunch.game;

/**
 * The decision part of {@link RenderScheduler}, with no Android code in it so it can be tested on
 * a plain JVM.
 *
 * The input is how much of the time the render thread spent ready to run but waiting for a CPU
 * core (the "run delay" the kernel keeps for every thread). Near zero means the render thread gets
 * a core whenever it wants one. A high value means other threads are in its way, and that is
 * what shows up on screen as a hitch.
 */
final class SchedulerPolicy {
    /** No helper threads lowered. */
    static final int LEVEL_NONE = 0;
    /** Helper threads slightly lowered. */
    static final int LEVEL_MILD = 1;
    /** Helper threads clearly lowered. */
    static final int LEVEL_STRONG = 2;

    /** Waiting this much of the time (6%) moves to the mild level, 12% to the strong level. */
    static final double ENTER_MILD = 0.06;
    static final double ENTER_STRONG = 0.12;
    /** Below this, the mild level is no longer needed. */
    static final double LEAVE_MILD = 0.025;
    /** Calm readings in a row needed to step down one level. */
    static final int CALM_SAMPLES_TO_EASE = 4;

    int level = LEVEL_NONE;
    private int calm;

    /** Rises at once, falls one level only after several calm readings in a row. */
    void step(double waitFraction) {
        int target = waitFraction >= ENTER_STRONG ? LEVEL_STRONG
                : waitFraction >= ENTER_MILD ? LEVEL_MILD : LEVEL_NONE;
        if (target > level) {
            level = target;
            calm = 0;
            return;
        }
        double leave = level == LEVEL_STRONG ? ENTER_MILD : level == LEVEL_MILD ? LEAVE_MILD : 0;
        if (level > LEVEL_NONE && waitFraction < leave) {
            if (++calm >= CALM_SAMPLES_TO_EASE) {
                level--;
                calm = 0;
            }
        } else {
            calm = 0;
        }
    }

    /** The share of the elapsed time spent waiting, from two readings of the kernel's counter. */
    static double waitFraction(long previousWaitNs, long currentWaitNs, long elapsedNs) {
        if (elapsedNs <= 0 || currentWaitNs < previousWaitNs) return 0;
        double fraction = (currentWaitNs - previousWaitNs) / (double) elapsedNs;
        return fraction > 1 ? 1 : fraction;
    }

    /**
     * Read the "run delay" nanoseconds out of a /proc/self/task/TID/schedstat line, which holds
     * "time on cpu, time waiting, number of timeslices". Returns -1 when it cannot be read.
     */
    static long parseWaitNanos(String schedstat) {
        if (schedstat == null) return -1;
        String[] parts = schedstat.trim().split("\\s+");
        if (parts.length < 2) return -1;
        try {
            long wait = Long.parseLong(parts[1]);
            return wait < 0 ? -1 : wait;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Thread names are cut to 15 characters by the kernel, hence the short prefixes. */
    static boolean isRenderThread(String name) {
        return "Render thread".equals(name) || "Client thread".equals(name);
    }

    /**
     * Background work that can wait a little without the player noticing: Minecraft's worker
     * pool (chunk and world tasks), its disk workers and Sodium's chunk builders. Never the
     * render thread, the integrated server, the network threads (PvP needs those prompt), the
     * garbage collector or the JIT compiler.
     */
    static boolean isHelperThread(String name) {
        if (name == null) return false;
        return name.startsWith("Worker-Main") || name.startsWith("Worker-Bootstr")
                || name.startsWith("IO-Worker") || name.startsWith("Sodium")
                || name.startsWith("Chunk Render") || name.startsWith("Chunk Builder");
    }

    /** Priority value for the voice chat's recording and playback threads (lower is more urgent). */
    static final int VOICE_NICE = -8;

    /**
     * Threads of a voice chat mod that record, play or send the voice. If they wait for a core,
     * the voice cuts out. The names are matched loosely, because Android keeps only the last 15
     * characters of a long thread name and mods name their threads differently.
     */
    static boolean isVoiceThread(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        // e.g. VoiceChatPacket.., MicrophoneThrea.., SoundSourceThre.. (the kernel keeps 15 characters)
        return lower.contains("voice") || lower.contains("microphone") || lower.contains("capture")
                || lower.contains("soundsource");
    }

    static int niceFor(int level) {
        return level >= LEVEL_STRONG ? 10 : level == LEVEL_MILD ? 4 : 0;
    }
}
