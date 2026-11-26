package dev.urinth.recordable.enc;

import dev.urinth.recordable.audio.AudioRecorder;
import dev.urinth.recordable.audio.AudioTap;
import dev.urinth.recordable.mux.Fmp4Writer;
import dev.urinth.recordable.nat.NdkCodec;
import dev.urinth.recordable.nat.NdkEncoder;

import java.io.IOException;
import java.nio.file.Path;

/** The normal path: the phone's H.264 encoder writes into a fragmented MP4. */
public final class H264Out implements VideoOut {
    /** How long a picture may wait for a free input buffer of the encoder before it is dropped. */
    private static final long INPUT_WAIT_US = 15_000;

    private final NdkEncoder encoder;
    private final Fmp4Writer muxer;
    private final int width;
    private final int height;
    private final boolean rgbaInput;
    private final boolean swapChroma;
    private final Runnable onFirstOutput;
    private final NdkCodec.Sink sink;
    private byte[] nv12;
    private boolean sawOutput;
    private boolean closed;
    private AudioRecorder audio;
    private String audioNote;
    private String audioLast;

    private H264Out(NdkEncoder encoder, Fmp4Writer muxer, int width, int height, boolean rgbaInput, boolean swapChroma,
                    Runnable onFirstOutput) {
        this.encoder = encoder;
        this.muxer = muxer;
        this.width = width;
        this.height = height;
        this.rgbaInput = rgbaInput;
        this.swapChroma = swapChroma;
        this.onFirstOutput = onFirstOutput;
        this.sink = (data, off, len, ptsUs, flags) -> {
            if (!sawOutput) {
                sawOutput = true;
                if (this.onFirstOutput != null) {
                    this.onFirstOutput.run();
                }
            }
            this.muxer.write(data, off, len, ptsUs);
        };
        if (rgbaInput) {
            this.nv12 = new byte[NdkEncoder.frameBytes(width, height)];
        }
    }

    /**
     * @param rgbaInput true if the pictures arrive as RGBA (bottom row first) and must be converted here; false for finished NV12
     * @param onFirstOutput runs once, when the encoder has produced its first output (proof that it works); may be null
     */
    public static H264Out open(Path file, int width, int height, int fps, int bitrateBps, boolean rgbaInput, boolean swapChroma,
                               Runnable onFirstOutput) throws IOException {
        return open(file, width, height, fps, bitrateBps, rgbaInput, swapChroma, onFirstOutput, false);
    }

    /** Opens the encoder even if it is a software one; used when the player accepts that. */
    public static H264Out openAllowingSoftware(Path file, int width, int height, int fps, int bitrateBps, boolean rgbaInput,
                                               boolean swapChroma, Runnable onFirstOutput) throws IOException {
        return open(file, width, height, fps, bitrateBps, rgbaInput, swapChroma, onFirstOutput, true);
    }

    private static H264Out open(Path file, int width, int height, int fps, int bitrateBps, boolean rgbaInput, boolean swapChroma,
                                Runnable onFirstOutput, boolean allowSoftware) throws IOException {
        NdkEncoder.Params p = new NdkEncoder.Params();
        p.width = width;
        p.height = height;
        p.fps = fps;
        p.bitrate = bitrateBps;
        NdkEncoder enc;
        try {
            enc = NdkEncoder.open(p, allowSoftware);
        } catch (NdkEncoder.SoftwareOnlyException e) {
            throw new SoftwareEncoderException(e.codec, e.getMessage());
        }
        try {
            Fmp4Writer mux = new Fmp4Writer(file, width, height, fps);
            return new H264Out(enc, mux, width, height, rgbaInput, swapChroma, onFirstOutput);
        } catch (IOException | RuntimeException e) {
            enc.close();
            throw e;
        }
    }

    /** Thrown by {@link #open} when the only encoder on the phone is a slow software one. */
    public static final class SoftwareEncoderException extends IOException {
        private static final long serialVersionUID = 1L;

        public SoftwareEncoderException(String codec, String detail) {
            super(detail);
        }
    }

    public boolean isSoftware() {
        return encoder.isSoftware();
    }

    /**
     * Adds a sound track that is fed from the tap. This never throws: if the phone has no AAC encoder the video is recorded without
     * sound, as it would be if this was not called.
     *
     * @return true if the sound track was added
     */
    public boolean attachAudio(AudioTap tap) {
        try {
            audio = AudioRecorder.open(tap, muxer);
            audioNote = "sound: recording through the phone's AAC encoder";
            return true;
        } catch (IOException | RuntimeException | LinkageError e) {
            audio = null;
            audioNote = "sound: not recorded (the phone's AAC encoder could not be used: " + e.getMessage() + ")";
            return false;
        }
    }

    public boolean hasAudio() {
        return audio != null;
    }

    @Override
    public String audioNote() {
        AudioRecorder a = audio;
        if (a != null) {
            return a.describe();
        }
        return audioLast != null ? audioLast : audioNote;
    }

    /** Takes the sound that has been collected and gives it to the encoder. A problem here only ends the sound, never the video. */
    private void serviceAudio() {
        AudioRecorder a = audio;
        if (a == null) {
            return;
        }
        try {
            a.service();
        } catch (IOException | RuntimeException e) {
            dropAudio(a, e);
        }
    }

    private void dropAudio(AudioRecorder a, Exception e) {
        audioLast = a.describe() + "; the sound stopped: " + e;
        audio = null;
        a.close();
    }

    /** What was tried while looking for a working encoder, for the report. */
    public String attempts() {
        return encoder.attempts();
    }

    @Override
    public boolean accept(Frame f) throws IOException {
        serviceAudio();
        byte[] picture = f.data;
        if (rgbaInput) {
            Nv12.fromRgbaBottomUp(f.data, width, height, nv12);
            if (swapChroma) {
                Nv12.swapChroma(nv12, width, height);
            }
            picture = nv12;
        }
        boolean taken = encoder.submit(picture, f.ptsUs, INPUT_WAIT_US);
        encoder.drain(sink, 0);
        return taken;
    }

    @Override
    public void idle() throws IOException {
        serviceAudio();
        encoder.drain(sink, 4_000);
    }

    @Override
    public void finish() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            finishAudio();
            encoder.finish(sink, 3_000);
        } finally {
            try {
                encoder.close();
            } finally {
                muxer.close();
            }
        }
    }

    private void finishAudio() {
        AudioRecorder a = audio;
        if (a == null) {
            return;
        }
        try {
            a.finish();
        } catch (IOException | RuntimeException e) {
            audioLast = a.describe() + "; the sound stopped: " + e;
        }
        audioLast = audioLast != null ? audioLast : a.describe();
        audio = null;
    }

    @Override
    public void abort() {
        if (closed) {
            return;
        }
        closed = true;
        AudioRecorder a = audio;
        if (a != null) {
            audioLast = a.describe() + "; the recording was cut short";
            audio = null;
            a.close();
        }
        try {
            encoder.close();
        } catch (RuntimeException ignored) {
            // nothing more to do
        }
        try {
            muxer.close();
        } catch (IOException | RuntimeException ignored) {
            // the file stays playable up to the last finished fragment
        }
    }

    @Override
    public long bytes() {
        return muxer.bytesWritten();
    }

    @Override
    public long millis() {
        return muxer.durationMs();
    }

    @Override
    public String describe() {
        return encoder.describe() + (rgbaInput ? ", colours converted on the CPU" : "");
    }
}
