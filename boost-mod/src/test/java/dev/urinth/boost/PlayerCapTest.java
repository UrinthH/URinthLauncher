package dev.urinth.boost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PlayerCapTest {
    private static int[] ids(int n) {
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) ids[i] = 100 + i;
        return ids;
    }

    /** Player 100 is at distance 1, player 101 at 2, and so on. */
    private static double[] inOrder(int n) {
        double[] d = new double[n];
        for (int i = 0; i < n; i++) d[i] = (i + 1) * (i + 1);
        return d;
    }

    @Test
    public void everybodyIsDrawnWhenThereAreNotMoreThanTheCap() {
        PlayerCap cap = new PlayerCap();
        cap.update(5, ids(5), inOrder(5));
        assertEquals(0, cap.hiddenCount());
        cap.update(5, ids(3), inOrder(3));
        assertEquals(0, cap.hiddenCount());
        cap.update(5, new int[0], new double[0]);
        assertEquals(0, cap.hiddenCount());
    }

    @Test
    public void onlyTheNearestAreDrawn() {
        PlayerCap cap = new PlayerCap();
        cap.update(4, ids(10), inOrder(10));
        for (int i = 0; i < 4; i++) assertFalse(cap.isHidden(100 + i), "near player " + i);
        for (int i = 4; i < 10; i++) assertTrue(cap.isHidden(100 + i), "far player " + i);
        assertEquals(6, cap.hiddenCount());
    }

    @Test
    public void theOrderOfTheListDoesNotMatter() {
        PlayerCap cap = new PlayerCap();
        int[] ids = {105, 101, 109, 100, 107, 102, 108, 103, 106, 104};
        double[] d = new double[ids.length];
        for (int i = 0; i < ids.length; i++) d[i] = (ids[i] - 99) * (ids[i] - 99);
        cap.update(3, ids, d);
        assertFalse(cap.isHidden(100));
        assertFalse(cap.isHidden(101));
        assertFalse(cap.isHidden(102));
        assertTrue(cap.isHidden(103));
        assertTrue(cap.isHidden(109));
    }

    @Test
    public void aPlayerDrawnBeforeStaysDrawnForAFewPlacesBeyondTheLimit() {
        PlayerCap cap = new PlayerCap();
        cap.update(4, ids(10), inOrder(10)); // 100..103 drawn
        // Player 103 moves away to sixth place; 104 moves up to fourth and 105 stays sixth-ish.
        double[] d = inOrder(10);
        d[3] = 40;
        cap.update(4, ids(10), d);
        assertFalse(cap.isHidden(104), "among the nearest four now");
        assertFalse(cap.isHidden(103), "was drawn, only two places beyond the limit");
        assertTrue(cap.isHidden(105), "was never drawn, so no grace");
    }

    @Test
    public void aPlayerFarBeyondTheLimitLosesTheGrace() {
        PlayerCap cap = new PlayerCap();
        cap.update(4, ids(10), inOrder(10));
        double[] d = inOrder(10);
        d[0] = 900; // player 100 ran far away
        cap.update(4, ids(10), d);
        assertTrue(cap.isHidden(100));
    }

    @Test
    public void tiesAtTheLimitAreAllDrawn() {
        PlayerCap cap = new PlayerCap();
        double[] d = {4, 4, 4, 4, 4, 100};
        cap.update(2, ids(6), d);
        for (int i = 0; i < 5; i++) assertFalse(cap.isHidden(100 + i));
        assertTrue(cap.isHidden(105));
    }

    @Test
    public void clearDrawsEverybodyAgain() {
        PlayerCap cap = new PlayerCap();
        cap.update(2, ids(8), inOrder(8));
        assertTrue(cap.hiddenCount() > 0);
        cap.clear();
        assertEquals(0, cap.hiddenCount());
        assertFalse(cap.isHidden(107));
    }

    @Test
    public void aCapOfZeroOrLessMeansNoCap() {
        PlayerCap cap = new PlayerCap();
        cap.update(0, ids(30), inOrder(30));
        assertEquals(0, cap.hiddenCount());
        cap.update(-1, ids(30), inOrder(30));
        assertEquals(0, cap.hiddenCount());
    }

    @Test
    public void aBadDistanceNeverBreaksIt() {
        PlayerCap cap = new PlayerCap();
        double[] d = {1, Double.NaN, 3, 4, 5, 6};
        cap.update(2, ids(6), d);
        assertFalse(cap.isHidden(100));
        assertTrue(cap.isHidden(101), "unknown distance counts as far");
    }

    @Test
    public void onlyTheFirstCountEntriesAreUsed() {
        PlayerCap cap = new PlayerCap();
        int[] ids = new int[64];
        double[] d = new double[64];
        for (int i = 0; i < 6; i++) {
            ids[i] = 100 + i;
            d[i] = (i + 1) * (i + 1);
        }
        for (int i = 6; i < 64; i++) { // leftovers of an earlier, longer list: they must be ignored
            ids[i] = 900 + i;
            d[i] = 0;
        }
        cap.update(2, ids, d, 6);
        assertEquals(4, cap.hiddenCount());
        assertFalse(cap.isHidden(900 + 10));
        assertTrue(cap.isHidden(105));
    }

    @Test
    public void aBigCrowdIsHandled() {
        PlayerCap cap = new PlayerCap();
        int n = 700;
        double[] d = new double[n];
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = i * 3 + 7;
            d[i] = (n - i) * 1.5; // the last ones are the nearest
        }
        cap.update(20, ids, d);
        assertEquals(n - 20, cap.hiddenCount());
        assertFalse(cap.isHidden(ids[n - 1]));
        assertTrue(cap.isHidden(ids[0]));
    }
}
