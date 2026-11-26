package dev.urinth.recordable.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AudioTapTest {
    private static byte[] piece(int marker) {
        byte[] b = new byte[AudioTap.CHUNK_BYTES];
        b[0] = (byte) marker;
        b[b.length - 1] = (byte) (marker + 1);
        return b;
    }

    @Test
    void nothingIsKeptWhileNoRecordingRuns() {
        AudioTap t = new AudioTap();
        t.offer(piece(1), 100);
        assertNull(t.poll());
        assertFalse(t.isArmed());
        assertEquals(0, t.delivered());
    }

    @Test
    void anArmedTapHandsOverPiecesWithTheirTimes() {
        AudioTap t = new AudioTap();
        t.arm(50);
        assertTrue(t.isArmed());
        assertEquals(50, t.startNs());
        t.offer(piece(7), 100);
        AudioTap.Chunk c = t.poll();
        assertNotNull(c);
        assertEquals(100, c.wallNs);
        assertEquals(0, c.pausedNs);
        assertEquals(7, c.pcm[0]);
        assertEquals(8, c.pcm[AudioTap.CHUNK_BYTES - 1]);
        t.release(c);
        assertNull(t.poll());
    }

    @Test
    void soundBeforeTheStartIsIgnored() {
        AudioTap t = new AudioTap();
        t.arm(1_000);
        t.offer(piece(1), 999);
        assertNull(t.poll());
        t.offer(piece(1), 1_000);
        assertNotNull(t.poll());
    }

    @Test
    void whilePausedNothingIsKeptAndTheResumeCarriesThePauseTime() {
        AudioTap t = new AudioTap();
        t.arm(0);
        t.offer(piece(1), 10);
        t.pause();
        t.offer(piece(2), 20);
        t.offer(piece(3), 30);
        t.resume(5_000);
        t.offer(piece(4), 9_000);
        AudioTap.Chunk a = t.poll();
        AudioTap.Chunk b = t.poll();
        assertNotNull(a);
        assertNotNull(b);
        assertNull(t.poll());
        assertEquals(1, a.pcm[0]);
        assertEquals(0, a.pausedNs);
        assertEquals(4, b.pcm[0]);
        assertEquals(5_000, b.pausedNs);
    }

    @Test
    void aFullQueueDropsPiecesAndCountsThemInsteadOfBlocking() {
        AudioTap t = new AudioTap();
        t.arm(0);
        for (int i = 0; i < 150; i++) {
            t.offer(piece(i), 10 + i);
        }
        assertEquals(100, t.delivered());
        assertEquals(50, t.dropped());
        int n = 0;
        AudioTap.Chunk c;
        while ((c = t.poll()) != null) {
            assertEquals(n, c.pcm[0], "pieces come out in order");
            n++;
            t.release(c);
        }
        assertEquals(100, n);
        t.offer(piece(1), 1000);
        assertNotNull(t.poll(), "released pieces can be used again");
    }

    @Test
    void stoppingKeepsWhatWasCollectedUntilTheNextStart() {
        AudioTap t = new AudioTap();
        t.arm(0);
        t.offer(piece(1), 10);
        t.disarm();
        t.offer(piece(2), 20);
        AudioTap.Chunk c = t.poll();
        assertNotNull(c, "the recorder can still take what was collected");
        assertEquals(1, c.pcm[0]);
        assertNull(t.poll());
        t.release(c);
        t.arm(0);
        t.offer(piece(3), 30);
        t.disarm();
        t.arm(0);
        assertNull(t.poll(), "a new recording starts empty");
    }
}
