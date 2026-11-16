package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

import org.junit.Test;

public class PerformanceMobileGluesTest {
    private static final String LTW = Renderers.LTW_RENDERER;

    private static PerformanceMobileGlues.Decision decide(boolean mode, boolean wanted, boolean safe, String tag,
                                                          boolean plugin, boolean version) {
        return PerformanceMobileGlues.decide(mode, wanted, safe, tag, plugin, version);
    }

    @Test
    public void performanceModeOnLtwWithThePluginAndASupportedVersionUsesMobileGlues() {
        PerformanceMobileGlues.Decision d = decide(true, true, false, LTW, true, true);
        assertTrue(d.useMobileGlues);
        assertEquals("renderer: MobileGlues (plugin found), instead of LTW", d.note);
    }

    @Test
    public void withoutThePluginLtwStaysAndTheLogSaysWhy() {
        PerformanceMobileGlues.Decision d = decide(true, true, false, LTW, false, true);
        assertFalse(d.useMobileGlues);
        assertTrue(d.note, d.note.contains("plugin app is not installed"));
    }

    @Test
    public void anOldGameVersionKeepsLtw() {
        PerformanceMobileGlues.Decision d = decide(true, true, false, LTW, true, false);
        assertFalse(d.useMobileGlues);
        assertTrue(d.note, d.note.contains("1.17"));
    }

    @Test
    public void thePlayerCanSwitchTheSwapOff() {
        PerformanceMobileGlues.Decision d = decide(true, false, false, LTW, true, true);
        assertFalse(d.useMobileGlues);
        assertTrue(d.note, d.note.contains("switched off"));
    }

    @Test
    public void aSafeLaunchKeepsTheBuiltInRenderer() {
        PerformanceMobileGlues.Decision d = decide(true, true, true, LTW, true, true);
        assertFalse(d.useMobileGlues);
        assertTrue(d.note, d.note.contains("did not start"));
    }

    @Test
    public void anythingButLtwIsNeverSwapped() {
        for (String tag : new String[] {Renderers.GL4ES_RENDERER, Renderers.MOBILEGLUES_RENDERER,
                Renderers.LEGACYZINK_RENDERER, Renderers.ZINK_RENDERER, "com.example.plugin", null}) {
            PerformanceMobileGlues.Decision d = decide(true, true, false, tag, true, true);
            assertFalse(String.valueOf(tag), d.useMobileGlues);
            assertTrue(String.valueOf(tag), d.note == null);
        }
    }

    @Test
    public void withPerformanceModeOffNothingIsInvolved() {
        PerformanceMobileGlues.Decision d = decide(false, true, false, LTW, true, true);
        assertFalse(d.useMobileGlues);
        assertNull(d.note);
    }

    @Test
    public void theCheapCheckMatchesWhenTheDecisionCanBeYes() {
        assertTrue(PerformanceMobileGlues.isCandidate(true, true, false, LTW));
        assertFalse(PerformanceMobileGlues.isCandidate(false, true, false, LTW));
        assertFalse(PerformanceMobileGlues.isCandidate(true, false, false, LTW));
        assertFalse(PerformanceMobileGlues.isCandidate(true, true, true, LTW));
        assertFalse(PerformanceMobileGlues.isCandidate(true, true, false, Renderers.GL4ES_RENDERER));
        assertFalse(PerformanceMobileGlues.isCandidate(true, true, false, null));
        // Whenever the cheap check says no, the full decision never says yes.
        for (int bits = 0; bits < 16; bits++) {
            boolean mode = (bits & 1) != 0, wanted = (bits & 2) != 0, safe = (bits & 4) != 0, ltw = (bits & 8) != 0;
            String tag = ltw ? LTW : Renderers.GL4ES_RENDERER;
            if (!PerformanceMobileGlues.isCandidate(mode, wanted, safe, tag)) {
                assertFalse(decide(mode, wanted, safe, tag, true, true).useMobileGlues);
            }
        }
        assertNotNull(decide(true, true, false, LTW, true, true).note);
    }
}
