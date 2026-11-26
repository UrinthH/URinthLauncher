package dev.urinth.recordable.audio;

import dev.urinth.recordable.mux.Fmp4Writer;
import dev.urinth.recordable.nat.NdkAudioEncoder;
import dev.urinth.recordable.nat.NdkCodec;

import java.io.IOException;

/**
 * Turns the game's sound into AAC and gives it to the MP4 writer. Everything here runs on the recorder's encoder thread: the pieces come
 * out of the {@link AudioTap}, get lined up with the video by the {@link AudioTimeline}, go through the phone's AAC encoder, and the AAC
 * frames go into the file.
 */
public final class AudioRecorder {
    private static final int BITRATE = 128_000;
    private static final long WAIT_US = 0;

    private final AudioTap tap;
    private final Fmp4Writer mux;
    private final NdkAudioEncoder enc;
    private final AudioTimeline timeline = new AudioTimeline();
    private final byte[] zeros = new byte[AudioTap.CHUNK_BYTES];
    private final NdkCodec.Sink sink;
    private AudioTap.Chunk cur;
    private int curOff;
    private long silenceBytes;
    private long inFrames;                                     // sample frames given to the encoder
    private long outFrames;                                    // AAC frames received
    private boolean closed;
    private boolean anchored;
    private String failure;

    private AudioRecorder(AudioTap tap, Fmp4Writer mux, NdkAudioEncoder enc) {
        this.tap = tap;
        this.mux = mux;
        this.enc = enc;
        this.sink = (data, off, len, ptsUs, flags) -> {
            if ((flags & NdkCodec.FLAG_CONFIG) != 0) {
                byte[] asc = new byte[len];
                System.arraycopy(data, off, asc, 0, len);
                this.mux.setAudioConfig(asc);
                return;
            }
            long pts = timeline.anchorUs() + outFrames * Fmp4Writer.AUDIO_FRAME_SAMPLES * 1_000_000L / AudioTap.RATE;
            outFrames++;
            this.mux.writeAudio(data, off, len, pts);
        };
    }

    /** Opens the AAC encoder and adds a sound track to the writer. Throws if the phone has no usable AAC encoder. */
    public static AudioRecorder open(AudioTap tap, Fmp4Writer mux) throws IOException {
        NdkAudioEncoder enc = NdkAudioEncoder.open(AudioTap.RATE, AudioTap.CHANNELS, BITRATE);
        mux.enableAudio(AudioTap.RATE, AudioTap.CHANNELS);
        mux.setAudioConfig(enc.defaultConfig());
        return new AudioRecorder(tap, mux, enc);
    }

    /** Takes what the audio thread collected, encodes it and writes what the encoder has finished. Never blocks for long. */
    public void service() throws IOException {
        if (closed) {
            return;
        }
        try {
            feed();
            enc.drain(sink, 0);
        } catch (IOException | RuntimeException e) {
            fail(e);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    private void feed() throws IOException {
        for (int guard = 0; guard < 256; guard++) {
            if (silenceBytes > 0) {
                int n = (int) Math.min(silenceBytes, zeros.length);
                int taken = enc.submit(zeros, 0, n, ptsOfInput(), WAIT_US);
                if (taken == 0) {
                    return;
                }
                silenceBytes -= taken;
                inFrames += taken / AudioTap.FRAME_BYTES;
                continue;
            }
            if (cur == null) {
                AudioTap.Chunk c = tap.poll();
                if (c == null) {
                    return;
                }
                long labelUs = (c.wallNs - tap.startNs() - c.pausedNs) / 1000L;
                int plan = timeline.plan(labelUs);
                if (plan == AudioTimeline.DROP) {
                    tap.release(c);
                    continue;
                }
                anchored = true;
                cur = c;
                curOff = 0;
                silenceBytes = (long) plan * AudioTap.FRAME_BYTES;
                if (silenceBytes > 0) {
                    continue;
                }
            }
            int taken = enc.submit(cur.pcm, curOff, cur.pcm.length - curOff, ptsOfInput(), WAIT_US);
            if (taken == 0) {
                return;
            }
            curOff += taken;
            inFrames += taken / AudioTap.FRAME_BYTES;
            if (curOff >= cur.pcm.length) {
                tap.release(cur);
                cur = null;
            }
        }
    }

    private long ptsOfInput() {
        return timeline.anchorUs() + inFrames * 1_000_000L / AudioTap.RATE;
    }

    /** Sends what is left to the encoder and writes the last frames. */
    public void finish() throws IOException {
        if (closed) {
            return;
        }
        try {
            feed();
            enc.finish(sink, 1_500);
        } catch (IOException | RuntimeException e) {
            fail(e);
        } finally {
            close();
        }
    }

    /** Frees the encoder; the writer is not touched. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (cur != null) {
            tap.release(cur);
            cur = null;
        }
        try {
            enc.close();
        } catch (RuntimeException ignored) {
            // nothing more to do
        }
    }

    private void fail(Exception e) {
        if (failure == null) {
            failure = e.toString();
        }
    }

    /** The reason the sound stopped working, or null. */
    public String failure() {
        return failure;
    }

    /** One line for the report. */
    public String describe() {
        long seconds = outFrames * Fmp4Writer.AUDIO_FRAME_SAMPLES / AudioTap.RATE;
        String s = "sound: " + enc.describe() + "; " + seconds + " s of sound written";
        if (timeline.silenceFrames() > 0) {
            s += ", " + (timeline.silenceFrames() * 1000L / AudioTap.RATE) + " ms of silence put in to keep it in step with the picture";
        }
        if (timeline.droppedPieces() > 0) {
            s += ", " + timeline.droppedPieces() + " pieces skipped to keep it in step";
        }
        if (tap.dropped() > 0) {
            s += ", " + tap.dropped() + " pieces lost because the recorder was busy";
        }
        if (failure != null) {
            s += "; stopped: " + failure;
        }
        return s;
    }

    /** True once the first piece of sound has reached the recorder. */
    public boolean started() {
        return anchored;
    }
}
