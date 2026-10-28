package net.kdt.pojavlaunch.game;

import java.io.File;
import java.util.Locale;

/**
 * Finds out whether a voice chat mod is installed in a game folder, going by the names of the
 * jar files in its mods folder. Plain Java with no Android classes, so it is covered by JVM
 * unit tests.
 */
public final class VoiceChatMods {
    private VoiceChatMods() {}

    /** True if a mod file name looks like a proximity voice chat mod. */
    public static boolean isVoiceChatJar(String fileName) {
        if (fileName == null) return false;
        String name = fileName.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jar")) return false;
        return name.contains("voicechat")
                || name.contains("voice-chat")
                || name.contains("voice_chat")
                || name.contains("plasmovoice")
                || name.contains("plasmo-voice");
    }

    /** True if the game folder's mods folder holds a voice chat mod. */
    public static boolean isInstalled(File gameDir) {
        if (gameDir == null) return false;
        File[] mods = new File(gameDir, "mods").listFiles(file -> file.isFile() && isVoiceChatJar(file.getName()));
        return mods != null && mods.length > 0;
    }
}
