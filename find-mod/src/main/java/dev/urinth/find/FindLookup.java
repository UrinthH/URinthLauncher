package dev.urinth.find;

import java.util.ArrayList;
import java.util.List;

/**
 * Looks a typed name up in a list of players who are online and says where that player is. Used by the server
 * (every player online), by the client in a world it hosts itself, and as the last step of the client's own
 * search. Plain Java, unit tested.
 */
public final class FindLookup {
    private FindLookup() {}

    public static FindReply lookup(String typed, List<Located> online) {
        List<String> names = new ArrayList<>();
        if (online != null) {
            for (Located player : online) names.add(player.name());
        }
        PlayerMatcher.Match match = PlayerMatcher.resolve(typed, names);
        String clean = typed == null ? "" : typed.trim();
        if (match.kind() == PlayerMatcher.Kind.FOUND) {
            for (Located player : online) {
                if (player.name().equals(match.name())) return FindReply.found(player);
            }
            return FindReply.notOnline(clean);
        }
        if (match.kind() == PlayerMatcher.Kind.AMBIGUOUS) return FindReply.ambiguous(clean, match.names());
        return FindReply.notOnline(clean);
    }
}
