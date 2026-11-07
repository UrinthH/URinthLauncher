package dev.urinth.find;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Text helpers for the /find output. Plain Java with no Minecraft classes in it, so it is unit tested on a
 * normal JVM.
 */
public final class FindFormat {
    private FindFormat() {}

    /**
     * Two decimals, and none at all when they would be ".00": 128.3 becomes "128.30", 63.0 becomes "63"
     * and -1073.0 becomes "-1073". Never "-0".
     */
    public static String coordinate(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "?";
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
        if (rounded.signum() == 0) return "0";
        String text = rounded.toPlainString();
        return text.endsWith(".00") ? text.substring(0, text.length() - 3) : text;
    }

    /** "x128.30 y63 z-1073" */
    public static String position(double x, double y, double z) {
        return "x" + coordinate(x) + " y" + coordinate(y) + " z" + coordinate(z);
    }

    /** "XYZ: x128.30 y63 z-1073" */
    public static String xyz(double x, double y, double z) {
        return "XYZ: " + position(x, y, z);
    }

    /** The three dimensions every world has; anything else (from a mod or a data pack) gets a name made from its id. */
    public static final String OVERWORLD = "Overworld";
    public static final String NETHER = "Nether";
    public static final String END = "End";

    /**
     * A short readable name for a dimension id: "minecraft:overworld" is "Overworld", "minecraft:the_nether" is
     * "Nether", "minecraft:the_end" is "End", and "mymod:deep_dark_plane" is "Deep Dark Plane".
     */
    public static String dimensionName(String dimensionId) {
        if (dimensionId == null) return "Unknown";
        String id = dimensionId.trim().toLowerCase(Locale.ROOT);
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        if (id.isEmpty()) return "Unknown";
        switch (id) {
            case "overworld": return OVERWORLD;
            case "the_nether": return NETHER;
            case "the_end": return END;
            default: break;
        }
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_\\-\\s]+")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.length() == 0 ? "Unknown" : out.toString();
    }

    /** True for the three dimensions a normal world has. */
    public static boolean isOverworld(String dimensionId) {
        return OVERWORLD.equals(dimensionName(dimensionId)) && isVanillaId(dimensionId);
    }

    public static boolean isNether(String dimensionId) {
        return NETHER.equals(dimensionName(dimensionId)) && isVanillaId(dimensionId);
    }

    public static boolean isEnd(String dimensionId) {
        return END.equals(dimensionName(dimensionId)) && isVanillaId(dimensionId);
    }

    private static boolean isVanillaId(String dimensionId) {
        if (dimensionId == null) return false;
        String id = dimensionId.trim().toLowerCase(Locale.ROOT);
        return id.indexOf(':') < 0 || id.startsWith("minecraft:");
    }

    /** At most {@code max} characters, so a string can never be too long for the network packet it goes in. */
    public static String cut(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }
}
