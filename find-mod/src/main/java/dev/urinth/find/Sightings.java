package dev.urinth.find;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each player was the last time this game could see them, and when.
 *
 * On a server the game is told where the players near it are, and nobody else. That cannot be changed from
 * the client. What can be done is to remember what was seen: a player who walked out of view a minute ago
 * was at a known place a minute ago, and that is worth showing, clearly marked as old. Plain Java with no
 * Minecraft classes in it, so it is unit tested on a normal JVM.
 */
public final class Sightings {
    /** A remembered player and how long ago they were seen. */
    public record Remembered(Located at, long ageMs) {
    }

    private record Entry(Located at, long seenAtMs) {
    }

    /** Most players remembered at once; the one seen longest ago is forgotten first. */
    static final int MAX = 256;

    private final Map<String, Entry> byName = new LinkedHashMap<>();

    /** Notes where this player is right now. A newer sighting replaces an older one. */
    public synchronized void record(Located at, long nowMs) {
        if (at == null || at.name().isEmpty()) return;
        byName.remove(at.name());
        byName.put(at.name(), new Entry(at, nowMs));
        while (byName.size() > MAX) {
            Iterator<String> oldest = byName.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** Everybody remembered, with how long ago each was seen as of {@code nowMs}. */
    public synchronized List<Remembered> all(long nowMs) {
        List<Remembered> out = new ArrayList<>(byName.size());
        for (Entry entry : byName.values()) {
            out.add(new Remembered(entry.at(), Math.max(0, nowMs - entry.seenAtMs())));
        }
        return out;
    }

    /** Forgets everybody: a new server, a new world. */
    public synchronized void clear() {
        byName.clear();
    }

    public synchronized int size() {
        return byName.size();
    }
}
