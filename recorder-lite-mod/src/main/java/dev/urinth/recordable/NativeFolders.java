package dev.urinth.recordable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Where the mod may unpack its small native helper when LWJGL's own call path cannot be used. Shared by the recorder and the sound. */
public final class NativeFolders {
    private NativeFolders() {
    }

    /** Places where the native helper may be unpacked; some phones forbid running code from some folders, so there are several. */
    public static Path[] list(Path gameDir) {
        List<Path> folders = new ArrayList<>();
        addFolder(folders, System.getProperty("java.io.tmpdir"), "recordable-lite");
        addFolder(folders, System.getProperty("user.home"), ".recordable-lite");
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isEmpty()) {
            folders.add(Path.of(javaHome, "lib", "recordable-lite"));
        }
        String pkg = androidPackage();
        if (pkg != null) {
            folders.add(Path.of("/data/data", pkg, "cache", "recordable-lite"));
            folders.add(Path.of("/data/data", pkg, "files", "recordable-lite"));
        }
        if (gameDir != null) {
            folders.add(Settings.configDir(gameDir).resolve("native"));
        }
        return folders.toArray(new Path[0]);
    }

    private static void addFolder(List<Path> list, String base, String name) {
        if (base != null && !base.isEmpty()) {
            list.add(Path.of(base).resolve(name));
        }
    }

    /** The package name of the Android app this Java runs in (the name of the process), or null if that is not a phone. */
    public static String androidPackage() {
        try {
            byte[] raw = Files.readAllBytes(Path.of("/proc/self/cmdline"));
            int end = 0;
            while (end < raw.length && raw[end] != 0) {
                end++;
            }
            String name = new String(raw, 0, end, StandardCharsets.UTF_8).trim();
            return name.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+") ? name : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
