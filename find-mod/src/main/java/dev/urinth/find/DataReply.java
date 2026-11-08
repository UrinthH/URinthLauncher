package dev.urinth.find;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what a server prints back for the vanilla commands
 * <pre>
 * /data get entity &lt;player&gt; Pos
 * /data get entity &lt;player&gt; Dimension
 * </pre>
 * That is how /find reaches a player who is far away on a server that does not have this mod: an operator may
 * ask any vanilla server where a player is, however far, and the server answers with a line such as
 * "Notkiahh has the following entity data: [128.3d, 63.0d, -1073.0d]". The line arrives as a translatable
 * message (a key and two values), and {@link FindOperator} takes it apart; this class reads the values. No
 * Minecraft classes in here, so the reading is unit tested on a normal JVM.
 *
 * Only that structured message is trusted. The same words typed into the chat by another player are plain
 * text, not a message with that key, so nobody can answer a /find with a made-up position.
 */
public final class DataReply {
    private DataReply() {
    }

    /** One line of the server's answer: who it is about, and the value that was printed. */
    public record Seen(String who, String value) {
    }

    /** "[128.3d, 63.0d, -1073.0d]" once the spaces are gone. */
    private static final Pattern POSITION = Pattern.compile("^\\[([^,\\]]+),([^,\\]]+),([^,\\]]+)]$");
    /** A resource id such as minecraft:overworld or some_pack:deep_space. */
    private static final Pattern DIMENSION = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    /** Anything that cannot be part of a player name; used to cut a printed name apart from a team prefix. */
    private static final Pattern NOT_A_NAME_CHARACTER = Pattern.compile("[^A-Za-z0-9_.\\-]+");

    /** x, y and z from "[128.3d, 63.0d, -1073.0d]", or null when the text is not a position. */
    public static double[] position(String value) {
        if (value == null) return null;
        Matcher matcher = POSITION.matcher(value.replaceAll("\\s+", ""));
        if (!matcher.matches()) return null;
        Double x = number(matcher.group(1));
        Double y = number(matcher.group(2));
        Double z = number(matcher.group(3));
        if (x == null || y == null || z == null) return null;
        return new double[] {x, y, z};
    }

    /** "minecraft:overworld" from "\"minecraft:overworld\"" (the quotes are part of what the server prints), or null. */
    public static String dimension(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' || first == '\'') && first == last) text = text.substring(1, text.length() - 1).trim();
        }
        return DIMENSION.matcher(text).matches() ? text : null;
    }

    /**
     * True when the name the server printed is this player. The server prints the display name, which can
     * carry a team prefix or suffix ("[VIP] Notkiahh"), so the printed name is cut apart and each piece is
     * compared on its own; "Bob" is not "Bobby".
     */
    public static boolean isPlayer(String printedName, String wanted) {
        if (printedName == null || wanted == null || wanted.isEmpty()) return false;
        for (String piece : NOT_A_NAME_CHARACTER.split(printedName)) {
            if (piece.equalsIgnoreCase(wanted)) return true;
        }
        return false;
    }

    private static Double number(String raw) {
        String text = raw.trim();
        if (text.isEmpty()) return null;
        char last = text.charAt(text.length() - 1);
        if (last == 'd' || last == 'D') text = text.substring(0, text.length() - 1);
        try {
            double value = Double.parseDouble(text);
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
