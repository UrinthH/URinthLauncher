package dev.urinth.find;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns what was typed after /find into one of the players who are online. Plain Java, unit tested.
 *
 * The exact name wins, then the same name in other letter case, then a name that starts with what was typed
 * ("/find notk" finds Notkiahh). When several names fit at the same step the answer is "ambiguous" and the
 * list of fitting names is returned so it can be shown.
 */
public final class PlayerMatcher {
    private PlayerMatcher() {}

    public enum Kind { FOUND, AMBIGUOUS, NONE }

    public static final class Match {
        private final Kind kind;
        private final List<String> names;

        private Match(Kind kind, List<String> names) {
            this.kind = kind;
            this.names = names;
        }

        public Kind kind() {
            return kind;
        }

        /** The player that was found (one name), or the players that fit equally well (several). */
        public List<String> names() {
            return names;
        }

        /** The one player that was found, or null. */
        public String name() {
            return kind == Kind.FOUND && !names.isEmpty() ? names.get(0) : null;
        }
    }

    public static Match resolve(String typed, Collection<String> online) {
        String wanted = typed == null ? "" : typed.trim();
        if (wanted.isEmpty() || online == null) return none();

        Set<String> names = new LinkedHashSet<>();
        for (String name : online) {
            if (name != null && !name.isEmpty()) names.add(name);
        }

        if (names.contains(wanted)) return found(wanted);

        List<String> sameIgnoringCase = new ArrayList<>();
        for (String name : names) {
            if (name.equalsIgnoreCase(wanted)) sameIgnoringCase.add(name);
        }
        if (sameIgnoringCase.size() == 1) return found(sameIgnoringCase.get(0));
        if (sameIgnoringCase.size() > 1) return ambiguous(sameIgnoringCase);

        String lower = wanted.toLowerCase(Locale.ROOT);
        List<String> starting = new ArrayList<>();
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).startsWith(lower)) starting.add(name);
        }
        if (starting.size() == 1) return found(starting.get(0));
        if (starting.size() > 1) return ambiguous(starting);
        return none();
    }

    private static Match found(String name) {
        List<String> one = new ArrayList<>(1);
        one.add(name);
        return new Match(Kind.FOUND, one);
    }

    private static Match ambiguous(List<String> names) {
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return new Match(Kind.AMBIGUOUS, sorted);
    }

    private static Match none() {
        return new Match(Kind.NONE, new ArrayList<String>(0));
    }
}
