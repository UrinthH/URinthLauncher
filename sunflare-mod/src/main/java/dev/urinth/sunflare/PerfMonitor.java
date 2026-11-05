package dev.urinth.sunflare;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Finds out where stutter and freezes come from, instead of guessing. Every frame time goes into {@link FrameStats}; when a frame
 * is a hitch or a freeze, one line is written to the stutter log with what else was going on: garbage collections since the last
 * line (how many, how long), how full the heap is, and what the frame-rate guard was doing. A hitch with a long collection next
 * to it is a memory problem; a hitch with none is something else (chunks, shaders, the effects).
 *
 * It costs nothing per frame (no garbage, no I/O). Lines are written by a background thread, at most four per second, and the
 * file stops growing at {@value #MAX_LOG_BYTES} bytes. Nothing here touches options.txt or the resolution.
 */
final class PerfMonitor {
    static final long MAX_LOG_BYTES = 256 * 1024;
    private static final long MIN_GAP_NANOS = 250_000_000L;

    private final FrameStats stats = new FrameStats();
    private final Path logFile;
    private final BooleanSupplier logOn;
    private final Supplier<String> state;
    private final List<GarbageCollectorMXBean> collectors;
    private long lastGcCount;
    private long lastGcMillis;
    private long lastLine;
    private long linesWritten;
    private long linesSkipped;
    private ExecutorService writer;

    PerfMonitor(Path logFile, BooleanSupplier logOn, Supplier<String> state) {
        this.logFile = logFile;
        this.logOn = logOn;
        this.state = state;
        List<GarbageCollectorMXBean> beans = null;
        try {
            beans = ManagementFactory.getGarbageCollectorMXBeans();
        } catch (Throwable ignored) {
            // no management classes in this Java: the log just has no GC numbers
        }
        this.collectors = beans;
        this.lastGcCount = gcCount();
        this.lastGcMillis = gcMillis();
    }

    FrameStats stats() {
        return stats;
    }

    /** Call once per frame with the time the last frame took, in milliseconds. */
    void frame(double ms) {
        int kind = stats.frame(ms);
        if (kind == FrameStats.NORMAL) return;
        long now = System.nanoTime();
        if (!logOn.getAsBoolean() || logFile == null) return;
        if (now - lastLine < MIN_GAP_NANOS) {
            linesSkipped++;
            return;
        }
        lastLine = now;
        long count = gcCount();
        long millis = gcMillis();
        String line = String.format(Locale.ROOT, "%s %s %.1f ms | avg %.0f fps | GC +%d (+%d ms) | heap %d/%d MB | %s%s",
            LocalTime.now().withNano(0), kind == FrameStats.FREEZE ? "FREEZE" : "hitch", ms, stats.avgFps(),
            count - lastGcCount, millis - lastGcMillis, usedHeapMb(), maxHeapMb(), state.get(),
            linesSkipped > 0 ? " | (" + linesSkipped + " more hitches within a quarter second)" : "");
        lastGcCount = count;
        lastGcMillis = millis;
        linesSkipped = 0;
        write(line);
    }

    /** Two lines for the /sunflare perf command. */
    String[] summary() {
        long gcPauses = gcCount();
        long gcTotal = gcMillis();
        return new String[] {
            String.format(Locale.ROOT, "FPS avg %.0f, 1%% low %.0f | hitches %d, freezes %d, worst %.0f ms",
                stats.avgFps(), stats.lowFps(), stats.hitches(), stats.freezes(), stats.worstMillis()),
            String.format(Locale.ROOT, "GC %d collections, %d ms total | heap %d/%d MB | %s",
                gcPauses, gcTotal, usedHeapMb(), maxHeapMb(), state.get())
        };
    }

    Path logFile() {
        return logFile;
    }

    long linesWritten() {
        return linesWritten;
    }

    private void write(String line) {
        try {
            if (writer == null) {
                writer = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "urinth-perf-log");
                    t.setDaemon(true);
                    return t;
                });
            }
            writer.execute(() -> append(line));
        } catch (Throwable ignored) {
            // the log is a help, never a reason to disturb the game
        }
    }

    private void append(String line) {
        try {
            Path parent = logFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (Files.exists(logFile) && Files.size(logFile) > MAX_LOG_BYTES) return;
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            linesWritten++;
        } catch (IOException | RuntimeException ignored) {
            // full disk or no permission: skip the line
        }
    }

    private long gcCount() {
        long n = 0;
        if (collectors != null) {
            for (GarbageCollectorMXBean b : collectors) n += Math.max(0, b.getCollectionCount());
        }
        return n;
    }

    private long gcMillis() {
        long n = 0;
        if (collectors != null) {
            for (GarbageCollectorMXBean b : collectors) n += Math.max(0, b.getCollectionTime());
        }
        return n;
    }

    private static long usedHeapMb() {
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
    }

    private static long maxHeapMb() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }
}
