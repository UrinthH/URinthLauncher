package net.kdt.pojavlaunch.downloads;

import java.util.ArrayList;
import java.util.List;

/** Which page numbers the page bar shows, like Modrinth's "1 2 … 3889 >". */
public final class Pagination {
    /** Marks a gap ("…") in the list from {@link #items}. */
    public static final int GAP = 0;

    private Pagination() {}

    public static int pageCount(int totalHits, int pageSize) {
        if (totalHits <= 0 || pageSize <= 0) return 0;
        return (totalHits + pageSize - 1) / pageSize;
    }

    /**
     * Page numbers (1 based) to show, with {@link #GAP} for a skipped stretch. Always the first
     * and the last page and the ones next to the current page; a single skipped number is shown
     * instead of a gap, since a gap that hides one page is no shorter than the page itself.
     *
     * @param current 0 based current page
     */
    public static List<Integer> items(int current, int pageCount) {
        List<Integer> result = new ArrayList<>();
        if (pageCount <= 0) return result;
        int page = Math.max(0, Math.min(current, pageCount - 1)) + 1;
        boolean[] shown = new boolean[pageCount + 2];
        int[] wanted = {1, 2, page - 1, page, page + 1, pageCount};
        for (int number : wanted) {
            if (number >= 1 && number <= pageCount) shown[number] = true;
        }
        int previous = 0;
        for (int number = 1; number <= pageCount; number++) {
            if (!shown[number]) continue;
            if (previous != 0 && number - previous == 2) result.add(previous + 1);
            else if (previous != 0 && number - previous > 2) result.add(GAP);
            result.add(number);
            previous = number;
        }
        return result;
    }
}
