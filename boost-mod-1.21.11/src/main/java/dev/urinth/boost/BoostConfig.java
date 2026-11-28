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
    /** The frame rate below which the automatic mode starts to help when the screen's rate is not known. */
    public static final int DEFAULT_TARGET = 75;
    public static final int MIN_TARGET = 15;
    public static final int MAX_TARGET = 240;
    /**
     * With a known screen the aim follows it: this share of the refresh rate counts as being there (85 on a 90 Hz
     * screen, 56 on 60 Hz). An aim above what the screen can show would never be reached and would keep Boost busy for nothing.
     */
    public static final double SCREEN_SHARE = 0.94;
    /** Refresh rates outside these are not believed. */
    public static final int MIN_SCREEN_HZ = 30;
    public static final int MAX_SCREEN_HZ = 480;
    /** The name of the Java system property in which the launcher tells the game its screen's refresh rate. */
    public static final String SCREEN_HZ_PROPERTY = "urinth.refreshHz";

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
        /** True when the file was still the untouched text of the first version and the aim was moved to the screen's rate. */
        public final boolean upgraded;

        Settings(BoostMode mode, int targetFps, int players) {
            this(mode, targetFps, players, false);
        }

        Settings(BoostMode mode, int targetFps, int players, boolean upgraded) {
            this.mode = mode;
            this.targetFps = targetFps;
            this.players = players;
            this.upgraded = upgraded;
        }
    }

    public static int clampTarget(int fps) {
        return Math.max(MIN_TARGET, Math.min(MAX_TARGET, fps));
    }

    /** The aim for a screen with this refresh rate; {@link #DEFAULT_TARGET} when the rate is not known (0). */
    public static int targetForScreen(int refreshHz) {
        if (refreshHz < MIN_SCREEN_HZ || refreshHz > MAX_SCREEN_HZ) return DEFAULT_TARGET;
        return clampTarget((int) Math.round(refreshHz * SCREEN_SHARE));
    }

    /** The refresh rate in the text the launcher passes, or 0 when there is none or it is not believable. */
    public static int screenHz(String text) {
        if (text == null) return 0;
        try {
            int hz = Integer.parseInt(text.trim());
            return hz < MIN_SCREEN_HZ || hz > MAX_SCREEN_HZ ? 0 : hz;
        } catch (NumberFormatException e) {
            return 0;
        }
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
                + "# A new file takes about " + Math.round(SCREEN_SHARE * 100) + "% of the screen's refresh rate when the launcher tells it (85 for 90 Hz).\n"
                + "target-fps=" + clampTarget(targetFps) + "\n"
                + "# Other players: auto (only in a crowd, only the nearest ones are drawn, and only when the level is strong or max),\n"
                + "# off (everybody is always drawn) or a number from " + MIN_PLAYERS + " to " + MAX_PLAYERS
                + " (only the nearest that many are drawn, always).\n"
                + "# Players left out are only not drawn; for the game and the server they are still there.\n"
                + "players=" + playersWord(players) + "\n";
    }

    public static final String DEFAULT_TEXT = render(DEFAULT_MODE, DEFAULT_TARGET, DEFAULT_PLAYERS);

    /**
     * The text the first version of the mod wrote when there was no file. A file that is still exactly this one was
     * never touched by the player, so its aim of 75 is only the old default and may follow the screen.
     */
    static final String LEGACY_DEFAULT_TEXT = "# URinth Boost\n"
            + "# mode: auto (takes things away only while the frame rate is low), off, light, strong or max.\n"
            + "# You can also change it in the game: /uboost auto, /uboost max, /uboost off ...\n"
            + "mode=auto\n"
            + "# The frame rate auto mode aims for (15 to 240). Below it Boost starts to help.\n"
            + "target-fps=75\n"
            + "# Other players: auto (only in a crowd, only the nearest ones are drawn, and only when the level is strong or max),\n"
            + "# off (everybody is always drawn) or a number from 4 to 200 (only the nearest that many are drawn, always).\n"
            + "# Players left out are only not drawn; for the game and the server they are still there.\n"
            + "players=auto\n";

    public static Settings parse(String text) {
        return parse(text, 0);
    }

    /**
     * Reads the file. Where it names no aim (or none that can be read), the aim follows the screen's refresh rate
     * ({@link #targetForScreen}); a number the player wrote is always kept. {@code refreshHz} 0 means the rate is not known.
     */
    public static Settings parse(String text, int refreshHz) {
        BoostMode mode = DEFAULT_MODE;
        int screenTarget = targetForScreen(refreshHz);
        int target = screenTarget;
        int players = DEFAULT_PLAYERS;
        boolean upgraded = false;
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
                    target = screenTarget; // a bad number only costs this one setting
                }
            }
            players = parsePlayers(properties.getProperty("players"), DEFAULT_PLAYERS);
            if (refreshHz >= MIN_SCREEN_HZ && LEGACY_DEFAULT_TEXT.equals(text)) {
                target = screenTarget;
                upgraded = true;
            }
        }
        return new Settings(mode, target, players, upgraded);
    }
}
