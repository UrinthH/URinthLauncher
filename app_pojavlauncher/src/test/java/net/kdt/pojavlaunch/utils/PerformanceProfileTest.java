package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PerformanceProfileTest {
    private static List<String> lines(String... rows) {
        return new ArrayList<>(Arrays.asList(rows));
    }

    @Test
    public void lowersHeavySettings() {
        List<String> options = lines("renderDistance:16", "simulationDistance:12", "entityDistanceScaling:1.0",
                "particles:0", "graphicsMode:1", "ao:true", "entityShadows:true", "biomeBlendRadius:5",
                "mipmapLevels:4", "renderClouds:\"true\"");
        List<PerformanceProfile.Change> changes = PerformanceProfile.lighten(options);
        assertEquals(10, changes.size());
        assertEquals(lines("renderDistance:8", "simulationDistance:6", "entityDistanceScaling:0.75",
                "particles:1", "graphicsMode:0", "ao:false", "entityShadows:false", "biomeBlendRadius:1",
                "mipmapLevels:2", "renderClouds:\"false\""), options);
    }

    @Test
    public void neverRaisesAndNeverTouchesOtherKeys() {
        List<String> options = lines("renderDistance:4", "simulationDistance:5", "particles:2", "graphicsMode:0",
                "ao:false", "overrideWidth:1280", "maxFps:260", "enableVsync:false", "fullscreen:true", "lang:en_us");
        List<String> before = new ArrayList<>(options);
        assertTrue(PerformanceProfile.lighten(options).isEmpty());
        assertEquals(before, options);
    }

    @Test
    public void doesNotInventMissingKeysOrParseGarbage() {
        List<String> options = lines("renderDistance:abc", "something", "");
        assertTrue(PerformanceProfile.lighten(options).isEmpty());
        assertEquals(lines("renderDistance:abc", "something", ""), options);
    }

    @Test
    public void oldNumericAmbientOcclusionAndCloudsWork() {
        List<String> options = lines("ao:2", "renderClouds:fast", "fancyGraphics:true");
        PerformanceProfile.lighten(options);
        assertEquals(lines("ao:0", "renderClouds:false", "fancyGraphics:false"), options);
    }

    @Test
    public void restoreOnlyWhileValueIsStillOurs() {
        List<String> options = lines("renderDistance:16", "particles:0", "ao:true");
        List<PerformanceProfile.Change> changes = PerformanceProfile.lighten(options);
        // The player raised render distance in game to 10 afterwards.
        options.set(0, "renderDistance:10");
        assertEquals(2, PerformanceProfile.restore(options, changes));
        assertEquals(lines("renderDistance:10", "particles:0", "ao:true"), options);
    }

    @Test
    public void encodeDecodeRoundTrip() {
        List<PerformanceProfile.Change> changes = PerformanceProfile.lighten(lines("renderDistance:16", "renderClouds:\"true\""));
        List<PerformanceProfile.Change> back = PerformanceProfile.decode(PerformanceProfile.encode(changes));
        assertEquals(2, back.size());
        assertEquals("renderDistance", back.get(0).key);
        assertEquals("16", back.get(0).original);
        assertEquals("8", back.get(0).applied);
        assertEquals("\"true\"", back.get(1).original);
        assertTrue(PerformanceProfile.decode("").isEmpty());
        assertTrue(PerformanceProfile.decode(null).isEmpty());
    }
}
