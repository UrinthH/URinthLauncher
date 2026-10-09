package net.kdt.pojavlaunch.utils;

public class GameOptionsUtils {
    /**
     * Parse an integer. If the input value is null or not a valid integer, return the default value.
     * @param value the String to parse
     * @param defaultValue the default value
     * @return the parsed value or default
     */
    public static int parseIntDefault(String value, int defaultValue) {
        if(value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        }catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Legacy compatibility hook retained for callers from older launcher code.
     * It deliberately does not write options.txt: the player owns render distance,
     * cloud range, narrator, fullscreen and resolution settings.
     *
     * @deprecated Renderer compatibility must not silently mutate player options.
     */
    @Deprecated
    public static void fixOptions(boolean isLtw) {
        // Intentionally no-op. Read-only compatibility checks live in GameRunner.
    }
}
