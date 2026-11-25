package dev.urinth.recordable.nat;

import org.lwjgl.system.JNI;
import org.lwjgl.system.linux.DynamicLinkLoader;

/**
 * The LWJGL side of {@link Native}. This class is only touched when {@link Native} runs, so a missing or different LWJGL shows up as
 * an error that {@link Native} catches, never as a crash of the game.
 */
final class LwjglCalls {
    private static final int RTLD_NOW = DynamicLinkLoader.RTLD_NOW;

    private LwjglCalls() {
    }

    static long call(long fn, long a0, long a1, long a2, long a3, long a4, long a5) {
        // The last argument is the address of the function; the C function sees a0..a5 in its argument registers.
        return JNI.invokePPPPPPP(a0, a1, a2, a3, a4, a5, fn);
    }

    static long dlopen(String library, int flags) {
        return DynamicLinkLoader.dlopen(library, flags);
    }

    static long dlsym(long handle, String symbol) {
        return DynamicLinkLoader.dlsym(handle, symbol);
    }

    static String dlerror() {
        try {
            String e = DynamicLinkLoader.dlerror();
            return e == null ? "" : e;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Proves that LWJGL's dynamic linker and call function work in this process, by loading the media codec library and making one
     * harmless call through it.
     *
     * @return null if everything works, otherwise what went wrong
     */
    static String selfTest() {
        long lib = DynamicLinkLoader.dlopen("libmediandk.so", RTLD_NOW);
        if (lib == 0) {
            return "cannot load libmediandk.so (" + dlerror() + ")";
        }
        long create = DynamicLinkLoader.dlsym(lib, "AMediaFormat_new");
        long delete = DynamicLinkLoader.dlsym(lib, "AMediaFormat_delete");
        if (create == 0 || delete == 0) {
            return "libmediandk.so has no AMediaFormat_new (" + dlerror() + ")";
        }
        long format = call(create, 0, 0, 0, 0, 0, 0);
        if (format == 0) {
            return "AMediaFormat_new returned nothing";
        }
        call(delete, format, 0, 0, 0, 0, 0);
        return null;
    }
}
