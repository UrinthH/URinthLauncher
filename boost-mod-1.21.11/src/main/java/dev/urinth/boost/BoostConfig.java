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
    /** The frame rate below which the automatic mode starts to help. */
    public static final int DEFAULT_TARGET = 50;
    public static final int MIN_TARGET = 15;
    public static final int MAX_TARGET = 240;

    public static final class Settings {
        public final BoostMode mode;
        public final int targetFps;

        Settings(BoostMode mode, int targetFps) {
            this.mode = mode;
            this.targetFps = targetFps;
        }
    }

    public static int clampTarget(int fps) {
        return Math.max(MIN_TARGET, Math.min(MAX_TARGET, fps));
    }

    /** The text of a settings file holding these values, with the explanation. */
    public static String render(BoostMode mode, int targetFps) {
        return "# URinth Boost\n"
                + "# mode: auto (takes things away only while the frame rate is low), off, light, strong or max.\n"
                + "# You can also change it in the game: /uboost auto, /uboost max, /uboost off ...\n"
                + "mode=" + mode.word() + "\n"
                + "# The frame rate auto mode aims for (" + MIN_TARGET + " to " + MAX_TARGET + "). Below it Boost starts to help.\n"
                + "target-fps=" + clampTarget(targetFps) + "\n";
    }

    public static final String DEFAULT_TEXT = render(DEFAULT_MODE, DEFAULT_TARGET);

    public static Settings parse(String text) {
        BoostMode mode = DEFAULT_MODE;
        int target = DEFAULT_TARGET;
        if (text != null) {
            Properties properties = new Properties();
            try {
                properties.load(new StringReader(text));
                mode = BoostMode.parseOr(properties.getProperty("mode"), DEFAULT_MODE);
                String raw = properties.getProperty("target-fps");
                if (raw != null) target = clampTarget(Integer.parseInt(raw.trim()));
            } catch (IOException | IllegalArgumentException e) {
                // A broken file means the defaults, whatever part of it could be read.
                if (target < MIN_TARGET || target > MAX_TARGET) target = DEFAULT_TARGET;
            }
        }
        return new Settings(mode, target);
    }
}
