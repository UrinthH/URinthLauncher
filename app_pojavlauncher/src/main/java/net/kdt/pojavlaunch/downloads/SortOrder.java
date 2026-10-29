package net.kdt.pojavlaunch.downloads;

/** The "Sort by" choices, with the {@code index} value Modrinth's search expects. */
public enum SortOrder {
    RELEVANCE("relevance", "Relevance"),
    DOWNLOADS("downloads", "Download count"),
    FOLLOWS("follows", "Follow count"),
    NEWEST("newest", "Recently published"),
    UPDATED("updated", "Recently updated");

    public final String index;
    public final String label;

    SortOrder(String index, String label) {
        this.index = index;
        this.label = label;
    }
}
