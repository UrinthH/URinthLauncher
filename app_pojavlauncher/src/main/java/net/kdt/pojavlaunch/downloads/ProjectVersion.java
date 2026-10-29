package net.kdt.pojavlaunch.downloads;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** One downloadable release of a project, as the Modrinth {@code version} endpoints describe it. */
public final class ProjectVersion {
    public static final class Dependency {
        public final String projectId, versionId, type;

        Dependency(JsonObject json) {
            projectId = ProjectHit.first(json, "project_id");
            versionId = ProjectHit.first(json, "version_id");
            type = ProjectHit.first(json, "dependency_type");
        }

        public boolean isRequired() {
            return "required".equalsIgnoreCase(type);
        }
    }

    public String id = "", projectId = "", name = "", number = "", type = "", datePublished = "";
    public final List<String> gameVersions = new ArrayList<>();
    public final List<String> loaders = new ArrayList<>();
    public final List<Dependency> dependencies = new ArrayList<>();
    /** The primary file of the release (or its first file). */
    public String url = "", filename = "", sha1 = "";
    public long size;

    public static ProjectVersion fromJson(JsonObject json) {
        ProjectVersion version = new ProjectVersion();
        version.id = ProjectHit.first(json, "id");
        version.projectId = ProjectHit.first(json, "project_id");
        version.name = ProjectHit.first(json, "name");
        version.number = ProjectHit.first(json, "version_number");
        version.type = ProjectHit.first(json, "version_type");
        version.datePublished = ProjectHit.first(json, "date_published");
        strings(version.gameVersions, json, "game_versions");
        strings(version.loaders, json, "loaders");
        if (json.has("dependencies") && json.get("dependencies").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("dependencies")) {
                if (element.isJsonObject()) version.dependencies.add(new Dependency(element.getAsJsonObject()));
            }
        }
        if (json.has("files") && json.get("files").isJsonArray() && json.getAsJsonArray("files").size() > 0) {
            JsonArray files = json.getAsJsonArray("files");
            JsonObject chosen = null;
            for (JsonElement element : files) {
                if (!element.isJsonObject()) continue;
                JsonObject file = element.getAsJsonObject();
                if (chosen == null) chosen = file;
                if (file.has("primary") && file.get("primary").isJsonPrimitive() && file.get("primary").getAsBoolean()) {
                    chosen = file;
                    break;
                }
            }
            if (chosen != null) {
                version.url = ProjectHit.first(chosen, "url");
                version.filename = ProjectHit.first(chosen, "filename");
                version.size = ProjectHit.number(chosen, "size");
                if (chosen.has("hashes") && chosen.get("hashes").isJsonObject()) {
                    version.sha1 = ProjectHit.first(chosen.getAsJsonObject("hashes"), "sha1");
                }
            }
        }
        return version;
    }

    public boolean hasFile() {
        return !url.isEmpty();
    }

    /** "1.21.4" or "1.21 – 1.21.4" style text for the list of game versions. */
    public String gameVersionText() {
        if (gameVersions.isEmpty()) return "";
        if (gameVersions.size() == 1) return gameVersions.get(0);
        return gameVersions.get(0) + " – " + gameVersions.get(gameVersions.size() - 1);
    }

    /** Whether this release suits the profile. Mods also need a matching loader; packs only the game version. */
    public boolean isCompatibleWith(ProfileTarget target, DownloadCategory category) {
        if (!target.gameVersion.isEmpty() && !gameVersions.contains(target.gameVersion)) return false;
        if (category == DownloadCategory.MODS) {
            for (String wanted : target.modLoaders()) {
                if (loaders.contains(wanted)) return true;
            }
            return false;
        }
        return true;
    }

    private static void strings(List<String> target, JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return;
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (element.isJsonPrimitive()) target.add(element.getAsString());
        }
    }
}
