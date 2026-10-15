package net.kdt.pojavlaunch.game.renderer.impl;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;

import net.kdt.pojavlaunch.plugins.LibraryPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds renderer plugin apps installed on the device. An app counts as a renderer plugin when it
 * declares fclPlugin or zalithRendererPlugin in its manifest meta-data and its library exists.
 */
public final class ApkRendererScanner {
    private static final String TAG = "ApkRendererScanner";

    /** Known renderer plugin packages, probed directly in case they have no launcher activity. */
    private static final String[] KNOWN_PACKAGES = {
            LibraryPlugin.ID_ANGLE_PLUGIN,
            LibraryPlugin.ID_ANGLE_PLUGIN_FCL,
            LibraryPlugin.ID_ANGLE_PLUGIN_MIO,
            LibraryPlugin.ID_GL4ES_PLUGIN_FCL,
            LibraryPlugin.ID_GL4ES_PLUGIN_MIO,
            LibraryPlugin.ID_LTW_PLUGIN_FCL,
            LibraryPlugin.ID_LTW_PLUGIN_MIO,
            LibraryPlugin.ID_ZINK_PLUGIN,
            LibraryPlugin.ID_MESA_PLUGIN,
            LibraryPlugin.ID_MESA_PLUGIN_FCL,
            LibraryPlugin.ID_MESA_PLUGIN_MIO_2319,
            LibraryPlugin.ID_MESA_PLUGIN_MIO_2427,
            LibraryPlugin.ID_MESA_PLUGIN_MIO_2434,
            LibraryPlugin.ID_MESA_PLUGIN_MIO_2500,
            LibraryPlugin.ID_MESA_PLUGIN_MIO_2500_RC1,
            LibraryPlugin.ID_MOBILEGLUES_PLUGIN,
            LibraryPlugin.ID_MOBILEGLUES_PLUGIN_FCL,
            LibraryPlugin.ID_MOBILEGLUES_PLUGIN_MIO,
            LibraryPlugin.ID_KRYPTON_PLUGIN,
            LibraryPlugin.ID_KRYPTON_ANGLELESS_PLUGIN
    };

    private static volatile List<ApkRenderSpec> sInstalled = Collections.emptyList();

    private ApkRendererScanner() {
    }

    /** Rescan installed apps. Call before listing renderers or starting a game. */
    public static synchronized void scan(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ApkRenderSpec> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        List<ResolveInfo> activities;
        try {
            activities = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN), PackageManager.GET_META_DATA);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not list launchable apps", e);
            activities = Collections.emptyList();
        }
        for (ResolveInfo resolve : activities) {
            ApplicationInfo info = resolve.activityInfo.applicationInfo;
            if (seen.add(info.packageName)) addIfRenderer(pm, info, found);
        }

        for (String packageName : KNOWN_PACKAGES) {
            if (seen.contains(packageName)) continue;
            seen.add(packageName);
            try {
                addIfRenderer(pm, pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA), found);
            } catch (PackageManager.NameNotFoundException ignored) {
                // Not installed.
            }
        }

        sInstalled = Collections.unmodifiableList(found);
    }

    /** Renderer plugins found by the last scan. */
    public static List<ApkRenderSpec> installed() {
        return sInstalled;
    }

    /** The installed plugin renderer with this tag (its package name), or null. */
    public static ApkRenderSpec find(String tag) {
        if (tag == null) return null;
        for (ApkRenderSpec spec : sInstalled) {
            if (spec.tag().equals(tag)) return spec;
        }
        return null;
    }

    private static void addIfRenderer(PackageManager pm, ApplicationInfo info, List<ApkRenderSpec> found) {
        try {
            ApkRenderSpec spec = parse(info);
            if (spec != null) found.add(spec);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not read renderer plugin " + info.packageName, e);
        }
    }

    private static ApkRenderSpec parse(ApplicationInfo info) {
        if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return null;
        Bundle meta = info.metaData;
        if (meta == null) return null;
        if (!meta.getBoolean("fclPlugin", false) && !meta.getBoolean("zalithRendererPlugin", false)) return null;

        String rendererMeta = text(meta, "renderer");
        if (rendererMeta == null || info.nativeLibraryDir == null) return null;
        String[] parts = rendererMeta.split(":");
        if (parts.length < 3) {
            Log.w(TAG, info.packageName + " has a malformed renderer entry: " + rendererMeta);
            return null;
        }

        String nativeDir = info.nativeLibraryDir;
        String eglLibrary = parts[2];
        if (!new File(nativeDir, eglLibrary).isFile()) {
            Log.i(TAG, info.packageName + " is installed but its library " + eglLibrary + " is missing");
            return null;
        }

        String description = text(meta, "des");
        if (description == null) description = info.packageName;

        Map<String, String> env = new LinkedHashMap<>();
        String dlopen = null;
        String pojavEnv = text(meta, "pojavEnv");
        if (pojavEnv != null) {
            for (String entry : pojavEnv.split(":")) {
                int eq = entry.indexOf('=');
                if (eq <= 0) continue;
                String key = entry.substring(0, eq);
                String value = entry.substring(eq + 1);
                switch (key) {
                    case "DLOPEN":
                        dlopen = value;
                        break;
                    case "LIB_MESA_NAME":
                    case "MESA_LIBRARY":
                        env.put(key, nativeDir + "/" + value);
                        break;
                    default:
                        env.put(key, value);
                }
            }
        }

        return new ApkRenderSpec(info.packageName, description, eglLibrary, nativeDir,
                text(meta, "minMCVer"), text(meta, "maxMCVer"), env, dlopen);
    }

    /** Meta-data value as text, or null when absent or blank. Numbers are converted to text. */
    private static String text(Bundle meta, String key) {
        Object value = meta.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
