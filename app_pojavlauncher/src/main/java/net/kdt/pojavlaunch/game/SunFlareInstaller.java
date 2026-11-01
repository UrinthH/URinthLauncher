package net.kdt.pojavlaunch.game;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;

/**
 * Puts the bundled sun lens flare mod into an instance's mods folder (or takes it out again) before the game starts.
 *
 * The mod is a Fabric mod built for Minecraft 1.21.4, so it is only placed in Fabric 1.21.4 instances. The only file this
 * class ever creates or deletes is mods/urinth-sunflare.jar. Plain Java, so it is covered by JVM unit tests.
 */
public final class SunFlareInstaller {
    private SunFlareInstaller() {}

    public static final String ASSET_PATH = "mods/urinth-sunflare.jar";
    public static final String FILE_NAME = "urinth-sunflare.jar";

    public interface Source {
        InputStream open() throws IOException;
    }

    public enum Result { INSTALLED, ALREADY_PRESENT, REMOVED, NOT_NEEDED, WRONG_VERSION, NOT_BUNDLED, FAILED }

    /** True for Fabric 1.21.4 version ids such as "fabric-loader-0.16.10-1.21.4". */
    public static boolean appliesTo(String versionId) {
        if (versionId == null) return false;
        String id = versionId.toLowerCase(Locale.ROOT);
        return id.contains("fabric") && id.contains("1.21.4");
    }

    public static Result sync(File gameDir, String versionId, boolean enabled, Source bundled) {
        if (gameDir == null) return Result.FAILED;
        File modsDir = new File(gameDir, "mods");
        File target = new File(modsDir, FILE_NAME);
        boolean applies = appliesTo(versionId);

        if (!enabled || !applies) {
            if (target.isFile()) return target.delete() ? Result.REMOVED : Result.FAILED;
            return enabled ? Result.WRONG_VERSION : Result.NOT_NEEDED;
        }

        byte[] jar;
        try (InputStream in = bundled.open()) {
            jar = readAll(in);
        } catch (IOException e) {
            return Result.NOT_BUNDLED;
        }
        if (jar.length == 0) return Result.NOT_BUNDLED;

        try {
            if (target.isFile() && Arrays.equals(Files.readAllBytes(target.toPath()), jar)) return Result.ALREADY_PRESENT;
            if (!modsDir.isDirectory() && !modsDir.mkdirs()) return Result.FAILED;
            File tmp = new File(modsDir, FILE_NAME + ".tmp");
            Files.write(tmp.toPath(), jar);
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return Result.INSTALLED;
        } catch (IOException e) {
            return Result.FAILED;
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
