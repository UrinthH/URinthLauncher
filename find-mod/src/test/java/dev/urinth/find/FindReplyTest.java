package dev.urinth.find;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FindReplyTest {
    private static Located notkiahh() {
        return new Located("Notkiahh", 128.3, 63.0, -1073.0, "minecraft:overworld");
    }

    @Test
    void aFoundPlayerIsTwoLinesLikeTheRequest() {
        FindReply reply = FindReply.found(notkiahh());
        assertTrue(reply.isSuccess());
        assertEquals("XYZ: x128.30 y63 z-1073\nOverworld < Notkiahh", reply.plainText());
    }

    @Test
    void theDimensionLineFollowsTheDimension() {
        assertEquals("XYZ: x1 y2 z3\nNether < Nora",
                FindReply.found(new Located("Nora", 1, 2, 3, "minecraft:the_nether")).plainText());
        assertEquals("XYZ: x0.50 y64 z0\nEnd < Bo",
                FindReply.found(new Located("Bo", 0.5, 64, -0.0, "minecraft:the_end")).plainText());
        assertEquals("XYZ: x1 y2 z3\nDeep Dark Plane < Pat",
                FindReply.found(new Located("Pat", 1, 2, 3, "mymod:deep_dark_plane")).plainText());
    }

    @Test
    void coloursFollowTheDimension() {
        assertEquals(FindReply.Tint.GREEN, FindReply.dimensionTint("minecraft:overworld"));
        assertEquals(FindReply.Tint.RED, FindReply.dimensionTint("minecraft:the_nether"));
        assertEquals(FindReply.Tint.PURPLE, FindReply.dimensionTint("minecraft:the_end"));
        assertEquals(FindReply.Tint.AQUA, FindReply.dimensionTint("mymod:anything"));
        assertEquals(FindReply.Tint.AQUA, FindReply.dimensionTint(null));
    }

    @Test
    void theFoundTextIsColouredPartByPart() {
        List<FindReply.Part> parts = FindReply.found(notkiahh()).parts();
        assertEquals("XYZ: ", parts.get(0).text());
        assertEquals(FindReply.Tint.GOLD, parts.get(0).tint());
        assertEquals("x128.30 y63 z-1073", parts.get(1).text());
        assertEquals("Overworld", parts.get(3).text());
        assertEquals(FindReply.Tint.GREEN, parts.get(3).tint());
    }

    @Test
    void notOnline() {
        FindReply reply = FindReply.notOnline("Ghost");
        assertFalse(reply.isSuccess());
        assertEquals("No player called \"Ghost\" is online.", reply.plainText());
    }

    @Test
    void ambiguous() {
        FindReply reply = FindReply.ambiguous("no", List.of("Nora", "Notkiahh"));
        assertEquals("More than one player matches \"no\": Nora, Notkiahh. Type a few more letters.", reply.plainText());
    }

    @Test
    void aLongListOfMatchesIsShortened() {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= 11; i++) names.add("Player" + i);
        String text = FindReply.ambiguous("p", names).plainText();
        assertTrue(text.contains("Player8 and 3 more"), text);
        assertFalse(text.contains("Player9"), text);
        assertTrue(FindReply.ambiguous("p", names).others().length() < 200);
    }

    @Test
    void noPermission() {
        assertEquals("You do not have permission to use /find on this server.", FindReply.noPermission().plainText());
    }

    @Test
    void tooFarSaysWhatIsNeeded() {
        assertEquals("Notkiahh is online, but too far away for your game to know where they are.\n"
                + "The server needs the URinth Find mod to tell you.", FindReply.tooFar("Notkiahh").plainText());
    }

    @Test
    void anUnknownStatusStillMakesAMessage() {
        assertEquals("Could not find \"x\".", new FindReply(99, "x", 0, 0, 0, "", "").plainText());
    }

    @Test
    void missingTextIsNeverNull() {
        FindReply reply = new FindReply(FindReply.OK, null, 1, 2, 3, null, null);
        assertEquals("", reply.name());
        assertEquals("", reply.dimension());
        assertEquals("", reply.others());
        assertEquals("XYZ: x1 y2 z3\nUnknown < ", reply.plainText());
    }
}
