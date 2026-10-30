package net.kdt.pojavlaunch.content;

import java.io.File;

/** One mod, resource pack or shader in an installation's folder. */
public final class ContentItem {
    public final File file;
    public final boolean enabled;
    public final boolean directory;
    public final long size;
    public final long modified;

    public ContentItem(File file, long size) {
        this.file = file;
        this.enabled = !ContentKind.isDisabledName(file.getName());
        this.directory = file.isDirectory();
        this.size = size;
        this.modified = file.lastModified();
    }

    /** The name of the file as the game knows it (without ".disabled"). */
    public String fileName() {
        return ContentKind.activeName(file.getName());
    }

    /** A switched-off folder would still be found by the game, so only single files can be switched. */
    public boolean canSwitch() {
        return !directory;
    }

    /** Key that changes whenever the file does, but not when it is only switched on or off. */
    public String cacheKey() {
        return file.getParent() + "/" + fileName() + "|" + size + "|" + modified;
    }

    /** "812 B", "48 KB", "3.4 MB". */
    public static String sizeText(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return (bytes / 1024) + " KB";
        double megabytes = bytes / (1024.0 * 1024.0);
        return String.format(java.util.Locale.ROOT, megabytes < 10 ? "%.1f MB" : "%.0f MB", megabytes);
    }
}
