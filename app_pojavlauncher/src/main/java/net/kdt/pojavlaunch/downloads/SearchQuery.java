package net.kdt.pojavlaunch.downloads;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One search on the Downloads page: what to look for and how, turned into a Modrinth API address.
 * Pure Java; the address building is what the unit tests check, because it cannot be tried against
 * the real service from a build machine.
 */
public final class SearchQuery {
    public static final String API_V2 = "https://api.modrinth.com/v2";
    public static final String API_V3 = "https://api.modrinth.com/v3";
    /** Largest page Modrinth allows. */
    public static final int MAX_LIMIT = 100;

    private static final String[] MOD_LOADERS = {"fabric", "forge", "quilt", "neoforge", "liteloader", "modloader", "rift"};
    private static final String[] PLUGIN_LOADERS = {"bukkit", "spigot", "paper", "purpur", "folia", "sponge", "velocity", "bungeecord", "waterfall"};

    public final DownloadCategory category;
    public String text = "";
    public SortOrder sort = SortOrder.RELEVANCE;
    public int pageSize = 20;
    /** Zero based. */
    public int page = 0;
    /** Minecraft version to restrict to, or null for any. */
    public String gameVersion;
    /** One loader (Modrinth's name, lower case) to restrict to, or null for any. */
    public String loader;
    /** {@code "client"}, {@code "server"} or null for any. */
    public String environment;

    public SearchQuery(DownloadCategory category) {
        this.category = category;
    }

    /** A copy to change without touching this one. */
    public SearchQuery copy() {
        SearchQuery copy = new SearchQuery(category);
        copy.text = text;
        copy.sort = sort;
        copy.pageSize = pageSize;
        copy.page = page;
        copy.gameVersion = gameVersion;
        copy.loader = loader;
        copy.environment = environment;
        return copy;
    }

    public boolean usesV3() {
        return category == DownloadCategory.SERVERS;
    }

    /** The search address for the current settings. */
    public String toUrl() {
        return toUrl(usesV3() ? API_V3 : API_V2);
    }

    /** The search address under another API root (for example a local test server). */
    public String toUrl(String apiRoot) {
        int limit = Math.max(1, Math.min(pageSize, MAX_LIMIT));
        int offset = Math.max(0, page) * limit;
        StringBuilder url = new StringBuilder(apiRoot).append("/search?query=").append(encode(text == null ? "" : text.trim()));
        url.append("&index=").append(sort.index);
        url.append("&limit=").append(limit);
        url.append("&offset=").append(offset);
        if (usesV3()) {
            url.append("&new_filters=").append(encode("project_types = \"minecraft_java_server\""));
        } else {
            url.append("&facets=").append(encode(facets()));
        }
        return url.toString();
    }

    /** The {@code facets} value: an AND of ORs, as Modrinth defines it. */
    String facets() {
        List<List<String>> groups = new ArrayList<>();
        switch (category) {
            case MODS:
                groups.add(list("project_type:mod"));
                groups.add(loader == null ? prefixed("categories:", MOD_LOADERS) : list("categories:" + loader));
                break;
            case RESOURCE_PACKS:
                groups.add(list("project_type:resourcepack"));
                break;
            case DATA_PACKS:
                groups.add(list("categories:datapack"));
                break;
            case SHADERS:
                groups.add(list("project_type:shader"));
                if (loader != null) groups.add(list("categories:" + loader));
                break;
            case MODPACKS:
                groups.add(list("project_type:modpack"));
                if (loader != null) groups.add(list("categories:" + loader));
                break;
            case PLUGINS:
                groups.add(loader == null ? prefixed("categories:", PLUGIN_LOADERS) : list("categories:" + loader));
                break;
            default:
                break;
        }
        if (gameVersion != null && !gameVersion.isEmpty()) groups.add(list("versions:" + gameVersion));
        if (category.hasEnvironmentFilter && environment != null) {
            if ("client".equals(environment)) groups.add(list("client_side:required", "client_side:optional"));
            else if ("server".equals(environment)) groups.add(list("server_side:required", "server_side:optional"));
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) json.append(',');
            json.append('[');
            List<String> group = groups.get(i);
            for (int j = 0; j < group.size(); j++) {
                if (j > 0) json.append(',');
                json.append('"').append(escape(group.get(j))).append('"');
            }
            json.append(']');
        }
        return json.append(']').toString();
    }

    private static List<String> list(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) result.add(value);
        return result;
    }

    private static List<String> prefixed(String prefix, String[] values) {
        List<String> result = new ArrayList<>();
        for (String value : values) result.add(prefix + value);
        return result;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 always exists
        }
    }

    /** Short description of the active filters, for the status line. */
    public String describeFilters() {
        List<String> parts = new ArrayList<>();
        if (gameVersion != null && !gameVersion.isEmpty()) parts.add("Minecraft " + gameVersion);
        if (loader != null) parts.add(Formatters.tagLabel(loader));
        if (category.hasEnvironmentFilter && environment != null) parts.add(environment.substring(0, 1).toUpperCase(Locale.ROOT) + environment.substring(1));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) text.append(i == 0 ? "" : " · ").append(parts.get(i));
        return text.toString();
    }
}
