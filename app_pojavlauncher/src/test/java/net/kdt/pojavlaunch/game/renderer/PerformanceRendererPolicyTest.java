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

    @Test
    public void vulkanRenderersAreListedWithTheModeOnAndOff() {
        assertTrue(PerformanceRendererPolicy.isListed(Renderers.ZINK_RENDERER, true));
        assertTrue(PerformanceRendererPolicy.isListed(Renderers.ZINK_RENDERER, false));
        assertTrue(PerformanceRendererPolicy.isListed(Renderers.LEGACYZINK_RENDERER, true));
        assertTrue(PerformanceRendererPolicy.isListed(Renderers.LEGACYZINK_RENDERER, false));
    }

    @Test
    public void turningTheModeOnKeepsAVulkanRendererSelected() {
        assertEquals(Renderers.ZINK_RENDERER, PerformanceRendererPolicy.selectionOnEnable(Renderers.ZINK_RENDERER));
        assertEquals(Renderers.LEGACYZINK_RENDERER, PerformanceRendererPolicy.selectionOnEnable(Renderers.LEGACYZINK_RENDERER));
    }

    @Test
    public void turningTheModeOnStillSelectsLtwForEveryOpenGlRenderer() {
        assertEquals(PM, PerformanceRendererPolicy.selectionOnEnable(Renderers.GL4ES_RENDERER));
        assertEquals(PM, PerformanceRendererPolicy.selectionOnEnable(Renderers.MOBILEGLUES_RENDERER));
        assertEquals(PM, PerformanceRendererPolicy.selectionOnEnable("some_plugin_renderer"));
        assertEquals(PM, PerformanceRendererPolicy.selectionOnEnable(PM));
        assertEquals(PM, PerformanceRendererPolicy.selectionOnEnable(null));
        assertEquals(PM, PerformanceRendererPolicy.selectionOnEnable(""));
    }

    @Test
    public void aVulkanRendererIsNeverRememberedAsTheOpenGlOneToGoBackTo() {
        assertEquals(Renderers.GL4ES_RENDERER,
                PerformanceRendererPolicy.rememberedOnEnable(Renderers.ZINK_RENDERER, Renderers.GL4ES_RENDERER));
        assertNull(PerformanceRendererPolicy.rememberedOnEnable(Renderers.ZINK_RENDERER, null));
    }

    @Test
    public void turningTheModeOffKeepsAVulkanRenderer() {
        assertEquals(Renderers.ZINK_RENDERER,
                PerformanceRendererPolicy.selectionOnDisable(Renderers.ZINK_RENDERER, Renderers.GL4ES_RENDERER));
        assertEquals(Renderers.ZINK_RENDERER, PerformanceRendererPolicy.selectionOnDisable(Renderers.ZINK_RENDERER, null));
    }

    @Test
    public void theStoredRendererIsOnlyCorrectedWhenItDisagreesWithTheMode() {
        // Mode on: LTW and Vulkan agree; every other renderer is corrected.
        assertFalse(PerformanceRendererPolicy.needsSync(true, PM));
        assertFalse(PerformanceRendererPolicy.needsSync(true, Renderers.ZINK_RENDERER));
        assertFalse(PerformanceRendererPolicy.needsSync(true, Renderers.LEGACYZINK_RENDERER));
        assertTrue(PerformanceRendererPolicy.needsSync(true, Renderers.GL4ES_RENDERER));
        assertTrue(PerformanceRendererPolicy.needsSync(true, Renderers.MOBILEGLUES_RENDERER));
        assertTrue(PerformanceRendererPolicy.needsSync(true, null));
        // Mode off: only LTW is corrected.
        assertTrue(PerformanceRendererPolicy.needsSync(false, PM));
        assertFalse(PerformanceRendererPolicy.needsSync(false, Renderers.ZINK_RENDERER));
        assertFalse(PerformanceRendererPolicy.needsSync(false, Renderers.GL4ES_RENDERER));
        assertFalse(PerformanceRendererPolicy.needsSync(false, Renderers.MOBILEGLUES_RENDERER));
    }
}
