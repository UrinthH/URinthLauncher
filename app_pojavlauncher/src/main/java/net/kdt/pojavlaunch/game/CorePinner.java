package net.kdt.pojavlaunch.game;

import android.util.Log;

/**
 * The one native call behind "Keep the render thread on the fast cores": moves a single thread onto a set of CPU cores
 * (sched_setaffinity, in the launcher's native library, jni/affinity.c). Android has no Java call for that.
 */
final class CorePinner {
    private static final String TAG = "CorePinner";
    private static boolean sLoaded;

    static {
        try {
            // Already loaded by the game's start-up code; loading it again by the same class loader does nothing.
            System.loadLibrary("pojavexec");
            sLoaded = true;
        } catch (Throwable error) {
            Log.w(TAG, "The native library is not available", error);
        }
    }

    private CorePinner() {}

    private static native int nativePinThread(int tid, long mask);

    /**
     * Allows a thread to run only on the cores of a mask (bit n is core n).
     *
     * @return 0 when it worked, the error number the kernel gave when it did not, -1 when the native call is not there
     */
    static int pin(int tid, long mask) {
        if (!sLoaded) return -1;
        try {
            return nativePinThread(tid, mask);
        } catch (UnsatisfiedLinkError missing) {
            return -1;
        }
    }
}
