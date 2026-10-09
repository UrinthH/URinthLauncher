package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

/** Guards against renderer IDs being listed without a matching implementation. */
public class RendererRegistryTest {
    @Test
    public void everyListedRendererHasAnImplementationAndUniqueId() {
        String[] rendererIds = Renderers.allRendererIds();
        assertTrue("Renderer registry must not be empty", rendererIds.length > 0);
        Set<String> uniqueIds = new HashSet<>();

        for (String rendererId : rendererIds) {
            assertTrue("Duplicate renderer ID in registry: " + rendererId,
                    uniqueIds.add(rendererId));
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
