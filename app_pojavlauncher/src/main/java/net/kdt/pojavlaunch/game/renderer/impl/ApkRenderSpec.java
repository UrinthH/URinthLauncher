package net.kdt.pojavlaunch.game.renderer.impl;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.game.renderer.RenderSpec;

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

    private final String packageName;
    private final String description;
    private final String eglLibraryName;
    private final String nativeLibraryDir;
    private final String minMcVersion;
    private final String maxMcVersion;
    private final Map<String, String> pojavEnv;
    private final String dlopenList;

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
        return new File(nativeLibraryDir, eglLibraryName).isFile();
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
        if (dlopenList != null) {
            Log.w(TAG, packageName + " asks to preload " + dlopenList
                    + " (DLOPEN), which this launcher does not support yet");
        }
    }

    /**
     * Renderer plugins often expose OpenGL ES rather than desktop OpenGL. Honour the
     * plugin's LIBGL_ES metadata, with POJAV_RENDERER as a compatibility fallback.
     * Passing false/0 here makes GLFW request a desktop OpenGL context, which causes
     * LTW plugins (including FCL's OpenLTW) to fail with EGL_BAD_MATCH/no OpenGL support.
     */
    int requestedGlesVersion() {
        String configured = pojavEnv.get("LIBGL_ES");
        if (configured != null) {
            int version = parseGlesVersion(configured);
            if (version > 0) return version;
        }

        String renderer = pojavEnv.get("POJAV_RENDERER");
        if (renderer != null) {
            String normalized = renderer.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("opengles3")) return 3;
            if (normalized.contains("opengles2")) return 2;
        }
        return 0;
    }

    private static int parseGlesVersion(String value) {
        if (value == null) return 0;
        String normalized = value.trim();
        if (normalized.isEmpty()) return 0;
        try {
            int version = Integer.parseInt(normalized);
            return version >= 2 && version <= 3 ? version : 0;
        } catch (NumberFormatException ignored) {
            // Accept common values such as "3.0" without guessing unsupported versions.
            if (normalized.startsWith("3.")) return 3;
            if (normalized.startsWith("2.")) return 2;
            return 0;
        }
    }

    @Override
    public boolean setupRenderer() {
        String egl = new File(nativeLibraryDir, eglLibraryName).getAbsolutePath();
        int glesVersion = requestedGlesVersion();
        boolean forceGles = glesVersion > 0;
        boolean ready = MojoExec.prepareEgl(egl, true, forceGles, glesVersion);
        Log.i(TAG, "Plugin renderer " + packageName + " EGL setup result=" + ready
                + ", library=" + egl + ", forceGles=" + forceGles
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
