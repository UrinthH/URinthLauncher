package dev.urinth.optimizer;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The garbage collector's own numbers, from the JVM's management beans: how long the game has stood still in collections, how many
 * of them were full collections (the slow ones: a second or more on a phone), and how full the old generation was right after the
 * last collection. A runtime without the management classes (or a collector with other names) gives -1 for what it cannot say and the
 * callers go without. Reading these is a few method calls; nothing here allocates a lot or blocks.
 */
final class GcWatch {
    private final List<GarbageCollectorMXBean> pauses = new ArrayList<>();
    private final List<GarbageCollectorMXBean> fulls = new ArrayList<>();
    private MemoryPoolMXBean oldGen;
    private final StringBuilder names = new StringBuilder();

    GcWatch() {
        try {
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                String name = bean.getName();
                if (names.length() > 0) names.append(", ");
                names.append(name);
                String lower = name.toLowerCase(Locale.ROOT);
                // a concurrent bean counts the time of work that runs beside the game, not the time the game stood still
                if (lower.contains("concurrent")) continue;
                pauses.add(bean);
                if (isFull(lower)) fulls.add(bean);
            }
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                String lower = pool.getName().toLowerCase(Locale.ROOT);
                if (pool.getType() == MemoryType.HEAP && (lower.contains("old gen") || lower.contains("tenured"))) {
                    oldGen = pool;
                    break;
                }
            }
        } catch (Throwable t) {
            // no management classes in this runtime: every answer below is -1
        }
    }

    /** The names of the collectors, for the log. */
    String collectors() {
        return names.length() == 0 ? "unknown" : names.toString();
    }

    boolean hasOldGen() {
        return oldGen != null;
    }

    /** True for the collectors that run the slow, complete collections ("G1 Old Generation", "PS MarkSweep"). */
    static boolean isFull(String lowerCaseName) {
        return lowerCaseName.contains("old") || lowerCaseName.contains("marksweep") || lowerCaseName.contains("full");
    }

    /** Milliseconds the game has stood still in collections since it started; -1 if unknown. */
    long pauseMillis() {
        if (pauses.isEmpty()) return -1;
        long sum = 0;
        try {
            for (GarbageCollectorMXBean bean : pauses) {
                long t = bean.getCollectionTime();
                if (t > 0) sum += t;
            }
        } catch (Throwable t) {
            return -1;
        }
        return sum;
    }

    /** Full collections since the game started; -1 if unknown. */
    long fullCollections() {
        if (pauses.isEmpty()) return -1;
        long sum = 0;
        try {
            for (GarbageCollectorMXBean bean : fulls) {
                long n = bean.getCollectionCount();
                if (n > 0) sum += n;
            }
        } catch (Throwable t) {
            return -1;
        }
        return sum;
    }

    /** Bytes in the old generation right after the last collection; -1 if unknown. */
    long oldAfterCollection() {
        MemoryPoolMXBean pool = oldGen;
        if (pool == null) return -1;
        try {
            var usage = pool.getCollectionUsage();
            return usage == null ? -1 : usage.getUsed();
        } catch (Throwable t) {
            return -1;
        }
    }
}
