package dev.urinth.find;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerMatcherTest {
    private static final List<String> ONLINE = Arrays.asList("Steve", "Notkiahh", "Alex");

    @Test
    void exactName() {
        PlayerMatcher.Match match = PlayerMatcher.resolve("Notkiahh", ONLINE);
        assertEquals(PlayerMatcher.Kind.FOUND, match.kind());
        assertEquals("Notkiahh", match.name());
    }

    @Test
    void anyLetterCase() {
        assertEquals("Notkiahh", PlayerMatcher.resolve("notkiahh", ONLINE).name());
        assertEquals("Notkiahh", PlayerMatcher.resolve("NOTKIAHH", ONLINE).name());
    }

    @Test
    void theStartOfAName() {
        PlayerMatcher.Match match = PlayerMatcher.resolve("notk", ONLINE);
        assertEquals(PlayerMatcher.Kind.FOUND, match.kind());
        assertEquals("Notkiahh", match.name());
    }

    @Test
    void severalNamesStartTheSameWay() {
        PlayerMatcher.Match match = PlayerMatcher.resolve("no", Arrays.asList("Notkiahh", "Nora", "Steve"));
        assertEquals(PlayerMatcher.Kind.AMBIGUOUS, match.kind());
        assertEquals(Arrays.asList("Nora", "Notkiahh"), match.names());
        assertEquals(null, match.name());
    }

    @Test
    void anExactNameBeatsLongerNamesThatStartWithIt() {
        PlayerMatcher.Match match = PlayerMatcher.resolve("bob", Arrays.asList("Bobby", "Bob"));
        assertEquals(PlayerMatcher.Kind.FOUND, match.kind());
        assertEquals("Bob", match.name());
    }

    @Test
    void sameLettersInDifferentCaseAreOnlyAmbiguousWhenTheCaseDoesNotDecide() {
        List<String> both = Arrays.asList("Bob", "bob");
        assertEquals("bob", PlayerMatcher.resolve("bob", both).name());
        assertEquals("Bob", PlayerMatcher.resolve("Bob", both).name());
        assertEquals(PlayerMatcher.Kind.AMBIGUOUS, PlayerMatcher.resolve("BOB", both).kind());
    }

    @Test
    void nobodyFits() {
        assertEquals(PlayerMatcher.Kind.NONE, PlayerMatcher.resolve("zzz", ONLINE).kind());
        assertEquals(PlayerMatcher.Kind.NONE, PlayerMatcher.resolve("kiahh", ONLINE).kind());
        assertTrue(PlayerMatcher.resolve("zzz", ONLINE).names().isEmpty());
    }

    @Test
    void emptyOrMissingInput() {
        assertEquals(PlayerMatcher.Kind.NONE, PlayerMatcher.resolve("", ONLINE).kind());
        assertEquals(PlayerMatcher.Kind.NONE, PlayerMatcher.resolve("   ", ONLINE).kind());
        assertEquals(PlayerMatcher.Kind.NONE, PlayerMatcher.resolve(null, ONLINE).kind());
        assertEquals(PlayerMatcher.Kind.NONE, PlayerMatcher.resolve("a", null).kind());
        assertEquals(PlayerMatcher.Kind.NONE, PlayerMatcher.resolve("a", Arrays.<String>asList()).kind());
    }

    @Test
    void spacesAroundTheTypedNameAreIgnored() {
        assertEquals("Steve", PlayerMatcher.resolve("  Steve ", ONLINE).name());
    }

    @Test
    void theSameNameTwiceIsOnePlayer() {
        PlayerMatcher.Match match = PlayerMatcher.resolve("st", Arrays.asList("Steve", "Steve"));
        assertEquals(PlayerMatcher.Kind.FOUND, match.kind());
        assertEquals("Steve", match.name());
    }

    @Test
    void blankAndMissingNamesInTheListAreSkipped() {
        PlayerMatcher.Match match = PlayerMatcher.resolve("st", Arrays.asList(null, "", "Steve"));
        assertEquals("Steve", match.name());
    }

    @Test
    void namesWithADotFromBedrockPlayers() {
        assertEquals(".Pocket_Pal", PlayerMatcher.resolve(".pocket", Arrays.asList(".Pocket_Pal", "Steve")).name());
    }
}
