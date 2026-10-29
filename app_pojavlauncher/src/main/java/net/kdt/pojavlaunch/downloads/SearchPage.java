package net.kdt.pojavlaunch.downloads;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The answer to one search: the hits of one page and how many there are in all. */
public final class SearchPage {
    public final List<ProjectHit> hits = new ArrayList<>();
    public int totalHits;
    public int offset;
    public int limit;

    public static SearchPage parse(String body) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) throw new IOException("Unexpected answer from Modrinth");
            JsonObject json = root.getAsJsonObject();
            SearchPage page = new SearchPage();
            if (json.has("hits") && json.get("hits").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("hits")) {
                    if (element.isJsonObject()) page.hits.add(ProjectHit.fromJson(element.getAsJsonObject()));
                }
            }
            page.totalHits = (int) Math.min(Integer.MAX_VALUE, ProjectHit.number(json, "total_hits"));
            if (page.totalHits == 0) page.totalHits = page.hits.size();
            page.offset = (int) ProjectHit.number(json, "offset");
            page.limit = (int) ProjectHit.number(json, "limit");
            return page;
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IOException("Modrinth sent something that is not a search answer", e);
        }
    }
}
