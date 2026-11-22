package dev.urinth.recordable.nat;

import java.io.IOException;

/**
 * The phone's hardware H.264 encoder, driven through the Android NDK media codec functions. Pictures go in as NV12 (or I420 when the
 * codec insists), and H.264 data comes out in "Annex B" form for the MP4 writer.
 *
 * <p>All methods must be called from the same thread. Nothing here is ever used by the render thread.
 */
public final class NdkEncoder implements AutoCloseable {
    public static final int FLAG_KEY = 1;
    public static final int FLAG_CONFIG = 2;
    public static final int FLAG_END = 4;

    private static final int FORMAT_I420 = 19;
    private static final int FORMAT_NV12 = 21;
    private static final long TRY_AGAIN = -1;
    private static final long FORMAT_CHANGED = -2;
    private static final long BUFFERS_CHANGED = -3;

    /** Settings for one recording. */
    public static final class Params {
        public int width;
        public int height;
        public int fps = 30;
        public int bitrate = 5_000_000;
        public int gopSeconds = 2;
    }

    /** Receives what the encoder produced. */
    public interface Sink {
        void onOutput(byte[] data, int off, int len, long ptsUs, int flags) throws IOException;
    }

    private final Ndk ndk;
    private final Params p;
    private long codec;
    private long info;
    private long sizeOut;
    private int colorFormat = FORMAT_NV12;
    private int stride;
    private int sliceHeight;
    private boolean started;
    private boolean sawEnd;
    private long lastPtsUs;
    private String codecName = "unknown";
    private String formatNotes = "";
    private byte[] scratch = new byte[1 << 16];
    private byte[] planar;

    private NdkEncoder(Ndk ndk, Params p) {
        this.ndk = ndk;
        this.p = p;
    }

    /** Creates, configures and starts an encoder. Throws if the phone cannot do it; the caller then uses the fallback. */
    public static NdkEncoder open(Params p) throws IOException {
        Ndk ndk = Ndk.open("libmediandk.so");
        NdkEncoder e = new NdkEncoder(ndk, p);
        try {
            e.start();
            return e;
        } catch (IOException | RuntimeException x) {
            e.close();
            throw x;
        }
    }

    public String codecName() {
        return codecName;
    }

    /** True for the slow software encoders that Android provides as a last resort. */
    public boolean isSoftware() {
        String n = codecName.toLowerCase(java.util.Locale.ROOT);
        return n.startsWith("c2.android.") || n.startsWith("omx.google.") || n.contains(".sw.") || n.contains("software");
    }

    public String describe() {
        return codecName + " " + p.width + "x" + p.height + " @" + p.fps + " fps, " + (p.bitrate / 1000) + " kbps, input "
                + (colorFormat == FORMAT_I420 ? "I420" : "NV12") + " stride=" + stride + " slice=" + sliceHeight + formatNotes;
    }

    private void start() throws IOException {
        info = Mem.alloc(24);
        sizeOut = Mem.alloc(8);
        String failure = "no attempt";
        int[] formats = {FORMAT_NV12, FORMAT_I420};
        outer:
        for (int cf : formats) {
            for (int attempt = 0; attempt < 2; attempt++) {
                long fmt = buildFormat(cf, attempt == 0);
                long type = Mem.cstr("video/avc");
                try {
                    codec = Shim.call(ndk.createEncoder, type, 0, 0, 0, 0, 0);
                } finally {
                    Mem.free(type);
                }
                if (codec == 0) {
                    Shim.call(ndk.fmtDelete, fmt, 0, 0, 0, 0, 0);
                    throw new IOException("the phone has no H.264 encoder");
                }
                int st = (int) Shim.call(ndk.configure, codec, fmt, 0, 0, 1, 0);      // 1 = encoder
                Shim.call(ndk.fmtDelete, fmt, 0, 0, 0, 0, 0);
                if (st == 0) {
                    colorFormat = cf;
                    formatNotes = attempt == 0 ? "" : " (basic settings)";
                    break outer;
                }
                failure = "configure failed (" + st + ") with color format " + cf + (attempt == 0 ? "" : " and basic settings");
                Shim.call(ndk.delete, codec, 0, 0, 0, 0, 0);
                codec = 0;
            }
        }
        if (codec == 0) {
            throw new IOException(failure);
        }
        int st = (int) Shim.call(ndk.start, codec, 0, 0, 0, 0, 0);
        if (st != 0) {
            throw new IOException("start failed (" + st + ")");
        }
        started = true;
        readName();
        readInputFormat();
    }

    private long buildFormat(int colorFormat, boolean all) throws IOException {
        long fmt = Shim.call(ndk.fmtNew, 0, 0, 0, 0, 0, 0);
        if (fmt == 0) {
            throw new IOException("cannot create a media format");
        }
        setString(fmt, "mime", "video/avc");
        setInt(fmt, "width", p.width);
        setInt(fmt, "height", p.height);
        setInt(fmt, "bitrate", p.bitrate);
        setInt(fmt, "frame-rate", p.fps);
        setInt(fmt, "i-frame-interval", p.gopSeconds);
        setInt(fmt, "color-format", colorFormat);
        if (all) {
            setInt(fmt, "bitrate-mode", 2);       // constant bitrate: the most widely supported mode
            setInt(fmt, "max-bframes", 0);        // frames are stored in the order they are shown
            setInt(fmt, "color-standard", 1);     // BT.709
            setInt(fmt, "color-range", 2);        // limited range
            setInt(fmt, "color-transfer", 3);     // SDR video
        }
        return fmt;
    }

    private void setInt(long fmt, String key, int value) {
        long k = Mem.cstr(key);
        try {
            Shim.call(ndk.fmtSetInt32, fmt, k, value, 0, 0, 0);
        } finally {
            Mem.free(k);
        }
    }

    private void setString(long fmt, String key, String value) {
        long k = Mem.cstr(key);
        long v = Mem.cstr(value);
        try {
            Shim.call(ndk.fmtSetString, fmt, k, v, 0, 0, 0);
        } finally {
            Mem.free(k);
            Mem.free(v);
        }
    }

    private int getInt(long fmt, String key, int fallback) {
        if (ndk.fmtGetInt32 == 0) {
            return fallback;
        }
        long k = Mem.cstr(key);
        long out = Mem.alloc(8);
        try {
            long r = Shim.call(ndk.fmtGetInt32, fmt, k, out, 0, 0, 0);
            return (r & 0xFF) != 0 ? Mem.getInt(out) : fallback;
        } finally {
            Mem.free(k);
            Mem.free(out);
        }
    }

    private void readName() {
        if (ndk.getName == 0) {
            return;
        }
        long out = Mem.alloc(8);
        try {
            int st = (int) Shim.call(ndk.getName, codec, out, 0, 0, 0, 0);
            long s = Mem.getLong(out);
            if (st == 0 && s != 0) {
                codecName = Mem.readCstr(s, 128);
                if (ndk.releaseName != 0) {
                    Shim.call(ndk.releaseName, codec, s, 0, 0, 0, 0);
                }
            }
        } catch (RuntimeException ignored) {
            // the name is only for the log
        } finally {
            Mem.free(out);
        }
    }

    private void readInputFormat() {
        stride = p.width;
        sliceHeight = p.height;
        if (ndk.getInputFormat != 0) {
            long f = Shim.call(ndk.getInputFormat, codec, 0, 0, 0, 0, 0);
            if (f != 0) {
                stride = getInt(f, "stride", p.width);
                sliceHeight = getInt(f, "slice-height", p.height);
                int cf = getInt(f, "color-format", colorFormat);
                if (cf == FORMAT_NV12 || cf == FORMAT_I420) {
                    colorFormat = cf;
                }
                Shim.call(ndk.fmtDelete, f, 0, 0, 0, 0, 0);
            }
        }
        if (stride < p.width) {
            stride = p.width;
        }
        if (sliceHeight < p.height) {
            sliceHeight = p.height;
        }
    }

    /** Size in bytes of one picture as {@link #submit} expects it: NV12 with no padding. */
    public static int frameBytes(int width, int height) {
        return width * height * 3 / 2;
    }

    /**
     * Hands one NV12 picture (width x height luma rows, then width x height/2 interleaved chroma rows) to the encoder.
     *
     * @param timeoutUs how long to wait for a free input buffer
     * @return false if the encoder had no room within the timeout
     */
    public boolean submit(byte[] nv12, long ptsUs, long timeoutUs) throws IOException {
        long idx = Shim.call(ndk.dequeueInput, codec, timeoutUs, 0, 0, 0, 0);
        if (idx < 0) {
            if (idx == TRY_AGAIN) {
                return false;
            }
            throw new IOException("dequeueInputBuffer failed (" + idx + ")");
        }
        long ptr = Shim.call(ndk.getInput, codec, idx, sizeOut, 0, 0, 0);
        long cap = Mem.getLong(sizeOut);
        if (ptr == 0) {
            throw new IOException("the encoder gave no input buffer");
        }
        long bytes = fill(ptr, cap, nv12);
        int st = (int) Shim.call(ndk.queueInput, codec, idx, 0, bytes, ptsUs, 0);
        if (st != 0) {
            throw new IOException("queueInputBuffer failed (" + st + ")");
        }
        lastPtsUs = ptsUs;
        return true;
    }

    private long fill(long ptr, long cap, byte[] nv12) throws IOException {
        int w = p.width;
        int h = p.height;
        int ySize = w * h;
        if (nv12.length < ySize * 3 / 2) {
            throw new IOException("picture is too small");
        }
        long total = (long) stride * sliceHeight * 3 / 2;
        if (colorFormat == FORMAT_NV12) {
            long uvStart = (long) stride * sliceHeight;
            if (uvStart + (long) stride * (h / 2 - 1) + w > cap) {
                throw new IOException("the encoder's input buffer is too small (" + cap + " bytes)");
            }
            if (stride == w) {
                Mem.put(ptr, nv12, 0, ySize);
                Mem.put(ptr + uvStart, nv12, ySize, ySize / 2);
            } else {
                for (int r = 0; r < h; r++) {
                    Mem.put(ptr + (long) r * stride, nv12, r * w, w);
                }
                for (int r = 0; r < h / 2; r++) {
                    Mem.put(ptr + uvStart + (long) r * stride, nv12, ySize + r * w, w);
                }
            }
            return Math.min(total, cap);
        }
        // I420: the Y plane, then the U plane, then the V plane, each half as wide and half as tall for chroma
        int cw = w / 2;
        int ch = h / 2;
        int cStride = stride / 2;
        int cSlice = sliceHeight / 2;
        long uStart = (long) stride * sliceHeight;
        long vStart = uStart + (long) cStride * cSlice;
        if (vStart + (long) cStride * (ch - 1) + cw > cap) {
            throw new IOException("the encoder's input buffer is too small (" + cap + " bytes)");
        }
        if (planar == null || planar.length < ySize / 2) {
            planar = new byte[ySize / 2];
        }
        int cn = cw * ch;
        for (int i = 0; i < cn; i++) {
            planar[i] = nv12[ySize + 2 * i];
            planar[cn + i] = nv12[ySize + 2 * i + 1];
        }
        for (int r = 0; r < h; r++) {
            Mem.put(ptr + (long) r * stride, nv12, r * w, w);
        }
        for (int r = 0; r < ch; r++) {
            Mem.put(ptr + uStart + (long) r * cStride, planar, r * cw, cw);
            Mem.put(ptr + vStart + (long) r * cStride, planar, cn + r * cw, cw);
        }
        return Math.min(total, cap);
    }

    /**
     * Takes everything the encoder has ready and gives it to the sink.
     *
     * @param timeoutUs how long to wait for the first result (0 = do not wait)
     * @return the number of buffers handled
     */
    public int drain(Sink sink, long timeoutUs) throws IOException {
        int n = 0;
        long wait = timeoutUs;
        while (true) {
            long idx = Shim.call(ndk.dequeueOutput, codec, info, wait, 0, 0, 0);
            if (idx >= 0) {
                int offset = Mem.getInt(info);
                int size = Mem.getInt(info + 4);
                long pts = Mem.getLong(info + 8);
                int flags = Mem.getInt(info + 16);
                try {
                    long buf = Shim.call(ndk.getOutput, codec, idx, sizeOut, 0, 0, 0);
                    long cap = Mem.getLong(sizeOut);
                    if (buf != 0 && size > 0 && offset >= 0 && offset + (long) size <= cap) {
                        if (scratch.length < size) {
                            scratch = new byte[Math.max(size, scratch.length * 2)];
                        }
                        Mem.get(buf + offset, scratch, 0, size);
                        sink.onOutput(scratch, 0, size, pts, flags);
                    }
                } finally {
                    Shim.call(ndk.releaseOutput, codec, idx, 0, 0, 0, 0);
                }
                n++;
                if ((flags & FLAG_END) != 0) {
                    sawEnd = true;
                    break;
                }
                wait = 0;
            } else if (idx == FORMAT_CHANGED || idx == BUFFERS_CHANGED) {
                wait = 0;
            } else if (idx == TRY_AGAIN) {
                break;
            } else {
                throw new IOException("dequeueOutputBuffer failed (" + idx + ")");
            }
        }
        return n;
    }

    /** Tells the encoder no more pictures come, and collects what is left. Gives up after {@code maxWaitMs}. */
    public void finish(Sink sink, long maxWaitMs) throws IOException {
        long deadline = System.nanoTime() + maxWaitMs * 1_000_000L;
        long idx;
        while ((idx = Shim.call(ndk.dequeueInput, codec, 10_000, 0, 0, 0, 0)) < 0) {
            drain(sink, 0);
            if (System.nanoTime() > deadline) {
                return;
            }
        }
        Shim.call(ndk.queueInput, codec, idx, 0, 0, lastPtsUs, FLAG_END);
        while (!sawEnd && System.nanoTime() < deadline) {
            drain(sink, 20_000);
        }
    }

    @Override
    public void close() {
        try {
            if (codec != 0) {
                if (started) {
                    Shim.call(ndk.stop, codec, 0, 0, 0, 0, 0);
                }
                Shim.call(ndk.delete, codec, 0, 0, 0, 0, 0);
                codec = 0;
            }
        } finally {
            Mem.free(info);
            Mem.free(sizeOut);
            info = 0;
            sizeOut = 0;
        }
    }
}
