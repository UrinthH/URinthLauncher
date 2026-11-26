package dev.urinth.recordable.audio;

import dev.urinth.recordable.nat.Mem;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * The thread that plays the game's sound. While the sound is recorded the game's OpenAL does not talk to the phone's speaker; it renders
 * into memory ("loopback"), and this thread asks it for 20 ms of sound at a time, plays that through the phone's audio output and gives a
 * copy to the recorder. The thread never waits for the recorder: if that is slow, the copy is dropped, never the playback.
 *
 * <p>The speaker's own buffer is the clock: writing blocks while it is full, so the sound is rendered just ahead of the time it is
 * played. If the output does not block (it failed, or there is none) the thread paces itself by the clock instead, so the game never
 * runs its sound faster than real time.
 */
public final class AudioPump implements Runnable {
    /** Makes the sound. */
    public interface Renderer {
        /** True once the sound engine can be asked for sound (its context exists). */
        boolean ready();

        /** Renders {@code frames} frames (stereo, 16-bit) into native memory at {@code ptr}. */
        void render(long ptr, int frames);
    }

    /** Plays the sound. */
    public interface Output {
        /** Plays the frames; blocks while the speaker is busy. Returns false if the frames were not played. */
        boolean write(long ptr, int frames);

        void close();

        String describe();
    }

    static final long MAX_AHEAD_NS = 120_000_000L;
    static final long TARGET_AHEAD_NS = 80_000_000L;
    static final long RESET_BEHIND_NS = 200_000_000L;
    /** The first moments after the start (the output is filling up) are not given to the recorder. */
    static final long WARMUP_NS = 500_000_000L;
    static final long STABLE_NS = 3_000_000_000L;
    static final long READY_WAIT_NS = 8_000_000_000L;
    private static final long NS_PER_SECOND = 1_000_000_000L;

    private final Renderer renderer;
    private final Output output;
    private final AudioTap tap;
    private final Runnable onStable;
    private final AtomicLong rendered = new AtomicLong();
    private final AtomicLong notPlayed = new AtomicLong();
    private volatile boolean running;
    private volatile Thread thread;
    private volatile String problem;
    private volatile boolean exited;

    public AudioPump(Renderer renderer, Output output, AudioTap tap, Runnable onStable) {
        this.renderer = renderer;
        this.output = output;
        this.tap = tap;
        this.onStable = onStable;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        exited = false;
        Thread t = new Thread(this, "Record-able Lite sound");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        thread = t;
        t.start();
    }

    /**
     * Stops the thread and waits until it is gone, so the sound engine can be taken down safely afterwards.
     *
     * @return false if the thread did not end in time
     */
    public boolean stop(long maxWaitMs) {
        running = false;
        Thread t = thread;
        if (t == null) {
            return true;
        }
        LockSupport.unpark(t);
        try {
            t.join(maxWaitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return !t.isAlive();
    }

    public boolean isRunning() {
        return running && !exited;
    }

    public long renderedChunks() {
        return rendered.get();
    }

    public long notPlayedChunks() {
        return notPlayed.get();
    }

    /** What went wrong, or null. */
    public String problem() {
        return problem;
    }

    @Override
    public void run() {
        long buf = Mem.alloc(AudioTap.CHUNK_BYTES);
        try {
            long waitEnd = System.nanoTime() + READY_WAIT_NS;
            while (running && !renderer.ready()) {
                if (System.nanoTime() > waitEnd) {
                    problem = "the sound engine never became ready";
                    return;
                }
                LockSupport.parkNanos(10_000_000L);
            }
            long clock = System.nanoTime();
            long frames = 0;
            long begun = clock;
            boolean stableReported = false;
            int errors = 0;
            while (running) {
                long now = System.nanoTime();
                long ahead = clock + frames * NS_PER_SECOND / AudioTap.RATE - now;
                if (ahead > MAX_AHEAD_NS) {
                    LockSupport.parkNanos(ahead - TARGET_AHEAD_NS);
                    continue;
                }
                if (ahead < -RESET_BEHIND_NS) {
                    clock = now;                                // a long stall: do not try to catch up with a burst
                    frames = 0;
                }
                try {
                    long wall = System.nanoTime();
                    renderer.render(buf, AudioTap.CHUNK_FRAMES);
                    if (wall - begun >= WARMUP_NS) {
                        tap.offer(buf, wall);
                    }
                    frames += AudioTap.CHUNK_FRAMES;
                    rendered.incrementAndGet();
                    if (!output.write(buf, AudioTap.CHUNK_FRAMES)) {
                        notPlayed.incrementAndGet();
                    }
                    errors = 0;
                } catch (RuntimeException | LinkageError e) {
                    problem = e.toString();
                    if (++errors > 100) {
                        return;
                    }
                    LockSupport.parkNanos(50_000_000L);
                }
                if (!stableReported && System.nanoTime() - begun >= STABLE_NS) {
                    stableReported = true;
                    if (onStable != null) {
                        onStable.run();
                    }
                }
            }
        } catch (Throwable t) {
            problem = t.toString();
        } finally {
            Mem.free(buf);
            exited = true;
        }
    }
}
