package net.kdt.pojavlaunch.prefs.screens;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text that "Copy game log" puts on the clipboard, for bug reports. It has to fit in a chat
 * message, so a long log is cut down to the parts that matter: the start (launcher, renderer and
 * driver settings), the lines from the middle that name an error or the graphics driver (each
 * kind once, with its line number), and the end (where a freeze or a crash shows). Above them goes
 * a short summary counted over the whole log (memory-collection pauses, how often the system
 * lowered the screen's refresh rate), since a lag report needs those numbers and a cut-down log
 * cannot show them. Stack traces are folded, long lines are cut and anything that looks like a
 * login token is hidden. The lines are the log's own text, so they are in English.
 */
final class GameLogExcerpt {
    /** Lines kept from the start of the log. */
    static final int HEAD_LINES = 40;
    /** Most lines kept from the middle of the log. */
    static final int KEY_LINES = 40;
    /** Lines kept from the end of the log. */
    static final int TAIL_LINES = 70;
    /** The whole excerpt stays under this many characters, so a chat message can hold it. */
    static final int BUDGET_CHARS = 15_000;
    /** A line longer than this is cut (Java class paths and launch arguments can be very long). */
    static final int MAX_LINE_CHARS = 180;
    /** Longer than this is cut before the token search, which only keeps the search fast. */
    private static final int MAX_SEARCH_CHARS = 20_000;
    /** How many different middle lines are remembered while reading, before the best are picked. */
    private static final int MAX_CANDIDATES = 400;
    /** The first lines of a stack trace that are kept; the rest are folded into one line. */
    private static final int FRAMES_KEPT = 2;
    private static final String HIDDEN = "[hidden]";

    private static final Pattern LAUNCH_ARGUMENT = Pattern.compile("(?i)(--(?:accessToken|xuid|clientId)\\s+)\\S+");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{16,}");
    private static final Pattern WEB_TOKEN = Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{5,}");
    private static final Pattern NAMED_TOKEN = Pattern.compile(
            "(?i)((?:access_?token|refresh_?token|id_?token|xbl_?token|xsts_?token|session_?id)[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}&]+");

    /** A line that names an error, the graphics driver or one of the launcher's own tags. */
    private static final Pattern KEY_LINE = Pattern.compile(
            "ERROR|FATAL|Exception|Caused by|WARNING:|Pause Full|(?i:crash|out ?of ?memory|shader|renderer|opengl|vulkan|zink|mesa|egl|glfw|lwjgl|bigcore)"
                    + "|\\[(?:RefreshRate|PerformanceMode|ThermalGuard|Startup|BigCore|Perf|LaunchGuard|MGConfig)\\]");
    private static final Pattern STACK_FRAME = Pattern.compile("^\\s+(?:at |\\.\\.\\. \\d+ more).*");

    private GameLogExcerpt() {}

    /** The excerpt of a log file, or null when there is no log or it is empty. */
    static String fromFile(File file) throws IOException {
        if (file == null || !file.isFile() || file.length() == 0) return null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            return fromReader(reader, HEAD_LINES, KEY_LINES, TAIL_LINES, BUDGET_CHARS);
        }
    }

    /**
     * The first {@code headLines}, up to {@code keyLines} telling lines from the middle and the
     * last {@code tailLines}, within {@code budgetChars}. A log that fits is returned whole. Null
     * for an empty log.
     */
    static String fromReader(BufferedReader reader, int headLines, int keyLines, int tailLines, int budgetChars)
            throws IOException {
        List<String> head = new ArrayList<>();
        ArrayDeque<String> tail = new ArrayDeque<>();
        List<Integer> candidateNumbers = new ArrayList<>();
        List<String> candidateLines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        LogStats stats = new LogStats();
        long total = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            total++;
            String raw = line.length() > MAX_SEARCH_CHARS ? line.substring(0, MAX_SEARCH_CHARS) : line;
            stats.accept(raw);
            if (head.size() < headLines) {
                head.add(clean(raw));
                continue;
            }
            if (tailLines > 0) {
                tail.addLast(raw);
                if (tail.size() > tailLines) tail.removeFirst();
            }
            if (keyLines > 0 && candidateLines.size() < MAX_CANDIDATES
                    && !STACK_FRAME.matcher(raw).matches() && KEY_LINE.matcher(raw).find()
                    && seen.add(signature(raw))) {
                candidateNumbers.add((int) Math.min(total, Integer.MAX_VALUE));
                candidateLines.add(raw);
            }
        }
        if (total == 0) return null;

        long tailFirst = total - tail.size() + 1; // line number of the first line of the end section
        List<String> tailText = new ArrayList<>();
        for (String kept : tail) tailText.add(clean(kept));
        tailText = foldStackTraces(tailText);

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < candidateLines.size() && keys.size() < keyLines; i++) {
            if (candidateNumbers.get(i) >= tailFirst) break; // already in the end of the log
            keys.add("#" + candidateNumbers.get(i) + " " + clean(candidateLines.get(i)));
        }

        List<String> summary = stats.summaryLines();
        String text = assemble(summary, head, keys, tailText, total, tailFirst);
        while (text.length() > budgetChars) {
            if (!keys.isEmpty()) keys.remove(keys.size() - 1);
            else if (head.size() > 16) head.remove(head.size() - 1);
            else if (tailText.size() > 20) {
                tailText.remove(0);
                tailFirst++;
            } else break;
            text = assemble(summary, head, keys, tailText, total, tailFirst);
        }
        return text;
    }

    private static String assemble(List<String> summary, List<String> head, List<String> keys, List<String> tail,
                                   long total, long tailFirst) {
        StringBuilder text = new StringBuilder();
        if (!summary.isEmpty()) {
            text.append("--- summary counted over the whole log ---\n");
            for (String kept : summary) text.append(kept).append('\n');
            text.append("--- the log itself ---\n");
        }
        for (String kept : head) text.append(kept).append('\n');
        if (keys.isEmpty()) {
            long leftOut = tailFirst - 1 - head.size();
            if (leftOut > 0) text.append("... ").append(leftOut).append(leftOut == 1 ? " line" : " lines").append(" left out ...\n");
        } else {
            text.append("--- lines from the middle of the log that name an error or the graphics driver (line number first) ---\n");
            for (String kept : keys) text.append(kept).append('\n');
            text.append("--- end of the log (").append(total).append(" lines in all) ---\n");
        }
        for (String kept : tail) text.append(kept).append('\n');
        return text.toString();
    }

    /** Keeps the first lines of every run of stack frames and folds the rest into one line. */
    static List<String> foldStackTraces(List<String> lines) {
        List<String> folded = new ArrayList<>();
        int run = 0;
        for (String line : lines) {
            if (STACK_FRAME.matcher(line).matches()) {
                run++;
                if (run <= FRAMES_KEPT) folded.add(line);
                continue;
            }
            addFoldNote(folded, run);
            run = 0;
            folded.add(line);
        }
        addFoldNote(folded, run);
        return folded;
    }

    private static void addFoldNote(List<String> folded, int run) {
        if (run > FRAMES_KEPT) {
            int hidden = run - FRAMES_KEPT;
            folded.add("\t... " + hidden + " more stack " + (hidden == 1 ? "line" : "lines"));
        }
    }

    /** Two lines that differ only in numbers (times, counters, addresses) count as the same kind. */
    private static String signature(String line) {
        String digitsGone = line.replaceAll("[0-9]+", "#");
        return digitsGone.length() > 90 ? digitsGone.substring(0, 90) : digitsGone;
    }

    /** One line, ready to copy: tokens hidden first, then cut, so no half of a token is left behind. */
    static String clean(String line) {
        String safe = hideSecrets(line.length() > MAX_SEARCH_CHARS ? line.substring(0, MAX_SEARCH_CHARS) : line);
        return safe.length() > MAX_LINE_CHARS ? safe.substring(0, MAX_LINE_CHARS) + " ... [cut]" : safe;
    }

    static String hideSecrets(String line) {
        String safe = LAUNCH_ARGUMENT.matcher(line).replaceAll("$1" + HIDDEN);
        safe = BEARER.matcher(safe).replaceAll("$1" + HIDDEN);
        safe = WEB_TOKEN.matcher(safe).replaceAll(HIDDEN);
        safe = NAMED_TOKEN.matcher(safe).replaceAll("$1" + HIDDEN);
        return safe;
    }

    /**
     * Numbers counted over every line of the log while it is read: the memory-collection pauses the
     * JVM logs ("[71.2s][info][gc] GC(18) Pause Young (Mixed) ... 94.223ms") and how often the
     * launcher noted that the system lowered the screen's refresh rate.
     */
    static final class LogStats {
        private static final Pattern GC_PAUSE = Pattern.compile(
                "^\\[(\\d+(?:\\.\\d+)?)s\\]\\[info\\s*\\]\\[gc\\s*\\]\\s+GC\\(\\d+\\)\\s+Pause\\s+(\\w+).*?\\s(\\d+(?:\\.\\d+)?)ms\\s*$");
        private static final Pattern GC_CYCLE_END = Pattern.compile(
                "^\\[(\\d+(?:\\.\\d+)?)s\\]\\[info\\s*\\]\\[gc\\s*\\]\\s+GC\\(\\d+\\)\\s+Concurrent Mark Cycle\\s+(\\d+(?:\\.\\d+)?)ms\\s*$");
        private static final String REFRESH_LOWERED = "[RefreshRate] the system lowered";

        private int pauses;
        private double totalMs;
        private double worstMs;
        private String worstKind = "";
        private int over50;
        private int over100;
        private int over200;
        private int full;
        private int cycles;
        private double longestCycleMs;
        private double lastStampSeconds;
        private int refreshLowered;

        void accept(String line) {
            if (line.isEmpty()) return;
            if (line.charAt(0) == '[' && line.indexOf("][gc") > 0) {
                Matcher pause = GC_PAUSE.matcher(line);
                if (pause.find()) {
                    double ms = Double.parseDouble(pause.group(3));
                    pauses++;
                    totalMs += ms;
                    if (ms > worstMs) {
                        worstMs = ms;
                        worstKind = pause.group(2);
                    }
                    if (ms > 50) over50++;
                    if (ms > 100) over100++;
                    if (ms > 200) over200++;
                    if ("Full".equals(pause.group(2))) full++;
                    lastStampSeconds = Math.max(lastStampSeconds, Double.parseDouble(pause.group(1)));
                    return;
                }
                Matcher cycle = GC_CYCLE_END.matcher(line);
                if (cycle.find()) {
                    cycles++;
                    longestCycleMs = Math.max(longestCycleMs, Double.parseDouble(cycle.group(2)));
                    lastStampSeconds = Math.max(lastStampSeconds, Double.parseDouble(cycle.group(1)));
                }
                return;
            }
            if (line.contains(REFRESH_LOWERED)) refreshLowered++;
        }

        /** The summary lines; empty when the log holds nothing to count. */
        List<String> summaryLines() {
            List<String> lines = new ArrayList<>();
            if (pauses > 0) {
                StringBuilder text = new StringBuilder("Memory (GC) pauses: ").append(pauses);
                if (lastStampSeconds >= 1) text.append(" in ").append(duration(lastStampSeconds));
                text.append(", ").append(String.format(Locale.US, "%.1f", totalMs / 1000)).append(" s paused in total; worst ")
                        .append(Math.round(worstMs)).append(" ms (").append(worstKind).append("); over 50 ms: ").append(over50)
                        .append(", over 100 ms: ").append(over100).append(", over 200 ms: ").append(over200)
                        .append("; full collections: ").append(full);
                if (cycles > 0) {
                    text.append("; background cycles: ").append(cycles).append(" (longest ")
                            .append(Math.round(longestCycleMs)).append(" ms)");
                }
                lines.add(text.toString());
            }
            if (refreshLowered > 0) {
                lines.add("Screen refresh rate: the system lowered it " + refreshLowered
                        + (refreshLowered == 1 ? " time" : " times") + " (the launcher asked for it back each time)");
            }
            return lines;
        }

        private static String duration(double seconds) {
            if (seconds < 120) return Math.round(seconds) + " s";
            return Math.round(seconds / 60) + " min";
        }
    }
}
