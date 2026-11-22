package dev.urinth.recordable.enc;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A small fixed set of frame buffers shared by the render thread and the encoder thread. The render thread takes a free buffer without
 * waiting; when none is free (the encoder is behind) it skips that picture instead of slowing the game down.
 */
public final class FramePool {
    private final ArrayBlockingQueue<Frame> free;
    private final ArrayBlockingQueue<Frame> ready;
    private final int frameBytes;
    private volatile long dropped;

    public FramePool(int count, int frameBytes) {
        this.frameBytes = frameBytes;
        this.free = new ArrayBlockingQueue<>(count);
        this.ready = new ArrayBlockingQueue<>(count);
        for (int i = 0; i < count; i++) {
            free.add(new Frame(frameBytes));
        }
    }

    public int frameBytes() {
        return frameBytes;
    }

    /** Render thread: a free buffer, or null when the encoder is behind. Never blocks. */
    public Frame acquire() {
        Frame f = free.poll();
        if (f == null) {
            dropped++;
        }
        return f;
    }

    /** Render thread: hands a filled buffer to the encoder. */
    public void submit(Frame f) {
        if (!ready.offer(f)) {
            dropped++;
            free.offer(f);
        }
    }

    /** Encoder thread: the next picture, or null after the timeout. */
    public Frame take(long timeoutMs) throws InterruptedException {
        return ready.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** Encoder thread: the next picture if one is waiting. */
    public Frame poll() {
        return ready.poll();
    }

    /** Encoder thread: gives a buffer back after it was encoded. */
    public void release(Frame f) {
        free.offer(f);
    }

    /** Number of pictures skipped so far because no buffer was free. */
    public long dropped() {
        return dropped;
    }

    /** How many pictures wait for the encoder. */
    public int queued() {
        return ready.size();
    }
}
