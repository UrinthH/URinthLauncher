package net.kdt.pojavlaunch.game;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Puts the bundled URinth Boost mod (the /uboost frame rate helper) into a Fabric 1.21.4 instance, once.
 *
 * Unlike the sun flare mod this one is an ordinary mod that shows up in the Mods list, so the player decides
 * what happens to it. The rules:
 * <ul>
 * <li>It is installed the first time an instance is launched, as mods/urinth-boost.jar.</li>
 * <li>A hidden marker file next to it remembers what was installed. If the player deletes the mod, or switches
 * it off in the Mods list, it is never put back.</li>
 * <li>A newer bundled version replaces the installed one, but only while the installed file is still exactly
 * what the launcher put there. A file the player replaced or changed is never touched.</li>
 * <li>If the player has the mod under another file name, nothing is installed (two copies of one mod make the
 * game stop at start), and a copy the launcher installed earlier is removed when the player adds another.</li>
 * </ul>
 * The only files this class creates or deletes are mods/urinth-boost.jar and the marker. Plain Java, so it is
 * covered by JVM unit tests.
 */
public final class BoostModInstaller {
    private BoostModInstaller() {}

    public static final String ASSET_PATH = "mods/urinth-boost.jar";
    public static final String FILE_NAME = "urinth-boost.jar";
    /** Hidden (the Mods list ignores names that start with a dot); holds the SHA-256 of the jar installed last. */
    static final String MARKER_NAME = ".urinth-boost.installed";
    static final String MOD_ID = "urinth_boost";

    private static final String DISABLED_SUFFIX = ".disabled";
    private static final Pattern MOD_ID_IN_JSON = Pattern.compile("\"id\"\\s*:\\s*\"" + MOD_ID + "\"");

    public interface Source {
        InputStream open() throws IOException;
    }

    public enum Result {
        /** First time: the mod was put into the mods folder. */
        INSTALLED,
        /** The installed copy was unchanged and has been replaced by the newer bundled one. */
        UPDATED,
        /** The mods folder already has exactly the bundled mod. */
        ALREADY_PRESENT,
        /** The player's choice was kept: deleted, switched off, changed, or present under another name. */
        LEFT_ALONE,
        /** The player added another copy, so the launcher's own copy was removed to avoid a duplicate. */
        REMOVED_DUPLICATE,
        WRONG_VERSION,
        NOT_BUNDLED,
        FAILED
    }

    /** True for Fabric 1.21.4 version ids such as "fabric-loader-0.16.10-1.21.4". */
    public static boolean appliesTo(String versionId) {
        if (versionId == null) return false;
        String id = versionId.toLowerCase(Locale.ROOT);
        return id.contains("fabric") && id.contains("1.21.4");
    }

    public static Result sync(File gameDir, String versionId, Source bundled) {
        if (gameDir == null) return Result.FAILED;
        if (!appliesTo(versionId)) return Result.WRONG_VERSION;

        byte[] jar;
        try (InputStream in = bundled.open()) {
            jar = readAll(in);
        } catch (IOException e) {
            return Result.NOT_BUNDLED;
        }
        if (jar.length == 0) return Result.NOT_BUNDLED;

        File modsDir = new File(gameDir, "mods");
        File target = new File(modsDir, FILE_NAME);
        File disabled = new File(modsDir, FILE_NAME + DISABLED_SUFFIX);
        File marker = new File(modsDir, MARKER_NAME);

        try {
            String bundledHash = sha256(jar);
            String installedHash = readMarker(marker);

            if (target.isFile()) {
                String targetHash = sha256(Files.readAllBytes(target.toPath()));
                boolean launchersCopy = targetHash.equals(installedHash);
                if (launchersCopy && hasOtherCopy(modsDir, false, false)) {
                    return target.delete() ? Result.REMOVED_DUPLICATE : Result.FAILED;
                }
                if (targetHash.equals(bundledHash)) {
                    if (!bundledHash.equals(installedHash)) writeMarker(marker, bundledHash);
                    return Result.ALREADY_PRESENT;
                }
                if (launchersCopy) {
                    write(modsDir, target, jar);
                    writeMarker(marker, bundledHash);
                    return Result.UPDATED;
                }
                return Result.LEFT_ALONE;
            }

            if (installedHash != null) return Result.LEFT_ALONE;
            if (disabled.isFile()) return Result.LEFT_ALONE;
            if (hasOtherCopy(modsDir, true, true)) return Result.LEFT_ALONE;

            write(modsDir, target, jar);
            writeMarker(marker, bundledHash);
            return Result.INSTALLED;
        } catch (IOException | RuntimeException e) {
            return Result.FAILED;
        }
    }

    /**
     * Whether the mods folder holds the mod under a name other than mods/urinth-boost.jar. A file counts when its
     * name looks like it ("urinth-boost-1.0.0.jar"), and, when {@code readJars} is set, when a jar with another
     * name has this mod's id in its fabric.mod.json.
     */
    static boolean hasOtherCopy(File modsDir, boolean includeSwitchedOff, boolean readJars) {
        File[] files = modsDir.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();
            boolean off = name.toLowerCase(Locale.ROOT).endsWith(DISABLED_SUFFIX);
            if (off && !includeSwitchedOff) continue;
            String active = off ? name.substring(0, name.length() - DISABLED_SUFFIX.length()) : name;
            if (active.equalsIgnoreCase(FILE_NAME)) continue;
            String lower = active.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".jar")) continue;
            if (lower.contains("urinth-boost") || lower.contains("urinth_boost")) return true;
            if (readJars && holdsModId(file)) return true;
        }
        return false;
    }

    private static boolean holdsModId(File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null || entry.getSize() > 1024 * 1024) return false;
            try (InputStream in = zip.getInputStream(entry)) {
                String json = new String(readAll(in), StandardCharsets.UTF_8);
                return MOD_ID_IN_JSON.matcher(json).find();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static void write(File modsDir, File target, byte[] jar) throws IOException {
        if (!modsDir.isDirectory() && !modsDir.mkdirs()) throw new IOException("Cannot create " + modsDir);
        File tmp = new File(modsDir, FILE_NAME + ".tmp");
        Files.write(tmp.toPath(), jar);
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String readMarker(File marker) throws IOException {
        if (!marker.isFile()) return null;
        String text = new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim();
        return text.isEmpty() ? null : text;
    }

    private static void writeMarker(File marker, String hash) throws IOException {
        Files.write(marker.toPath(), hash.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
