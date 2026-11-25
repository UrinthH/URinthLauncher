package dev.urinth.recordable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A small text file that says what the recorder found out about the phone and what it did, so that a problem (for example "the
 * phone's video encoder did not start") can be understood without guessing. It is rewritten at the start and at the end of every
 * recording and lives next to the game's log.
 */
final class Report {
    private static volatile String header = "Record-able Lite";

    private final Path file;
    private final List<String> lines = new ArrayList<>();

    Report(Path file) {
        this.file = file;
    }

    /** Where the report of a game folder is kept. */
    static Path fileFor(Path gameDir) {
        return gameDir.resolve("logs").resolve("record-able-report.txt");
    }

    /** The first line of every report, normally the mod's name and version. */
    static void setHeader(String text) {
        header = text == null || text.isBlank() ? "Record-able Lite" : text;
    }

    Path file() {
        return file;
    }

    synchronized void add(String line) {
        lines.add(line);
    }

    synchronized void addAll(Collection<String> more) {
        lines.addAll(more);
    }

    synchronized List<String> snapshot() {
        return new ArrayList<>(lines);
    }

    /** Writes the report; a problem with writing it is never a problem for the game. */
    synchronized void write() {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder b = new StringBuilder();
            b.append(header).append(" - report of ").append(LocalDateTime.now().withNano(0)).append('\n');
            for (String l : lines) {
                b.append(l).append('\n');
            }
            Files.write(file, b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) {
            // the report is a convenience
        }
    }

    /** Facts about the machine and the game folder, for the top of a report. */
    static List<String> environment(Path gameDir) {
        List<String> l = new ArrayList<>();
        l.add("system: " + prop("os.name") + " " + prop("os.version") + " " + prop("os.arch") + ", Java " + prop("java.version")
                + " (" + prop("java.vendor") + ")");
        l.add("java.home: " + prop("java.home"));
        l.add("game folder: " + gameDir + "; temp folder: " + prop("java.io.tmpdir") + "; home: " + prop("user.home"));
        String abi = System.getenv("ANDROID_ROOT");
        l.add("android: " + (abi != null ? "yes (" + abi + ")" : "not detected"));
        return l;
    }

    /** The lines of a report file that matter most, for showing in the game's chat. */
    static List<String> summary(Path file) {
        List<String> out = new ArrayList<>();
        try {
            if (!Files.isRegularFile(file)) {
                out.add("No recording was started in this game yet, so there is no report. Report file: " + file);
                return out;
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                boolean skip = line.isBlank();
                for (String key : SKIPPED_IN_CHAT) {
                    skip |= line.startsWith(key);
                }
                if (!skip) {
                    out.add(line.length() > 230 ? line.substring(0, 227) + "..." : line);
                }
            }
            out.add("Full report: " + file);
        } catch (IOException | RuntimeException e) {
            out.add("The report could not be read: " + e);
        }
        return out;
    }

    /** Long lines that tell little to a player; they stay in the file. */
    private static final String[] SKIPPED_IN_CHAT = {"settings:", "java.home:", "game folder:", "android:", "Record-able Lite"};

    private static String prop(String key) {
        String v = System.getProperty(key);
        return v == null ? "?" : v;
    }
}
