package dev.urinth.recordable.mux;

import java.util.Arrays;

/** A growable big-endian byte writer with helpers for MP4 style boxes (4-byte size, 4-character type, content). */
final class ByteSink {
    private byte[] buf = new byte[512];
    private int n;

    int size() {
        return n;
    }

    byte[] toArray() {
        return Arrays.copyOf(buf, n);
    }

    private void ensure(int extra) {
        if (n + extra > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, n + extra));
        }
    }

    ByteSink u8(int v) {
        ensure(1);
        buf[n++] = (byte) v;
        return this;
    }

    ByteSink u16(int v) {
        ensure(2);
        buf[n++] = (byte) (v >>> 8);
        buf[n++] = (byte) v;
        return this;
    }

    ByteSink u32(long v) {
        ensure(4);
        buf[n++] = (byte) (v >>> 24);
        buf[n++] = (byte) (v >>> 16);
        buf[n++] = (byte) (v >>> 8);
        buf[n++] = (byte) v;
        return this;
    }

    ByteSink u64(long v) {
        u32(v >>> 32);
        return u32(v & 0xFFFFFFFFL);
    }

    ByteSink bytes(byte[] b) {
        return bytes(b, 0, b.length);
    }

    ByteSink bytes(byte[] b, int off, int len) {
        ensure(len);
        System.arraycopy(b, off, buf, n, len);
        n += len;
        return this;
    }

    ByteSink fourcc(String s) {
        for (int i = 0; i < 4; i++) {
            u8(s.charAt(i));
        }
        return this;
    }

    ByteSink zeros(int count) {
        ensure(count);
        n += count;                                            // the array is zero filled when it grows; clear in case of reuse
        Arrays.fill(buf, n - count, n, (byte) 0);
        return this;
    }

    /** Starts a box and returns its start position for {@link #end}. */
    int begin(String type) {
        int pos = n;
        u32(0);
        fourcc(type);
        return pos;
    }

    /** Starts a "full box" (version and flags first). */
    int beginFull(String type, int version, int flags) {
        int pos = begin(type);
        u32(((long) version << 24) | (flags & 0xFFFFFF));
        return pos;
    }

    void end(int pos) {
        patch32(pos, n - pos);
    }

    void patch32(int pos, long v) {
        buf[pos] = (byte) (v >>> 24);
        buf[pos + 1] = (byte) (v >>> 16);
        buf[pos + 2] = (byte) (v >>> 8);
        buf[pos + 3] = (byte) v;
    }
}
