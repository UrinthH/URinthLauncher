package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The logic behind the "URinth" page of the video settings. The page itself is built from Sodium's classes (UrinthPage), which
 * only exist in the game, so what can be checked here is everything the page shows and decides.
 */
class UrinthPageTest {
    @AfterEach
    void resetState() {
        UrinthPageState.setAdded(false);
    }

    @Test
    void everyRowOffersExactlyTheChoicesTheSettingsHave() {
        assertEquals(FlareConfig.NAMES.length, PageChoices.Flare.values().length);
        assertEquals(FlareConfig.BEAM_NAMES.length, PageChoices.Beams.values().length);
        assertEquals(FlareConfig.LEAVES_NAMES.length, PageChoices.Leaves.values().length);
        assertEquals(FlareConfig.FPS_CHOICES.length, PageChoices.Guard.values().length);
        for (PageChoices.Flare choice : PageChoices.Flare.values()) assertEquals(FlareConfig.NAMES[choice.ordinal()], choice.label());
        for (PageChoices.Beams choice : PageChoices.Beams.values()) assertEquals(FlareConfig.BEAM_NAMES[choice.ordinal()], choice.label());
        for (PageChoices.Leaves choice : PageChoices.Leaves.values()) assertEquals(FlareConfig.LEAVES_NAMES[choice.ordinal()], choice.label());
        for (PageChoices.Guard choice : PageChoices.Guard.values()) {
            assertEquals(FlareConfig.FPS_CHOICES[choice.ordinal()], choice.fps());
            assertEquals(FlareConfig.fpsName(choice.fps()), choice.label());
        }
    }

    @Test
    void aSavedValueOutsideTheChoicesStillShowsAsTheNearestOne() {
        assertEquals(PageChoices.Flare.OFF, PageChoices.Flare.of(-3));
        assertEquals(PageChoices.Flare.STRONG, PageChoices.Flare.of(99));
        assertEquals(PageChoices.Beams.OFF, PageChoices.Beams.of(-1));
        assertEquals(PageChoices.Beams.EXTREME, PageChoices.Beams.of(99));
        assertEquals(PageChoices.Leaves.OFF, PageChoices.Leaves.of(-1));
        assertEquals(PageChoices.Leaves.SAFE, PageChoices.Leaves.of(99));
        assertEquals(PageChoices.Guard.OFF, PageChoices.Guard.nearest(0));
        assertEquals(PageChoices.Guard.OFF, PageChoices.Guard.nearest(-5));
        // a frame rate typed with /sunflare fps that is not one of the steps
        assertEquals(PageChoices.Guard.FPS_72, PageChoices.Guard.nearest(75));
        assertEquals(PageChoices.Guard.FPS_80, PageChoices.Guard.nearest(79));
        assertEquals(PageChoices.Guard.FPS_60, PageChoices.Guard.nearest(30));
        assertEquals(PageChoices.Guard.FPS_250, PageChoices.Guard.nearest(240));
        assertEquals(PageChoices.Guard.FPS_250, PageChoices.Guard.nearest(1000));
        for (PageChoices.Guard choice : PageChoices.Guard.values()) assertEquals(choice, PageChoices.Guard.nearest(choice.fps()));
    }

    @Test
    void theStyleRowMapsToTheSettingBothWays() {
        assertEquals(PageChoices.Style.REAL, PageChoices.Style.of(true));
        assertEquals(PageChoices.Style.PACK, PageChoices.Style.of(false));
        assertTrue(PageChoices.Style.REAL.isReal());
        assertFalse(PageChoices.Style.PACK.isReal());
        assertEquals("Real", PageChoices.Style.REAL.label());
        assertEquals("Pack", PageChoices.Style.PACK.label());
    }

    @Test
    void theFrameRateStepsGoUpAndTheTopOneIsWhatTheSettingAccepts() {
        assertEquals(0, FlareConfig.FPS_CHOICES[0], "the first step is Off");
        for (int i = 2; i < FlareConfig.FPS_CHOICES.length; i++) {
            assertTrue(FlareConfig.FPS_CHOICES[i] > FlareConfig.FPS_CHOICES[i - 1], "ascending at " + i);
        }
        assertEquals(FlareConfig.MAX_TARGET_FPS, FlareConfig.FPS_CHOICES[FlareConfig.FPS_CHOICES.length - 1]);
        for (int fps : FlareConfig.FPS_CHOICES) {
            FlareConfig config = new FlareConfig(java.nio.file.Path.of("unused.properties"));
            assertEquals(fps, config.setTargetFps(fps), "the setting keeps " + fps);
        }
    }

    @Test
    void everyRowHasANameAndAnExplanationThatFitsOnTheScreen() {
        String[][] rows = {
            {PageChoices.FLARE_NAME, PageChoices.FLARE_TIP},
            {PageChoices.BEAMS_NAME, PageChoices.BEAMS_TIP},
            {PageChoices.STYLE_NAME, PageChoices.STYLE_TIP},
            {PageChoices.LEAVES_NAME, PageChoices.LEAVES_TIP},
            {PageChoices.GUARD_NAME, PageChoices.GUARD_TIP},
            {PageChoices.LOG_NAME, PageChoices.LOG_TIP},
        };
        for (String[] row : rows) {
            assertTrue(row[0].length() >= 3 && row[0].length() <= 24, "name length of " + row[0]);
            assertTrue(row[1].length() >= 30 && row[1].length() <= 260, "tip length of " + row[0]);
        }
        assertEquals("URinth", PageChoices.PAGE_NAME);
    }

    @Test
    void theSeparateButtonsComeBackWhenTheSodiumPageWasNotAdded() {
        UrinthPageState.setAdded(false);
        assertTrue(UrinthPageState.needsButtons(SettingsScreens.Kind.SODIUM), "no page: the buttons are the way in");
        assertTrue(UrinthPageState.needsButtons(SettingsScreens.Kind.VANILLA));
        assertFalse(UrinthPageState.needsButtons(SettingsScreens.Kind.NONE));
    }

    @Test
    void theSeparateButtonsStayAwayFromSodiumOnceThePageIsThere() {
        UrinthPageState.setAdded(true);
        assertFalse(UrinthPageState.needsButtons(SettingsScreens.Kind.SODIUM), "the page has every setting");
        assertTrue(UrinthPageState.needsButtons(SettingsScreens.Kind.VANILLA), "the vanilla screen has no page");
        assertFalse(UrinthPageState.needsButtons(SettingsScreens.Kind.NONE));
        assertTrue(UrinthPageState.isAdded());
    }
}
