package dev.urinth.recordable.nat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The phone's hardware H.264 encoder, driven through the Android NDK media codec functions. Pictures go in as NV12 (or I420 when the
 * codec insists), and H.264 data comes out in "Annex B" form for the MP4 writer.
 *
 * <p>Phones differ a lot in what their encoder accepts, so {@link #open} tries several ways, in this order: the encoder Android picks, with
 * all settings; the same with plain settings; the other colour layout; a lower frame rate; and then the well known hardware encoders by
 * name (for the case that Android picked a software one). Everything that was tried and why it failed is kept for the report.
 */
public final class NdkEncoder extends NdkCodec {
    private static final int FORMAT_I420 = 19;
    private static final int FORMAT_NV12 = 21;
    private static final long SEARCH_BUDGET_NS = 3_000_000_000L;
    private static final String MIME = "video/avc";

    /** Well known names of hardware H.264 encoders, for phones where the default pick is a software one. */
    private static final String[] KNOWN_HARDWARE = {
            "c2.mtk.avc.encoder", "OMX.MTK.VIDEO.ENCODER.AVC",
            "c2.qti.avc.encoder", "OMX.qcom.video.encoder.avc",
            "c2.exynos.h264.encoder", "OMX.Exynos.AVC.Encoder", "OMX.Exynos.avc.enc",
            "c2.sec.avc.encoder", "OMX.hisi.video.encoder.avc", "c2.hisi.avc.encoder",
            "OMX.sprd.h264.encoder", "c2.unisoc.avc.encoder", "OMX.rk.video_encoder.avc", "OMX.Nvidia.h264.encoder",
    };

    /** Settings for one recording. */
    public static final class Params {
        public int width;
        public int height;
        public int fps = 30;
        public int bitrate = 5_000_000;
        public int gopSeconds = 2;
    }

    /** Thrown when the phone has no hardware H.264 encoder, only a software one. */
    public static final class SoftwareOnlyException extends IOException {
        private static final long serialVersionUID = 1L;
        public final String codec;

        public SoftwareOnlyException(String codec, String tried) {
            super("only a software encoder is available (" + codec + ")" + (tried.isEmpty() ? "" : "; tried: " + tried));
            this.codec = codec;
        }
    }

    private final Params p;
    private int colorFormat = FORMAT_NV12;
    private int fpsUsed;
    private int stride;
    private int sliceHeight;
    private String formatNotes = "";
    private byte[] planar;
    private final List<String> tried = new ArrayList<>();

    private NdkEncoder(Ndk ndk, Params p) {
        super(ndk);
        this.p = p;
    }

    /** Creates, configures and starts a hardware encoder. Throws if the phone cannot do it. */
    public static NdkEncoder open(Params p) throws IOException {
        return open(p, false);
    }

    /**
     * @param allowSoftware accept a software encoder too (slow, but it works everywhere)
     */
    public static NdkEncoder open(Params p, boolean allowSoftware) throws IOException {
        Ndk ndk = Ndk.open("libmediandk.so");
        NdkEncoder e = new NdkEncoder(ndk, p);
        try {
            e.search(allowSoftware);
            return e;
        } catch (IOException | RuntimeException x) {
            e.close();
            throw x;
        }
    }

    /** What was tried while looking for a working encoder, one line per attempt. */
    public String attempts() {
        return String.join("; ", tried);
    }

    public String describe() {
        return codecName + " " + p.width + "x" + p.height + " @" + fpsUsed + " fps, " + (p.bitrate / 1000) + " kbps, input "
                + (colorFormat == FORMAT_I420 ? "I420" : "NV12") + " stride=" + stride + " slice=" + sliceHeight + formatNotes;
    }

    // ---- searching for a working configuration -----------------------------------------------------------------------------------------

    /** One way of asking for an encoder. */
    private static final class Attempt {
        final String name;       // null: the encoder Android picks for the type
        final int colorFormat;
        final boolean full;
        final int fps;

        Attempt(String name, int colorFormat, boolean full, int fps) {
            this.name = name;
            this.colorFormat = colorFormat;
            this.full = full;
            this.fps = fps;
        }

        String label() {
            return (name == null ? "default" : name) + "/" + (colorFormat == FORMAT_I420 ? "I420" : "NV12") + (full ? "" : "/basic") + "@" + fps;
        }
    }

    private List<Attempt> plan() {
        List<Attempt> list = new ArrayList<>();
        int fps = p.fps;
        list.add(new Attempt(null, FORMAT_NV12, true, fps));
        list.add(new Attempt(null, FORMAT_NV12, false, fps));
        list.add(new Attempt(null, FORMAT_I420, false, fps));
        if (fps > 30) {
            list.add(new Attempt(null, FORMAT_NV12, false, 30));
            list.add(new Attempt(null, FORMAT_I420, false, 30));
        }
        for (String n : KNOWN_HARDWARE) {
            list.add(new Attempt(n, FORMAT_NV12, false, fps));
            list.add(new Attempt(n, FORMAT_I420, false, fps));
            if (fps > 30) {
                list.add(new Attempt(n, FORMAT_NV12, false, 30));
            }
        }
        return list;
    }

    private void search(boolean allowSoftware) throws IOException {
        long deadline = System.nanoTime() + SEARCH_BUDGET_NS;
        String softwareSeen = null;
        boolean skipDefault = false;
        java.util.Set<String> missing = new java.util.HashSet<>();
        for (Attempt a : plan()) {
            if (codec != 0) {
                break;
            }
            if (System.nanoTime() > deadline) {
                tried.add("stopped looking after " + SEARCH_BUDGET_NS / 1_000_000_000L + " s");
                break;
            }
            if (a.name == null ? skipDefault : missing.contains(a.name)) {
                continue;
            }
            long c = createCodec(a.name, MIME);
            if (c == 0) {
                tried.add(a.label() + ": " + (a.name == null ? "the phone has no H.264 encoder" : "no such encoder"));
                if (a.name == null) {
                    skipDefault = true;
                } else {
                    missing.add(a.name);
                }
                continue;
            }
            String name = nameOf(c);
            if (!allowSoftware && !name.isEmpty() && isSoftwareName(name)) {
                softwareSeen = name;
                tried.add(a.label() + ": " + name + " is a software encoder");
                deleteCodec(c);
                if (a.name == null) {
                    skipDefault = true;                        // the other settings would only give the same software encoder
                } else {
                    missing.add(a.name);
                }
                continue;
            }
            long fmt = buildFormat(a);
            int st;
            try {
                st = configureEncoder(c, fmt);
            } finally {
                deleteFormat(fmt);
            }
            if (st != 0) {
                tried.add(a.label() + ": " + (name.isEmpty() ? "" : name + " ") + "configure failed (" + st + ")");
                deleteCodec(c);
                continue;
            }
            codec = c;
            codecName = name.isEmpty() ? "unknown" : name;
            colorFormat = a.colorFormat;
            fpsUsed = a.fps;
            formatNotes = (a.full ? "" : " (basic settings)") + (a.fps != p.fps ? " (configured for " + a.fps + " fps)" : "");
            try {
                startCodec();
            } catch (IOException x) {
                tried.add(a.label() + ": " + codecName + " " + x.getMessage());
                deleteCodec(codec);
                codec = 0;
                continue;
            }
            readInputFormat();
            tried.add(a.label() + ": " + codecName + " works");
        }
        if (codec == 0) {
            if (softwareSeen != null && !allowSoftware) {
                throw new SoftwareOnlyException(softwareSeen, attempts());
            }
            throw new IOException(tried.isEmpty() ? "no encoder could be started" : String.join("; ", tried));
        }
    }

    private long buildFormat(Attempt a) throws IOException {
        long fmt = newFormat();
        setString(fmt, "mime", MIME);
        setInt(fmt, "width", p.width);
        setInt(fmt, "height", p.height);
        setInt(fmt, "bitrate", p.bitrate);
        setInt(fmt, "frame-rate", a.fps);
        setInt(fmt, "i-frame-interval", p.gopSeconds);
        setInt(fmt, "color-format", a.colorFormat);
        if (a.full) {
            setInt(fmt, "bitrate-mode", 2);                    // constant bitrate: the most widely supported mode
            setInt(fmt, "max-bframes", 0);                     // frames are stored in the order they are shown
            setInt(fmt, "color-standard", 1);                  // BT.709
            setInt(fmt, "color-range", 2);                     // limited range
            setInt(fmt, "color-transfer", 3);                  // SDR video
            setInt(fmt, "prepend-sps-pps-to-idr-frames", 1);   // every keyframe can start a clip
        }
        return fmt;
    }

    private void readInputFormat() {
        stride = p.width;
        sliceHeight = p.height;
        if (ndk.getInputFormat != 0) {
            long f = Native.call(ndk.getInputFormat, codec, 0, 0, 0, 0, 0);
            if (f != 0) {
                stride = getInt(f, "stride", p.width);
                sliceHeight = getInt(f, "slice-height", p.height);
                int cf = getInt(f, "color-format", colorFormat);
                if (cf == FORMAT_NV12 || cf == FORMAT_I420) {
                    colorFormat = cf;
                }
                deleteFormat(f);
            }
        }
        if (stride < p.width) {
            stride = p.width;
        }
        if (sliceHeight < p.height) {
            sliceHeight = p.height;
        }
    }

    // ---- feeding ----------------------------------------------------------------------------------------------------------------------

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
        if (!dequeueInput(timeoutUs)) {
            return false;
        }
        long bytes = fill(inputPtr, inputCap, nv12);
        queueInput(bytes, ptsUs, 0);
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
}
