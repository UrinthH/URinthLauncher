package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

/** Guards against adding a renderer to the Settings list without wiring its implementation. */
public class RendererRegistryTest {
    @Test
    public void everyListedRendererHasAnImplementation() {
        String[] rendererIds = {
                Renderers.GL4ES_RENDERER,
                Renderers.KRYPTON_RENDERER,
                Renderers.LTW_RENDERER,
                Renderers.ZINK_RENDERER,
                Renderers.VIRGL_RENDERER,
                Renderers.FREEDRENO_RENDERER,
                Renderers.PANFROST_RENDERER,
                Renderers.MESA_RENDERER,
                Renderers.MESA_RENDERER_EXT,
                Renderers.LEGACYZINK_RENDERER,
                Renderers.MOBILEGLUES_RENDERER
        };

        for (String rendererId : rendererIds) {
            RenderSpec spec = GameRenderer.getKnownRenderer(rendererId);
            assertNotNull("Missing RenderSpec for renderer ID: " + rendererId, spec);
            assertEquals("Renderer tag does not match registry ID: " + rendererId,
                    rendererId, spec.tag());
        }
    }

    @Test
    public void legacyGl4esIdsStillResolveToGl4es() {
        assertEquals(Renderers.GL4ES_RENDERER,
                GameRenderer.getKnownRenderer("opengles2_4").tag());
        assertEquals(Renderers.GL4ES_RENDERER,
                GameRenderer.getKnownRenderer("opengles2_5").tag());
    }
}
