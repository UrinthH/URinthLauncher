package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

/** Guards against renderer IDs being listed without a matching implementation. */
public class RendererRegistryTest {
    @Test
    public void everyListedRendererHasAnImplementation() {
        String[] rendererIds = Renderers.allRendererIds();
        assertEquals("Unexpected renderer registry size", 11, rendererIds.length);

        for (String rendererId : rendererIds) {
            RenderSpec spec = GameRenderer.getKnownRenderer(rendererId);
            assertNotNull("Missing RenderSpec for renderer ID: " + rendererId, spec);
            assertEquals("Renderer tag does not match registry ID: " + rendererId,
                    rendererId, spec.tag());
        }
    }

    @Test
    public void registryReturnsDefensiveCopy() {
        String[] rendererIds = Renderers.allRendererIds();
        String originalFirstId = rendererIds[0];
        rendererIds[0] = "not-a-real-renderer";

        assertEquals("Mutating a returned list must not alter the canonical registry",
                originalFirstId, Renderers.allRendererIds()[0]);
    }

    @Test
    public void legacyGl4esIdsStillResolveToGl4es() {
        assertEquals(Renderers.GL4ES_RENDERER,
                GameRenderer.getKnownRenderer("opengles2_4").tag());
        assertEquals(Renderers.GL4ES_RENDERER,
                GameRenderer.getKnownRenderer("opengles2_5").tag());
    }
}
