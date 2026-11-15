package net.kdt.pojavlaunch.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Decides whether the "Force the renderer to run on the big core" setting can do anything useful
 * on this chip.
 *
 * <p>The library that does the pinning picks one core from the top speed each core reports in
 * sysfs (cpuinfo_max_freq). On a chip whose cores all report the same top speed, that pick is
 * arbitrary and can land on a slow core. The Unisoc T606 is such a chip: two fast and six slow
 * cores that all report 1.6 GHz. So the launcher leaves the setting out of that launch when it
 * can see that every core reports the same speed, and says so in the game log.
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

    private BigCorePolicy() {}

    /**
     * @param maxFreqKhz the top speed each core reports, in kHz; zero or less for a core whose
     *                   speed could not be read
     */
    public static Verdict decide(long[] maxFreqKhz) {
        int known = 0;
        long slowest = Long.MAX_VALUE;
        long fastest = 0;
        int fastestCore = -1;
        for (int core = 0; core < maxFreqKhz.length; core++) {
            long speed = maxFreqKhz[core];
            if (speed <= 0) continue;
            known++;
            if (speed < slowest) slowest = speed;
            if (speed > fastest) {
                fastest = speed;
                fastestCore = core;
            }
        }
        String layout = describe(maxFreqKhz, 1000, "MHz");
        if (known < 2) {
            return new Verdict(true, "core speeds could not be read here (" + layout + "), so the setting is passed on as it is");
        }
        if (slowest == fastest) {
            return new Verdict(false, "every core reports the same top speed (" + layout
                    + "), so there is no faster core to pick; the setting is skipped for this launch");
        }
        return new Verdict(true, "core speeds: " + layout + "; cpu" + fastestCore
                + " reports the highest, so the game library can pick a fast core");
    }

    /** "cpu0-5 1600 MHz, cpu6-7 2000 MHz": runs of cores that report the same value, in order. */
    static String describe(long[] values, int divisor, String unit) {
        if (values.length == 0) return "no cores listed";
        StringBuilder text = new StringBuilder();
        int start = 0;
        for (int core = 1; core <= values.length; core++) {
            if (core < values.length && values[core] == values[start]) continue;
            if (text.length() > 0) text.append(", ");
            text.append("cpu").append(start);
            if (core - 1 > start) text.append('-').append(core - 1);
            text.append(' ');
            if (values[start] <= 0) text.append('?');
            else text.append(values[start] / divisor).append(' ').append(unit);
            start = core;
        }
        return text.toString();
    }

    /** The top speed of every core in kHz, from sysfs; zero for a core that does not say. */
    public static long[] readMaxFrequenciesKhz() {
        int cores = 0;
        while (cores < MAX_CORES && new File("/sys/devices/system/cpu/cpu" + cores).isDirectory()) cores++;
        long[] speeds = new long[cores];
        for (int core = 0; core < cores; core++) {
            speeds[core] = readNumber(new File("/sys/devices/system/cpu/cpu" + core + "/cpufreq/cpuinfo_max_freq"));
        }
        return speeds;
    }

    private static long readNumber(File file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? 0 : Long.parseLong(line.trim());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }
}
