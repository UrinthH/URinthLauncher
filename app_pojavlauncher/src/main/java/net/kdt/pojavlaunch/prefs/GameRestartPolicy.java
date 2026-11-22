package net.kdt.pojavlaunch.prefs;

import java.util.Collection;

/**
 * Decides, when Play is pressed, whether the game that is still running in the background was started
 * with the settings now chosen. Pure Java: the Android side only reads the file and lists the running
 * game processes, and hands both to {@link #decide}.
 *
 * The record is what the game process writes when it starts the game: its process id, and the
 * {@link LaunchFingerprint} of its settings. The process id ties the record to one process, so a record left
 * behind by a game that has ended, or by an earlier version of the launcher, is never taken for the
 * running game.
 */
public final class GameRestartPolicy {
    private GameRestartPolicy() {}

    public enum Verdict {
        /** No game process is running; Play starts a new game. */
        NO_GAME,
        /** A game process is running but there is no record of its settings; Play is left as it was. */
        UNKNOWN,
        /** The running game was started with the settings that are chosen now. */
        SAME,
        /** The running game was started with other settings: the player should be asked to restart it. */
        CHANGED
    }

    /** The process that started a game, and the settings it started it with. */
    public static final class Record {
        public final int pid;
        public final LaunchFingerprint fingerprint;

        public Record(int pid, LaunchFingerprint fingerprint) {
            this.pid = pid;
            this.fingerprint = fingerprint;
        }
    }

    public static String encode(Record record) {
        return "pid=" + record.pid + "\n" + record.fingerprint.encode();
    }

    /** Reads {@link #encode} back; null for text that is not a record (a torn or an unknown file). */
    public static Record decode(String text) {
        if (text == null) return null;
        int lineEnd = text.indexOf('\n');
        if (lineEnd < 0) return null;
        String first = text.substring(0, lineEnd).trim();
        if (!first.startsWith("pid=")) return null;
        int pid;
        try {
            pid = Integer.parseInt(first.substring(4).trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
        if (pid <= 0) return null;
        LaunchFingerprint fingerprint = LaunchFingerprint.decode(text.substring(lineEnd + 1));
        return fingerprint == null ? null : new Record(pid, fingerprint);
    }

    /**
     * @param livePids the process ids of the game processes that are running now
     * @param record   what the game process wrote, or null when there is no readable record
     * @param now      the fingerprint of the settings as they are now
     */
    public static Verdict decide(Collection<Integer> livePids, Record record, LaunchFingerprint now) {
        if (livePids == null || livePids.isEmpty()) return Verdict.NO_GAME;
        if (record == null || now == null || !livePids.contains(record.pid)) return Verdict.UNKNOWN;
        return record.fingerprint.differences(now).isEmpty() ? Verdict.SAME : Verdict.CHANGED;
    }
}
