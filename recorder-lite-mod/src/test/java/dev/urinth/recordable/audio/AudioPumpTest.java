package dev.urinth.recordable.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.urinth.recordable.nat.Mem;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** These run in real time (about a second each): the pump's whole job is keeping time. */
final class AudioPumpTest {
    /** Puts a running piece number in the first four bytes of every piece. */
    private static final class CountingRenderer implements AudioPump.Renderer {
        final AtomicInteger seq = new AtomicInteger();
        final AtomicBoolean ready = new AtomicBoolean(true);

        @Override
        public boolean ready() {
            return ready.get();
        }

        @Override
        public void render(long ptr, int frames) {
            Mem.putInt(ptr, seq.getAndIncrement());
        }
    }

    private static final class FakeOutput implements AudioPump.Output {
        final boolean blocking;
        final boolean plays;
        final AtomicInteger writes = new AtomicInteger();
        volatile boolean closed;

        FakeOutput(boolean blocking, boolean plays) {
            this.blocking = blocking;
            this.plays = plays;
        }

        @Override
        public boolean write(long ptr, int frames) {
            writes.incrementAndGet();
            if (blocking) {
                try {
                    Thread.sleep(20);                           // a speaker that takes one piece per 20 ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return plays;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public String describe() {
            return "fake";
        }
    }

    @Test
    void withoutABlockingSpeakerTheThreadKeepsRealTimeItself() throws Exception {
        AudioTap tap = new AudioTap();
        tap.arm(System.nanoTime());
        CountingRenderer r = new CountingRenderer();
        FakeOutput o = new FakeOutput(false, false);
        AudioPump p = new AudioPump(r, o, tap, null);
        p.start();
        Thread.sleep(1500);
        assertTrue(p.isRunning());
        assertTrue(p.stop(2000), "the thread ends");
        assertFalse(p.isRunning());
        long n = p.renderedChunks();
        assertTrue(n >= 74 && n <= 84, "about 50 pieces a second, a little ahead of time: " + n);
        assertEquals(n, p.notPlayedChunks(), "a speaker that plays nothing is noted");
    }

    @Test
    void theRecorderGetsEveryPieceInOrderAfterTheWarmUp() throws Exception {
        AudioTap tap = new AudioTap();
        tap.arm(System.nanoTime());
        CountingRenderer r = new CountingRenderer();
        AudioPump p = new AudioPump(r, new FakeOutput(true, true), tap, null);
        p.start();
        Thread.sleep(1500);
        assertTrue(p.stop(2000));
        long n = p.renderedChunks();
        assertTrue(n >= 70 && n <= 80, "a blocking speaker sets the pace: " + n);
        long delivered = tap.delivered();
        assertTrue(delivered >= 44 && delivered <= 54, "the first half second is not given to the recorder: " + delivered);
        AudioTap.Chunk c;
        int previous = -1;
        int count = 0;
        long lastWall = 0;
        while ((c = tap.poll()) != null) {
            int seq = (c.pcm[0] & 0xFF) | ((c.pcm[1] & 0xFF) << 8) | ((c.pcm[2] & 0xFF) << 16) | ((c.pcm[3] & 0xFF) << 24);
            if (previous >= 0) {
                assertEquals(previous + 1, seq, "no piece is lost or repeated");
                assertTrue(c.wallNs > lastWall, "times only go forward");
            }
            previous = seq;
            lastWall = c.wallNs;
            count++;
        }
        assertEquals(delivered, count);
        assertEquals(0, tap.dropped());
    }

    @Test
    void anEngineThatNeverGetsReadyEndsTheWaitWhenStopped() throws Exception {
        AudioTap tap = new AudioTap();
        CountingRenderer r = new CountingRenderer();
        r.ready.set(false);
        AudioPump p = new AudioPump(r, new FakeOutput(true, true), tap, null);
        p.start();
        Thread.sleep(150);
        assertEquals(0, p.renderedChunks(), "nothing is rendered before the engine is ready");
        assertTrue(p.stop(1000));
        assertEquals(0, r.seq.get());
    }

    @Test
    void nothingIsCopiedWhileNoRecordingRuns() throws Exception {
        AudioTap tap = new AudioTap();
        CountingRenderer r = new CountingRenderer();
        AudioPump p = new AudioPump(r, new FakeOutput(true, true), tap, null);
        p.start();
        Thread.sleep(800);
        assertTrue(p.stop(2000));
        assertTrue(p.renderedChunks() > 30);
        assertEquals(0, tap.delivered());
        assertNotNull(tap);
    }
}
