package dev.urinth.recordable.nat;

import java.io.IOException;

/** The addresses of the media codec functions of the Android NDK (libmediandk.so), found with dlopen/dlsym through the shim. */
final class Ndk {
    private static final int RTLD_NOW = 2;

    final long fmtNew;
    final long fmtDelete;
    final long fmtSetInt32;
    final long fmtSetString;
    final long fmtGetInt32;            // optional
    final long createEncoder;
    final long configure;
    final long start;
    final long stop;
    final long delete;
    final long dequeueInput;
    final long getInput;
    final long queueInput;
    final long dequeueOutput;
    final long getOutput;
    final long releaseOutput;
    final long getInputFormat;         // optional, Android 9+
    final long getName;                // optional, Android 9+
    final long releaseName;            // optional, Android 9+

    private Ndk(long handle, long dlsym) throws IOException {
        fmtNew = sym(handle, dlsym, "AMediaFormat_new", true);
        fmtDelete = sym(handle, dlsym, "AMediaFormat_delete", true);
        fmtSetInt32 = sym(handle, dlsym, "AMediaFormat_setInt32", true);
        fmtSetString = sym(handle, dlsym, "AMediaFormat_setString", true);
        fmtGetInt32 = sym(handle, dlsym, "AMediaFormat_getInt32", false);
        createEncoder = sym(handle, dlsym, "AMediaCodec_createEncoderByType", true);
        configure = sym(handle, dlsym, "AMediaCodec_configure", true);
        start = sym(handle, dlsym, "AMediaCodec_start", true);
        stop = sym(handle, dlsym, "AMediaCodec_stop", true);
        delete = sym(handle, dlsym, "AMediaCodec_delete", true);
        dequeueInput = sym(handle, dlsym, "AMediaCodec_dequeueInputBuffer", true);
        getInput = sym(handle, dlsym, "AMediaCodec_getInputBuffer", true);
        queueInput = sym(handle, dlsym, "AMediaCodec_queueInputBuffer", true);
        dequeueOutput = sym(handle, dlsym, "AMediaCodec_dequeueOutputBuffer", true);
        getOutput = sym(handle, dlsym, "AMediaCodec_getOutputBuffer", true);
        releaseOutput = sym(handle, dlsym, "AMediaCodec_releaseOutputBuffer", true);
        getInputFormat = sym(handle, dlsym, "AMediaCodec_getInputFormat", false);
        getName = sym(handle, dlsym, "AMediaCodec_getName", false);
        releaseName = sym(handle, dlsym, "AMediaCodec_releaseName", false);
    }

    static Ndk open(String library) throws IOException {
        if (!Shim.isLoaded()) {
            throw new IOException("the native helper is not loaded");
        }
        long dlopen = Shim.dlopenAddr();
        long dlsym = Shim.dlsymAddr();
        if (dlopen == 0 || dlsym == 0) {
            throw new IOException("this system has no dlopen/dlsym");
        }
        long name = Mem.cstr(library);
        long handle;
        try {
            handle = Shim.call(dlopen, name, RTLD_NOW, 0, 0, 0, 0);
        } finally {
            Mem.free(name);
        }
        if (handle == 0) {
            throw new IOException("cannot load " + library);
        }
        return new Ndk(handle, dlsym);
    }

    private static long sym(long handle, long dlsym, String name, boolean required) throws IOException {
        long n = Mem.cstr(name);
        long addr;
        try {
            addr = Shim.call(dlsym, handle, n, 0, 0, 0, 0);
        } finally {
            Mem.free(n);
        }
        if (addr == 0 && required) {
            throw new IOException("the media codec library has no " + name);
        }
        return addr;
    }
}
