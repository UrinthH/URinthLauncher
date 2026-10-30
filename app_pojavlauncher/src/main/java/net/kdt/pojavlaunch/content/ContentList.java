package net.kdt.pojavlaunch.content;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Reads what an installation has in its mods, resourcepacks or shaderpacks folder. */
public final class ContentList {
    private ContentList() {}

    public static File folderOf(File gameDirectory, ContentKind kind) {
        return new File(gameDirectory, kind.folder);
    }

    /** Sorted by name, ignoring case and the ".disabled" ending. A missing folder is an empty list. */
    public static List<ContentItem> scan(File gameDirectory, ContentKind kind) {
        File[] files = folderOf(gameDirectory, kind).listFiles();
        List<ContentItem> items = new ArrayList<>();
        if (files == null) return items;
        for (File file : files) {
            if (!kind.accepts(file)) continue;
            items.add(new ContentItem(file, file.isDirectory() ? 0 : file.length()));
        }
        Collections.sort(items, (a, b) -> a.fileName().toLowerCase(Locale.ROOT).compareTo(b.fileName().toLowerCase(Locale.ROOT)));
        return items;
    }

    public static int count(File gameDirectory, ContentKind kind) {
        File[] files = folderOf(gameDirectory, kind).listFiles();
        int count = 0;
        if (files == null) return 0;
        for (File file : files) if (kind.accepts(file)) count++;
        return count;
    }
}
