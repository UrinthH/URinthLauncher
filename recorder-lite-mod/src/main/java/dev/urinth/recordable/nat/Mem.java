package dev.urinth.recordable.nat;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

/** Raw native memory for the calls into the media codec library. Everything here is only used by the encoder thread. */
final class Mem {
    private static final sun.misc.Unsafe U;
    private static final long BYTE_BASE;

    static {
        sun.misc.Unsafe u = null;
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = (sun.misc.Unsafe) f.get(null);
        } catch (Throwable ignored) {
            // leave null: the hardware encoder path reports itself as unavailable
        }
        U = u;
        BYTE_BASE = u == null ? 0 : u.arrayBaseOffset(byte[].class);
    }

    private Mem() {
    }

    static boolean available() {
        return U != null;
    }

    static long alloc(long bytes) {
        long p = U.allocateMemory(Math.max(8, bytes));
        U.setMemory(p, Math.max(8, bytes), (byte) 0);
        return p;
    }

    static void free(long p) {
        if (p != 0) {
            U.freeMemory(p);
        }
    }

    static void putInt(long p, int v) {
        U.putInt(p, v);
    }

    static void putLong(long p, long v) {
        U.putLong(p, v);
    }

    static int getInt(long p) {
        return U.getInt(p);
    }

    static long getLong(long p) {
        return U.getLong(p);
    }

    static void put(long dst, byte[] src, int off, int len) {
        U.copyMemory(src, BYTE_BASE + off, null, dst, len);
    }

    static void get(long src, byte[] dst, int off, int len) {
        U.copyMemory(null, src, dst, BYTE_BASE + off, len);
    }

    /** Copies native memory to native memory. */
    static void copy(long src, long dst, long len) {
        U.copyMemory(src, dst, len);
    }

    /** A NUL terminated copy of the text; the caller frees it. */
    static long cstr(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        long p = alloc(b.length + 1L);
        put(p, b, 0, b.length);
        return p;
    }

    /** Reads a NUL terminated string, at most {@code max} bytes. */
    static String readCstr(long p, int max) {
        if (p == 0) {
            return "";
        }
        byte[] tmp = new byte[max];
        int n = 0;
        while (n < max) {
            byte c = U.getByte(p + n);
            if (c == 0) {
                break;
            }
            tmp[n++] = c;
        }
        return new String(tmp, 0, n, StandardCharsets.UTF_8);
    }
}
