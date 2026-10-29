package net.kdt.pojavlaunch.downloads;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** One search result: what a card on the Downloads page shows. */
public final class ProjectHit {
    public String id = "", slug = "", type = "", title = "", author = "", description = "";
    public String iconUrl = "", dateModified = "", clientSide = "", serverSide = "";
    /** Address of a listed server, when the search result carries one. */
    public String serverAddress = "";
    public long downloads, follows;
    /** Categories and loaders as Modrinth lists them for the card. */
    public final List<String> tags = new ArrayList<>();
    public final List<String> gameVersions = new ArrayList<>();

    /** Reads one entry of the search {@code hits} array (v2 names; the v3 names are accepted too). */
    public static ProjectHit fromJson(JsonObject json) {
        ProjectHit hit = new ProjectHit();
        hit.id = first(json, "project_id", "id");
        hit.slug = first(json, "slug");
        hit.type = first(json, "project_type");
        if (hit.type.isEmpty() && json.has("project_types") && json.get("project_types").isJsonArray()
                && json.getAsJsonArray("project_types").size() > 0) {
            hit.type = json.getAsJsonArray("project_types").get(0).getAsString();
        }
        hit.title = first(json, "title", "name");
        if (hit.title.isEmpty()) hit.title = "Unnamed project";
        hit.author = first(json, "author");
        hit.description = first(json, "description", "summary");
        hit.iconUrl = first(json, "icon_url");
        hit.dateModified = first(json, "date_modified");
        hit.clientSide = first(json, "client_side");
        hit.serverSide = first(json, "server_side");
        hit.downloads = number(json, "downloads");
        hit.follows = number(json, "follows");
        addAll(hit.tags, json, "display_categories");
        if (hit.tags.isEmpty()) addAll(hit.tags, json, "categories");
        addAll(hit.gameVersions, json, "versions");
        if (json.has("minecraft_java_server") && json.get("minecraft_java_server").isJsonObject()) {
            hit.serverAddress = findAddress(json.getAsJsonObject("minecraft_java_server"), 0);
        }
        return hit;
    }

    /** Categories first, then loaders, so the card reads like Modrinth's: "Library", then "Fabric". */
    public List<String> orderedTags() {
        List<String> categories = new ArrayList<>();
        List<String> loaders = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null || tag.isEmpty()) continue;
            (Formatters.isLoader(tag) ? loaders : categories).add(tag);
        }
        categories.addAll(loaders);
        return categories;
    }

    public String environment() {
        return Formatters.environmentLabel(clientSide, serverSide);
    }

    private static String findAddress(JsonObject object, int depth) {
        if (depth > 3) return "";
        for (String key : new String[]{"address", "ip"}) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                String value = object.get(key).getAsString();
                if (!value.isEmpty()) return value;
            }
        }
        for (String key : object.keySet()) {
            JsonElement child = object.get(key);
            if (child.isJsonObject()) {
                String found = findAddress(child.getAsJsonObject(), depth + 1);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    static String first(JsonObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key) && json.get(key).isJsonPrimitive()) {
                String value = json.get(key).getAsString();
                if (value != null && !value.isEmpty()) return value;
            }
        }
        return "";
    }

    static long number(JsonObject json, String key) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsLong() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void addAll(List<String> target, JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return;
        JsonArray array = json.getAsJsonArray(key);
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) target.add(element.getAsString());
        }
    }
}
