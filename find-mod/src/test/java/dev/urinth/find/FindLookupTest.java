package dev.urinth.find;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FindLookupTest {
    private static final List<Located> ONLINE = Arrays.asList(
            new Located("Notkiahh", 128.3, 63.0, -1073.0, "minecraft:overworld"),
            new Located("Nora", 1, 2, 3, "minecraft:the_nether"),
            new Located("Steve", -5.25, 70, 12, "minecraft:the_end"));

    @Test
    void findsAPlayerWhateverTheLetterCase() {
        FindReply reply = FindLookup.lookup("notkiahh", ONLINE);
        assertEquals(FindReply.OK, reply.status());
        assertEquals("XYZ: x128.30 y63 z-1073\nOverworld < Notkiahh", reply.plainText());
    }

    @Test
    void findsAPlayerByTheStartOfTheName() {
        assertEquals("XYZ: x1 y2 z3\nNether < Nora", FindLookup.lookup("nor", ONLINE).plainText());
        assertEquals("XYZ: x-5.25 y70 z12\nEnd < Steve", FindLookup.lookup("ste", ONLINE).plainText());
    }

    @Test
    void severalPlayersFit() {
        FindReply reply = FindLookup.lookup("no", ONLINE);
        assertEquals(FindReply.AMBIGUOUS, reply.status());
        assertEquals("More than one player matches \"no\": Nora, Notkiahh. Type a few more letters.", reply.plainText());
    }

    @Test
    void nobodyFits() {
        FindReply reply = FindLookup.lookup("Herobrine", ONLINE);
        assertEquals(FindReply.NOT_ONLINE, reply.status());
        assertEquals("No player called \"Herobrine\" is online.", reply.plainText());
    }

    @Test
    void theTypedNameIsTrimmedInTheAnswer() {
        assertEquals("No player called \"x\" is online.", FindLookup.lookup("  x ", ONLINE).plainText());
    }

    @Test
    void nobodyOnlineAtAll() {
        assertEquals(FindReply.NOT_ONLINE, FindLookup.lookup("Steve", Arrays.<Located>asList()).status());
        assertEquals(FindReply.NOT_ONLINE, FindLookup.lookup("Steve", null).status());
        assertEquals(FindReply.NOT_ONLINE, FindLookup.lookup(null, ONLINE).status());
    }
}
