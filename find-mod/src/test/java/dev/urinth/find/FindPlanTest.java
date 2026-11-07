package dev.urinth.find;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FindPlanTest {
    private static final List<Located> SEEN = Arrays.asList(
            new Located("Notkiahh", 128.3, 63.0, -1073.0, "minecraft:overworld"),
            new Located("Me", 0, 64, 0, "minecraft:overworld"));
    private static final List<String> TAB = Arrays.asList("Me", "Notkiahh", "FarAway", "Farmer");

    @Test
    void aPlayerTheGameCanSeeIsAnsweredAtOnceEvenWhenTheServerCouldHelp() {
        FindPlan plan = FindPlan.decide("notkiahh", SEEN, TAB, true);
        assertNull(plan.ask());
        assertEquals("XYZ: x128.30 y63 z-1073\nOverworld < Notkiahh", plan.show().plainText());
    }

    @Test
    void aPlayerOutOfSightIsAskedAboutAtTheServer() {
        FindPlan plan = FindPlan.decide("faraway", SEEN, TAB, true);
        assertNull(plan.show());
        assertEquals("FarAway", plan.ask());
    }

    @Test
    void aPlayerOutOfSightOnAServerWithoutTheModIsExplainedHonestly() {
        FindPlan plan = FindPlan.decide("faraway", SEEN, TAB, false);
        assertNull(plan.ask());
        assertEquals(FindReply.TOO_FAR, plan.show().status());
        assertEquals("FarAway", plan.show().name());
    }

    @Test
    void severalMatchesAreNeverSentToTheServer() {
        FindPlan plan = FindPlan.decide("far", SEEN, TAB, true);
        assertNull(plan.ask());
        assertEquals(FindReply.AMBIGUOUS, plan.show().status());
        assertEquals("far", plan.show().name());
        assertEquals("FarAway, Farmer", plan.show().others());
    }

    @Test
    void aNameNobodyHasIsLeftToTheServerWhenItCanAnswer() {
        FindPlan plan = FindPlan.decide("Hidden", SEEN, TAB, true);
        assertNull(plan.show());
        assertEquals("Hidden", plan.ask());
    }

    @Test
    void aNameNobodyHasIsReportedWhenTheServerCannotAnswer() {
        FindPlan plan = FindPlan.decide("Hidden", SEEN, TAB, false);
        assertNull(plan.ask());
        assertEquals(FindReply.NOT_ONLINE, plan.show().status());
        assertEquals("Hidden", plan.show().name());
    }

    @Test
    void aPlayerTheGameSeesButTheTabListHidesIsStillFound() {
        FindPlan plan = FindPlan.decide("notk", SEEN, Arrays.asList("Me"), false);
        assertNull(plan.ask());
        assertEquals(FindReply.OK, plan.show().status());
        assertEquals("Notkiahh", plan.show().name());
    }

    @Test
    void emptyInputNeverAsksTheServer() {
        FindPlan plan = FindPlan.decide("  ", SEEN, TAB, true);
        assertNull(plan.ask());
        assertEquals(FindReply.NOT_ONLINE, plan.show().status());
    }

    @Test
    void worksWithNothingKnown() {
        FindPlan plan = FindPlan.decide("x", null, null, false);
        assertTrue(plan.show() != null && plan.ask() == null);
        assertEquals(FindReply.NOT_ONLINE, plan.show().status());
    }
}
