package net.kdt.pojavlaunch.downloads;

/**
 * The tabs of the Downloads page, in the order Modrinth shows them.
 * Plain Java (no Android classes) so the search logic around it is covered by JVM unit tests.
 */
public enum DownloadCategory {
    MODS("mods", "Mods", "mods", true, true),
    RESOURCE_PACKS("resourcepacks", "Resource Packs", "resourcepacks", false, false),
    DATA_PACKS("datapacks", "Data Packs", null, false, false),
    SHADERS("shaders", "Shaders", "shaderpacks", true, false),
    MODPACKS("modpacks", "Modpacks", null, true, true),
    PLUGINS("plugins", "Plugins", "plugins", true, true),
    SERVERS("servers", "Servers", null, false, false);

    /** Stable name for saving and for fragment arguments. */
    public final String key;
    public final String label;
    /** Folder inside the game directory the files are saved to, or null when they are not plain files. */
    public final String installFolder;
    /** Whether the loader choice in the filter makes sense for this tab. */
    public final boolean hasLoaderFilter;
    /** Whether "client / server" makes sense for this tab. */
    public final boolean hasEnvironmentFilter;

    DownloadCategory(String key, String label, String installFolder, boolean hasLoaderFilter, boolean hasEnvironmentFilter) {
        this.key = key;
        this.label = label;
        this.installFolder = installFolder;
        this.hasLoaderFilter = hasLoaderFilter;
        this.hasEnvironmentFilter = hasEnvironmentFilter;
    }

    /** The category saved under {@code key}; {@link #MODS} when it is unknown. */
    public static DownloadCategory fromKey(String key) {
        for (DownloadCategory category : values()) {
            if (category.key.equals(key)) return category;
        }
        return MODS;
    }

    /** Loaders that can be picked in the filter for this tab, as Modrinth names them (lower case). */
    public String[] loaderChoices() {
        switch (this) {
            case MODS:
            case MODPACKS:
                return new String[]{"fabric", "forge", "neoforge", "quilt"};
            case SHADERS:
                return new String[]{"iris", "optifine", "canvas", "vanilla"};
            case PLUGINS:
                return new String[]{"paper", "spigot", "bukkit", "purpur", "folia", "velocity", "sponge"};
            default:
                return new String[0];
        }
    }
}
