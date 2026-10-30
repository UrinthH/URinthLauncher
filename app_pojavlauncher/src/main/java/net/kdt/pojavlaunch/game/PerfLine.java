package net.kdt.pojavlaunch.game;

/** The text of a {@link PerfReport} line and the readers behind it. No Android classes, so it is unit tested. */
final class PerfLine {
    private PerfLine() {}

    /** Pure part of the report line, so it can be tested without a phone. */
    static String describe(long[] stat, long[] before, long elapsedMs, long rssMb, long availMb,
                           boolean lowMemory, int thermalStatus, String loadAvg) {
        StringBuilder out = new StringBuilder();
        out.append("game memory ").append(rssMb < 0 ? "?" : rssMb + " MB");
        out.append(", system free ").append(availMb < 0 ? "?" : availMb + " MB");
        if (lowMemory) out.append(" (LOW)");
        if (stat != null && before != null && elapsedMs > 0) {
            long majorFaults = stat[0] - before[0];
            double cpuSeconds = (stat[1] - before[1] + stat[2] - before[2]) / 100.0; // clock ticks, 100 per second
            out.append(", major page faults ").append(Math.max(0, majorFaults));
            out.append(", game CPU ").append(Math.round(100.0 * cpuSeconds / (elapsedMs / 1000.0))).append("% of one core");
        }
        if (thermalStatus >= 0) out.append(", heat level ").append(thermalStatus);
        String load = loadAverage(loadAvg);
        if (!load.isEmpty()) out.append(", phone load ").append(load);
        return out.toString();
    }

    /**
     * Reads {major page faults, user ticks, system ticks} from /proc/self/stat. The process name
     * sits in brackets and may itself hold spaces or brackets, so the fields are counted from the
     * last closing bracket. Returns null when the text is not a stat line.
     */
    static long[] parseStat(String text) {
        if (text == null) return null;
        int close = text.lastIndexOf(')');
        if (close < 0) return null;
        String[] fields = text.substring(close + 1).trim().split("\\s+");
        // After the name: state(0) ppid(1) pgrp(2) session(3) tty(4) tpgid(5) flags(6) minflt(7)
        // cminflt(8) majflt(9) cmajflt(10) utime(11) stime(12)
        if (fields.length < 13) return null;
        try {
            return new long[]{Long.parseLong(fields[9]), Long.parseLong(fields[11]), Long.parseLong(fields[12])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** VmRSS of /proc/self/status in megabytes, or -1. */
    static long residentMegabytes(String status) {
        if (status == null) return -1;
        for (String line : status.split("\n")) {
            if (line.startsWith("VmRSS:")) {
                String digits = line.replaceAll("[^0-9]", "");
                if (digits.isEmpty()) return -1;
                try {
                    return Long.parseLong(digits) / 1024;
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /** The first three numbers of /proc/loadavg ("1.20 0.90 0.70"), or "". */
    static String loadAverage(String text) {
        if (text == null) return "";
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 3) return "";
        return parts[0] + " / " + parts[1] + " / " + parts[2];
    }
}
