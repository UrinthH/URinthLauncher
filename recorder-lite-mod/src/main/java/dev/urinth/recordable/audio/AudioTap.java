package dev.urinth.recordable.audio;

import dev.urinth.recordable.nat.Mem;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The hand-over point for the game's sound: the audio thread puts every 20 ms piece of sound in, the recorder's encoder thread takes them
 * out. Nothing here ever blocks the audio thread, and while no recording runs it costs nothing (the pieces are not even copied).
 *
 * <p>Every piece carries the time it was made (on the system's monotonic clock) and the total time spent paused by then, so the recorder
 * can put it on the same timeline as the video pictures.
 */
public final class AudioTap {
    public static final int RATE = 48_000;
    public static final int CHANNELS = 2;
    public static final int CHUNK_FRAMES = 960;                 // 20 ms
    public static final int FRAME_BYTES = 2 * CHANNELS;
    public static final int CHUNK_BYTES = CHUNK_FRAMES * FRAME_BYTES;
    /** Two seconds of sound; more than that waiting means the encoder thread is stuck. */
    private static final int SLOTS = 100;

    private static final int IDLE = 0;
    private static final int ARMED = 1;
    private static final int PAUSED = 2;

    /** One piece of sound: 960 stereo frames of 16-bit samples, little endian. */
    public static final class Chunk {
        public final byte[] pcm = new byte[CHUNK_BYTES];
        /** System.nanoTime() when it was rendered. */
        public long wallNs;
        /** Time spent paused (summed over the recording) when it was rendered. */
        public long pausedNs;
    }

    private final ArrayBlockingQueue<Chunk> free = new ArrayBlockingQueue<>(SLOTS);
    private final ArrayBlockingQueue<Chunk> filled = new ArrayBlockingQueue<>(SLOTS);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private volatile int state = IDLE;
    private volatile long startNs;
    private volatile long pausedNs;

    public AudioTap() {
        for (int i = 0; i < SLOTS; i++) {
            free.add(new Chunk());
        }
    }

    // ---- recorder side -------------------------------------------------------------------------------------------------------------

    /** Starts collecting. {@code startNs} is the recording's zero point on the system's monotonic clock. */
    public void arm(long startNs) {
        clear();
        this.startNs = startNs;
        this.pausedNs = 0;
        dropped.set(0);
        delivered.set(0);
        state = ARMED;
    }

    /** Stops collecting. What is already waiting stays available to {@link #poll} until the next {@link #arm}. */
    public void disarm() {
        state = IDLE;
    }

    /** While paused no sound is kept. */
    public void pause() {
        if (state == ARMED) {
            state = PAUSED;
        }
    }

    /** @param pausedTotalNs all the time spent paused so far, including the pause that just ended */
    public void resume(long pausedTotalNs) {
        if (state == PAUSED) {
            pausedNs = pausedTotalNs;
            state = ARMED;
        }
    }

    public boolean isArmed() {
        return state != IDLE;
    }

    public long startNs() {
        return startNs;
    }

    /** The next piece, or null if there is none right now. Give it back with {@link #release}. */
    public Chunk poll() {
        return filled.poll();
    }

    public void release(Chunk c) {
        if (c != null) {
            free.offer(c);
        }
    }

    /** Pieces that could not be kept because the recorder was too slow. */
    public long dropped() {
        return dropped.get();
    }

    /** Pieces handed over since the last {@link #arm}. */
    public long delivered() {
        return delivered.get();
    }

    private void clear() {
        Chunk c;
        while ((c = filled.poll()) != null) {
            free.offer(c);
        }
    }

    // ---- audio thread side ---------------------------------------------------------------------------------------------------------

    /** Copies {@link #CHUNK_FRAMES} frames from native memory. Does nothing unless a recording is running. */
    public void offer(long nativePtr, long wallNs) {
        if (state != ARMED || wallNs < startNs) {
            return;
        }
        Chunk c = free.poll();
        if (c == null) {
            dropped.incrementAndGet();
            return;
        }
        Mem.get(nativePtr, c.pcm, 0, CHUNK_BYTES);
        publish(c, wallNs);
    }

    /** Same as {@link #offer(long, long)} for sound that is in a Java array (tests). */
    public void offer(byte[] pcm, long wallNs) {
        if (state != ARMED || wallNs < startNs) {
            return;
        }
        Chunk c = free.poll();
        if (c == null) {
            dropped.incrementAndGet();
            return;
        }
        System.arraycopy(pcm, 0, c.pcm, 0, CHUNK_BYTES);
        publish(c, wallNs);
    }

    private void publish(Chunk c, long wallNs) {
        c.wallNs = wallNs;
        c.pausedNs = pausedNs;
        if (filled.offer(c)) {
            delivered.incrementAndGet();
        } else {
            dropped.incrementAndGet();
            free.offer(c);
        }
    }
}
