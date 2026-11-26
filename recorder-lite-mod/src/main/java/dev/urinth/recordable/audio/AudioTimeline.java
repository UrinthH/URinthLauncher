package dev.urinth.recordable.audio;

/**
 * Keeps the recorded sound lined up with the video. The sound is a gapless stream of samples, but the video's clock is the wall clock, so
 * where the audio thread stalled (the sound that should have played during the stall does not exist) silence is put in, and where it
 * delivered more than real time allows, a piece is skipped. Small differences (the audio thread wakes up a few milliseconds early or
 * late) are left alone; the sound simply continues.
 *
 * <p>The position is measured against what was really handed to the encoder (the first piece's time plus the samples fed since), so a
 * hole that is longer than one correction can fill is closed by the next pieces.
 */
public final class AudioTimeline {
    /** Returned by {@link #plan} for a piece that has to be skipped. */
    public static final int DROP = -1;
    /** The difference between the clock and the sound's own count that is tolerated before the sound is corrected. */
    public static final long TOLERANCE_US = 80_000;
    /** The most silence that is put in front of one piece. */
    public static final long MAX_SILENCE_US = 2_000_000;

    private boolean anchored;
    private long anchorUs;
    private long fedFrames;
    private long silenceFrames;
    private long dropped;

    /** Time of the first piece, on the recording's clock (microseconds). Valid after the first {@link #plan}. */
    public long anchorUs() {
        return anchorUs;
    }

    /** Silence inserted so far, in sample frames. */
    public long silenceFrames() {
        return silenceFrames;
    }

    /** Pieces skipped so far. */
    public long droppedPieces() {
        return dropped;
    }

    /** The recording time at which the next sample lands, given everything handed out so far. */
    public long positionUs() {
        return anchorUs + fedFrames * 1_000_000L / AudioTap.RATE;
    }

    /**
     * Decides what to do with a piece that was made at {@code labelUs} (recording time in microseconds).
     *
     * @return the number of silent sample frames to put in front of the piece, 0 to use it as it is, or {@link #DROP}
     */
    public int plan(long labelUs) {
        if (!anchored) {
            anchored = true;
            anchorUs = labelUs;
            fedFrames = AudioTap.CHUNK_FRAMES;
            return 0;
        }
        long diff = labelUs - positionUs();
        if (diff > TOLERANCE_US) {
            long frames = Math.min(diff, MAX_SILENCE_US) * AudioTap.RATE / 1_000_000L;
            fedFrames += frames + AudioTap.CHUNK_FRAMES;
            silenceFrames += frames;
            return (int) frames;
        }
        if (diff < -TOLERANCE_US) {
            dropped++;
            return DROP;
        }
        fedFrames += AudioTap.CHUNK_FRAMES;
        return 0;
    }
}
