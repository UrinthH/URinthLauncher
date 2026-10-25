package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

import org.junit.Test;

public class PerformanceRendererPolicyTest {
    private static final String PM = Renderers.LTW_RENDERER;

    @Test
    public void modeOnListsOnlyPerformanceMode() {
        assertTrue(PerformanceRendererPolicy.isListed(PM, true));
        assertFalse(PerformanceRendererPolicy.isListed(Renderers.GL4ES_RENDERER, true));
        assertFalse(PerformanceRendererPolicy.isListed(Renderers.MOBILEGLUES_RENDERER, true));
        assertFalse(PerformanceRendererPolicy.isListed("some_plugin_renderer", true));
    }

    @Test
    public void modeOffHidesOnlyPerformanceMode() {
        assertFalse(PerformanceRendererPolicy.isListed(PM, false));
        assertTrue(PerformanceRendererPolicy.isListed(Renderers.GL4ES_RENDERER, false));
        assertTrue(PerformanceRendererPolicy.isListed(Renderers.MOBILEGLUES_RENDERER, false));
        assertTrue(PerformanceRendererPolicy.isListed("some_plugin_renderer", false));
    }

    @Test
    public void turningOnRemembersTheCurrentChoice() {
        assertEquals(Renderers.MOBILEGLUES_RENDERER,
                PerformanceRendererPolicy.rememberedOnEnable(Renderers.MOBILEGLUES_RENDERER, null));
        assertEquals(Renderers.MOBILEGLUES_RENDERER,
                PerformanceRendererPolicy.rememberedOnEnable(Renderers.MOBILEGLUES_RENDERER, Renderers.GL4ES_RENDERER));
    }

    @Test
    public void turningOnWhileAlreadyOnKeepsTheOldMemory() {
        assertEquals(Renderers.GL4ES_RENDERER,
                PerformanceRendererPolicy.rememberedOnEnable(PM, Renderers.GL4ES_RENDERER));
        assertNull(PerformanceRendererPolicy.rememberedOnEnable(PM, null));
        assertNull(PerformanceRendererPolicy.rememberedOnEnable("", null));
    }

    @Test
    public void turningOffPutsTheRememberedRendererBack() {
        assertEquals(Renderers.MOBILEGLUES_RENDERER,
                PerformanceRendererPolicy.selectionOnDisable(PM, Renderers.MOBILEGLUES_RENDERER));
    }

    @Test
    public void turningOffWithNothingRememberedFallsBack() {
        assertEquals(PerformanceRendererPolicy.FALLBACK_RENDERER,
                PerformanceRendererPolicy.selectionOnDisable(PM, null));
        assertEquals(PerformanceRendererPolicy.FALLBACK_RENDERER,
                PerformanceRendererPolicy.selectionOnDisable(PM, PM));
        assertEquals(PerformanceRendererPolicy.FALLBACK_RENDERER,
                PerformanceRendererPolicy.selectionOnDisable(null, ""));
    }

    @Test
    public void turningOffKeepsAnOrdinaryCurrentChoice() {
        assertEquals(Renderers.MOBILEGLUES_RENDERER,
                PerformanceRendererPolicy.selectionOnDisable(Renderers.MOBILEGLUES_RENDERER, Renderers.GL4ES_RENDERER));
    }
}
