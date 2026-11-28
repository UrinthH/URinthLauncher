package net.kdt.pojavlaunch.utils.jre;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ModJvmPropertiesTest {
    @Test
    public void aKnownScreenRateBecomesTheArgument() {
        assertEquals("-Durinth.refreshHz=90", ModJvmProperties.refreshRate(90f));
        assertEquals("-Durinth.refreshHz=60", ModJvmProperties.refreshRate(60f));
        assertEquals("-Durinth.refreshHz=120", ModJvmProperties.refreshRate(120f));
    }

    @Test
    public void ratesThatAreAlmostWholeNumbersAreRounded() {
        assertEquals("-Durinth.refreshHz=90", ModJvmProperties.refreshRate(89.9f));
        assertEquals("-Durinth.refreshHz=60", ModJvmProperties.refreshRate(59.94f));
        assertEquals("-Durinth.refreshHz=90", ModJvmProperties.refreshRate(90.0004f));
    }

    @Test
    public void anUnknownOrUnbelievableRateIsNotPassedOn() {
        assertNull(ModJvmProperties.refreshRate(0f));
        assertNull(ModJvmProperties.refreshRate(-60f));
        assertNull(ModJvmProperties.refreshRate(29.9f));
        assertNull(ModJvmProperties.refreshRate(Float.NaN));
        assertNull(ModJvmProperties.refreshRate(Float.POSITIVE_INFINITY));
        assertNull(ModJvmProperties.refreshRate(5000f));
        assertEquals("-Durinth.refreshHz=30", ModJvmProperties.refreshRate(30f));
        assertEquals("-Durinth.refreshHz=480", ModJvmProperties.refreshRate(480f));
    }

    @Test
    public void thePropertyNameIsTheOneTheModReads() {
        // boost-mod: BoostConfig.SCREEN_HZ_PROPERTY
        assertEquals("urinth.refreshHz", ModJvmProperties.REFRESH_RATE);
    }

    @Test
    public void aPlayersOwnArgumentIsRecognised() {
        assertTrue(ModJvmProperties.names(Arrays.asList("-Xmx2G", "-Durinth.refreshHz=60")));
        assertFalse(ModJvmProperties.names(Arrays.asList("-Xmx2G", "-Durinth.other=1")));
        assertFalse(ModJvmProperties.names(Collections.<String>emptyList()));
        assertFalse(ModJvmProperties.names(Arrays.asList("-Durinth.refreshHz")));
    }
}
