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

    @Test
    public void thermalStatusFormatterCoversAllAndroidStates() {
        assertEquals("none", URinthRender.thermalStatusName(android.os.PowerManager.THERMAL_STATUS_NONE));
        assertEquals("light", URinthRender.thermalStatusName(android.os.PowerManager.THERMAL_STATUS_LIGHT));
        assertEquals("moderate", URinthRender.thermalStatusName(android.os.PowerManager.THERMAL_STATUS_MODERATE));
        assertEquals("severe", URinthRender.thermalStatusName(android.os.PowerManager.THERMAL_STATUS_SEVERE));
        assertEquals("critical", URinthRender.thermalStatusName(android.os.PowerManager.THERMAL_STATUS_CRITICAL));
        assertEquals("emergency", URinthRender.thermalStatusName(android.os.PowerManager.THERMAL_STATUS_EMERGENCY));
        assertEquals("shutdown", URinthRender.thermalStatusName(android.os.PowerManager.THERMAL_STATUS_SHUTDOWN));
    }

    @Test
    public void thermalStatusFormatterLabelsUnknownValues() {
        assertEquals("unknown(99)", URinthRender.thermalStatusName(99));
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
