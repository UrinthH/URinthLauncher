package net.kdt.pojavlaunch.downloads;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to Modrinth's public API. Blocking calls: run them off the main thread. The address of
 * the API is a parameter so the unit tests can point it at a small local server.
 */
public class ModrinthClient {
    public static final String USER_AGENT = "UrinthH/URinthLauncher/1.0 (Android; github.com/UrinthH/URinthLauncher)";

    private final String apiBase;

    public ModrinthClient() {
        this(SearchQuery.API_V2);
    }

    public ModrinthClient(String apiBase) {
        this.apiBase = apiBase;
    }

    public SearchPage search(SearchQuery query) throws IOException {
        return SearchPage.parse(get(query.toUrl(searchRoot(query.usesV3()))));
    }

    /** The v2 or v3 root next to the API address this client was made with. */
    String searchRoot(boolean v3) {
        String root = apiBase.endsWith("/v2") ? apiBase.substring(0, apiBase.length() - 3) : apiBase;
        if (root.equals(apiBase) && !v3) return apiBase; // a bare test server: v2 calls go to its root
        return root + (v3 ? "/v3" : "/v2");
    }

    /**
     * Releases of a project, newest first, optionally only those for given loaders and game versions
     * (Modrinth does the filtering).
     */
    public List<ProjectVersion> projectVersions(String projectId, String[] loaders, String[] gameVersions) throws IOException {
        StringBuilder url = new StringBuilder(apiBase).append("/project/").append(SearchQuery.encode(projectId)).append("/version");
        char separator = '?';
        if (loaders != null && loaders.length > 0) {
            url.append(separator).append("loaders=").append(SearchQuery.encode(jsonArray(loaders)));
            separator = '&';
        }
        if (gameVersions != null && gameVersions.length > 0) {
            url.append(separator).append("game_versions=").append(SearchQuery.encode(jsonArray(gameVersions)));
        }
        List<ProjectVersion> versions = new ArrayList<>();
        for (JsonElement element : parseArray(get(url.toString()))) {
            if (!element.isJsonObject()) continue;
            ProjectVersion version = ProjectVersion.fromJson(element.getAsJsonObject());
            if (version.hasFile()) versions.add(version);
        }
        return versions;
    }

    public ProjectVersion version(String versionId) throws IOException {
        try {
            JsonElement element = JsonParser.parseString(get(apiBase + "/version/" + SearchQuery.encode(versionId)));
            if (!element.isJsonObject()) throw new IOException("Unexpected answer for version " + versionId);
            return ProjectVersion.fromJson(element.getAsJsonObject());
        } catch (JsonSyntaxException e) {
            throw new IOException("Unreadable answer for version " + versionId, e);
        }
    }

    /**
     * Identifies files by SHA-1: the answer maps each known hash (lower case) to the release it belongs
     * to. Hashes Modrinth does not know are missing from the answer.
     */
    public Map<String, ProjectVersion> versionsByHashes(Collection<String> sha1Hashes) throws IOException {
        Map<String, ProjectVersion> result = new HashMap<>();
        if (sha1Hashes.isEmpty()) return result;
        StringBuilder body = new StringBuilder("{\"algorithm\":\"sha1\",\"hashes\":[");
        boolean first = true;
        for (String hash : sha1Hashes) {
            if (!first) body.append(',');
            first = false;
            body.append('"').append(hash.replace("\\", "").replace("\"", "")).append('"');
        }
        body.append("]}");
        try {
            JsonElement root = JsonParser.parseString(post(apiBase + "/version_files", body.toString()));
            if (!root.isJsonObject()) throw new IOException("Unexpected answer from Modrinth");
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    result.put(entry.getKey().toLowerCase(java.util.Locale.ROOT), ProjectVersion.fromJson(entry.getValue().getAsJsonObject()));
                }
            }
        } catch (JsonSyntaxException e) {
            throw new IOException("Modrinth sent something unreadable", e);
        }
        return result;
    }

    /** Full release versions of Minecraft, newest first, for the filter's version list. */
    public List<String> releaseGameVersions() throws IOException {
        List<String> versions = new ArrayList<>();
        for (JsonElement element : parseArray(get(apiBase + "/tag/game_version"))) {
            if (!element.isJsonObject()) continue;
            JsonObject json = element.getAsJsonObject();
            if ("release".equals(ProjectHit.first(json, "version_type"))) {
                String version = ProjectHit.first(json, "version");
                if (!version.isEmpty()) versions.add(version);
            }
        }
        return versions;
    }

    static String jsonArray(String[] values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) json.append(',');
            json.append('"').append(values[i].replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return json.append(']').toString();
    }

    private static JsonArray parseArray(String body) throws IOException {
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonArray()) throw new IOException("Unexpected answer from Modrinth");
            return element.getAsJsonArray();
        } catch (JsonSyntaxException e) {
            throw new IOException("Modrinth sent something unreadable", e);
        }
    }

    /** POST a JSON body, answer as text. */
    public String post(String address, String json) throws IOException {
        HttpURLConnection connection = open(address);
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (java.io.OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
            return readAnswer(connection);
        } finally {
            connection.disconnect();
        }
    }

    /** GET as text. Throws with the status code for an error answer. */
    public String get(String address) throws IOException {
        HttpURLConnection connection = open(address);
        try {
            return readAnswer(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static String readAnswer(HttpURLConnection connection) throws IOException {
        {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException(code == 429 ? "Modrinth says to slow down (HTTP 429). Try again in a minute."
                        : "Modrinth answered HTTP " + code);
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }

    /** A connection with the settings every call to Modrinth needs. The caller disconnects. */
    public static HttpURLConnection open(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(20_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }
}
