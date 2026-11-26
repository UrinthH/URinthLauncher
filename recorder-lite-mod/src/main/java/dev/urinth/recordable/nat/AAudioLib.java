package dev.urinth.recordable.nat;

import java.io.IOException;

/**
 * Android's AAudio (libaaudio.so, Android 8.1 and later) through {@link Native}: opens a 16-bit PCM stream for playing or recording and
 * reads or writes it. Used for the game's sound (the game's audio is rendered by the mod and played through here) and for the microphone.
 * All streams are opened in the shared mode, so other apps (and the phone's own sound mixer) keep working.
 */
public final class AAudioLib {
    public static final int OK = 0;
    public static final int ERROR_DISCONNECTED = -899;

    private static final int DIRECTION_OUTPUT = 0;
    private static final int DIRECTION_INPUT = 1;
    private static final int SHARING_SHARED = 1;
    private static final int FORMAT_PCM_I16 = 1;
    private static final int PERFORMANCE_NONE = 10;
    private static final int USAGE_GAME = 14;
    private static final int INPUT_PRESET_CAMCORDER = 5;

    private final long createBuilder;
    private final long setDirection;
    private final long setSharing;
    private final long setFormat;
    private final long setChannels;
    private final long setRate;
    private final long setPerformance;
    private final long setUsage;           // optional, Android 9+
    private final long setInputPreset;     // optional, Android 9+
    private final long openStream;
    private final long deleteBuilder;
    private final long requestStart;
    private final long requestStop;
    private final long closeStream;
    private final long write;
    private final long read;
    private final long setBufferSize;
    private final long getBufferSize;
    private final long getCapacity;
    private final long getXRuns;

    private AAudioLib(long h) throws IOException {
        createBuilder = sym(h, "AAudio_createStreamBuilder", true);
        setDirection = sym(h, "AAudioStreamBuilder_setDirection", true);
        setSharing = sym(h, "AAudioStreamBuilder_setSharingMode", true);
        setFormat = sym(h, "AAudioStreamBuilder_setFormat", true);
        setChannels = sym(h, "AAudioStreamBuilder_setChannelCount", true);
        setRate = sym(h, "AAudioStreamBuilder_setSampleRate", true);
        setPerformance = sym(h, "AAudioStreamBuilder_setPerformanceMode", true);
        setUsage = sym(h, "AAudioStreamBuilder_setUsage", false);
        setInputPreset = sym(h, "AAudioStreamBuilder_setInputPreset", false);
        openStream = sym(h, "AAudioStreamBuilder_openStream", true);
        deleteBuilder = sym(h, "AAudioStreamBuilder_delete", true);
        requestStart = sym(h, "AAudioStream_requestStart", true);
        requestStop = sym(h, "AAudioStream_requestStop", true);
        closeStream = sym(h, "AAudioStream_close", true);
        write = sym(h, "AAudioStream_write", true);
        read = sym(h, "AAudioStream_read", true);
        setBufferSize = sym(h, "AAudioStream_setBufferSizeInFrames", true);
        getBufferSize = sym(h, "AAudioStream_getBufferSizeInFrames", true);
        getCapacity = sym(h, "AAudioStream_getBufferCapacityInFrames", true);
        getXRuns = sym(h, "AAudioStream_getXRunCount", true);
    }

    /** Loads the library. Throws if this phone has no AAudio or the native call path is not set up. */
    public static AAudioLib open() throws IOException {
        if (!Native.ready()) {
            throw new IOException("the native call path is not available (" + Native.status() + ")");
        }
        long h = Native.dlopen("libaaudio.so");
        if (h == 0) {
            String why = Native.dlerror();
            throw new IOException("cannot load libaaudio.so" + (why.isEmpty() ? "" : " (" + why + ")"));
        }
        return new AAudioLib(h);
    }

    private static long sym(long h, String name, boolean required) throws IOException {
        long a = Native.dlsym(h, name);
        if (a == 0 && required) {
            throw new IOException("libaaudio.so has no " + name);
        }
        return a;
    }

    /** Opens and starts a stream of 16-bit samples. Returns its handle; throws (and leaves nothing open) if that fails. */
    public long openStream(boolean input, int rate, int channels, int bufferFrames) throws IOException {
        long box = Mem.alloc(8);
        try {
            int r = (int) Native.call(createBuilder, box, 0, 0, 0, 0, 0);
            long builder = Mem.getLong(box);
            if (r != OK || builder == 0) {
                throw new IOException("AAudio_createStreamBuilder failed (" + r + ")");
            }
            long stream;
            try {
                Native.call(setDirection, builder, input ? DIRECTION_INPUT : DIRECTION_OUTPUT, 0, 0, 0, 0);
                Native.call(setSharing, builder, SHARING_SHARED, 0, 0, 0, 0);
                Native.call(setFormat, builder, FORMAT_PCM_I16, 0, 0, 0, 0);
                Native.call(setChannels, builder, channels, 0, 0, 0, 0);
                Native.call(setRate, builder, rate, 0, 0, 0, 0);
                Native.call(setPerformance, builder, PERFORMANCE_NONE, 0, 0, 0, 0);
                if (input && setInputPreset != 0) {
                    Native.call(setInputPreset, builder, INPUT_PRESET_CAMCORDER, 0, 0, 0, 0);
                }
                if (!input && setUsage != 0) {
                    Native.call(setUsage, builder, USAGE_GAME, 0, 0, 0, 0);
                }
                Mem.putLong(box, 0);
                r = (int) Native.call(openStream, builder, box, 0, 0, 0, 0);
                stream = Mem.getLong(box);
                if (r != OK || stream == 0) {
                    throw new IOException("AAudio could not open the " + (input ? "recording" : "playback") + " stream (" + r + ")");
                }
            } finally {
                Native.call(deleteBuilder, builder, 0, 0, 0, 0, 0);
            }
            if (bufferFrames > 0) {
                Native.call(setBufferSize, stream, bufferFrames, 0, 0, 0, 0);
            }
            r = (int) Native.call(requestStart, stream, 0, 0, 0, 0, 0);
            if (r != OK) {
                Native.call(closeStream, stream, 0, 0, 0, 0, 0);
                throw new IOException("AAudio could not start the stream (" + r + ")");
            }
            return stream;
        } finally {
            Mem.free(box);
        }
    }

    /** Plays frames (each frame is one 16-bit sample per channel); waits up to the timeout. Returns the frames taken, or a negative error. */
    public int write(long stream, long ptr, int frames, long timeoutNs) {
        return (int) Native.call(write, stream, ptr, frames, timeoutNs, 0, 0);
    }

    /** Reads frames; waits up to the timeout. Returns the frames read, or a negative error. */
    public int read(long stream, long ptr, int frames, long timeoutNs) {
        return (int) Native.call(read, stream, ptr, frames, timeoutNs, 0, 0);
    }

    public int xruns(long stream) {
        return (int) Native.call(getXRuns, stream, 0, 0, 0, 0, 0);
    }

    public int bufferFrames(long stream) {
        return (int) Native.call(getBufferSize, stream, 0, 0, 0, 0, 0);
    }

    public int capacityFrames(long stream) {
        return (int) Native.call(getCapacity, stream, 0, 0, 0, 0, 0);
    }

    /** Asks for a different buffer size; returns the size the stream really uses (or a negative error). */
    public int setBufferFrames(long stream, int frames) {
        return (int) Native.call(setBufferSize, stream, frames, 0, 0, 0, 0);
    }

    public void close(long stream) {
        if (stream == 0) {
            return;
        }
        try {
            Native.call(requestStop, stream, 0, 0, 0, 0, 0);
        } catch (RuntimeException ignored) {
            // closing is all that matters
        }
        Native.call(closeStream, stream, 0, 0, 0, 0, 0);
    }
}
