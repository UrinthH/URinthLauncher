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

    // ---- operator mode: with no mod on the server, an operator is answered by the server's own /data command

    @Test
    void anOperatorIsAnsweredByTheServersOwnCommandWhenTheServerHasNoMod() {
        FindPlan plan = FindPlan.decide("faraway", SEEN, TAB, false, true);
        assertNull(plan.show());
        assertNull(plan.ask());
        assertEquals("FarAway", plan.command());
    }

    @Test
    void theServerModComesBeforeTheCommand() {
        FindPlan plan = FindPlan.decide("faraway", SEEN, TAB, true, true);
        assertNull(plan.show());
        assertNull(plan.command());
        assertEquals("FarAway", plan.ask());
    }

    @Test
    void aPlayerWhoIsNotAnOperatorIsToldWhatWouldHelp() {
        FindPlan plan = FindPlan.decide("faraway", SEEN, TAB, false, false);
        assertNull(plan.ask());
        assertNull(plan.command());
        assertEquals(FindReply.TOO_FAR, plan.show().status());
        assertTrue(plan.show().plainText().contains("URinth Find mod"));
        assertTrue(plan.show().plainText().contains("operator"));
    }

    @Test
    void aPlayerTheGameSeesIsAnsweredAtOnceEvenForAnOperator() {
        FindPlan plan = FindPlan.decide("notkiahh", SEEN, TAB, false, true);
        assertNull(plan.ask());
        assertNull(plan.command());
        assertEquals("XYZ: x128.30 y63 z-1073\nOverworld < Notkiahh", plan.show().plainText());
    }

    @Test
    void severalMatchesAreNeverLookedUpEvenForAnOperator() {
        FindPlan plan = FindPlan.decide("far", SEEN, TAB, false, true);
        assertNull(plan.ask());
        assertNull(plan.command());
        assertEquals(FindReply.AMBIGUOUS, plan.show().status());
    }

    @Test
    void aNameNobodyHasIsNotLookedUpByCommandEvenForAnOperator() {
        FindPlan plan = FindPlan.decide("Hidden", SEEN, TAB, false, true);
        assertNull(plan.ask());
        assertNull(plan.command());
        assertEquals(FindReply.NOT_ONLINE, plan.show().status());
    }

    @Test
    void theShortFormMeansNotAnOperator() {
        FindPlan plan = FindPlan.decide("faraway", SEEN, TAB, false);
        assertNull(plan.command());
        assertEquals(FindReply.TOO_FAR, plan.show().status());
    }

    @Test
    void exactlyOneOfTheThreeIsSetWhateverIsTyped() {
        String[] typed = {"notkiahh", "faraway", "far", "Hidden", "  ", "", null};
        boolean[] both = {false, true};
        for (String text : typed) {
            for (boolean serverHelps : both) {
                for (boolean operator : both) {
                    FindPlan plan = FindPlan.decide(text, SEEN, TAB, serverHelps, operator);
                    int set = (plan.show() != null ? 1 : 0) + (plan.ask() != null ? 1 : 0) + (plan.command() != null ? 1 : 0);
                    assertEquals(1, set, text + " " + serverHelps + " " + operator);
                }
            }
        }
    }
}
