package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
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
            assertTrue("Duplicate renderer ID in registry: " + rendererId, uniqueIds.add(rendererId));
            RenderSpec spec = GameRenderer.getKnownRenderer(rendererId);
            assertNotNull("Missing RenderSpec for renderer ID: " + rendererId, spec);
            assertEquals("Renderer tag does not match registry ID: " + rendererId, rendererId, spec.tag());
        }
    }

    @Test
    public void registryReturnsDefensiveCopy() {
        String[] rendererIds = Renderers.allRendererIds();
        String originalFirstId = rendererIds[0];
        rendererIds[0] = "not-a-real-renderer";
        assertEquals(originalFirstId, Renderers.allRendererIds()[0]);
    }

    @Test
    public void legacyGl4esIdsStillResolveToGl4es() {
        assertEquals(Renderers.GL4ES_RENDERER, GameRenderer.getKnownRenderer("opengles2_4").tag());
        assertEquals(Renderers.GL4ES_RENDERER, GameRenderer.getKnownRenderer("opengles2_5").tag());
    }

    @Test
    public void unknownAndNullRendererIdsDoNotCrashLookup() {
        org.junit.Assert.assertNull(GameRenderer.getKnownRenderer(null));
        org.junit.Assert.assertNull(GameRenderer.getKnownRenderer("not-a-real-renderer"));
    }

    @Test
    public void setupFailuresBecomeFallbackEligibleResults() {
        assertFalse(GameRenderer.setupRendererSafely(new FailingRenderSpec(false, false, false)));
        assertFalse(GameRenderer.setupRendererSafely(new FailingRenderSpec(true, false, false)));
        assertFalse(GameRenderer.setupRendererSafely(null));
        assertFalse(GameRenderer.prepareRendererSafely(new FailingRenderSpec(false, false, true)));
    }

    @Test
    public void compatibilityProbeFailuresOnlyHideTheBrokenRenderer() {
        assertFalse(RendererCache.isCompatibleSafely(new FailingRenderSpec(false, true, false), null));
        assertFalse(RendererCache.isCompatibleSafely(new FailingRenderSpec(true, true, false), null));
        assertFalse(RendererCache.isCompatibleSafely(null, null));
    }

    private static final class FailingRenderSpec implements RenderSpec {
        private final boolean linkageFailure;
        private final boolean failCompatibility;
        private final boolean failLibraryPath;

        FailingRenderSpec(boolean linkageFailure, boolean failCompatibility, boolean failLibraryPath) {
            this.linkageFailure = linkageFailure;
            this.failCompatibility = failCompatibility;
            this.failLibraryPath = failLibraryPath;
        }

        @Override
        public boolean compatibleDevice(Context context) {
            if (!failCompatibility) return true;
            if (linkageFailure) throw new UnsatisfiedLinkError("test compatibility linkage failure");
            throw new IllegalStateException("test compatibility probe failure");
        }

        @Override public String name() { return "test-failing-renderer"; }
        @Override public int displayName() { return 0; }
        @Override public String tag() { return "test-failing-renderer"; }
        @Override public String library() { return "libtest.so"; }

        @Override
        public String librarySearchPath() {
            if (failLibraryPath) throw new IllegalStateException("test stale plugin path");
            return null;
        }

        @Override public void setupEnvironment(Context context, Map<String, String> envMap) { }

        @Override
        public boolean setupRenderer() {
            if (linkageFailure) throw new UnsatisfiedLinkError("test missing native library");
            throw new IllegalStateException("test renderer setup failure");
        }
    }
}
