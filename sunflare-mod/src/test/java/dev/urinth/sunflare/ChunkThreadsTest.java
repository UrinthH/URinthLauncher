package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** How many chunk helper threads Sodium is told to start. */
class ChunkThreadsTest {
    @AfterEach
    void reset() {
        ChunkThreads.setSetting(ChunkThreads.AUTO);
    }

    /** What Sodium picks on its own: max(cores / 3, cores - 6), between 1 and 10. */
    private static int sodiums(int cores) {
        return Math.max(1, Math.min(10, Math.max(cores / 3, cores - 6)));
    }

    @Test
    void theChoicesStartWithAutoAndGoUp() {
        assertEquals(ChunkThreads.AUTO, ChunkThreads.CHOICES[0]);
        assertEquals(0, ChunkThreads.AUTO);
        for (int i = 2; i < ChunkThreads.CHOICES.length; i++) {
            assertTrue(ChunkThreads.CHOICES[i] > ChunkThreads.CHOICES[i - 1], "ascending at " + i);
        }
        assertTrue(ChunkThreads.CHOICES[1] >= 1);
    }

    @Test
    void anyNumberBecomesTheClosestChoice() {
        assertEquals(ChunkThreads.AUTO, ChunkThreads.nearest(0));
        assertEquals(ChunkThreads.AUTO, ChunkThreads.nearest(-4));
        assertEquals(2, ChunkThreads.nearest(1));
        assertEquals(2, ChunkThreads.nearest(2));
        assertEquals(3, ChunkThreads.nearest(3));
        assertEquals(4, ChunkThreads.nearest(4));
        assertEquals(4, ChunkThreads.nearest(5), "between 4 and 6 the smaller one");
        assertEquals(6, ChunkThreads.nearest(6));
        assertEquals(6, ChunkThreads.nearest(7));
        assertEquals(6, ChunkThreads.nearest(64));
        for (int choice : ChunkThreads.CHOICES) assertEquals(choice, ChunkThreads.nearest(choice));
    }

    @Test
    void theSettingKeepsOnlyRealChoices() {
        ChunkThreads.setSetting(5);
        assertEquals(4, ChunkThreads.setting());
        ChunkThreads.setSetting(-1);
        assertEquals(ChunkThreads.AUTO, ChunkThreads.setting());
        ChunkThreads.setSetting(6);
        assertEquals(6, ChunkThreads.setting());
    }

    @Test
    void autoGivesAPhoneWithManyCoresOneHelperMoreThanSodiumDoes() {
        assertEquals(2, sodiums(8), "what Sodium picks on an 8-core phone");
        assertEquals(3, ChunkThreads.autoCount(sodiums(8), 8));
        assertEquals(3, ChunkThreads.autoCount(sodiums(6), 6));
        assertEquals(3, ChunkThreads.autoCount(sodiums(7), 7));
    }

    @Test
    void autoNeverGoesBelowSodiumAndLeavesSmallPhonesAlone() {
        assertEquals(1, ChunkThreads.autoCount(sodiums(4), 4));
        assertEquals(1, ChunkThreads.autoCount(sodiums(5), 5), "Sodium picks 1 helper on 5 cores and Auto leaves that alone");
        assertEquals(1, ChunkThreads.autoCount(sodiums(2), 2));
        assertEquals(1, ChunkThreads.autoCount(sodiums(1), 1));
        for (int cores = 1; cores <= 64; cores++) {
            int sodium = sodiums(cores);
            int auto = ChunkThreads.autoCount(sodium, cores);
            assertTrue(auto >= sodium, "never fewer than Sodium at " + cores + " cores");
            assertTrue(auto <= Math.max(sodium, ChunkThreads.AUTO_MINIMUM), "no more than needed at " + cores + " cores");
            assertTrue(auto <= Math.max(1, cores), "never more helpers than cores at " + cores + " cores");
        }
        assertEquals(6, ChunkThreads.autoCount(sodiums(12), 12), "a big PC keeps Sodium's number");
        assertEquals(10, ChunkThreads.autoCount(sodiums(32), 32));
    }

    @Test
    void aFixedNumberIsUsedButNeverMoreThanTheCores() {
        assertEquals(3, ChunkThreads.fixedCount(3, 8));
        assertEquals(6, ChunkThreads.fixedCount(6, 8));
        assertEquals(4, ChunkThreads.fixedCount(6, 4), "a 4-core phone cannot use 6");
        assertEquals(1, ChunkThreads.fixedCount(3, 1));
        assertEquals(1, ChunkThreads.fixedCount(2, 0), "a phone that reports no cores still gets one");
    }

    @Test
    void autoLeavesTheNumberToSodium() {
        assertEquals(0, ChunkThreads.fixedCount(ChunkThreads.AUTO, 8));
        assertEquals(0, ChunkThreads.fixedCount(-3, 8));
    }

    @Test
    void theSettingsFileTextGoesBothWays() {
        assertEquals("auto", ChunkThreads.toName(ChunkThreads.AUTO));
        assertEquals("3", ChunkThreads.toName(3));
        for (int choice : ChunkThreads.CHOICES) assertEquals(choice, ChunkThreads.fromName(ChunkThreads.toName(choice)));
        assertEquals(ChunkThreads.AUTO, ChunkThreads.fromName(null));
        assertEquals(ChunkThreads.AUTO, ChunkThreads.fromName(""));
        assertEquals(ChunkThreads.AUTO, ChunkThreads.fromName("  AUTO "));
        assertEquals(ChunkThreads.AUTO, ChunkThreads.fromName("many"));
        assertEquals(ChunkThreads.AUTO, ChunkThreads.fromName("0"));
        assertEquals(ChunkThreads.AUTO, ChunkThreads.fromName("-2"));
        assertEquals(4, ChunkThreads.fromName(" 4 "));
        assertEquals(4, ChunkThreads.fromName("5"));
        assertEquals(6, ChunkThreads.fromName("99"));
    }

    @Test
    void everyChoiceHasAShortLabel() {
        assertEquals("Auto", ChunkThreads.label(ChunkThreads.AUTO));
        assertEquals("3", ChunkThreads.label(3));
        for (int choice : ChunkThreads.CHOICES) assertTrue(ChunkThreads.label(choice).length() <= 4);
    }
}
