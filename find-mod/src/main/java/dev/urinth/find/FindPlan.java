package dev.urinth.find;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * What the client does for a /find on a server it is only a guest on: show an answer right away, ask the
 * server mod, or ask the server with vanilla commands. Exactly one of the three is set. Plain Java, unit tested.
 *
 * A client only ever knows where the players near it are; the server does not send the position of anybody
 * further away, however close to the edge of the view they are. So the order is:
 * <ol>
 * <li>players the game can see are answered at once from what the game knows;</li>
 * <li>a player who is online but out of sight is asked about at the server, when the server has this mod: it
 * knows everybody, at any distance;</li>
 * <li>failing that, when the player using /find is an operator, the server is asked with the vanilla command
 * /data get entity, which an operator may use on anybody, at any distance, in any dimension;</li>
 * <li>otherwise the player is told honestly that the game cannot know, and what would change that.</li>
 * </ol>
 */
public final class FindPlan {
    private final FindReply show;
    private final String ask;
    private final String command;

    private FindPlan(FindReply show, String ask, String command) {
        this.show = show;
        this.ask = ask;
        this.command = command;
    }

    /** The answer to show now, or null when the server is asked. */
    public FindReply show() {
        return show;
    }

    /** The name to send to the server mod, or null when this is not the way. */
    public String ask() {
        return ask;
    }

    /** The name to look up with /data get entity as an operator, or null when this is not the way. */
    public String command() {
        return command;
    }

    private static FindPlan showing(FindReply reply) {
        return new FindPlan(reply, null, null);
    }

    private static FindPlan asking(String name) {
        return new FindPlan(null, name, null);
    }

    private static FindPlan commanding(String name) {
        return new FindPlan(null, null, name);
    }

    /** Same as the longer form for a player who is not an operator. */
    public static FindPlan decide(String typed, List<Located> seen, Collection<String> online, boolean serverHelps) {
        return decide(typed, seen, online, serverHelps, false);
    }

    /**
     * @param typed       what was typed after /find
     * @param seen        the players the game has in the world around it right now, with their positions
     * @param online      the names the server lists as online (the tab list)
     * @param serverHelps true when the server has this mod, so it can be asked about players out of sight
     * @param operator    true when the player using /find may use /data on this server (operator level 2)
     */
    public static FindPlan decide(String typed, List<Located> seen, Collection<String> online, boolean serverHelps,
                                  boolean operator) {
        List<String> names = new ArrayList<>();
        if (online != null) names.addAll(online);
        if (seen != null) {
            for (Located player : seen) {
                if (!names.contains(player.name())) names.add(player.name());
            }
        }
        String clean = typed == null ? "" : typed.trim();
        PlayerMatcher.Match match = PlayerMatcher.resolve(clean, names);

        if (match.kind() == PlayerMatcher.Kind.FOUND) {
            if (seen != null) {
                for (Located player : seen) {
                    if (player.name().equals(match.name())) return showing(FindReply.found(player));
                }
            }
            if (serverHelps) return asking(match.name());
            if (operator) return commanding(match.name());
            return showing(FindReply.tooFar(match.name()));
        }
        if (match.kind() == PlayerMatcher.Kind.AMBIGUOUS) return showing(FindReply.ambiguous(clean, match.names()));
        // Nobody in the tab list fits. Some servers hide players from the list, so the server gets the last word.
        return serverHelps && !clean.isEmpty() ? asking(clean) : showing(FindReply.notOnline(clean));
    }
}
