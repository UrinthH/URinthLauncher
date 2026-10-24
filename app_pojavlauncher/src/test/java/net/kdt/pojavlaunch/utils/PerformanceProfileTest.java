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
    public void decodesRememberedChanges() {
        List<PerformanceProfile.Change> changes = PerformanceProfile.decode("renderDistance\t16\t8\nrenderClouds\t\"true\"\t\"false\"");
        assertEquals(2, changes.size());
        assertEquals("renderDistance", changes.get(0).key);
        assertEquals("16", changes.get(0).original);
        assertEquals("8", changes.get(0).applied);
        assertEquals("\"true\"", changes.get(1).original);
        assertTrue(PerformanceProfile.decode("").isEmpty());
        assertTrue(PerformanceProfile.decode(null).isEmpty());
        assertTrue(PerformanceProfile.decode("garbage line").isEmpty());
    }

    @Test
    public void restoresOnlyWhileValueIsStillTheOneThatWasSet() {
        List<String> options = lines("renderDistance:10", "particles:1", "ao:false", "maxFps:260", "overrideWidth:1280");
        List<PerformanceProfile.Change> changes = PerformanceProfile.decode(
                "renderDistance\t16\t8\nparticles\t0\t1\nao\ttrue\tfalse");
        // render distance was changed by the player to 10 since, so it stays
        assertEquals(2, PerformanceProfile.restore(options, changes));
        assertEquals(lines("renderDistance:10", "particles:0", "ao:true", "maxFps:260", "overrideWidth:1280"), options);
    }

    @Test
    public void nothingRememberedChangesNothing() {
        List<String> options = lines("renderDistance:16", "overrideWidth:1280");
        assertEquals(0, PerformanceProfile.restore(options, new ArrayList<PerformanceProfile.Change>()));
        assertEquals(lines("renderDistance:16", "overrideWidth:1280"), options);
    }
}
