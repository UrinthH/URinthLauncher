package net.kdt.pojavlaunch.game.renderer;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.system.ErrnoException;
import android.system.Os;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;
import net.kdt.pojavlaunch.utils.GpuUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Reversible opt-in renderer profile. It never edits options.txt, resolution,
 * or the user's selected renderer. Profile changes are applied at game-launch
 * environment setup time, so an already-running game must be restarted.
 */
public final class URinthRender {
    private static final String TAG = "URinthRender";
    private static final String PREFS = "urinth_ui";
    private static final String KEY_ULTRA = "ultra";
    private static final String PROFILE_KEY = "URINTH_RENDER_PROFILE";
    private static final String ULTRA_KEY = "URINTH_ULTRA_MODE";
    private static final String BACKEND_KEY = "URINTH_RENDER_BACKEND";
    private static final String PROFILE_VERSION_KEY = "URINTH_RENDER_PROFILE_VERSION";

    private static final String[] PROFILE_ENV_KEYS = {
            ULTRA_KEY,
            PROFILE_KEY,
            BACKEND_KEY,
            PROFILE_VERSION_KEY,
            "URINTH_ULTRA_WRAPPER",
            "URINTH_ULTRA_WRAPPER_VERSION",
            "MESA_SHADER_CACHE_MAX_SIZE",
            "MESA_SHADER_CACHE_DIR",
            "mesa_glthread",
            "MESA_GLSL_CACHE_DIR",
            "LIBGL_BATCH"
    };

    // Snapshot the launcher's original process environment before we change it.
    // OFF restores these values (or unsets variables that were originally absent).
    private static final Map<String, String> ORIGINAL_ENV = captureOriginalEnvironment();

    private static PowerManager thermalPowerManager;
    private static PowerManager.OnThermalStatusChangedListener thermalStatusListener;

    /**
     * Monitor thermal status while the game activity is alive. This is diagnostic only:
     * it never changes clocks, resolution, graphics options, or renderer selection.
     */
    public static synchronized void startThermalMonitoring(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || context == null) return;
        stopThermalMonitoring();
        try {
            PowerManager manager = (PowerManager) context.getApplicationContext()
                    .getSystemService(Context.POWER_SERVICE);
            if (manager == null) {
                Log.i(TAG, "Live thermal monitoring unavailable: PowerManager missing");
                return;
            }
            Executor executor = context.getMainExecutor();
            PowerManager.OnThermalStatusChangedListener listener = status -> {
                String state = thermalStatusName(status);
                if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                    Log.w(TAG, "Live thermal status=" + state
                            + "; frame pacing may degrade due to device throttling. No graphics settings were changed.");
                } else {
                    Log.i(TAG, "Live thermal status=" + state);
                }
            };
            manager.addThermalStatusListener(executor, listener);
            thermalPowerManager = manager;
            thermalStatusListener = listener;
            Log.i(TAG, "Live thermal monitoring started");
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to start live thermal monitoring", e);
        }
    }

    public static synchronized void stopThermalMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && thermalPowerManager != null && thermalStatusListener != null) {
            try {
                thermalPowerManager.removeThermalStatusListener(thermalStatusListener);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to stop live thermal monitoring cleanly", e);
            }
        }
        thermalPowerManager = null;
        thermalStatusListener = null;
    }

    static String thermalStatusName(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "none";
            case PowerManager.THERMAL_STATUS_LIGHT: return "light";
            case PowerManager.THERMAL_STATUS_MODERATE: return "moderate";
            case PowerManager.THERMAL_STATUS_SEVERE: return "severe";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "shutdown";
            default: return "unknown(" + status + ")";
        }
    }

    private URinthRender() {}

    private static Map<String, String> captureOriginalEnvironment() {
        Map<String, String> values = new HashMap<>();
        for (String key : PROFILE_ENV_KEYS) {
            values.put(key, System.getenv(key));
        }
        return values;
    }

    /**
     * Avoid Mesa's extra GL worker thread on memory-constrained phones. The thread can
     * improve throughput on some devices, but its queues and extra allocations are not
     * a safe default when Android reports a low-RAM device or <= 4 GiB physical RAM.
     */
    private static boolean isMemoryConstrainedDevice(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            // If Android cannot provide ActivityManager, do not assume the device has
            // enough headroom for an extra Mesa worker thread.
            if (manager == null) {
                Log.w(TAG, "ActivityManager unavailable; treating memory budget as constrained");
                return true;
            }
            if (manager.isLowRamDevice()) return true;
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memoryInfo);
            return memoryInfo.totalMem > 0 && memoryInfo.totalMem <= 4L * 1024L * 1024L * 1024L;
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not determine memory budget; leaving Mesa worker-thread decision conservative", e);
            return true;
        }
    }

    /** Log a reproducible device/backend snapshot without changing player graphics settings. */
    private static void logDeviceSnapshot(Context context, RenderSpec renderer, boolean ultraEnabled) {
        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0] : "unknown";
        long totalRamMb = -1;
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(memoryInfo);
                totalRamMb = memoryInfo.totalMem / (1024L * 1024L);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read device memory information", e);
        }

        String gpuVendor = "unknown";
        String gpuRenderer = "unknown";
        int glesMajor = -1;
        try {
            GpuUtils.GLInfo glInfo = GpuUtils.getGlInfo();
            if (glInfo != null) {
                gpuVendor = glInfo.vendor;
                gpuRenderer = glInfo.renderer;
                glesMajor = glInfo.glesMajorVersion;
            }
        } catch (Throwable error) {
            Log.w(TAG, "GPU capability snapshot failed; leaving graphics details unknown", error);
        }

        String thermalStatus = "unavailable";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (powerManager != null) {
                    int status = powerManager.getCurrentThermalStatus();
                    switch (status) {
                        case PowerManager.THERMAL_STATUS_NONE: thermalStatus = "none"; break;
                        case PowerManager.THERMAL_STATUS_LIGHT: thermalStatus = "light"; break;
                        case PowerManager.THERMAL_STATUS_MODERATE: thermalStatus = "moderate"; break;
                        case PowerManager.THERMAL_STATUS_SEVERE: thermalStatus = "severe"; break;
                        case PowerManager.THERMAL_STATUS_CRITICAL: thermalStatus = "critical"; break;
                        case PowerManager.THERMAL_STATUS_EMERGENCY: thermalStatus = "emergency"; break;
                        case PowerManager.THERMAL_STATUS_SHUTDOWN: thermalStatus = "shutdown"; break;
                        default: thermalStatus = "unknown(" + status + ")";
                    }
                    if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                        Log.w(TAG, "Thermal pressure is " + thermalStatus
                                + "; expect possible clock throttling. URinthRender will not change resolution or user graphics settings.");
                    }
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Thermal status query failed; continuing without thermal diagnostics", e);
            }
        }

        Log.i(TAG, "Device snapshot: manufacturer=" + Build.MANUFACTURER
                + ", model=" + Build.MODEL
                + ", device=" + Build.DEVICE
                + ", Android=" + Build.VERSION.RELEASE
                + ", SDK=" + Build.VERSION.SDK_INT
                + ", ABI=" + abi
                + ", RAM_MB=" + totalRamMb
                + ", thermalStatus=" + thermalStatus
                + ", systemProbe_GPU_vendor=" + gpuVendor
                + ", systemProbe_GPU_renderer=" + gpuRenderer
                + ", systemProbe_GLES_major=" + glesMajor
                + ", ultra=" + ultraEnabled
                + ", selectedRenderer=" + renderer.name()
                + ", rendererTag=" + renderer.tag()
                + ", urinthUltraWrapperIntegrated=" + ultraEnabled
                + ", customNativeDriverReplacement=false"
                + ", resolutionAndOptionsTxtModified=false");
    }

    public static boolean isUltraEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ULTRA, false);
    }

    /**
     * Restore the launcher's original values before each renderer setup.
     * The selected RenderSpec then supplies its normal backend environment.
     */
    public static void restoreNormalEnvironment() {
        for (String key : PROFILE_ENV_KEYS) {
            try {
                String original = ORIGINAL_ENV.get(key);
                if (original == null) {
                    Os.unsetenv(key);
                } else {
                    Os.setenv(key, original, true);
                }
            } catch (ErrnoException e) {
                Log.w(TAG, "Could not restore normal environment variable " + key, e);
            }
        }
        Log.i(TAG, "Restored baseline renderer environment before profile selection");
    }

    /**
     * Apply only the opt-in environment overrides. If OFF, no Ultra overrides
     * are added after the normal renderer has configured its environment.
     */
    public static void applyProfile(Context context, RenderSpec renderer, Map<String, String> env) {
        if (renderer == null) {
            Log.w(TAG, "Renderer profile skipped because no renderer was selected");
            return;
        }

        String tag = renderer.tag();
        boolean ultraEnabled = isUltraEnabled(context);
        logDeviceSnapshot(context, renderer, ultraEnabled);
        if (!ultraEnabled) {
            Log.i(TAG, "URinthUltra Mode OFF; selected backend remains "
                    + renderer.name() + " (" + tag + "); no Ultra overrides added");
            return;
        }

        env.put(ULTRA_KEY, "1");
        env.put(PROFILE_KEY, "ultra");
        env.put(BACKEND_KEY, tag);
        env.put(PROFILE_VERSION_KEY, "3");
        Log.i(TAG, "URinthUltra Mode ON; profile version=3; selected backend="
                + renderer.name() + " (" + tag + ")");

        // Mesa's shader cache and GL worker thread apply only to Mesa-backed
        // implementations (including Zink); leave GL4ES/LTW and other backends alone.
        if (Renderers.ZINK_RENDERER.equals(tag)
                || Renderers.MESA_RENDERER.equals(tag)
                || Renderers.MESA_RENDERER_EXT.equals(tag)
                || Renderers.LEGACYZINK_RENDERER.equals(tag)) {
            env.put("MESA_SHADER_CACHE_MAX_SIZE", "128M");
            env.put("MESA_SHADER_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
            // A worker thread can help throughput but adds queues/allocations. Prefer
            // a lower-memory profile on phones that Android identifies as low-RAM.
            if (isMemoryConstrainedDevice(context)) {
                // Explicit false is important: restoreNormalEnvironment() may have restored
                // an inherited true value, so merely omitting this key would not disable it.
                env.put("mesa_glthread", "false");
                Log.i(TAG, "Memory-constrained device detected; explicitly disabled mesa_glthread to limit worker-thread pressure");
            } else {
                // Preserve the restored baseline value on other devices until repeatable
                // per-device benchmarks demonstrate that forcing a worker thread is beneficial.
                // Do not inject an unvalidated global performance setting.
                Log.i(TAG, "Non-low-RAM device; preserving baseline mesa_glthread value pending benchmark evidence");
            }
            // Retain the legacy cache key used elsewhere in this launcher fork.
            env.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
            Log.i(TAG, "Enabled Mesa shader cache sizing; worker-thread decision is memory-aware");
        } else if (Renderers.GL4ES_RENDERER.equals(tag)) {
            // GL4ES supports conservative draw-call batching. Keep it opt-in with
            // Ultra Mode and restore the original value when Ultra is disabled.
            // The effect is workload/device-dependent and must be benchmarked.
            env.put("LIBGL_BATCH", "1");
            Log.i(TAG, "Enabled experimental GL4ES draw batching (LIBGL_BATCH=1); benchmark FPS and visual correctness on-device");
        } else {
            // The wrapper alone is not a performance optimization.
            Log.i(TAG, "No backend-specific Ultra optimization is implemented for " + tag
                    + "; this profile does not replace the selected renderer");
        }
    }
}
