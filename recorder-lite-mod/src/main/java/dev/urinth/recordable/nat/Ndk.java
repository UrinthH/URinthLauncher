package dev.urinth.recordable.nat;

import java.io.IOException;

/** The addresses of the media codec functions of the Android NDK (libmediandk.so), found through {@link Native}. */
final class Ndk {
    final long fmtNew;
    final long fmtDelete;
    final long fmtSetInt32;
    final long fmtSetString;
    final long fmtGetInt32;            // optional
    final long createEncoder;
    final long createByName;           // optional
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
    final long setParameters;          // optional, Android 8+

    private Ndk(long handle) throws IOException {
        fmtNew = sym(handle, "AMediaFormat_new", true);
        fmtDelete = sym(handle, "AMediaFormat_delete", true);
        fmtSetInt32 = sym(handle, "AMediaFormat_setInt32", true);
        fmtSetString = sym(handle, "AMediaFormat_setString", true);
        fmtGetInt32 = sym(handle, "AMediaFormat_getInt32", false);
        createEncoder = sym(handle, "AMediaCodec_createEncoderByType", true);
        createByName = sym(handle, "AMediaCodec_createCodecByName", false);
        configure = sym(handle, "AMediaCodec_configure", true);
        start = sym(handle, "AMediaCodec_start", true);
        stop = sym(handle, "AMediaCodec_stop", true);
        delete = sym(handle, "AMediaCodec_delete", true);
        dequeueInput = sym(handle, "AMediaCodec_dequeueInputBuffer", true);
        getInput = sym(handle, "AMediaCodec_getInputBuffer", true);
        queueInput = sym(handle, "AMediaCodec_queueInputBuffer", true);
        dequeueOutput = sym(handle, "AMediaCodec_dequeueOutputBuffer", true);
        getOutput = sym(handle, "AMediaCodec_getOutputBuffer", true);
        releaseOutput = sym(handle, "AMediaCodec_releaseOutputBuffer", true);
        getInputFormat = sym(handle, "AMediaCodec_getInputFormat", false);
        getName = sym(handle, "AMediaCodec_getName", false);
        releaseName = sym(handle, "AMediaCodec_releaseName", false);
        setParameters = sym(handle, "AMediaCodec_setParameters", false);
    }

    static Ndk open(String library) throws IOException {
        if (!Native.ready()) {
            throw new IOException("the native call path is not available (" + Native.status() + ")");
        }
        long handle = Native.dlopen(library);
        if (handle == 0) {
            String why = Native.dlerror();
            throw new IOException("cannot load " + library + (why.isEmpty() ? "" : " (" + why + ")"));
        }
        return new Ndk(handle);
    }

    private static long sym(long handle, String name, boolean required) throws IOException {
        long addr = Native.dlsym(handle, name);
        if (addr == 0 && required) {
            throw new IOException("the media codec library has no " + name);
        }
        return addr;
    }
}
