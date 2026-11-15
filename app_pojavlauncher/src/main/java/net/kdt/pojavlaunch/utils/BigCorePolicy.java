package net.kdt.pojavlaunch.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Decides whether the "Force the renderer to run on the big core" setting should be passed on to
 * the game, by working out the core the game library would pin and checking that it is a strong one.
 *
 * <p>The library walks cpu0, cpu1, ... while the file cpufreq/cpuinfo_max_freq can be opened and
 * remembers the <em>last</em> core whose top speed is the highest so far (it compares with "&gt;=").
 * On a chip whose cores all report the same top speed that is the last core, which on the usual
 * layouts (slow cores first, fast cores last) is a fast one. If the first speed cannot be read at
 * all, the library falls back to cpu0.
 *
 * <p>The setting is passed on unless the Linux capacity numbers (cpu_capacity, when the phone
 * reports them) show that the core the library would pick is weaker than another core, or the
 * library would be left to guess cpu0. The game log gets one line that says what was found.
 */
public final class BigCorePolicy {
    /** What to do for this launch, and a line for the game log that says why. */
    public static final class Verdict {
        public final boolean pin;
        public final String note;

        Verdict(boolean pin, String note) {
            this.pin = pin;
            this.note = note;
        }
    }

    private static final int MAX_CORES = 64;
    private static final long NOT_READABLE = Long.MIN_VALUE;

    private BigCorePolicy() {}

    /**
     * @param maxFreqKhz the top speed, in kHz, of the cores the library can read: cpu0 up to the
     *                   first core whose speed file cannot be opened (see {@link #readMaxFrequenciesKhz()})
     * @param capacity   cpu_capacity of every core in order; zero or less where it is not reported
     */
    public static Verdict decide(long[] maxFreqKhz, long[] capacity) {
        String speeds = describe(maxFreqKhz, 1000, "MHz");
        String sizes = hasAny(capacity) ? describe(capacity, 1, "") : "not reported by this phone";
        String seen = "core speeds: " + speeds + "; capacities: " + sizes + "; ";
        if (maxFreqKhz.length == 0) {
            return new Verdict(false, seen + "the game library cannot read any core speed and would fall back to cpu0, "
                    + "which is a guess, so the setting is skipped for this launch");
        }
        int pick = pickedCore(maxFreqKhz);
        long pickedCapacity = pick < capacity.length ? capacity[pick] : 0;
        long strongest = 0;
        for (long value : capacity) strongest = Math.max(strongest, value);
        if (pickedCapacity > 0 && strongest > pickedCapacity) {
            return new Verdict(false, seen + "the game library picks the last core with the top speed, cpu" + pick
                    + ", but its capacity " + pickedCapacity + " is below the strongest core's " + strongest
                    + ", so the setting is skipped for this launch");
        }
        String strength = pickedCapacity > 0 ? " (capacity " + pickedCapacity + " of " + strongest + ")" : "";
        return new Verdict(true, seen + "the game library picks the last core with the top speed: cpu" + pick
                + strength + ", so the setting is passed on");
    }

    /** The core the game library picks: the last one whose speed is at least as high as every one before it. */
    static int pickedCore(long[] maxFreqKhz) {
        long highest = 0;
        int pick = 0;
        for (int core = 0; core < maxFreqKhz.length; core++) {
            if (maxFreqKhz[core] >= highest) {
                highest = maxFreqKhz[core];
                pick = core;
            }
        }
        return pick;
    }

    private static boolean hasAny(long[] values) {
        for (long value : values) if (value > 0) return true;
        return false;
    }

    /** "cpu0-5 1600 MHz, cpu6-7 2000 MHz": runs of cores that report the same value, in order. */
    static String describe(long[] values, int divisor, String unit) {
        if (values.length == 0) return "none readable";
        StringBuilder text = new StringBuilder();
        int start = 0;
        for (int core = 1; core <= values.length; core++) {
            if (core < values.length && values[core] == values[start]) continue;
            if (text.length() > 0) text.append(", ");
            text.append("cpu").append(start);
            if (core - 1 > start) text.append('-').append(core - 1);
            text.append(' ');
            if (values[start] <= 0) {
                text.append('?');
            } else {
                text.append(values[start] / divisor);
                if (!unit.isEmpty()) text.append(' ').append(unit);
            }
            start = core;
        }
        return text.toString();
    }

    /**
     * The top speed in kHz of cpu0, cpu1, ... up to the first core whose speed file cannot be
     * opened, which is the stretch of cores the game library looks at.
     */
    public static long[] readMaxFrequenciesKhz() {
        long[] speeds = new long[MAX_CORES];
        int count = 0;
        while (count < MAX_CORES) {
            long speed = readNumber(new File("/sys/devices/system/cpu/cpu" + count + "/cpufreq/cpuinfo_max_freq"));
            if (speed == NOT_READABLE) break;
            speeds[count++] = speed;
        }
        return java.util.Arrays.copyOf(speeds, count);
    }

    /** cpu_capacity of every core; zero for a core that does not report it. */
    public static long[] readCapacities() {
        int cores = 0;
        while (cores < MAX_CORES && new File("/sys/devices/system/cpu/cpu" + cores).isDirectory()) cores++;
        long[] capacities = new long[cores];
        for (int core = 0; core < cores; core++) {
            long value = readNumber(new File("/sys/devices/system/cpu/cpu" + core + "/cpu_capacity"));
            capacities[core] = value == NOT_READABLE ? 0 : value;
        }
        return capacities;
    }

    /** The number in the file, zero when the text is not a number, NOT_READABLE when it cannot be opened. */
    private static long readNumber(File file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) return 0;
            try {
                return Long.parseLong(line.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        } catch (IOException e) {
            return NOT_READABLE;
        }
    }
}
