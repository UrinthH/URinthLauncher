package dev.urinth.find;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * What the client does for a /find on a server it is only a guest on: show an answer right away, or ask the
 * server. Exactly one of the two is set. Plain Java, unit tested.
 *
 * A client only ever knows where the players near it are; the server does not send the position of anybody
 * further away. So the order is: players the game can see are answered at once from what the game knows, a
 * player who is online but out of sight is asked about at the server (when the server has this mod), and
 * otherwise the player is told honestly that the game cannot know.
 */
public final class FindPlan {
    private final FindReply show;
    private final String ask;

    private FindPlan(FindReply show, String ask) {
        this.show = show;
        this.ask = ask;
    }

    /** The answer to show now, or null when the server is asked. */
    public FindReply show() {
        return show;
    }

    /** The name to send to the server, or null when the answer is shown now. */
    public String ask() {
        return ask;
    }

    private static FindPlan showing(FindReply reply) {
        return new FindPlan(reply, null);
    }

    private static FindPlan asking(String name) {
        return new FindPlan(null, name);
    }

    /**
     * @param typed       what was typed after /find
     * @param seen        the players the game has in the world around it right now, with their positions
     * @param online      the names the server lists as online (the tab list)
     * @param serverHelps true when the server has this mod, so it can be asked about players out of sight
     */
    public static FindPlan decide(String typed, List<Located> seen, Collection<String> online, boolean serverHelps) {
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
            return serverHelps ? asking(match.name()) : showing(FindReply.tooFar(match.name()));
        }
        if (match.kind() == PlayerMatcher.Kind.AMBIGUOUS) return showing(FindReply.ambiguous(clean, match.names()));
        // Nobody in the tab list fits. Some servers hide players from the list, so the server gets the last word.
        return serverHelps && !clean.isEmpty() ? asking(clean) : showing(FindReply.notOnline(clean));
    }
}
