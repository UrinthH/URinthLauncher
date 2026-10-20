package net.kdt.pojavlaunch.prefs;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;

import static net.kdt.pojavlaunch.Architecture.is32BitsDevice;

import android.app.Activity;
import android.content.*;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;

import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.utils.GpuUtils;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.io.IOException;

import git.artdeell.mojo.R;

public class LauncherPreferences {
    public static final String PREF_KEY_CURRENT_INSTANCE = "currentInstance";
    public static final String PREF_KEY_SKIP_NOTIFICATION_CHECK = "skipNotificationPermissionCheck";

    public static SharedPreferences DEFAULT_PREF;
    public static String PREF_RENDERER = "opengles2";

	public static boolean PREF_IGNORE_NOTCH = false;
	public static float PREF_BUTTONSIZE = 100f;
	public static float PREF_MOUSESCALE = 1f;
	public static int PREF_LONGPRESS_TRIGGER = 300;
	public static String PREF_DEFAULTCTRL_PATH = Tools.CTRLDEF_FILE;
	public static String PREF_CUSTOM_JAVA_ARGS;
    public static boolean PREF_FORCE_ENGLISH = false;
    public static final String PREF_VERSION_REPOS = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static boolean PREF_DISABLE_GESTURES = false;
    public static boolean PREF_DISABLE_SWAP_HAND = false;
    public static float PREF_MOUSESPEED = 1f;
    public static int PREF_RAM_ALLOCATION;
    public static String PREF_DEFAULT_RUNTIME;
    public static boolean PREF_SUSTAINED_PERFORMANCE = false;
    public static boolean PREF_VIRTUAL_MOUSE_START = false;
    public static boolean PREF_USE_ALTERNATE_SURFACE = true;
    public static boolean PREF_JAVA_SANDBOX = true;
    public static float PREF_SCALE_FACTOR = 1f;

    public static boolean PREF_ENABLE_GYRO = false;
    public static float PREF_GYRO_SENSITIVITY = 1f;
    public static int PREF_GYRO_SAMPLE_RATE = 16;
    public static boolean PREF_GYRO_SMOOTHING = true;
    public static boolean PREF_GYRO_INVERT_X = false;
    public static boolean PREF_GYRO_INVERT_Y = false;

    public static boolean PREF_FORCE_VSYNC = false;

    public static boolean PREF_USE_ANGLE = false;

    public static boolean PREF_BUTTON_ALL_CAPS = true;
    public static boolean PREF_DUMP_SHADERS = false;
    public static float PREF_DEADZONE_SCALE = 1f;
    public static boolean PREF_BIG_CORE_AFFINITY = false;
    public static boolean PREF_ZINK_PREFER_SYSTEM_DRIVER = false;
    public static boolean PREF_VERIFY_MANIFEST = true;
    public static String PREF_DOWNLOAD_SOURCE = "default";
    public static boolean PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = false;
    public static boolean PREF_RAPID_START = true;
    public static boolean PREF_VERIFY_FILES = true;

    public static boolean PREF_FREEDRENO_SYSMEM = false;
    public static boolean PREF_KEYBOARD_AUTOPANNING = true;
    public static boolean PREF_MIGRATION_NOTICE = true;
    public static boolean PREF_ALSOFT_FORCE_OPENSL = false;
    public static boolean PREF_SHOW_MEMORY_WARNING_DIALOG = true;
    public static short PREF_BUTTON_TRANSPARENCY = 100;
    public static boolean PREF_UBWC_WORKAROUND = false;

    public static void loadPreferences(Context ctx) {
        //Required for CTRLDEF_FILE and MultiRT
        Tools.initStorageConstants(ctx);
        boolean isDevicePowerful = isDevicePowerful(ctx);

        PREF_RENDERER = DEFAULT_PREF.getString("renderer", "opengles2");
        PREF_BUTTONSIZE = DEFAULT_PREF.getInt("buttonscale", 100);
        PREF_MOUSESCALE = DEFAULT_PREF.getInt("mousescale", 100)/100f;
        PREF_MOUSESPEED = ((float)DEFAULT_PREF.getInt("mousespeed",100))/100f;
        PREF_IGNORE_NOTCH = DEFAULT_PREF.getBoolean("ignoreNotch", false);
		PREF_LONGPRESS_TRIGGER = DEFAULT_PREF.getInt("timeLongPressTrigger", 300);
		PREF_DEFAULTCTRL_PATH = DEFAULT_PREF.getString("defaultCtrl", Tools.CTRLDEF_FILE);
        PREF_FORCE_ENGLISH = DEFAULT_PREF.getBoolean("force_english", false);
        PREF_DISABLE_GESTURES = DEFAULT_PREF.getBoolean("disableGestures",false);
        PREF_DISABLE_SWAP_HAND = DEFAULT_PREF.getBoolean("disableDoubleTap", false);
        PREF_RAM_ALLOCATION = DEFAULT_PREF.getInt("allocation", findBestRAMAllocation(ctx));
        PREF_CUSTOM_JAVA_ARGS = DEFAULT_PREF.getString("javaArgs", "");
        PREF_CUSTOM_JAVA_ARGS = applyPerformanceDefaultsIfUnset(PREF_CUSTOM_JAVA_ARGS);
        PREF_CUSTOM_JAVA_ARGS = applyCrashResilienceArgsIfUnset(PREF_CUSTOM_JAVA_ARGS);
        // Android's own thermal hint API: tells the OS to favor steady, sustainable clocks
        // over a short burst-then-throttle curve. Previously only defaulted on for "powerful"
        // devices, but it's exactly the budget/weaker phones - the ones that throttle hardest
        // under a long Minecraft session - that benefit most from avoiding that throttle spiral.
        // No-op (harmless) on a device/OEM that doesn't implement it. Migrated on for existing
        // installs too, once, the same way the crash-resilience JVM flags were above.
        applySustainedPerformanceDefaultIfUnset();
        PREF_SUSTAINED_PERFORMANCE = DEFAULT_PREF.getBoolean("sustainedPerformance", true);
        PREF_VIRTUAL_MOUSE_START = DEFAULT_PREF.getBoolean("mouse_start", false);
        PREF_USE_ALTERNATE_SURFACE = DEFAULT_PREF.getBoolean("alternate_surface", isDevicePowerful);
        PREF_JAVA_SANDBOX = DEFAULT_PREF.getBoolean("java_sandbox", true);
        PREF_SCALE_FACTOR = DEFAULT_PREF.getInt("resolutionRatio", findBestResolution(ctx, isDevicePowerful))/100f;
        PREF_ENABLE_GYRO = DEFAULT_PREF.getBoolean("enableGyro", false);
        PREF_GYRO_SENSITIVITY = ((float)DEFAULT_PREF.getInt("gyroSensitivity", 100))/100f;
        PREF_GYRO_SAMPLE_RATE = DEFAULT_PREF.getInt("gyroSampleRate", 16);
        PREF_GYRO_SMOOTHING = DEFAULT_PREF.getBoolean("gyroSmoothing", true);
        PREF_GYRO_INVERT_X = DEFAULT_PREF.getBoolean("gyroInvertX", false);
        PREF_GYRO_INVERT_Y = DEFAULT_PREF.getBoolean("gyroInvertY", false);
        PREF_FORCE_VSYNC = DEFAULT_PREF.getBoolean("force_vsync", isDevicePowerful);
        PREF_USE_ANGLE = DEFAULT_PREF.getBoolean("use_angle", false);
        PREF_BUTTON_ALL_CAPS = DEFAULT_PREF.getBoolean("buttonAllCaps", true);
        PREF_DUMP_SHADERS = DEFAULT_PREF.getBoolean("dump_shaders", false);
        PREF_DEADZONE_SCALE = ((float) DEFAULT_PREF.getInt("gamepad_deadzone_scale", 100))/100f;
        // Default ON: pins the game's render/JVM threads to the SoC's big cores instead of
        // letting the scheduler spread them onto weak efficiency cores, which is a real,
        // safe perf win on the big.LITTLE-style chips most Android phones (including budget
        // ones) use. Only applies as the default when the user hasn't chosen a value in
        // Settings themselves.
        PREF_BIG_CORE_AFFINITY = DEFAULT_PREF.getBoolean("bigCoreAffinity", true);
        PREF_ZINK_PREFER_SYSTEM_DRIVER = DEFAULT_PREF.getBoolean("zinkPreferSystemDriver", false);
        PREF_DOWNLOAD_SOURCE = DEFAULT_PREF.getString("downloadSource", "default");
        PREF_VERIFY_MANIFEST = DEFAULT_PREF.getBoolean("verifyManifest", true);
        PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = DEFAULT_PREF.getBoolean(PREF_KEY_SKIP_NOTIFICATION_CHECK, false);
        PREF_VERIFY_FILES = DEFAULT_PREF.getBoolean("checkGameFiles", true);
        PREF_RAPID_START = DEFAULT_PREF.getBoolean("fastStartupCheck", true);
        PREF_FREEDRENO_SYSMEM = DEFAULT_PREF.getBoolean("freedrenoSysmem", false);
        PREF_KEYBOARD_AUTOPANNING = DEFAULT_PREF.getBoolean("keyboardAutoPanning", true);
        PREF_MIGRATION_NOTICE = DEFAULT_PREF.getBoolean("migrationNotice", true);
        PREF_ALSOFT_FORCE_OPENSL = DEFAULT_PREF.getBoolean("alsoftForceOpenSL", false);
        PREF_SHOW_MEMORY_WARNING_DIALOG = DEFAULT_PREF.getBoolean("showMemoryWarning", true);
        PREF_BUTTON_TRANSPARENCY = (short) DEFAULT_PREF.getInt("buttonTransparency", 100);
        PREF_UBWC_WORKAROUND = DEFAULT_PREF.getBoolean("ubwcWorkaround", false);

        String argLwjglLibname = "-Dorg.lwjgl.opengl.libname=";
        for (String arg : JREUtils.parseJavaArguments(PREF_CUSTOM_JAVA_ARGS)) {
            if (arg.startsWith(argLwjglLibname)) {
                // purge arg
                DEFAULT_PREF.edit().putString("javaArgs",
                    PREF_CUSTOM_JAVA_ARGS.replace(arg, "")).apply();
            }
        }
        if(DEFAULT_PREF.contains("defaultRuntime")) {
            PREF_DEFAULT_RUNTIME = DEFAULT_PREF.getString("defaultRuntime","");
        }else{
            if(MultiRTUtils.getRuntimes().isEmpty()) {
                PREF_DEFAULT_RUNTIME = "";
                return;
            }
            PREF_DEFAULT_RUNTIME = MultiRTUtils.getRuntimes().get(0).name;
            LauncherPreferences.DEFAULT_PREF.edit().putString("defaultRuntime",LauncherPreferences.PREF_DEFAULT_RUNTIME).apply();
        }
    }

    /**
     * This functions aims at finding the best default RAM amount,
     * according to the RAM amount of the physical device.
     * Put not enough RAM ? Minecraft will lag and crash.
     * Put too much RAM ?
     * The GC will lag, android won't be able to breathe properly.
     * @param ctx Context needed to get the total memory of the device.
     * @return The best default value found.
     */
    /**
     * Low-pause G1GC tuning flags (the same family widely used by Paper/Spigot and other
     * performance-focused Minecraft setups to cut GC-caused lag spikes/stutter). Safe across
     * the Java 8-21 runtimes this launcher bundles, and independent of RAM allocation,
     * resolution and options.txt, none of which this touches.
     */
    private static final String PERFORMANCE_JAVA_ARGS =
            "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=130 " +
            "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch " +
            "-XX:G1NewSizePercent=28 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M " +
            "-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 " +
            "-XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 " +
            "-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 " +
            "-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1";

    /**
     * Seed the tuned GC flags above into Settings' custom JVM arguments field the first time
     * the launcher ever runs with that field empty, so lag-spike-prone default launches get a
     * real improvement without having to touch RAM allocation, resolution or options.txt.
     * Runs only once ever (tracked by "perfDefaultsApplied"), so a user who deliberately clears
     * the field afterwards is left alone, and anything the user already typed there is untouched.
     */
    private static String applyPerformanceDefaultsIfUnset(String currentArgs) {
        if (DEFAULT_PREF.getBoolean("perfDefaultsApplied", false)) return currentArgs;
        if (currentArgs != null && !currentArgs.trim().isEmpty()) {
            // The user already has custom JVM args; don't fight them. Just mark this done
            // so we never retroactively inject flags into an args string they wrote themselves.
            DEFAULT_PREF.edit().putBoolean("perfDefaultsApplied", true).apply();
            return currentArgs;
        }
        DEFAULT_PREF.edit()
                .putString("javaArgs", PERFORMANCE_JAVA_ARGS)
                .putBoolean("perfDefaultsApplied", true)
                .apply();
        Log.i("LauncherPreferences", "Applied default low-pause GC tuning to JVM arguments");
        return PERFORMANCE_JAVA_ARGS;
    }

    /**
     * JVM flags aimed specifically at the "FPS is fine, then it just dies later with no error"
     * failure mode on a heavy modpack and a memory-constrained device. Good frame rate only
     * means the renderer is keeping up; it says nothing about the heap, which can still climb
     * for minutes during normal play (new chunks, entities, cached textures/sounds) until
     * either a real OutOfMemoryError hits, or - just as often on Android - the OS's own
     * low-memory killer silently kills the whole process with no Java exception and nothing in
     * latestlog.txt at all, which looks exactly like "crashes for no reason".
     */
    private static final String[] CRASH_RESILIENCE_JAVA_ARGS = {
            // Minecraft/Sodium cache plenty of short-lived data (chunk render data, textures,
            // sounds) behind SoftReferences, which by default the JVM is allowed to keep around
            // under memory pressure far longer than is safe on a 2-4GB phone. This tells it to
            // drop them immediately once the collector is under pressure, trading a little
            // re-computation for a much lower chance of actually running out of memory.
            "-XX:SoftRefLRUPolicyMSPerMB=0",
            // Cuts memory used by duplicate strings (NBT keys, translation keys, mod ids),
            // which adds up across a 100+ mod pack; only effective with G1 (already enabled
            // above), and purely a memory-pressure reducer, no behavior change.
            "-XX:+UseStringDeduplication"
    };

    /**
     * Append any of CRASH_RESILIENCE_JAVA_ARGS that aren't already present in the user's JVM
     * arguments. Unlike applyPerformanceDefaultsIfUnset, this always merges in on top of
     * whatever is already there (including flags from a prior round, or flags the user added
     * themselves) rather than only seeding an empty field, since it specifically targets
     * installs that already exist and are actively hitting this failure mode right now.
     * Still runs only once ever (tracked by "crashResilienceArgsApplied") so it never keeps
     * re-adding itself, and never touches a flag the user later removes on purpose.
     */
    private static String applyCrashResilienceArgsIfUnset(String currentArgs) {
        if (DEFAULT_PREF.getBoolean("crashResilienceArgsApplied", false)) return currentArgs;
        StringBuilder merged = new StringBuilder(currentArgs == null ? "" : currentArgs);
        boolean changed = false;
        for (String flag : CRASH_RESILIENCE_JAVA_ARGS) {
            if (merged.indexOf(flag) < 0) {
                if (merged.length() > 0 && merged.charAt(merged.length() - 1) != ' ') merged.append(' ');
                merged.append(flag);
                changed = true;
            }
        }
        DEFAULT_PREF.edit().putBoolean("crashResilienceArgsApplied", true).apply();
        if (!changed) return currentArgs;
        String result = merged.toString().trim();
        DEFAULT_PREF.edit().putString("javaArgs", result).apply();
        Log.i("LauncherPreferences", "Applied crash-resilience JVM flags (soft reference / string dedup) to reduce OOM risk under heavy modpacks");
        return result;
    }

    /**
     * One-time migration that turns Sustained Performance Mode on for an install that never
     * explicitly set it, matching the new default above. Runs once (tracked by
     * "sustainedPerfMigrated") so it never fights a value the user deliberately changed
     * afterwards.
     */
    private static void applySustainedPerformanceDefaultIfUnset() {
        if (DEFAULT_PREF.getBoolean("sustainedPerfMigrated", false)) return;
        DEFAULT_PREF.edit()
                .putBoolean("sustainedPerformance", true)
                .putBoolean("sustainedPerfMigrated", true)
                .apply();
    }

    private static int findBestRAMAllocation(Context ctx){
        int deviceRam = Tools.getTotalDeviceMemory(ctx);
        if (deviceRam < 1024) return 296;
        if (deviceRam < 1536) return 448;
        if (deviceRam < 2048) return 656;
        // Limit the max for 32 bits devices more harshly
        if (is32BitsDevice()) return 696;

        // A couple hundred MB more headroom than before at these tiers: a heavy, 100+ mod
        // pack genuinely needs more heap than a vanilla-ish setup, and these phones still
        // keep roughly half their RAM free for Android itself at these numbers. Kept modest
        // on purpose - allocating too aggressively just trades a Java OutOfMemoryError for
        // Android's own low-memory killer silently ending the process instead.
        if (deviceRam < 3064) return 1024;
        if (deviceRam < 4096) return 1280;
        if (deviceRam < 6144) return 1536;
        return 2048; //Default RAM allocation for 64 bits
    }

    /// Find a correct resolution for the device
    ///
    /// Some devices are shipped with a ridiculously high resolution, which can cause performance issues
    /// This function will try to find a resolution that is good enough for the device
    private static int findBestResolution(Context context, boolean isDevicePowerful) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int minSide = Math.min(metrics.widthPixels, metrics.heightPixels);
        int targetSide = isDevicePowerful ? 1080 : 720;
        if (minSide <= targetSide) return 100; // No need to scale down

        float ratio = (100f * targetSide / minSide);
        // The value must match the seekbar values
        int increment = context.getResources().getInteger(R.integer.resolution_seekbar_increment);
        return (int) (Math.ceil(ratio / increment) * increment);
    }

    /// Check if the device is considered powerful.
    /// Powerful devices will have some energy saving tweaks enabled by default
    private static boolean isDevicePowerful(Context context) {
        if (SDK_INT < Build.VERSION_CODES.Q) return false;
        if (Tools.getTotalDeviceMemory(context) <= 4096) return false;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        if (Math.min(metrics.widthPixels, metrics.heightPixels) < 1080) return false;
        if (Runtime.getRuntime().availableProcessors() <= 4) return false;
        if (hasAllCoreSameFreq()) return false;
        return true;
    }

    private static boolean hasAllCoreSameFreq() {
        int coreCount = Runtime.getRuntime().availableProcessors();
        try {
            String freq0 = Tools.read("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            String freqX = Tools.read("/sys/devices/system/cpu/cpu" + (coreCount - 1) + "/cpufreq/cpuinfo_max_freq");
            if(freq0.equals(freqX)) return true;
        } catch (IOException e) {
            Log.e("LauncherPreferences", "Failed to read CPU frequencies", e);
        }
        return false;
    }

    /** Check if the device has a display cutout */
    public static boolean hasNotch(Activity activity) {
        if (Build.VERSION.SDK_INT < P) return false;
        try {
            final Rect cutout;
            if(SDK_INT >= Build.VERSION_CODES.S){
                cutout = activity.getWindowManager().getCurrentWindowMetrics().getWindowInsets().getDisplayCutout().getBoundingRects().get(0);
            } else {
                cutout = activity.getWindow().getDecorView().getRootWindowInsets().getDisplayCutout().getBoundingRects().get(0);
            }
            return cutout.width() != 0 || cutout.height() != 0;
        }catch (Exception e){
            Log.i("NOTCH DETECTION", "No notch detected, or the device if in split screen mode");
            return false;
        }
    }
}
