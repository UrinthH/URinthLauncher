package net.kdt.pojavlaunch.prefs.screens;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The text that "Copy game log" puts on the clipboard, for bug reports. A game log can be many
 * thousands of lines, so this keeps the start (launcher, renderer and driver lines) and the end
 * (where a freeze or a crash shows), cuts very long lines and hides anything that looks like a
 * login token. The lines are the log's own text, so they are in English.
 */
final class GameLogExcerpt {
    /** Lines kept from the start of the log. */
    static final int HEAD_LINES = 220;
    /** Lines kept from the end of the log. */
    static final int TAIL_LINES = 160;
    /** A line longer than this is cut (Java class paths and launch arguments can be very long). */
    static final int MAX_LINE_CHARS = 400;
    /** Longer than this is cut before the token search, which only keeps the search fast. */
    private static final int MAX_SEARCH_CHARS = 20_000;
    private static final String HIDDEN = "[hidden]";

    private static final Pattern LAUNCH_ARGUMENT = Pattern.compile("(?i)(--(?:accessToken|xuid|clientId)\\s+)\\S+");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{16,}");
    private static final Pattern WEB_TOKEN = Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{5,}");
    private static final Pattern NAMED_TOKEN = Pattern.compile(
            "(?i)((?:access_?token|refresh_?token|id_?token|xbl_?token|xsts_?token|session_?id)[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}&]+");

    private GameLogExcerpt() {}

    /** The excerpt of a log file, or null when there is no log or it is empty. */
    static String fromFile(File file, int headLines, int tailLines) throws IOException {
        if (file == null || !file.isFile() || file.length() == 0) return null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            return fromReader(reader, headLines, tailLines);
        }
    }

    /**
     * The first {@code headLines} and the last {@code tailLines} lines, with a line that says how
     * many were left out between them. A log that fits is returned whole. Null for an empty log.
     */
    static String fromReader(BufferedReader reader, int headLines, int tailLines) throws IOException {
        List<String> head = new ArrayList<>();
        ArrayDeque<String> tail = new ArrayDeque<>();
        long total = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            total++;
            if (head.size() < headLines) {
                head.add(clean(line));
            } else if (tailLines > 0) {
                // Searched for tokens only when it is certain to be kept (below), to stay quick on a huge log.
                tail.addLast(line.length() > MAX_SEARCH_CHARS ? line.substring(0, MAX_SEARCH_CHARS) : line);
                if (tail.size() > tailLines) tail.removeFirst();
            }
        }
        if (total == 0) return null;

        long leftOut = total - head.size() - tail.size();
        StringBuilder text = new StringBuilder();
        for (String kept : head) text.append(kept).append('\n');
        if (leftOut > 0) text.append("... ").append(leftOut).append(leftOut == 1 ? " line" : " lines").append(" left out ...\n");
        for (String kept : tail) text.append(clean(kept)).append('\n');
        return text.toString();
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
