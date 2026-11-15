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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The text that "Copy game log" puts on the clipboard, for bug reports. It has to fit in a chat
 * message, so a long log is cut down to the parts that matter: the start (launcher, renderer and
 * driver settings), the lines from the middle that name an error or the graphics driver (each
 * kind once, with its line number), and the end (where a freeze or a crash shows). Stack traces
 * are folded, long lines are cut and anything that looks like a login token is hidden. The lines
 * are the log's own text, so they are in English.
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
            "ERROR|FATAL|Exception|Caused by|WARNING:|(?i:crash|out ?of ?memory|shader|renderer|opengl|vulkan|zink|mesa|egl|glfw|lwjgl|bigcore)"
                    + "|\\[(?:RefreshRate|PerformanceMode|ThermalGuard|Startup|BigCore|Perf|LaunchGuard)\\]");
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
        long total = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            total++;
            String raw = line.length() > MAX_SEARCH_CHARS ? line.substring(0, MAX_SEARCH_CHARS) : line;
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

        String text = assemble(head, keys, tailText, total, tailFirst);
        while (text.length() > budgetChars) {
            if (!keys.isEmpty()) keys.remove(keys.size() - 1);
            else if (head.size() > 16) head.remove(head.size() - 1);
            else if (tailText.size() > 20) {
                tailText.remove(0);
                tailFirst++;
            } else break;
            text = assemble(head, keys, tailText, total, tailFirst);
        }
        return text;
    }

    private static String assemble(List<String> head, List<String> keys, List<String> tail, long total, long tailFirst) {
        StringBuilder text = new StringBuilder();
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
}
