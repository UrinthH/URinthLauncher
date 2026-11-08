package dev.urinth.find;

import java.util.ArrayList;
import java.util.List;

/**
 * The answer to one /find, as plain data: what happened, who, where, in which dimension. It is what the server
 * sends back to the client, and it is turned into coloured chat text by {@link #parts()}. No Minecraft classes
 * in here, so the wording and the layout are unit tested on a normal JVM.
 *
 * The chat text for a player who was found is two lines:
 * <pre>
 * XYZ: x128.30 y63 z-1073
 * Overworld &lt; Notkiahh
 * </pre>
 */
public record FindReply(int status, String name, double x, double y, double z, String dimension, String others) {
    /** The player was found; name, x, y, z and dimension are filled in. */
    public static final int OK = 0;
    /** Nobody online fits the typed name; name is what was typed. */
    public static final int NOT_ONLINE = 1;
    /** Several players fit; name is what was typed and others lists them. */
    public static final int AMBIGUOUS = 2;
    /** The server does not let this player use /find. */
    public static final int NO_PERMISSION = 3;
    /** The player is online, but the game cannot tell where they are. Only made on the client. */
    public static final int TOO_FAR = 4;
    /** The server was asked with /data, as an operator may, and sent nothing usable back. Only made on the client. */
    public static final int NO_ANSWER = 5;

    /** Most names listed when several players fit; the rest are counted. */
    static final int MAX_LISTED = 8;

    /** The colours the chat text uses. The Minecraft side maps these to chat colours. */
    public enum Tint { WHITE, GRAY, GOLD, YELLOW, GREEN, RED, AQUA, PURPLE }

    /** A piece of chat text in one colour. A "\n" starts a new line. */
    public record Part(String text, Tint tint) {
    }

    public FindReply {
        name = name == null ? "" : name;
        dimension = dimension == null ? "" : dimension;
        others = others == null ? "" : others;
    }

    public static FindReply found(Located player) {
        return new FindReply(OK, player.name(), player.x(), player.y(), player.z(), player.dimension(), "");
    }

    public static FindReply notOnline(String typed) {
        return new FindReply(NOT_ONLINE, typed, 0, 0, 0, "", "");
    }

    public static FindReply ambiguous(String typed, List<String> names) {
        StringBuilder list = new StringBuilder();
        int shown = Math.min(names.size(), MAX_LISTED);
        for (int i = 0; i < shown; i++) {
            if (i > 0) list.append(", ");
            list.append(names.get(i));
        }
        if (names.size() > shown) list.append(" and ").append(names.size() - shown).append(" more");
        return new FindReply(AMBIGUOUS, typed, 0, 0, 0, "", list.toString());
    }

    public static FindReply noPermission() {
        return new FindReply(NO_PERMISSION, "", 0, 0, 0, "", "");
    }

    public static FindReply tooFar(String name) {
        return new FindReply(TOO_FAR, name, 0, 0, 0, "", "");
    }

    public static FindReply noAnswer(String name) {
        return new FindReply(NO_ANSWER, name, 0, 0, 0, "", "");
    }

    public boolean isSuccess() {
        return status == OK;
    }

    /** The colour of a dimension's name: green, red and purple for the three normal ones, aqua for any other. */
    public static Tint dimensionTint(String dimensionId) {
        if (FindFormat.isOverworld(dimensionId)) return Tint.GREEN;
        if (FindFormat.isNether(dimensionId)) return Tint.RED;
        if (FindFormat.isEnd(dimensionId)) return Tint.PURPLE;
        return Tint.AQUA;
    }

    public List<Part> parts() {
        List<Part> out = new ArrayList<>();
        switch (status) {
            case OK:
                out.add(new Part("XYZ: ", Tint.GOLD));
                out.add(new Part(FindFormat.position(x, y, z), Tint.WHITE));
                out.add(new Part("\n", Tint.WHITE));
                out.add(new Part(FindFormat.dimensionName(dimension), dimensionTint(dimension)));
                out.add(new Part(" < ", Tint.GRAY));
                out.add(new Part(name, Tint.WHITE));
                break;
            case NOT_ONLINE:
                out.add(new Part("No player called \"" + name + "\" is online.", Tint.RED));
                break;
            case AMBIGUOUS:
                out.add(new Part("More than one player matches \"", Tint.YELLOW));
                out.add(new Part(name, Tint.WHITE));
                out.add(new Part("\": ", Tint.YELLOW));
                out.add(new Part(others, Tint.WHITE));
                out.add(new Part(". Type a few more letters.", Tint.YELLOW));
                break;
            case NO_PERMISSION:
                out.add(new Part("You do not have permission to use /find on this server.", Tint.RED));
                break;
            case TOO_FAR:
                out.add(new Part(name, Tint.WHITE));
                out.add(new Part(" is online, but too far away for your game to know where they are.", Tint.YELLOW));
                out.add(new Part("\n", Tint.WHITE));
                out.add(new Part("The server must have the URinth Find mod, or you must be an operator on it.", Tint.GRAY));
                break;
            case NO_ANSWER:
                out.add(new Part(name, Tint.WHITE));
                out.add(new Part(" is online, but the server did not say where they are.", Tint.YELLOW));
                out.add(new Part("\n", Tint.WHITE));
                out.add(new Part("The server may block /data or have command feedback turned off.", Tint.GRAY));
                break;
            default:
                out.add(new Part("Could not find \"" + name + "\".", Tint.RED));
                break;
        }
        return out;
    }

    /** The chat text without colours. */
    public String plainText() {
        StringBuilder text = new StringBuilder();
        for (Part part : parts()) text.append(part.text());
        return text.toString();
    }
}
