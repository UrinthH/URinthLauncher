package dev.urinth.boost;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

/**
 * The settings file of the mod, config/urinth_boost.properties: the mode and the frame rate the automatic mode
 * aims for. Plain Java, unit tested. Anything missing or unreadable gives the default, so a damaged file can never
 * stop the game from starting.
 */
public final class BoostConfig {
    private BoostConfig() {}

    public static final String FILE_NAME = "urinth_boost.properties";

    public static final BoostMode DEFAULT_MODE = BoostMode.AUTO;
    /** The frame rate below which the automatic mode starts to help (a 90 Hz screen is the goal, so it starts early). */
    public static final int DEFAULT_TARGET = 75;
    public static final int MIN_TARGET = 15;
    public static final int MAX_TARGET = 240;

    /** The player limit follows the level of Boost (see {@link Limits#maxPlayers}). */
    public static final int PLAYERS_AUTO = -2;
    /** Players are never left out. */
    public static final int PLAYERS_OFF = -1;
    /** Or a number: only the nearest this many other players are drawn. */
    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 200;
    public static final int DEFAULT_PLAYERS = PLAYERS_AUTO;

    public static final class Settings {
        public final BoostMode mode;
        public final int targetFps;
        /** {@link #PLAYERS_AUTO}, {@link #PLAYERS_OFF} or a number from {@link #MIN_PLAYERS} to {@link #MAX_PLAYERS}. */
        public final int players;

        Settings(BoostMode mode, int targetFps, int players) {
            this.mode = mode;
            this.targetFps = targetFps;
            this.players = players;
        }
    }

    public static int clampTarget(int fps) {
        return Math.max(MIN_TARGET, Math.min(MAX_TARGET, fps));
    }

    /** Auto, off, or a number kept inside the limits. */
    public static int clampPlayers(int players) {
        if (players == PLAYERS_AUTO || players == PLAYERS_OFF) return players;
        return Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, players));
    }

    /** The word for a players setting: auto, off or the number. */
    public static String playersWord(int players) {
        int p = clampPlayers(players);
        if (p == PLAYERS_AUTO) return "auto";
        if (p == PLAYERS_OFF) return "off";
        return Integer.toString(p);
    }

    /** Reads "auto", "off" or a number; anything else, or nothing, gives {@code fallback}. */
    public static int parsePlayers(String text, int fallback) {
        if (text == null) return fallback;
        String word = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (word.equals("auto")) return PLAYERS_AUTO;
        if (word.equals("off")) return PLAYERS_OFF;
        try {
            return clampPlayers(Integer.parseInt(word));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String render(BoostMode mode, int targetFps) {
        return render(mode, targetFps, DEFAULT_PLAYERS);
    }

    /** The text of a settings file holding these values, with the explanation. */
    public static String render(BoostMode mode, int targetFps, int players) {
        return "# URinth Boost\n"
                + "# mode: auto (takes things away only while the frame rate is low), off, light, strong or max.\n"
                + "# You can also change it in the game: /uboost auto, /uboost max, /uboost off ...\n"
                + "mode=" + mode.word() + "\n"
                + "# The frame rate auto mode aims for (" + MIN_TARGET + " to " + MAX_TARGET + "). Below it Boost starts to help.\n"
                + "target-fps=" + clampTarget(targetFps) + "\n"
                + "# Other players: auto (only in a crowd, only the nearest ones are drawn, and only when the level is strong or max),\n"
                + "# off (everybody is always drawn) or a number from " + MIN_PLAYERS + " to " + MAX_PLAYERS
                + " (only the nearest that many are drawn, always).\n"
                + "# Players left out are only not drawn; for the game and the server they are still there.\n"
                + "players=" + playersWord(players) + "\n";
    }

    public static final String DEFAULT_TEXT = render(DEFAULT_MODE, DEFAULT_TARGET, DEFAULT_PLAYERS);

    public static Settings parse(String text) {
        BoostMode mode = DEFAULT_MODE;
        int target = DEFAULT_TARGET;
        int players = DEFAULT_PLAYERS;
        if (text != null) {
            Properties properties = new Properties();
            try {
                properties.load(new StringReader(text));
            } catch (IOException | IllegalArgumentException e) {
                // A file that cannot even be read means the defaults.
                return new Settings(mode, target, players);
            }
            mode = BoostMode.parseOr(properties.getProperty("mode"), DEFAULT_MODE);
            String raw = properties.getProperty("target-fps");
            if (raw != null) {
                try {
                    target = clampTarget(Integer.parseInt(raw.trim()));
                } catch (NumberFormatException e) {
                    target = DEFAULT_TARGET; // a bad number only costs this one setting
                }
            }
            players = parsePlayers(properties.getProperty("players"), DEFAULT_PLAYERS);
        }
        return new Settings(mode, target, players);
    }
}
