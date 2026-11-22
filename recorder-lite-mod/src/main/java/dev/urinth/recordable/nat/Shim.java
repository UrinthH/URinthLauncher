package dev.urinth.recordable.nat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * The three native entry points of libreclite (see native/reclite.c). Everything the mod does with the phone's media codec goes through
 * {@link #call}. {@link #load} unpacks the matching library from the jar and loads it.
 */
public final class Shim {
    private static boolean tried;
    private static boolean ok;
    private static String status = "not loaded";

    private Shim() {
    }

    /** Calls the C function at address {@code fn} with up to six integer or pointer arguments and returns its raw result register. */
    public static native long call(long fn, long a0, long a1, long a2, long a3, long a4, long a5);

    public static native long dlopenAddr();

    public static native long dlsymAddr();

    public static synchronized boolean isLoaded() {
        return ok;
    }

    /** Human readable result of the last {@link #load}, for the log. */
    public static synchronized String status() {
        return status;
    }

    /** The folder name inside the jar for this machine, or null if the mod has no library for it. */
    public static String platformDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!os.contains("linux") && !os.contains("android")) {
            return null;
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "linux-aarch64";
        }
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "linux-x86_64";
        }
        return null;
    }

    /**
     * Unpacks the library into the first folder that allows loading it and loads it. Folders are tried in order because some Android
     * storage locations do not allow running code from them.
     */
    public static synchronized boolean load(Path... folders) {
        if (ok) {
            return true;
        }
        if (tried && !status.startsWith("failed")) {
            return false;
        }
        tried = true;
        if (!Mem.available()) {
            status = "unavailable: no raw memory access on this Java";
            return false;
        }
        String dir = platformDir();
        if (dir == null) {
            status = "unavailable: no native library for " + System.getProperty("os.name") + "/" + System.getProperty("os.arch");
            return false;
        }
        byte[] bytes;
        try (InputStream in = Shim.class.getResourceAsStream("/natives/" + dir + "/libreclite.so")) {
            if (in == null) {
                status = "unavailable: library missing from the jar";
                return false;
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            status = "failed: could not read the library: " + e;
            return false;
        }
        CRC32 crc = new CRC32();
        crc.update(bytes);
        String name = "libreclite-" + Long.toHexString(crc.getValue()) + ".so";
        StringBuilder why = new StringBuilder();
        for (Path folder : folders) {
            if (folder == null) {
                continue;
            }
            try {
                Files.createDirectories(folder);
                Path target = folder.resolve(name);
                if (!Files.exists(target) || Files.size(target) != bytes.length) {
                    Path tmp = Files.createTempFile(folder, "reclite", ".tmp");
                    Files.write(tmp, bytes);
                    Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                System.load(target.toAbsolutePath().toString());
                ok = true;
                status = "loaded from " + target;
                return true;
            } catch (Throwable t) {
                why.append(folder).append(": ").append(t.getClass().getSimpleName()).append(' ').append(t.getMessage()).append("; ");
            }
        }
        status = "failed: " + why;
        return false;
    }
}
