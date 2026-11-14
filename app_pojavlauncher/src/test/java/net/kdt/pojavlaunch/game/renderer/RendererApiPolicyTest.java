package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.kdt.pojavlaunch.game.renderer.RendererApiPolicy.Choice;
import net.kdt.pojavlaunch.game.renderer.RendererApiPolicy.Outcome;
import net.kdt.pojavlaunch.game.renderer.RendererApiPolicy.Result;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RendererApiPolicyTest {
    private static Choice gl(String id, boolean usable) {
        return new Choice(id, id, GraphicsApi.OPENGL, usable);
    }

    private static Choice vk(String id, boolean usable) {
        return new Choice(id, id, GraphicsApi.VULKAN, usable);
    }

    @Test
    public void zinkAndLegacyZinkAreVulkan() {
        assertEquals(GraphicsApi.VULKAN, RendererApiPolicy.apiOf(Renderers.ZINK_RENDERER, null));
        assertEquals(GraphicsApi.VULKAN, RendererApiPolicy.apiOf(Renderers.LEGACYZINK_RENDERER, null));
    }

    @Test
    public void theOtherBuiltInRenderersAreOpenGl() {
        String[] ids = {Renderers.GL4ES_RENDERER, Renderers.LTW_RENDERER, Renderers.LTW_LEGACY_RENDERER,
                Renderers.KRYPTON_RENDERER, Renderers.MOBILEGLUES_RENDERER, Renderers.VIRGL_RENDERER,
                Renderers.PANFROST_RENDERER, Renderers.FREEDRENO_RENDERER, Renderers.FREEDRENO_GALLIUM_RENDERER,
                Renderers.MESA_RENDERER, Renderers.MESA_RENDERER_EXT};
        StringBuilder wrong = new StringBuilder();
        for (String id : ids) {
            if (RendererApiPolicy.apiOf(id, null) != GraphicsApi.OPENGL) wrong.append(id).append(' ');
        }
        assertEquals("", wrong.toString());
    }

    @Test
    public void everyRegisteredIdIsClassifiedWithoutFailing() {
        for (String id : Renderers.allRendererIds()) {
            assertTrue(RendererApiPolicy.apiOf(id, null) != null);
        }
    }

    @Test
    public void aPluginThatCallsItselfVulkanOrZinkIsVulkan() {
        assertEquals(GraphicsApi.VULKAN, RendererApiPolicy.apiOf("com.example.renderer", "Zink (Mesa)"));
        assertEquals(GraphicsApi.VULKAN, RendererApiPolicy.apiOf("com.example.renderer", "My VULKAN renderer"));
        assertEquals(GraphicsApi.VULKAN, RendererApiPolicy.apiOf("com.example.vulkan.plugin", "Plugin"));
        assertEquals(GraphicsApi.OPENGL, RendererApiPolicy.apiOf("com.example.mobileglues", "MobileGlues"));
        assertEquals(GraphicsApi.OPENGL, RendererApiPolicy.apiOf(null, null));
    }

    @Test
    public void choicesAreSplitByApiInTheOrderGiven() {
        List<Choice> all = Arrays.asList(gl("a", true), vk("z", true), gl("b", true), vk("y", false));
        assertEquals(2, RendererApiPolicy.of(GraphicsApi.OPENGL, all).size());
        assertEquals("z", RendererApiPolicy.of(GraphicsApi.VULKAN, all).get(0).id);
        assertEquals(1, RendererApiPolicy.usableCount(GraphicsApi.VULKAN, all));
        assertEquals(0, RendererApiPolicy.of(GraphicsApi.OPENGL, null).size());
    }

    @Test
    public void pickPrefersTheLastUsedOneThenTheFirstUsable() {
        List<Choice> all = Arrays.asList(vk("zink", true), vk("legacy", true), vk("broken", false));
        assertEquals("legacy", RendererApiPolicy.pick(GraphicsApi.VULKAN, all, "legacy"));
        assertEquals("zink", RendererApiPolicy.pick(GraphicsApi.VULKAN, all, null));
        assertEquals("zink", RendererApiPolicy.pick(GraphicsApi.VULKAN, all, "broken"));
        assertEquals("zink", RendererApiPolicy.pick(GraphicsApi.VULKAN, all, "gone"));
        assertNull(RendererApiPolicy.pick(GraphicsApi.OPENGL, all, null));
    }

    @Test
    public void switchingToTheApiInUseDoesNothing() {
        List<Choice> all = Arrays.asList(gl("gl4es", true), vk("zink", true));
        Result r = RendererApiPolicy.decideSwitch(GraphicsApi.OPENGL, GraphicsApi.OPENGL, all, null, false, true);
        assertEquals(Outcome.ALREADY, r.outcome);
        assertNull(r.rendererId);
    }

    @Test
    public void switchingToVulkanSelectsZinkWhenItCanRun() {
        List<Choice> all = Arrays.asList(gl("gl4es", true), vk("vulkan_zink", true), vk("vulkan_legacyzink", true));
        Result r = RendererApiPolicy.decideSwitch(GraphicsApi.VULKAN, GraphicsApi.OPENGL, all, null, false, true);
        assertEquals(Outcome.SELECT, r.outcome);
        assertEquals("vulkan_zink", r.rendererId);
    }

    @Test
    public void switchingBackRestoresTheRendererUsedLastWithOpenGl() {
        List<Choice> all = Arrays.asList(gl("gl4es", true), gl("plugin.mobileglues", true), vk("vulkan_zink", true));
        Result r = RendererApiPolicy.decideSwitch(GraphicsApi.OPENGL, GraphicsApi.VULKAN, all, "plugin.mobileglues", false, true);
        assertEquals(Outcome.SELECT, r.outcome);
        assertEquals("plugin.mobileglues", r.rendererId);
    }

    @Test
    public void aPhoneWithoutVulkanCannotSwitchToIt() {
        List<Choice> all = Arrays.asList(gl("gl4es", true), vk("vulkan_zink", true));
        Result r = RendererApiPolicy.decideSwitch(GraphicsApi.VULKAN, GraphicsApi.OPENGL, all, null, false, false);
        assertEquals(Outcome.NO_VULKAN_HARDWARE, r.outcome);
        assertNull(r.rendererId);
    }

    @Test
    public void vulkanWithoutAUsableRendererIsRefusedWithAReasonInsteadOfSwitching() {
        List<Choice> all = Arrays.asList(gl("gl4es", true), vk("vulkan_zink", false), vk("vulkan_legacyzink", false));
        Result r = RendererApiPolicy.decideSwitch(GraphicsApi.VULKAN, GraphicsApi.OPENGL, all, null, false, true);
        assertEquals(Outcome.NO_VULKAN_RENDERER, r.outcome);
        assertNull(r.rendererId);
    }

    @Test
    public void performanceModeIsTurnedOffFirstBeforeVulkanIsSelected() {
        List<Choice> all = Arrays.asList(gl("opengles3_ltw", true), vk("vulkan_zink", true));
        Result r = RendererApiPolicy.decideSwitch(GraphicsApi.VULKAN, GraphicsApi.OPENGL, all, null, true, true);
        assertEquals(Outcome.TURN_OFF_PERFORMANCE_MODE, r.outcome);
        assertEquals("vulkan_zink", r.rendererId);
    }

    @Test
    public void noOpenGlRendererAtAllIsReportedNotSelected() {
        List<Choice> all = new ArrayList<>(Arrays.asList(gl("gl4es", false), vk("vulkan_zink", true)));
        Result r = RendererApiPolicy.decideSwitch(GraphicsApi.OPENGL, GraphicsApi.VULKAN, all, null, false, true);
        assertEquals(Outcome.NO_OPENGL_RENDERER, r.outcome);
    }

    @Test
    public void vulkanVersionsAreShownAsMajorDotMinor() {
        assertEquals("1.1", RendererApiPolicy.formatVulkanVersion(0x401000));
        assertEquals("1.0", RendererApiPolicy.formatVulkanVersion(0x400003));
        assertEquals("1.3", RendererApiPolicy.formatVulkanVersion((1 << 22) | (3 << 12)));
        assertNull(RendererApiPolicy.formatVulkanVersion(0));
        assertNull(RendererApiPolicy.formatVulkanVersion(-5));
    }
    @Test
    public void shortNamesDropTheNotesOfTheDisplayName() {
        assertEquals("GL4ES", RendererApiPolicy.shortName("GL4ES (OpenGL ES 2) - 1.21.4 and lower"));
        assertEquals("Zink", RendererApiPolicy.shortName("Zink (Vulkan) - all versions"));
        assertEquals("Legacy Zink", RendererApiPolicy.shortName("Legacy Zink (Vulkan) - all versions, low performance"));
        assertEquals("Performance Mode", RendererApiPolicy.shortName("Performance Mode"));
        assertEquals("MobileGlues", RendererApiPolicy.shortName("  MobileGlues "));
        assertEquals("", RendererApiPolicy.shortName(null));
    }
}
