package dev.urinth.find;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

/**
 * The one setting of the mod, read on a server: who may use /find. Plain Java, unit tested.
 *
 * Playing alone, or in a world you host yourself, is never limited by it.
 */
public final class FindConfig {
    private FindConfig() {}

    public static final String FILE_NAME = "urinth_find.properties";

    /** Operators (the level that may use /gamemode and /tp). */
    public static final int DEFAULT_LEVEL = 2;

    public static final String DEFAULT_TEXT =
            "# URinth Find\n"
            + "# Who may use /find to see where other players are, on a server that has this mod.\n"
            + "# 0 = everyone, 1-3 = operators of that level or higher, 4 = full operators only.\n"
            + "permission-level=" + DEFAULT_LEVEL + "\n";

    /** The permission level from the text of the settings file; anything missing or unreadable gives the default. */
    public static int permissionLevel(String text) {
        if (text == null) return DEFAULT_LEVEL;
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            return DEFAULT_LEVEL;
        }
        String raw = properties.getProperty("permission-level");
        if (raw == null) return DEFAULT_LEVEL;
        try {
            int level = Integer.parseInt(raw.trim());
            return level < 0 ? 0 : Math.min(level, 4);
        } catch (NumberFormatException e) {
            return DEFAULT_LEVEL;
        }
    }
}
