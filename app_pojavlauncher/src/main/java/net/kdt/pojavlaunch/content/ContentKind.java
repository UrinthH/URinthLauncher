package net.kdt.pojavlaunch.content;

import net.kdt.pojavlaunch.downloads.DownloadCategory;

import java.io.File;
import java.util.Locale;

/** The three kinds of files an installation keeps in its game folder. Plain Java, so it is unit tested. */
public enum ContentKind {
    MODS("mods", "Mods", "mods", DownloadCategory.MODS, false),
    RESOURCE_PACKS("resourcepacks", "Resource Packs", "resourcepacks", DownloadCategory.RESOURCE_PACKS, true),
    SHADERS("shaders", "Shaders", "shaderpacks", DownloadCategory.SHADERS, true);

    public static final String DISABLED_SUFFIX = ".disabled";

    public final String key;
    public final String label;
    /** Folder inside the installation's game folder. */
    public final String folder;
    public final DownloadCategory category;
    /** Packs may also be unpacked folders; mods are always a single .jar file. */
    public final boolean allowsFolders;

    ContentKind(String key, String label, String folder, DownloadCategory category, boolean allowsFolders) {
        this.key = key;
        this.label = label;
        this.folder = folder;
        this.category = category;
        this.allowsFolders = allowsFolders;
    }

    public static ContentKind fromKey(String key) {
        for (ContentKind kind : values()) if (kind.key.equals(key)) return kind;
        return MODS;
    }

    /** The name without the ".disabled" the launcher adds to switch a file off. */
    public static String activeName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(DISABLED_SUFFIX) ? fileName.substring(0, fileName.length() - DISABLED_SUFFIX.length()) : fileName;
    }

    public static boolean isDisabledName(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(DISABLED_SUFFIX);
    }

    /** True for a file or folder of this kind, switched on or off. */
    public boolean accepts(File file) {
        String name = activeName(file.getName()).toLowerCase(Locale.ROOT);
        if (name.startsWith(".")) return false;
        if (file.isDirectory()) return allowsFolders;
        if (this == MODS) return name.endsWith(".jar");
        return name.endsWith(".zip");
    }
}
