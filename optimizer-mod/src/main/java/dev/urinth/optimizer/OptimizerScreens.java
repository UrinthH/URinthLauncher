package dev.urinth.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Which screens get the Optimizer button, and where, and how the settings screen lays out its buttons. Pure logic (no game
 * classes), so it is unit tested. Sodium replaces the vanilla Video Settings screen with its own, so both are handled.
 */
final class OptimizerScreens {
    enum Kind { NONE, VANILLA, SODIUM }

    static final String VANILLA_CLASS = "net.minecraft.client.gui.screens.options.VideoSettingsScreen";

    private OptimizerScreens() {}

    /** Classifies a screen by its class name, so there is no compile-time dependency on Sodium. */
    static Kind classify(String className) {
        if (className == null) return Kind.NONE;
        if (className.equals(VANILLA_CLASS)) return Kind.VANILLA;
        String n = className.toLowerCase(Locale.ROOT);
        if (n.endsWith(".sodiumoptionsgui")) return Kind.SODIUM;
        if (!n.contains("sodium")) return Kind.NONE;
        for (String bad : new String[] {"corrupt", "prompt", "widget", "button", "page", "control", "tooltip"}) {
            if (n.contains(bad)) return Kind.NONE;
        }
        return n.contains("option") || n.contains("video") || n.contains("setting") ? Kind.SODIUM : Kind.NONE;
    }

    /**
     * {x, y, w, h} of the button, or null when the screen has no room for it.
     *
     * <p>The Sun Flare mod uses the top corners of the vanilla screen and the first five rows of Sodium's right-hand column, so
     * this one takes the bottom-left corner of the vanilla screen (left of the centred Done button) and the sixth row of Sodium's.
     */
    static int[] bounds(Kind kind, int width, int height) {
        if (kind == Kind.SODIUM) {
            int w = width >= 380 ? 112 : 86;
            int y = 30 + 22 * 5;
            if (y + 20 > height - 6) return null;
            return new int[] {width - w - 6, y, w, 20};
        }
        if (kind == Kind.VANILLA) {
            // the Done button is 200 wide and centred: the corner is free up to (width - 200) / 2
            int free = (width - 200) / 2 - 8;
            int w = Math.min(118, free);
            if (w < 54) return null;
            return new int[] {4, height - 26, w, 20};
        }
        return null;
    }

    /** The text of the button for a width: the short form when it is narrow. */
    static String buttonLabel(int width) {
        return width >= 100 ? "URinth Optimizer" : "Optimizer";
    }

    /**
     * Splits {@code text} at its spaces into lines that are not wider than {@code maxWidth} (as {@code width} measures them). A single
     * word wider than that stays whole on a line of its own.
     */
    static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            if (line.length() == 0) {
                line.append(word);
            } else if (width.applyAsInt(line + " " + word) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.append(' ').append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /**
     * The cells of the settings screen: {x, y, w, h} for each of {@code count} buttons, in reading order, in as many columns as
     * the width allows (1 to 3). The rows start under the title and the status lines and leave the bottom for the Done button.
     */
    static int[][] grid(int width, int height, int count) {
        int gap = 6;
        int cols = width >= 3 * 140 + 2 * gap + 16 ? 3 : width >= 2 * 120 + gap + 16 ? 2 : 1;
        int cellW = Math.max(60, Math.min(150, (width - 16 - (cols - 1) * gap) / cols));
        int rows = Math.max(1, (count + cols - 1) / cols);
        int top = 56;
        int bottom = height - 34;
        int pitch = Math.max(22, Math.min(26, (bottom - top) / rows));
        int totalW = cols * cellW + (cols - 1) * gap;
        int x0 = (width - totalW) / 2;
        int[][] cells = new int[count][];
        for (int i = 0; i < count; i++) {
            int col = i % cols;
            int row = i / cols;
            cells[i] = new int[] {x0 + col * (cellW + gap), top + row * pitch, cellW, 20};
        }
        return cells;
    }
}
