package net.kdt.pojavlaunch.game.renderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The folder the launcher gives MobileGlues for its {@code config.json}, its log and its shader cache, and
 * the way the config is written into it. Plain {@code java.io}, so it can be tested without a device.
 *
 * <p>The folder sits inside the launcher's own storage. The game runs in this app's process, so it can use
 * that place without any permission, unlike MobileGlues' own default {@code /sdcard/MG}, which needs "All
 * files access" on Android 11 and newer.
 */
public final class MobileGluesFolder {
    static final String FOLDER_NAME = "mobileglues";
    static final String CONFIG_NAME = "config.json";

    private MobileGluesFolder() {}

    /**
     * The first candidate that is, or can be made into, a folder this process can write to; null when none
     * is. MobileGlues makes its folder with a plain mkdir, which does not create missing parents, so the
     * folder has to exist before the game starts.
     */
    public static File pick(File... candidates) {
        for (File dir : candidates) {
            if (dir == null) continue;
            try {
                if ((dir.isDirectory() || dir.mkdirs()) && dir.canWrite()) return dir;
            } catch (SecurityException ignored) {
                // Try the next place.
            }
        }
        return null;
    }

    /**
     * Writes {@code config.json} into {@code dir}: to a sibling file first and then renamed over the old one,
     * so a game that starts at that moment reads either the old file or the new one, never half of one.
     */
    public static File writeConfig(File dir, String json) throws IOException {
        File target = new File(dir, CONFIG_NAME);
        File temp = new File(dir, CONFIG_NAME + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        if (!temp.renameTo(target)) {
            // A file system that will not rename over an existing file: replace it the plain way.
            if (target.exists() && !target.delete()) {
                temp.delete();
                throw new IOException("could not replace " + target);
            }
            if (!temp.renameTo(target)) {
                temp.delete();
                throw new IOException("could not write " + target);
            }
        }
        return target;
    }
}
