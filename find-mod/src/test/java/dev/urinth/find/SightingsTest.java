package dev.urinth.find;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SightingsTest {
    private static Located at(String name, double x) {
        return new Located(name, x, 64, -20, "minecraft:overworld");
    }

    @Test
    void aSightingComesBackWithItsAge() {
        Sightings sightings = new Sightings();
        sightings.record(at("Notkiahh", 500), 1_000);
        List<Sightings.Remembered> all = sightings.all(253_000);
        assertEquals(1, all.size());
        assertEquals("Notkiahh", all.get(0).at().name());
        assertEquals(500.0, all.get(0).at().x(), 0.0);
        assertEquals(252_000L, all.get(0).ageMs());
    }

    @Test
    void aNewerSightingReplacesTheOlderOne() {
        Sightings sightings = new Sightings();
        sightings.record(at("Notkiahh", 500), 1_000);
        sightings.record(at("Notkiahh", 900), 5_000);
        List<Sightings.Remembered> all = sightings.all(6_000);
        assertEquals(1, all.size());
        assertEquals(900.0, all.get(0).at().x(), 0.0);
        assertEquals(1_000L, all.get(0).ageMs());
    }

    @Test
    void everyPlayerIsRememberedSeparately() {
        Sightings sightings = new Sightings();
        sightings.record(at("Notkiahh", 1), 0);
        sightings.record(at("Nora", 2), 0);
        assertEquals(2, sightings.size());
    }

    @Test
    void theAgeIsNeverNegative() {
        Sightings sightings = new Sightings();
        sightings.record(at("Notkiahh", 1), 5_000);
        assertEquals(0L, sightings.all(1_000).get(0).ageMs());
    }

    @Test
    void forgettingEmptiesIt() {
        Sightings sightings = new Sightings();
        sightings.record(at("Notkiahh", 1), 0);
        sightings.clear();
        assertEquals(0, sightings.size());
        assertTrue(sightings.all(10).isEmpty());
    }

    @Test
    void thePlayerSeenLongestAgoIsForgottenFirst() {
        Sightings sightings = new Sightings();
        for (int i = 0; i < Sightings.MAX + 5; i++) sightings.record(at("P" + i, i), i);
        assertEquals(Sightings.MAX, sightings.size());
        List<Sightings.Remembered> all = sightings.all(1_000_000);
        assertEquals("P5", all.get(0).at().name());
        assertEquals("P" + (Sightings.MAX + 4), all.get(all.size() - 1).at().name());
    }

    @Test
    void seeingAnOldPlayerAgainKeepsThemFromBeingForgotten() {
        Sightings sightings = new Sightings();
        for (int i = 0; i < Sightings.MAX; i++) sightings.record(at("P" + i, i), i);
        sightings.record(at("P0", 99), 10_000);
        sightings.record(at("New", 1), 10_001);
        boolean p0 = false;
        boolean p1 = false;
        for (Sightings.Remembered r : sightings.all(20_000)) {
            if (r.at().name().equals("P0")) p0 = true;
            if (r.at().name().equals("P1")) p1 = true;
        }
        assertTrue(p0);
        assertTrue(!p1);
    }

    @Test
    void nothingUsefulIsNotRemembered() {
        Sightings sightings = new Sightings();
        sightings.record(null, 0);
        sightings.record(at("", 1), 0);
        assertEquals(0, sightings.size());
    }
}
