package dev.urinth.recordable.audio;

import dev.urinth.recordable.nat.AAudioLib;

import java.io.IOException;

/**
 * Plays the game's sound through Android's AAudio. The stream is shared (other apps keep their sound), uses the "game" usage, and starts
 * with a buffer of 60 ms that grows when the phone is too busy to keep it filled (each underrun adds 20 ms, up to 120 ms). If the stream
 * is lost (a headset was plugged in or out) it is opened again.
 */
public final class AAudioOutput implements AudioPump.Output {
    private static final int START_BUFFER = 3 * AudioTap.CHUNK_FRAMES;
    private static final int MAX_BUFFER = 6 * AudioTap.CHUNK_FRAMES;
    private static final long WRITE_TIMEOUT_NS = 60_000_000L;
    private static final long CHUNK_GIVE_UP_NS = 300_000_000L;

    private final AAudioLib lib;
    private long stream;
    private int bufferFrames;
    private int lastXruns;
    private int totalXruns;
    private int writes;
    private int reopened;
    private int stalls;
    private long retryAtNs;
    private String problem = "";

    private AAudioOutput(AAudioLib lib) {
        this.lib = lib;
    }

    /** Opens the phone's audio output. Throws if that is not possible. */
    public static AAudioOutput open() throws IOException {
        AAudioOutput o = new AAudioOutput(AAudioLib.open());
        o.openStream();
        return o;
    }

    private void openStream() throws IOException {
        stream = lib.openStream(false, AudioTap.RATE, AudioTap.CHANNELS, START_BUFFER);
        int size = lib.bufferFrames(stream);
        bufferFrames = size > 0 ? size : START_BUFFER;
        lastXruns = 0;
    }

    @Override
    public boolean write(long ptr, int frames) {
        if (stream == 0) {
            if (System.nanoTime() < retryAtNs) {
                return false;
            }
            try {
                openStream();
                reopened++;
            } catch (IOException | RuntimeException e) {
                problem = String.valueOf(e.getMessage());
                retryAtNs = System.nanoTime() + 2_000_000_000L;
                return false;
            }
        }
        long p = ptr;
        int left = frames;
        long giveUp = System.nanoTime() + CHUNK_GIVE_UP_NS;
        while (left > 0) {
            int r = lib.write(stream, p, left, WRITE_TIMEOUT_NS);
            if (r < 0) {
                problem = "AAudio write error " + r;
                lib.close(stream);
                stream = 0;
                retryAtNs = System.nanoTime() + (r == AAudioLib.ERROR_DISCONNECTED ? 100_000_000L : 1_000_000_000L);
                return false;
            }
            if (r == 0) {
                if (System.nanoTime() > giveUp) {
                    stalls++;
                    return false;
                }
                continue;
            }
            p += (long) r * AudioTap.FRAME_BYTES;
            left -= r;
        }
        if ((++writes & 63) == 0) {
            tune();
        }
        return true;
    }

    /** Every second or so: if the speaker ran dry since the last look, use a deeper buffer. */
    private void tune() {
        int x = lib.xruns(stream);
        if (x > lastXruns) {
            totalXruns += x - lastXruns;
            lastXruns = x;
            if (bufferFrames < MAX_BUFFER) {
                int set = lib.setBufferFrames(stream, Math.min(MAX_BUFFER, bufferFrames + AudioTap.CHUNK_FRAMES));
                if (set > 0) {
                    bufferFrames = set;
                }
            }
        }
    }

    @Override
    public void close() {
        long s = stream;
        stream = 0;
        if (s != 0) {
            lib.close(s);
        }
    }

    @Override
    public String describe() {
        return "AAudio 48000 Hz stereo, buffer " + bufferFrames * 1000 / AudioTap.RATE + " ms, " + totalXruns + " underruns, "
                + reopened + " reopened, " + stalls + " stalls" + (problem.isEmpty() ? "" : ", last problem: " + problem);
    }
}
