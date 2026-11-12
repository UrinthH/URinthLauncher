package dev.urinth.optimizer;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLongArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches the game from a thread of its own, for two reasons.
 *
 * <p><b>Freezes.</b> The render thread tells it, once per frame, that a frame was drawn (one clock read and a few counters: no
 * measurable cost). A frame that took 100 ms or more is written to the game log with what the game was doing at that time: whether
 * the garbage collector was running (and for how long), how full the heap and the system memory were, how many page faults the game
 * took, how slow the integrated server was. If the render thread has not finished a frame for a quarter of a second and it is not
 * the garbage collector's doing, the thread's stack is taken while it is still stuck, so the line says where. A freeze report in the
 * log says why it froze instead of that it did. The lines are rate limited and a summary comes once a minute.
 *
 * <p><b>Memory.</b> Every two seconds it looks at the heap, the garbage collector and the system's free memory and gives them to
 * {@link MemoryGuard}, which says how short of memory the game is. The client reads that level once a second and acts on it.
 *
 * <p>Nothing here touches the game's classes (the facts that come from the game are handed in by the client), and every step is
 * fail-soft: if it keeps failing it stops, and the game does not notice.
 */
public final class Monitor {
    private Monitor() {}

    private static final Logger LOG = LoggerFactory.getLogger("urinth_optimizer");

    static final long POLL_MS = 50;
    static final long MEMORY_NANOS = 2_000_000_000L;
    static final long SUMMARY_NANOS = 60_000_000_000L;
    /** The guard is not fed for this long after the monitor starts: loading the mods and the resources is not the game's normal run. */
    static final long WARMUP_NANOS = 90_000_000_000L;
    /** A frame this long is reported. */
    static final long HITCH_NANOS = 100_000_000L;
    /** A frame still not finished after this long gets its stack taken. */
    static final long STACK_NANOS = 250_000_000L;
    private static final long[] STALL_LINE_AT = {2_000_000_000L, 8_000_000_000L, 30_000_000_000L};
    private static final long TEN_SECONDS = 10_000_000_000L;
    private static final long TWO_MINUTES = 120_000_000_000L;

    private static final int FRAMES = 0;
    private static final int OVER_50 = 1;
    private static final int OVER_100 = 2;
    private static final int OVER_250 = 3;
    private static final int WORST = 4;
    private static final AtomicLongArray STATS = new AtomicLongArray(5);

    // written by the render thread
    private static volatile long lastFrame;
    private static volatile boolean watching;
    private static volatile Thread renderThread;
    private static volatile long pendingEnd;
    private static volatile long pendingNs;
    private static volatile int gameFps;
    private static volatile float serverTickMs = -1f;

    // written by the monitor thread
    private static volatile MemoryGuard.Level level = MemoryGuard.Level.OK;
    private static volatile long availableBytes = -1;
    private static volatile int oldPercent = -1;
    private static volatile int heapPercent = -1;
    private static volatile double faultsPerSec = -1;
    private static volatile String lastHitch = "";
    private static volatile Thread thread;

    // ---- called by the game's thread --------------------------------------------------------------------------------------------

    /** The render thread drew a frame (called from the fog hook, once per frame). */
    public static void frame() {
        long now = System.nanoTime();
        long last = lastFrame;
        lastFrame = now;
        if (renderThread == null) renderThread = Thread.currentThread();
        if (last == 0L || !watching) return;
        long d = now - last;
        STATS.incrementAndGet(FRAMES);
        if (d > STATS.get(WORST)) STATS.set(WORST, d);
        if (d >= 50_000_000L) {
            STATS.incrementAndGet(OVER_50);
            if (d >= HITCH_NANOS) {
                STATS.incrementAndGet(OVER_100);
                if (d >= STACK_NANOS) STATS.incrementAndGet(OVER_250);
                pendingEnd = now;
                pendingNs = d;
            }
        }
    }

    /**
     * True while the game really runs in a world (not loading, not in the background): only then do frame times mean something.
     * Called by the client tick, which runs on the render thread like the frames.
     */
    static void setWatching(boolean on) {
        if (on != watching || !on) lastFrame = 0L;
        watching = on;
    }

    /** Facts of the game for the lines in the log, handed in once a second by the client. */
    static void setFacts(int fps, float serverTick) {
        gameFps = fps;
        serverTickMs = serverTick;
    }

    // ---- read by the client -----------------------------------------------------------------------------------------------------

    static MemoryGuard.Level level() {
        return level;
    }

    /** The simulation distance (chunks) the memory guard asks the server for; 0 = no limit. */
    static int simulationLimit() {
        if (OptimizerConfig.current().memoryGuard() == OptimizerConfig.MEMORY_OFF) return 0;
        switch (level) {
            case CRITICAL: return ServerGovernor.MIN_SIMULATION;
            case TIGHT: return 6;
            default: return 0;
        }
    }

    /** A line on the memory for the status. */
    static String describe() {
        return "Memory " + level + " | " + mb(availableBytes) + " free | heap " + (oldPercent < 0 ? "?" : oldPercent + "%");
    }

    /** The last freeze that was written to the log, for the status ("" if none). */
    static String lastHitch() {
        return lastHitch;
    }

    static boolean running() {
        Thread t = thread;
        return t != null && t.isAlive();
    }

    // ---- the thread -------------------------------------------------------------------------------------------------------------

    public static synchronized void start() {
        if (running()) return;
        try {
            Thread t = new Thread(new Runner(), "urinth-monitor");
            t.setDaemon(true);
            thread = t;
            t.start();
        } catch (Throwable t) {
            LOG.warn("[Optimizer] could not start the freeze monitor", t);
        }
    }

    public static synchronized void stop() {
        Thread t = thread;
        thread = null;
        if (t != null) t.interrupt();
    }

    private static final class Runner implements Runnable {
        private final GcWatch gc = new GcWatch();
        private final GcRing ring = new GcRing(256);
        private final MemoryGuard guard = new MemoryGuard();
        private final HitchLimiter limiter = new HitchLimiter();
        private final long startedAt = System.nanoTime();
        private long nextMemory = startedAt + MEMORY_NANOS;
        private long nextSummary = startedAt + SUMMARY_NANOS;
        private long lastFullCount = -1;
        private final long[] fullTimes = new long[8];
        private int fullHead;
        private int fullKept;
        private long lastFaults = -1;
        private long lastFaultsAt;
        private long summaryGc = -1;
        private long summaryFull = -1;
        private long summaryAt = startedAt;
        // the stall that is going on now, identified by the clock of the last frame that was drawn before it
        private long stalledFor;
        private String stallStack;
        private int stallLines;

        @Override
        public void run() {
            LOG.info("[Optimizer] monitor on: collectors [{}], old generation {}, free memory {}, heap limit {} MB", gc.collectors(),
                gc.hasOldGen() ? "readable" : "not readable", SystemMemory.availableBytes() >= 0 ? "readable" : "not readable",
                Runtime.getRuntime().maxMemory() >> 20);
            while (thread == Thread.currentThread()) {
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                if (!Hooks.usable("monitor")) return;
                try {
                    poll(System.nanoTime());
                } catch (Throwable t) {
                    Hooks.failed("monitor", t);
                }
            }
        }

        private void poll(long now) {
            long gcMs = gc.pauseMillis();
            if (gcMs >= 0) ring.add(now, gcMs);
            boolean log = OptimizerConfig.current().diagnostics();

            long ns = pendingNs;
            if (ns != 0L) {
                long end = pendingEnd;
                pendingNs = 0L;
                if (log) reportHitch(ns, end, now);
            }

            long last = lastFrame;
            if (watching && last != 0L && now - last >= STACK_NANOS) watchStall(last, now - last, log);

            if (now >= nextMemory) {
                nextMemory = now + MEMORY_NANOS;
                sampleMemory(now);
            }
            if (now >= nextSummary) {
                nextSummary = now + SUMMARY_NANOS;
                if (log) summary(now);
            }
        }

        /** The render thread is in the middle of a frame that is already long: take its stack, once, unless the collector is why. */
        private void watchStall(long lastFrameClock, long stallNs, boolean log) {
            if (stalledFor != lastFrameClock) {
                stalledFor = lastFrameClock;
                stallStack = null;
                stallLines = 0;
            }
            if (stallStack == null && !collectorExplains(lastFrameClock, stallNs)) {
                stallStack = stackOfRenderThread();
            }
            if (log && stallLines < STALL_LINE_AT.length && stallNs >= STALL_LINE_AT[stallLines]) {
                stallLines++;
                LOG.warn("[Optimizer] the game has not drawn a frame for {} ms{}", stallNs / 1_000_000L,
                    stallStack == null ? " (the garbage collector is busy)" : " | the render thread is in: " + stallStack);
            }
        }

        private boolean collectorExplains(long fromNanos, long spanNs) {
            long now = ring.latest();
            long then = ring.totalAt(fromNanos);
            if (now < 0 || then < 0) return false;
            return (now - then) * 1_000_000L >= spanNs / 2;
        }

        private void reportHitch(long ns, long end, long now) {
            if (!limiter.allow(now)) return;
            long collecting = ring.isEmpty() ? -1 : ring.latest() - ring.totalAt(end - ns);
            StringBuilder sb = new StringBuilder("hitch ").append(ns / 1_000_000L).append(" ms: ");
            if (collecting < 0) sb.append("garbage collection unknown");
            else if (collecting * 1_000_000L >= ns / 2) sb.append("garbage collection ").append(collecting).append(" ms");
            else if (collecting > 0) sb.append("mostly not the collector (").append(collecting).append(" ms of it)");
            else sb.append("no garbage collection");
            Runtime rt = Runtime.getRuntime();
            sb.append(" | heap ").append((rt.totalMemory() - rt.freeMemory()) >> 20).append('/').append(rt.maxMemory() >> 20)
                .append(" MB, after cleanup ").append(oldPercent < 0 ? "?" : oldPercent + "%")
                .append(" | free RAM ").append(mb(availableBytes))
                .append(" | page faults ").append(faultsPerSec < 0 ? "?" : Math.round(faultsPerSec) + "/s")
                .append(" | server tick ").append(serverTickMs < 0 ? "?" : Math.round(serverTickMs) + " ms")
                .append(" | fps ").append(gameFps)
                .append(" | jvm ").append(String.format(Locale.ROOT, "%.1f", uptimeSeconds())).append(" s");
            if (stallStack != null && stalledFor == end - ns) sb.append(" | the render thread was in: ").append(stallStack);
            stallStack = null;
            stalledFor = 0L;
            String line = sb.toString();
            lastHitch = line;
            LOG.info("[Optimizer] {}", line);
        }

        private void sampleMemory(long now) {
            Runtime rt = Runtime.getRuntime();
            long max = rt.maxMemory();
            long oldAfter = gc.oldAfterCollection();
            double oldShare = oldAfter < 0 || max <= 0 ? 0 : (double) oldAfter / max;
            oldPercent = oldAfter < 0 || max <= 0 ? -1 : (int) Math.round(100.0 * oldAfter / max);
            heapPercent = max <= 0 ? -1 : (int) Math.round(100.0 * (rt.totalMemory() - rt.freeMemory()) / max);

            long fullCount = gc.fullCollections();
            if (fullCount >= 0) {
                if (lastFullCount >= 0) {
                    for (long i = lastFullCount; i < fullCount && i < lastFullCount + fullTimes.length; i++) rememberFull(now);
                }
                lastFullCount = fullCount;
            }

            long faults = SystemMemory.majorFaults();
            if (faults >= 0 && lastFaults >= 0 && now > lastFaultsAt) {
                faultsPerSec = (faults - lastFaults) * 1.0e9 / (now - lastFaultsAt);
            } else {
                faultsPerSec = -1;
            }
            lastFaults = faults;
            lastFaultsAt = now;

            long available = SystemMemory.availableBytes();
            availableBytes = available;
            double share = ring.share(TEN_SECONDS);
            int recentFull = recentFulls(now);

            MemoryGuard.Level before = guard.level();
            MemoryGuard.Level after = now - startedAt < WARMUP_NANOS ? before
                : guard.update(oldShare, share, recentFull, available, faultsPerSec);
            level = after;
            if (after != before) {
                LOG.info("[Optimizer] memory {} -> {}: heap after cleanup {}, garbage collection {}% of the last 10 s, full collections "
                        + "in 2 min {}, free RAM {}, page faults {}", before, after, oldPercent < 0 ? "?" : oldPercent + "%",
                    Math.round(share * 100), recentFull, mb(available), faultsPerSec < 0 ? "?" : Math.round(faultsPerSec) + "/s");
            }
        }

        private void rememberFull(long now) {
            fullTimes[fullHead] = now;
            fullHead = (fullHead + 1) % fullTimes.length;
            if (fullKept < fullTimes.length) fullKept++;
        }

        private int recentFulls(long now) {
            int n = 0;
            for (int i = 0; i < fullKept; i++) {
                if (now - fullTimes[(fullHead - 1 - i + fullTimes.length * 2) % fullTimes.length] <= TWO_MINUTES) n++;
            }
            return n;
        }

        private void summary(long now) {
            long frames = STATS.getAndSet(FRAMES, 0);
            long o50 = STATS.getAndSet(OVER_50, 0);
            long o100 = STATS.getAndSet(OVER_100, 0);
            long o250 = STATS.getAndSet(OVER_250, 0);
            long worst = STATS.getAndSet(WORST, 0);
            long gcNow = gc.pauseMillis();
            long fullNow = gc.fullCollections();
            double elapsedMs = Math.max(1, (now - summaryAt) / 1_000_000.0);
            String gcText = gcNow < 0 || summaryGc < 0 ? "?" : String.format(Locale.ROOT, "%.1f%%", 100.0 * (gcNow - summaryGc) / elapsedMs);
            String fullText = fullNow < 0 || summaryFull < 0 ? "?" : Long.toString(fullNow - summaryFull);
            summaryGc = gcNow;
            summaryFull = fullNow;
            summaryAt = now;
            int held = limiter.takeSuppressed();
            if (frames <= 0) return;
            LOG.info("[Optimizer] last minute: {} frames, worst {} ms, over 50 ms: {}, over 100 ms: {}, over 250 ms: {} | garbage collection {} "
                    + "of the time, {} full | heap {}% (after cleanup {}) | free RAM {} | memory {}{}", frames, worst / 1_000_000L, o50, o100, o250,
                gcText, fullText, heapPercent < 0 ? "?" : heapPercent, oldPercent < 0 ? "?" : oldPercent + "%", mb(availableBytes), level,
                held > 0 ? " | " + held + " more freezes were not listed" : "");
        }

        private String stackOfRenderThread() {
            Thread t = renderThread;
            if (t == null) return null;
            try {
                StackTraceElement[] frames = t.getStackTrace();
                if (frames.length == 0) return null;
                StringBuilder sb = new StringBuilder();
                int n = 0;
                for (StackTraceElement e : frames) {
                    String cls = e.getClassName();
                    int dot = cls.lastIndexOf('.');
                    if (n > 0) sb.append(" < ");
                    sb.append(dot >= 0 ? cls.substring(dot + 1) : cls).append('.').append(e.getMethodName());
                    if (e.getLineNumber() > 0) sb.append(':').append(e.getLineNumber());
                    if (++n >= 9) break;
                }
                return sb.toString();
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static double uptimeSeconds() {
        try {
            return ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;
        } catch (Throwable t) {
            return -1;
        }
    }

    static String mb(long bytes) {
        return bytes < 0 ? "?" : (bytes >> 20) + " MB";
    }
}
