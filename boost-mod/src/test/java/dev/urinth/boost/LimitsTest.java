package dev.urinth.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class LimitsTest {
    @Test
    public void levelZeroTakesNothingAway() {
        Limits l = Limits.forLevel(0);
        assertEquals(Limits.UNLIMITED, l.particlesPerTick);
        assertTrue(Double.isInfinite(l.particleDistSq));
        assertTrue(Double.isInfinite(l.itemDistSq));
        assertTrue(Double.isInfinite(l.standDistSq));
        assertTrue(Double.isInfinite(l.mobDistSq));
        assertTrue(Double.isInfinite(l.nameTagDistSq));
        assertTrue(Double.isInfinite(l.layerDistSq));
    }

    @Test
    public void everyLevelTakesAtLeastAsMuchAsTheOneBelow() {
        for (int level = 1; level <= 3; level++) {
            Limits lower = Limits.forLevel(level - 1);
            Limits l = Limits.forLevel(level);
            assertEquals(level, l.level);
            assertTrue(lower.particlesPerTick == Limits.UNLIMITED || l.particlesPerTick <= lower.particlesPerTick);
            assertTrue(l.particlesPerTick != Limits.UNLIMITED, "level " + level + " has a particle budget");
            assertTrue(l.particleDistSq <= lower.particleDistSq);
            assertTrue(l.itemDistSq <= lower.itemDistSq);
            assertTrue(l.standDistSq <= lower.standDistSq);
            assertTrue(l.mobDistSq <= lower.mobDistSq);
            assertTrue(l.nameTagDistSq <= lower.nameTagDistSq);
            assertTrue(l.layerDistSq <= lower.layerDistSq);
        }
    }

    @Test
    public void levelsOutsideTheRangeAreClamped() {
        assertEquals(0, Limits.forLevel(-4).level);
        assertEquals(3, Limits.forLevel(9).level);
    }

    @Test
    public void distancesAreStoredSquaredAndCanBeShownAsBlocks() {
        Limits l = Limits.forLevel(3);
        assertEquals(20.0, Limits.blocks(l.particleDistSq), 1e-9);
        assertEquals(14.0, Limits.blocks(l.itemDistSq), 1e-9);
        assertTrue(Double.isInfinite(Limits.blocks(Double.POSITIVE_INFINITY)));
    }

    @Test
    public void noLimitIsSoLargeThatAServerNeverReachesIt() {
        // A server sends entities within roughly 32 to 64 blocks, so a limit above that would never do anything.
        for (int level = 1; level <= 3; level++) {
            Limits l = Limits.forLevel(level);
            assertTrue(Limits.blocks(l.itemDistSq) <= 64);
            assertTrue(Limits.blocks(l.standDistSq) <= 64);
            assertTrue(Limits.blocks(l.nameTagDistSq) <= 64);
            assertTrue(Limits.blocks(l.particleDistSq) <= 64);
        }
    }


    @Test
    public void thePlayerLimitExistsOnlyAtTheStrongLevelsAndGetsTighter() {
        assertEquals(Limits.UNLIMITED, Limits.forLevel(0).maxPlayers);
        assertEquals(Limits.UNLIMITED, Limits.forLevel(1).maxPlayers);
        assertTrue(Limits.forLevel(2).maxPlayers > Limits.forLevel(3).maxPlayers);
        assertTrue(Limits.forLevel(3).maxPlayers >= BoostConfig.MIN_PLAYERS);
        assertTrue(Limits.forLevel(2).maxPlayers <= BoostConfig.MAX_PLAYERS);
    }
}
