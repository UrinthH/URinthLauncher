package dev.urinth.recordable.enc;

import dev.urinth.recordable.mux.Fmp4Writer;
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
    private final NdkEncoder.Sink sink;
    private byte[] nv12;
    private boolean sawOutput;
    private boolean closed;

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
        NdkEncoder.Params p = new NdkEncoder.Params();
        p.width = width;
        p.height = height;
        p.fps = fps;
        p.bitrate = bitrateBps;
        NdkEncoder enc = NdkEncoder.open(p);
        try {
            if (enc.isSoftware()) {
                throw new SoftwareEncoderException(enc.codecName());
            }
            Fmp4Writer mux = new Fmp4Writer(file, width, height, fps);
            return new H264Out(enc, mux, width, height, rgbaInput, swapChroma, onFirstOutput);
        } catch (IOException | RuntimeException e) {
            enc.close();
            throw e;
        }
    }

    /** Opens the encoder even if it is a software one; used when the player accepts that. */
    public static H264Out openAllowingSoftware(Path file, int width, int height, int fps, int bitrateBps, boolean rgbaInput,
                                               boolean swapChroma, Runnable onFirstOutput) throws IOException {
        NdkEncoder.Params p = new NdkEncoder.Params();
        p.width = width;
        p.height = height;
        p.fps = fps;
        p.bitrate = bitrateBps;
        NdkEncoder enc = NdkEncoder.open(p);
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

        public SoftwareEncoderException(String codec) {
            super("only a software encoder is available (" + codec + ")");
        }
    }

    public boolean isSoftware() {
        return encoder.isSoftware();
    }

    @Override
    public boolean accept(Frame f) throws IOException {
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
        encoder.drain(sink, 4_000);
    }

    @Override
    public void finish() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            encoder.finish(sink, 3_000);
        } finally {
            try {
                encoder.close();
            } finally {
                muxer.close();
            }
        }
    }

    @Override
    public void abort() {
        if (closed) {
            return;
        }
        closed = true;
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
