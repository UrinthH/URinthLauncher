package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The rule for skipping leaf faces that are buried inside a tree. */
class LeafCullTest {
    @Test
    void nothingIsSkippedWhenOff() {
        assertFalse(LeafCull.hidden(0, n -> true));
    }

    @Test
    void aFaceIsSkippedOnlyWhenEveryBlockInFrontOfItUpToTheDepthIsLeaves() {
        // leaves at 1 and 2 steps, air at 3
        java.util.function.IntPredicate twoLeaves = n -> n <= 2;
        assertTrue(LeafCull.hidden(1, twoLeaves));
        assertTrue(LeafCull.hidden(2, twoLeaves));
        assertFalse(LeafCull.hidden(3, twoLeaves), "the third block in front is air, so the face is on the outside");
        assertFalse(LeafCull.hidden(1, n -> false), "a face next to air always stays");
    }

    @Test
    void depthIsClampedAndTheOutsideLayersAlwaysStay() {
        LeafCull.setDepth(99);
        assertEquals(LeafCull.MAX_DEPTH, LeafCull.depth());
        LeafCull.setDepth(-4);
        assertEquals(0, LeafCull.depth());
        LeafCull.setDepth(LeafCull.DEFAULT_DEPTH);
        // a leaf block with air or a log in front of it keeps all its faces however deep the setting
        assertFalse(LeafCull.hidden(LeafCull.MAX_DEPTH, n -> n != 1));
    }
}
