package dev.urinth.recordable.nat;

import java.io.IOException;
import java.util.Locale;

/**
 * The part of an Android media codec (through the NDK) that video and audio encoders share: creating, starting, feeding, draining and
 * stopping. All methods must be called from the same thread; nothing here is ever used by the render thread.
 */
public abstract class NdkCodec implements AutoCloseable {
    public static final int FLAG_KEY = 1;
    public static final int FLAG_CONFIG = 2;
    public static final int FLAG_END = 4;

    protected static final long TRY_AGAIN = -1;
    protected static final long FORMAT_CHANGED = -2;
    protected static final long BUFFERS_CHANGED = -3;

    /** Receives what the encoder produced. */
    public interface Sink {
        void onOutput(byte[] data, int off, int len, long ptsUs, int flags) throws IOException;
    }

    protected final Ndk ndk;
    protected long codec;
    protected String codecName = "unknown";
    private long info;
    private long sizeOut;
    private boolean started;
    private boolean sawEnd;
    private long lastPtsUs;
    private byte[] scratch = new byte[1 << 16];

    protected NdkCodec(Ndk ndk) {
        this.ndk = ndk;
        this.info = Mem.alloc(24);
        this.sizeOut = Mem.alloc(8);
    }

    public final String codecName() {
        return codecName;
    }

    /** True for the slow software encoders that Android provides as a last resort. */
    public final boolean isSoftware() {
        return isSoftwareName(codecName);
    }

    public static boolean isSoftwareName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("c2.android.") || n.startsWith("omx.google.") || n.contains(".sw.") || n.contains("software");
    }

    // ---- formats -----------------------------------------------------------------------------------------------------------------

    protected final long newFormat() throws IOException {
        long fmt = Native.call(ndk.fmtNew, 0, 0, 0, 0, 0, 0);
        if (fmt == 0) {
            throw new IOException("cannot create a media format");
        }
        return fmt;
    }

    protected final void deleteFormat(long fmt) {
        if (fmt != 0) {
            Native.call(ndk.fmtDelete, fmt, 0, 0, 0, 0, 0);
        }
    }

    protected final void setInt(long fmt, String key, int value) {
        long k = Mem.cstr(key);
        try {
            Native.call(ndk.fmtSetInt32, fmt, k, value, 0, 0, 0);
        } finally {
            Mem.free(k);
        }
    }

    protected final void setString(long fmt, String key, String value) {
        long k = Mem.cstr(key);
        long v = Mem.cstr(value);
        try {
            Native.call(ndk.fmtSetString, fmt, k, v, 0, 0, 0);
        } finally {
            Mem.free(k);
            Mem.free(v);
        }
    }

    protected final int getInt(long fmt, String key, int fallback) {
        if (ndk.fmtGetInt32 == 0) {
            return fallback;
        }
        long k = Mem.cstr(key);
        long out = Mem.alloc(8);
        try {
            long r = Native.call(ndk.fmtGetInt32, fmt, k, out, 0, 0, 0);
            return (r & 0xFF) != 0 ? Mem.getInt(out) : fallback;
        } finally {
            Mem.free(k);
            Mem.free(out);
        }
    }

    // ---- codec life cycle ----------------------------------------------------------------------------------------------------------

    /** Creates an encoder for the mime type, or the one with the given name. Returns 0 if there is none. */
    protected final long createCodec(String nameOrNull, String mime) {
        if (nameOrNull != null) {
            if (ndk.createByName == 0) {
                return 0;
            }
            long n = Mem.cstr(nameOrNull);
            try {
                return Native.call(ndk.createByName, n, 0, 0, 0, 0, 0);
            } finally {
                Mem.free(n);
            }
        }
        long type = Mem.cstr(mime);
        try {
            return Native.call(ndk.createEncoder, type, 0, 0, 0, 0, 0);
        } finally {
            Mem.free(type);
        }
    }

    /** The name Android gives the codec ("" if this Android cannot tell). */
    protected final String nameOf(long c) {
        if (ndk.getName == 0 || c == 0) {
            return "";
        }
        long out = Mem.alloc(8);
        try {
            int st = (int) Native.call(ndk.getName, c, out, 0, 0, 0, 0);
            long s = Mem.getLong(out);
            if (st == 0 && s != 0) {
                String n = Mem.readCstr(s, 128);
                if (ndk.releaseName != 0) {
                    Native.call(ndk.releaseName, c, s, 0, 0, 0, 0);
                }
                return n;
            }
        } catch (RuntimeException ignored) {
            // the name is only for the log and for telling software from hardware
        } finally {
            Mem.free(out);
        }
        return "";
    }

    protected final void deleteCodec(long c) {
        if (c != 0) {
            Native.call(ndk.delete, c, 0, 0, 0, 0, 0);
        }
    }

    /** Configures the codec as an encoder. Returns the status of the call (0 is success). */
    protected final int configureEncoder(long c, long fmt) {
        return (int) Native.call(ndk.configure, c, fmt, 0, 0, 1, 0);       // 1 = encoder
    }

    protected final void startCodec() throws IOException {
        int st = (int) Native.call(ndk.start, codec, 0, 0, 0, 0, 0);
        if (st != 0) {
            throw new IOException("start failed (" + st + ")");
        }
        started = true;
    }

    // ---- feeding and draining ------------------------------------------------------------------------------------------------------

    /** Result of {@link #dequeueInput}: where to write the next input. */
    protected long inputPtr;
    protected long inputCap;
    protected long inputIndex;

    /**
     * Gets a free input buffer; after success {@link #inputPtr}, {@link #inputCap} and {@link #inputIndex} describe it.
     *
     * @return false if there was none within the timeout
     */
    protected final boolean dequeueInput(long timeoutUs) throws IOException {
        long idx = Native.call(ndk.dequeueInput, codec, timeoutUs, 0, 0, 0, 0);
        if (idx < 0) {
            if (idx == TRY_AGAIN) {
                return false;
            }
            throw new IOException("dequeueInputBuffer failed (" + idx + ")");
        }
        long ptr = Native.call(ndk.getInput, codec, idx, sizeOut, 0, 0, 0);
        if (ptr == 0) {
            throw new IOException("the encoder gave no input buffer");
        }
        inputIndex = idx;
        inputPtr = ptr;
        inputCap = Mem.getLong(sizeOut);
        return true;
    }

    protected final void queueInput(long bytes, long ptsUs, int flags) throws IOException {
        int st = (int) Native.call(ndk.queueInput, codec, inputIndex, 0, bytes, ptsUs, flags);
        if (st != 0) {
            throw new IOException("queueInputBuffer failed (" + st + ")");
        }
        lastPtsUs = ptsUs;
    }

    /**
     * Takes everything the encoder has ready and gives it to the sink.
     *
     * @param timeoutUs how long to wait for the first result (0 = do not wait)
     * @return the number of buffers handled
     */
    public final int drain(Sink sink, long timeoutUs) throws IOException {
        int n = 0;
        long wait = timeoutUs;
        while (true) {
            long idx = Native.call(ndk.dequeueOutput, codec, info, wait, 0, 0, 0);
            if (idx >= 0) {
                int offset = Mem.getInt(info);
                int size = Mem.getInt(info + 4);
                long pts = Mem.getLong(info + 8);
                int flags = Mem.getInt(info + 16);
                try {
                    long buf = Native.call(ndk.getOutput, codec, idx, sizeOut, 0, 0, 0);
                    long cap = Mem.getLong(sizeOut);
                    if (buf != 0 && size > 0 && offset >= 0 && offset + (long) size <= cap) {
                        if (scratch.length < size) {
                            scratch = new byte[Math.max(size, scratch.length * 2)];
                        }
                        Mem.get(buf + offset, scratch, 0, size);
                        sink.onOutput(scratch, 0, size, pts, flags);
                    }
                } finally {
                    Native.call(ndk.releaseOutput, codec, idx, 0, 0, 0, 0);
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

    /** Tells the encoder no more input comes, and collects what is left. Gives up after {@code maxWaitMs}. */
    public final void finish(Sink sink, long maxWaitMs) throws IOException {
        long deadline = System.nanoTime() + maxWaitMs * 1_000_000L;
        while (!dequeueInput(10_000)) {
            drain(sink, 0);
            if (System.nanoTime() > deadline) {
                return;
            }
        }
        queueInput(0, lastPtsUs, FLAG_END);
        while (!sawEnd && System.nanoTime() < deadline) {
            drain(sink, 20_000);
        }
    }

    /** Asks for a keyframe soon (Android 8 and later); false if this Android cannot do it. */
    public final boolean requestKeyFrame() {
        if (ndk.setParameters == 0 || codec == 0 || !started) {
            return false;
        }
        try {
            long fmt = newFormat();
            try {
                setInt(fmt, "request-sync", 0);
                return (int) Native.call(ndk.setParameters, codec, fmt, 0, 0, 0, 0) == 0;
            } finally {
                deleteFormat(fmt);
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (codec != 0) {
                if (started) {
                    Native.call(ndk.stop, codec, 0, 0, 0, 0, 0);
                }
                Native.call(ndk.delete, codec, 0, 0, 0, 0, 0);
                codec = 0;
                started = false;
            }
        } finally {
            Mem.free(info);
            Mem.free(sizeOut);
            info = 0;
            sizeOut = 0;
        }
    }
}
