package dev.urinth.recordable.nat;

import java.nio.file.Path;

/**
 * The one place the mod calls C code from. Two ways are tried in this order:
 * <ol>
 *   <li>LWJGL's own call functions ({@code org.lwjgl.system.JNI}) and its dynamic linker binding. LWJGL is already loaded and working in
 *       the game (it draws every frame), so this needs no extra file, no unpacking and no permission to run code from a folder.</li>
 *   <li>The mod's own tiny native helper ({@link Shim}), unpacked from the jar into the first folder that lets it run.</li>
 * </ol>
 * Both give the same thing: call a C function through its address with up to six integer or pointer arguments, and look up functions
 * of system libraries (libmediandk for the video and audio encoder, libaaudio for sound output).
 */
public final class Native {
    public enum Kind { NONE, LWJGL, HELPER }

    private static final int RTLD_NOW = 2;
    private static Kind kind = Kind.NONE;
    private static String status = "not set up yet";

    private Native() {
    }

    /** Sets up the call path. Safe to call again; after a success it does nothing, after a failure it tries once more. */
    public static synchronized boolean init(Path... helperFolders) {
        if (kind != Kind.NONE) {
            return true;
        }
        if (!Mem.available()) {
            status = "no raw memory access on this Java (sun.misc.Unsafe is missing)";
            return false;
        }
        StringBuilder why = new StringBuilder();
        try {
            String problem = LwjglCalls.selfTest();
            if (problem == null) {
                kind = Kind.LWJGL;
                status = "LWJGL call path";
                return true;
            }
            why.append("LWJGL path: ").append(problem).append("; ");
        } catch (Throwable t) {
            why.append("LWJGL path: ").append(t.getClass().getSimpleName()).append(' ').append(t.getMessage()).append("; ");
        }
        try {
            if (Shim.load(helperFolders) && helperSelfTest()) {
                kind = Kind.HELPER;
                status = "native helper (" + Shim.status() + ")";
                return true;
            }
            why.append("helper: ").append(Shim.status()).append("; ");
        } catch (Throwable t) {
            why.append("helper: ").append(t.getClass().getSimpleName()).append(' ').append(t.getMessage()).append("; ");
        }
        status = why.toString();
        return false;
    }

    public static synchronized boolean ready() {
        return kind != Kind.NONE;
    }

    public static synchronized Kind kind() {
        return kind;
    }

    /** Human readable state, for the log and the report file. */
    public static synchronized String status() {
        return status;
    }

    /** Used by tests that need the helper path even though LWJGL is around. */
    public static synchronized void forceHelper(Path... folders) {
        kind = Kind.NONE;
        if (Shim.load(folders)) {
            kind = Kind.HELPER;
            status = "native helper (forced)";
        }
    }

    private static boolean helperSelfTest() {
        long dlopen = Shim.dlopenAddr();
        long dlsym = Shim.dlsymAddr();
        return dlopen != 0 && dlsym != 0;
    }

    // ---- calls -------------------------------------------------------------------------------------------------------------------

    /** Calls the C function at {@code fn}. Arguments beyond what the function takes are ignored by it. */
    public static long call(long fn, long a0, long a1, long a2, long a3, long a4, long a5) {
        if (fn == 0) {
            return 0;
        }
        if (kind == Kind.LWJGL) {
            return LwjglCalls.call(fn, a0, a1, a2, a3, a4, a5);
        }
        return Shim.call(fn, a0, a1, a2, a3, a4, a5);
    }

    /** dlopen with the given library name; 0 if it cannot be loaded. */
    static long dlopen(String library) {
        if (kind == Kind.LWJGL) {
            return LwjglCalls.dlopen(library, RTLD_NOW);
        }
        long name = Mem.cstr(library);
        try {
            return Shim.call(Shim.dlopenAddr(), name, RTLD_NOW, 0, 0, 0, 0);
        } finally {
            Mem.free(name);
        }
    }

    /** dlsym; 0 if the library has no such function. */
    static long dlsym(long handle, String symbol) {
        if (kind == Kind.LWJGL) {
            return LwjglCalls.dlsym(handle, symbol);
        }
        long n = Mem.cstr(symbol);
        try {
            return Shim.call(Shim.dlsymAddr(), handle, n, 0, 0, 0, 0);
        } finally {
            Mem.free(n);
        }
    }

    /** The system's last dynamic linking complaint, if the call path can tell. */
    static String dlerror() {
        if (kind == Kind.LWJGL) {
            return LwjglCalls.dlerror();
        }
        return "";
    }
}
