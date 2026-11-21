package net.kdt.pojavlaunch.game;

/**
 * Works out which CPU cores of the phone are the fast ones, with no Android code in it so it can be
 * tested on a plain JVM.
 *
 * Phones mix strong and weak cores. A strong core can finish the same work in about half the time,
 * and the game's render thread is the one thread whose speed sets the frame rate. The usual way to
 * tell the cores apart is the Linux "capacity" number each core reports (cpu_capacity, 1024 for the
 * strongest). Some chips give every core the same top clock speed and differ only in the core design,
 * so the clock speed cannot be trusted alone: the Unisoc T606 in the Infinix Smart 8 reports 1612 MHz
 * on all eight cores, and capacities of 544 for cpu0-5 and 1024 for cpu6-7.
 *
 * Cores are numbered from 0 and a core is a bit in a mask (bit n is cpu n), so up to 63 cores fit in
 * a long.
 */
final class FastCores {
    static final int MAX_CPUS = 63;
    /** A core is in the top group when it reaches this share of the strongest core's value. */
    private static final double TOP_SHARE = 0.85;
    /** The strongest core must beat the weakest by at least this much, else the cores are all alike. */
    private static final double MIN_SPREAD = 1.10;

    private FastCores() {}

    /**
     * The mask of the fast cores, or 0 when the phone's cores cannot be told apart (every core alike,
     * or the numbers are missing for some of them, so a guess would be a coin flip).
     *
     * @param capacity    cpu_capacity of every core in order, zero or less where it is not reported
     * @param maxFreqKhz  top clock speed of every core in order, zero or less where it is not reported
     */
    static long maskFor(long[] capacity, long[] maxFreqKhz) {
        long fromCapacity = fromValues(capacity);
        return fromCapacity != 0 ? fromCapacity : fromValues(maxFreqKhz);
    }

    /**
     * The cores at the top of the list of numbers. When only one core is that high (a prime core on
     * a three-tier chip), the whole next tier joins it, so the render thread always has at least two
     * cores to move between and is not boxed in with the one core other threads also want.
     */
    private static long fromValues(long[] values) {
        if (values == null || values.length < 2) return 0;
        int count = Math.min(values.length, MAX_CPUS);
        long strongest = 0;
        long weakest = Long.MAX_VALUE;
        for (int cpu = 0; cpu < count; cpu++) {
            if (values[cpu] <= 0) return 0;
            strongest = Math.max(strongest, values[cpu]);
            weakest = Math.min(weakest, values[cpu]);
        }
        if (strongest < weakest * MIN_SPREAD) return 0;
        long mask = 0;
        int top = 0;
        long nextBest = 0;
        for (int cpu = 0; cpu < count; cpu++) {
            if (values[cpu] >= strongest * TOP_SHARE) {
                mask |= 1L << cpu;
                top++;
            } else {
                nextBest = Math.max(nextBest, values[cpu]);
            }
        }
        if (top == 1 && nextBest > 0) {
            for (int cpu = 0; cpu < count; cpu++) {
                if (values[cpu] == nextBest) mask |= 1L << cpu;
            }
        }
        return mask;
    }

    static boolean contains(long mask, int cpu) {
        return cpu >= 0 && cpu < MAX_CPUS && (mask & (1L << cpu)) != 0;
    }

    static int count(long mask) {
        return Long.bitCount(mask & ((1L << MAX_CPUS) - 1));
    }

    /** "cpu6-7", "cpu0-3, cpu6", "none" for an empty mask. */
    static String describe(long mask) {
        StringBuilder text = new StringBuilder();
        int cpu = 0;
        while (cpu < MAX_CPUS) {
            if (!contains(mask, cpu)) {
                cpu++;
                continue;
            }
            int end = cpu;
            while (end + 1 < MAX_CPUS && contains(mask, end + 1)) end++;
            if (text.length() > 0) text.append(", ");
            text.append("cpu").append(cpu);
            if (end > cpu) text.append('-').append(end);
            cpu = end + 1;
        }
        return text.length() == 0 ? "none" : text.toString();
    }
}
