package net.kdt.pojavlaunch.game.renderer.impl;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.game.renderer.RenderSpec;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import git.artdeell.mojoexec.MojoExec;

/**
 * A renderer supplied by an installed plugin app. The plugin declares its renderer in its
 * manifest meta-data (fclPlugin or zalithRendererPlugin, renderer, des, pojavEnv, minMCVer,
 * maxMCVer), the same format FCL and Zalith renderer plugins use.
 */
public class ApkRenderSpec implements RenderSpec {
    private static final String TAG = "ApkRenderSpec";
    /** A version part that is plain digits, optionally followed by a suffix such as "-pre". */
    private static final Pattern PLAIN_VERSION_PART = Pattern.compile("^\\d+([-+_].*)?$");
    /** A plain positive integer, used to read a GLES major version out of plugin metadata. */
    private static final Pattern PLAIN_INT = Pattern.compile("^(\\d+).*$");

    private final String packageName;
    private final String description;
    private final String eglLibraryName;
    private final String nativeLibraryDir;
    private final String minMcVersion;
    private final String maxMcVersion;
    private final Map<String, String> pojavEnv;
    private final String dlopenList;
    /**
     * Whether this plugin wraps desktop GL on top of the device's own OpenGL ES driver
     * (the same family as the launcher's built-in LTW/GL4ES), as opposed to shipping a
     * complete, self-contained EGL/GL implementation (the same family as MobileGlues).
     * A plugin like this still needs the native loader to actually request an ES-capable
     * EGL context; without that, EGL has nothing to offer it and window creation fails
     * with "EGL: Failed to find support for OpenGL" even though the library loaded fine.
     */
    private final boolean isGlesWrapper;
    private final int glesVersion;

    ApkRenderSpec(String packageName, String description, String eglLibraryName, String nativeLibraryDir,
                  String minMcVersion, String maxMcVersion, Map<String, String> pojavEnv, String dlopenList) {
        this.packageName = packageName;
        this.description = description;
        this.eglLibraryName = eglLibraryName;
        this.nativeLibraryDir = nativeLibraryDir;
        this.minMcVersion = minMcVersion;
        this.maxMcVersion = maxMcVersion;
        this.pojavEnv = Collections.unmodifiableMap(new LinkedHashMap<>(pojavEnv));
        this.dlopenList = dlopenList;

        // The plugin's own declared environment is the only reliable signal available here:
        // a GLES-wrapper plugin (LTW-family) advertises the ES version it needs, the same way
        // the launcher's bundled LTW does, via LIBGL_ES or POJAV_RENDERER=openglesN.
        int detectedVersion = 0;
        String libglEs = this.pojavEnv.get("LIBGL_ES");
        if (libglEs != null) detectedVersion = parseLeadingInt(libglEs);
        if (detectedVersion <= 0) {
            String pojavRenderer = this.pojavEnv.get("POJAV_RENDERER");
            if (pojavRenderer != null && pojavRenderer.startsWith("opengles")) {
                detectedVersion = parseLeadingInt(pojavRenderer.substring("opengles".length()));
            }
        }
        this.isGlesWrapper = detectedVersion > 0;
        this.glesVersion = detectedVersion > 0 ? detectedVersion : 0;
    }

    /** Map.putIfAbsent needs API 24; minSdk here is 21, so do it by hand. */
    private static void putIfAbsent(Map<String, String> map, String key, String value) {
        if (!map.containsKey(key)) map.put(key, value);
    }

    private static int parseLeadingInt(String value) {
        if (value == null) return 0;
        java.util.regex.Matcher matcher = PLAIN_INT.matcher(value.trim());
        if (!matcher.matches()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Text shown for this renderer in Settings. */
    public String label() {
        return description;
    }

    /** Whether this renderer declares support for the given Minecraft version. Unknown formats are allowed. */
    public boolean supportsMinecraft(String mcVersion) {
        Integer low = compareMcVersions(mcVersion, minMcVersion);
        if (low != null && low < 0) return false;
        Integer high = compareMcVersions(mcVersion, maxMcVersion);
        return high == null || high <= 0;
    }

    @Override
    public boolean compatibleDevice(Context context) {
        if (!new File(nativeLibraryDir, eglLibraryName).isFile()) return false;
        // A GLES-wrapper plugin can only create a context at the ES version it wraps;
        // below that, EGL has no matching config for it to use, just like the built-in LTW.
        return !isGlesWrapper || JREUtils.getDetectedVersion() >= glesVersion;
    }

    @Override
    public String name() {
        return description;
    }

    @Override
    public int displayName() {
        // Settings uses label() for plugin renderers, so there is no string resource.
        return 0;
    }

    @Override
    public String tag() {
        return packageName;
    }

    @Override
    public String library() {
        return eglLibraryName;
    }

    @Override
    public String librarySearchPath() {
        return nativeLibraryDir;
    }

    @Override
    public String[] pluginPackages() {
        return new String[] { packageName };
    }

    @Override
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        envMap.putAll(pojavEnv);
        if (isGlesWrapper) {
            // Same compatibility flags the launcher's own bundled LTW/GL4ES wrappers set,
            // needed by libgl4es-family wrappers for strict shader/extension handling.
            // Only fill them in where the plugin did not already declare its own value.
            putIfAbsent(envMap, "force_glsl_extensions_warn", "true");
            putIfAbsent(envMap, "allow_higher_compat_version", "true");
            putIfAbsent(envMap, "allow_glsl_extension_directive_midshader", "true");
            putIfAbsent(envMap, "LIBGL_NOERROR", "1");
        }
        if (dlopenList != null) {
            Log.w(TAG, packageName + " asks to preload " + dlopenList
                    + " (DLOPEN), which this launcher does not support yet");
        }
    }

    @Override
    public boolean setupRenderer() {
        String egl = new File(nativeLibraryDir, eglLibraryName).getAbsolutePath();
        // A GLES-wrapper plugin (LTW-family) needs the native loader to actually request an
        // ES-capable EGL context at its declared version; a self-contained driver plugin
        // (MobileGlues-family) manages its own context and must be left at isGles=false/0,
        // same as MobileGluesRenderSpec, so this only changes behavior for wrapper plugins.
        boolean ready = isGlesWrapper
                ? MojoExec.prepareEgl(egl, true, true, glesVersion)
                : MojoExec.prepareEgl(egl, true, false, 0);
        Log.i(TAG, "Plugin renderer " + packageName + " EGL setup result=" + ready
                + ", library=" + egl + ", glesWrapper=" + isGlesWrapper
                + ", glesVersion=" + glesVersion);
        return ready;
    }

    /**
     * Compare two Minecraft version strings numerically, for example 1.8.9 and 1.17.
     * @return negative, zero or positive as a compares to b, or null when either is not a plain
     *         numeric version (such as a snapshot id like 25w10a).
     */
    static Integer compareMcVersions(String a, String b) {
        int[] left = parsePlainVersion(a);
        int[] right = parsePlainVersion(b);
        if (left == null || right == null) return null;
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int x = i < left.length ? left[i] : 0;
            int y = i < right.length ? right[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int[] parsePlainVersion(String version) {
        if (version == null || version.trim().isEmpty()) return null;
        String[] parts = version.trim().split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (!PLAIN_VERSION_PART.matcher(parts[i]).matches()) return null;
            try {
                numbers[i] = Integer.parseInt(parts[i].replaceFirst("[^0-9].*$", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return numbers;
    }
}
