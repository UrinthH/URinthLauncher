package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsScreensTest {
    @Test
    void recognisesSodiumsScreenAndTheVanillaOne() {
        assertEquals(SettingsScreens.Kind.SODIUM, SettingsScreens.classify("net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"));
        assertEquals(SettingsScreens.Kind.VANILLA, SettingsScreens.classify(SettingsScreens.VANILLA_CLASS));
        assertEquals(SettingsScreens.Kind.SODIUM,
            SettingsScreens.classify("me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen"));
    }

    @Test
    void ignoresOtherScreensAndSodiumsHelperScreens() {
        assertEquals(SettingsScreens.Kind.NONE, SettingsScreens.classify("net.minecraft.client.gui.screens.options.OptionsScreen"));
        assertEquals(SettingsScreens.Kind.NONE, SettingsScreens.classify("net.minecraft.client.gui.screens.TitleScreen"));
        assertEquals(SettingsScreens.Kind.NONE, SettingsScreens.classify("net.caffeinemc.mods.sodium.client.gui.ConfigCorruptedScreen"));
        assertEquals(SettingsScreens.Kind.NONE, SettingsScreens.classify("net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPrompt"));
        assertEquals(SettingsScreens.Kind.NONE, SettingsScreens.classify(null));
    }

    @Test
    void sodiumButtonSitsInTheFreeColumnOnTheRightAndNeverOverTheOptionColumn() {
        for (int width : new int[] {320, 360, 400, 480, 537, 640, 806, 1024}) {
            int[] r = SettingsScreens.bounds(SettingsScreens.Kind.SODIUM, width, SettingsScreens.FLARE);
            assertTrue(r[0] + r[2] <= width - 4, "inside the screen at " + width);
            assertTrue(r[1] >= 28, "below the donate row at " + width);
            if (width >= 380) assertTrue(r[0] >= 6 + 240, "right of Sodium's 240px option column at " + width);
        }
    }

    @Test
    void vanillaButtonKeepsItsOldSpot() {
        int[] r = SettingsScreens.bounds(SettingsScreens.Kind.VANILLA, 400, SettingsScreens.FLARE);
        assertEquals(400 - 118 - 4, r[0]);
        assertEquals(6, r[1]);
    }

    @Test
    void labelShortensOnNarrowButtonsAndShowsTheMode() {
        assertEquals("Sun Flare: Full", SettingsScreens.label(SettingsScreens.FLARE, FlareConfig.FULL, 112));
        assertEquals("Flare: Strong", SettingsScreens.label(SettingsScreens.FLARE, FlareConfig.STRONG, 86));
    }

    @Test
    void beamsButtonIsStackedUnderTheFlareButtonOnSodiumAndAtTheTopLeftOnVanilla() {
        for (int width : new int[] {320, 400, 537, 806}) {
            int[] f = SettingsScreens.bounds(SettingsScreens.Kind.SODIUM, width, SettingsScreens.FLARE);
            int[] b = SettingsScreens.bounds(SettingsScreens.Kind.SODIUM, width, SettingsScreens.BEAMS);
            assertEquals(f[0], b[0]);
            assertTrue(b[1] >= f[1] + f[3], "no overlap at " + width);
        }
        int[] vb = SettingsScreens.bounds(SettingsScreens.Kind.VANILLA, 400, SettingsScreens.BEAMS);
        assertEquals(4, vb[0]);
        assertEquals("Sun Beams: Medium", SettingsScreens.label(SettingsScreens.BEAMS, FlareConfig.BEAMS_DEFAULT, 112));
        assertEquals("Beams: Off", SettingsScreens.label(SettingsScreens.BEAMS, FlareConfig.BEAMS_OFF, 86));
    }
}
