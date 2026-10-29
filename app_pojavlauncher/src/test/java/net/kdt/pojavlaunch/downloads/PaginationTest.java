package net.kdt.pojavlaunch.downloads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PaginationTest {
    private static final int GAP = Pagination.GAP;

    @Test
    public void pageCountRoundsUp() {
        assertEquals(0, Pagination.pageCount(0, 20));
        assertEquals(1, Pagination.pageCount(1, 20));
        assertEquals(1, Pagination.pageCount(20, 20));
        assertEquals(2, Pagination.pageCount(21, 20));
        assertEquals(3889, Pagination.pageCount(77_773, 20));
        assertEquals(0, Pagination.pageCount(100, 0));
    }

    @Test
    public void firstPageLooksLikeModrinths() {
        assertEquals(Arrays.asList(1, 2, GAP, 3889), Pagination.items(0, 3889));
    }

    @Test
    public void middleShowsNeighboursAndBothEnds() {
        assertEquals(Arrays.asList(1, 2, GAP, 49, 50, 51, GAP, 3889), Pagination.items(49, 3889));
    }

    @Test
    public void aGapNeverHidesOnlyOnePage() {
        // pages 1, 2, [3 would be a gap of one], 4 5 6
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, GAP, 100), Pagination.items(4, 100));
        assertEquals(Arrays.asList(1, 2, 3, 4), Pagination.items(2, 4));
    }

    @Test
    public void lastPageKeepsTheEndVisible() {
        assertEquals(Arrays.asList(1, 2, GAP, 3888, 3889), Pagination.items(3888, 3889));
    }

    @Test
    public void smallAndEmptyCounts() {
        assertTrue(Pagination.items(0, 0).isEmpty());
        assertEquals(Arrays.asList(1), Pagination.items(0, 1));
        assertEquals(Arrays.asList(1, 2), Pagination.items(0, 2));
        assertEquals(Arrays.asList(1, 2, 3), Pagination.items(1, 3));
    }

    @Test
    public void aCurrentPageOutsideTheRangeIsClamped() {
        List<Integer> beyond = Pagination.items(99, 5);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), beyond);
        assertEquals(Pagination.items(0, 5), Pagination.items(-3, 5));
        assertEquals(Arrays.asList(1, 2, GAP, 5), Pagination.items(-3, 5));
    }
}
