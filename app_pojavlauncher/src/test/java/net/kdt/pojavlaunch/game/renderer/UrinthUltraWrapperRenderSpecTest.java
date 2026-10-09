package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class UrinthUltraWrapperRenderSpecTest {
    @Test
    public void wrapperDelegatesBackendIdentityAndCompatibility() {
        FakeRenderSpec backend = new FakeRenderSpec();
        UrinthUltraWrapperRenderSpec wrapper = new UrinthUltraWrapperRenderSpec(backend);

        assertSame(backend, wrapper.getDelegate());
        assertTrue(wrapper.compatibleDevice(null));
        assertEquals("Fake backend", wrapper.getDelegate().name());
        assertEquals("fake-backend", wrapper.tag());
        assertEquals("libfake.so", wrapper.library());
        assertEquals("/fake/lib", wrapper.librarySearchPath());
        assertEquals(42, wrapper.displayName());
    }

    @Test
    public void wrappingAnExistingWrapperDoesNotNestDelegates() {
        FakeRenderSpec backend = new FakeRenderSpec();
        UrinthUltraWrapperRenderSpec first = new UrinthUltraWrapperRenderSpec(backend);
        UrinthUltraWrapperRenderSpec second = new UrinthUltraWrapperRenderSpec(first);

        assertSame(backend, second.getDelegate());
        assertEquals("UrinthUltra Wrapper (Fake backend)", second.name());
        assertEquals("fake-backend", second.tag());
    }

    private static final class FakeRenderSpec implements RenderSpec {
        @Override
        public boolean compatibleDevice(Context context) {
            return true;
        }

        @Override
        public String name() {
            return "Fake backend";
        }

        @Override
        public int displayName() {
            return 42;
        }

        @Override
        public String tag() {
            return "fake-backend";
        }

        @Override
        public String library() {
            return "libfake.so";
        }

        @Override
        public String librarySearchPath() {
            return "/fake/lib";
        }

        @Override
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            envMap.put("FAKE_BACKEND", "1");
        }

        @Override
        public boolean setupRenderer() {
            return true;
        }
    }
}
