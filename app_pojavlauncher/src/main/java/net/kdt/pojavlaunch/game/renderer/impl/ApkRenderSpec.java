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

    @Override
    public boolean setupRenderer() {
        String egl = new File(nativeLibraryDir, eglLibraryName).getAbsolutePath();
        boolean ready = MojoExec.prepareEgl(egl, true, false, 0);
        Log.i(TAG, "Plugin renderer " + packageName + " EGL setup result=" + ready + ", library=" + egl);
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
