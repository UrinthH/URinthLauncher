package dev.urinth.optimizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What the system says about memory, read from /proc (Linux and Android). Every answer is -1 when it cannot be read (another
 * system, a phone that does not allow it), and the callers then simply go without it. The parsing is separate from the reading so it
 * can be tested.
 */
final class SystemMemory {
    private SystemMemory() {}

    private static final Path MEMINFO = Path.of("/proc/meminfo");
    private static final Path SELF_STAT = Path.of("/proc/self/stat");

    /** Bytes the system could still give to a program without taking them from another (MemAvailable), or -1. */
    static long availableBytes() {
        long kb = kilobytes(read(MEMINFO), "MemAvailable");
        return kb < 0 ? -1 : kb * 1024L;
    }

    /** Major page faults of this process since it started, or -1. */
    static long majorFaults() {
        return majorFaults(read(SELF_STAT));
    }

    /** The value of a "Key:   123 kB" line of /proc/meminfo, in kilobytes, or -1. */
    static long kilobytes(String meminfo, String key) {
        if (meminfo == null) return -1;
        String prefix = key + ":";
        int from = 0;
        while (from < meminfo.length()) {
            int end = meminfo.indexOf('\n', from);
            if (end < 0) end = meminfo.length();
            if (meminfo.startsWith(prefix, from)) {
                String rest = meminfo.substring(from + prefix.length(), end).trim();
                int space = rest.indexOf(' ');
                if (space > 0) rest = rest.substring(0, space);
                try {
                    long value = Long.parseLong(rest);
                    return value < 0 ? -1 : value;
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
            from = end + 1;
        }
        return -1;
    }

    /**
     * The "majflt" field (the 12th) of a /proc/PID/stat line. The second field is the program name in brackets and may hold spaces
     * and brackets itself, so the fields are counted from the last closing bracket.
     */
    static long majorFaults(String stat) {
        if (stat == null) return -1;
        int close = stat.lastIndexOf(')');
        if (close < 0 || close + 2 >= stat.length()) return -1;
        String[] fields = stat.substring(close + 2).trim().split("\\s+");
        // after the name: state(3) ppid(4) pgrp(5) session(6) tty(7) tpgid(8) flags(9) minflt(10) cminflt(11) majflt(12)
        if (fields.length < 10) return -1;
        try {
            long value = Long.parseLong(fields[9]);
            return value < 0 ? -1 : value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
