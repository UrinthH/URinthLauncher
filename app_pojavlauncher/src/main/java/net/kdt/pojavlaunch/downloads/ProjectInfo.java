package net.kdt.pojavlaunch.downloads;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The full page of one project (name, long description, links), from Modrinth's project endpoint. */
public final class ProjectInfo {
    public String id = "", slug = "", type = "", title = "", description = "", body = "", iconUrl = "";
    public String updated = "", published = "", clientSide = "", serverSide = "", licenseName = "";
    public String sourceUrl = "", issuesUrl = "", wikiUrl = "", discordUrl = "";
    public long downloads, followers;
    public final List<String> categories = new ArrayList<>();
    public final List<String> loaders = new ArrayList<>();
    public final List<String> gameVersions = new ArrayList<>();

    public static ProjectInfo parse(String body) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) throw new IOException("Unexpected answer from Modrinth");
            return fromJson(root.getAsJsonObject());
        } catch (JsonSyntaxException e) {
            throw new IOException("Modrinth sent something that is not a project", e);
        }
    }

    public static ProjectInfo fromJson(JsonObject json) {
        ProjectInfo info = new ProjectInfo();
        info.id = ProjectHit.first(json, "id");
        info.slug = ProjectHit.first(json, "slug");
        info.type = ProjectHit.first(json, "project_type");
        info.title = ProjectHit.first(json, "title", "name");
        info.description = ProjectHit.first(json, "description", "summary");
        info.body = ProjectHit.first(json, "body");
        info.iconUrl = ProjectHit.first(json, "icon_url");
        info.updated = ProjectHit.first(json, "updated");
        info.published = ProjectHit.first(json, "published");
        info.clientSide = ProjectHit.first(json, "client_side");
        info.serverSide = ProjectHit.first(json, "server_side");
        info.sourceUrl = ProjectHit.first(json, "source_url");
        info.issuesUrl = ProjectHit.first(json, "issues_url");
        info.wikiUrl = ProjectHit.first(json, "wiki_url");
        info.discordUrl = ProjectHit.first(json, "discord_url");
        info.downloads = ProjectHit.number(json, "downloads");
        info.followers = ProjectHit.number(json, "followers");
        if (json.has("license") && json.get("license").isJsonObject()) {
            JsonObject license = json.getAsJsonObject("license");
            info.licenseName = ProjectHit.first(license, "name", "id");
        }
        strings(info.categories, json, "categories");
        strings(info.loaders, json, "loaders");
        strings(info.gameVersions, json, "game_versions");
        return info;
    }

    /** Categories first, then loaders, the way the cards list them. */
    public List<String> orderedTags() {
        List<String> tags = new ArrayList<>();
        for (String category : categories) if (!Formatters.isLoader(category)) tags.add(category);
        for (String loader : loaders) if (!tags.contains(loader)) tags.add(loader);
        return tags;
    }

    public String environment() {
        return Formatters.environmentLabel(clientSide, serverSide);
    }

    /** The project's page on the Modrinth site, for "open in browser". */
    public String webAddress() {
        String path;
        switch (type) {
            case "resourcepack": path = "resourcepack"; break;
            case "shader": path = "shader"; break;
            case "modpack": path = "modpack"; break;
            case "plugin": path = "plugin"; break;
            case "datapack": path = "datapack"; break;
            default: path = "mod";
        }
        String name = slug.isEmpty() ? id : slug;
        return "https://modrinth.com/" + path + "/" + SearchQuery.encode(name);
    }

    private static void strings(List<String> target, JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return;
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (element.isJsonPrimitive()) target.add(element.getAsString());
        }
    }
}
