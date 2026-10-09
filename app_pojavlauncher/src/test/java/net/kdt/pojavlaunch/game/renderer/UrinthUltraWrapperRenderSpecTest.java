package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;

import org.junit.Test;

import java.util.ArrayList;
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


    @Test
    public void gl4esBatchingRequiresUltraMode() {
        assertTrue(URinthRender.shouldEnableGl4esBatching(
                net.kdt.pojavlaunch.game.renderer.def.Renderers.GL4ES_RENDERER, true));
        org.junit.Assert.assertFalse(URinthRender.shouldEnableGl4esBatching(
                net.kdt.pojavlaunch.game.renderer.def.Renderers.GL4ES_RENDERER, false));
    }

    @Test
    public void gl4esBatchingDoesNotLeakToOtherBackends() {
        assertTrue(!URinthRender.shouldEnableGl4esBatching("ltw", true));
        assertTrue(!URinthRender.shouldEnableGl4esBatching("zink", true));
        assertTrue(!URinthRender.shouldEnableGl4esBatching("fake-backend", true));
    }

    @Test
    public void ultraJvmDefaultsAreAddedWithoutReplacingExistingArguments() {
        java.util.List<String> args = new ArrayList<>();
        URinthRender.addJvmOptimizationArgs(args);

        org.junit.Assert.assertTrue(args.contains("-XX:+UseG1GC"));
        org.junit.Assert.assertTrue(args.contains("-XX:MaxGCPauseMillis=100"));
        org.junit.Assert.assertTrue(args.contains("-XX:+ParallelRefProcEnabled"));

        int sizeAfterFirstPass = args.size();
        URinthRender.addJvmOptimizationArgs(args);
        assertEquals(sizeAfterFirstPass, args.size());
    }

    @Test
    public void ultraJvmDefaultsRespectExplicitUserGcAndPauseSettings() {
        java.util.List<String> args = new ArrayList<>();
        args.add("-XX:+UseSerialGC");
        args.add("-XX:MaxGCPauseMillis=250");
        args.add("-XX:-ParallelRefProcEnabled");

        URinthRender.addJvmOptimizationArgs(args);

        assertTrue(args.contains("-XX:+UseSerialGC"));
        assertTrue(args.contains("-XX:MaxGCPauseMillis=250"));
        assertTrue(args.contains("-XX:-ParallelRefProcEnabled"));
        org.junit.Assert.assertFalse(args.contains("-XX:+UseG1GC"));
        org.junit.Assert.assertFalse(args.contains("-XX:MaxGCPauseMillis=100"));
        org.junit.Assert.assertFalse(args.contains("-XX:+ParallelRefProcEnabled"));
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
