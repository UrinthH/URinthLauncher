package net.kdt.pojavlaunch.downloads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;

public class ParsingTest {
    private static final String SEARCH = "{\"hits\":[{\"project_id\":\"P7dR8mSH\",\"slug\":\"fabric-api\",\"project_type\":\"mod\","
            + "\"title\":\"Fabric API\",\"author\":\"modmuss50\",\"description\":\"Lightweight and modular API\","
            + "\"icon_url\":\"https://cdn.modrinth.com/data/P7dR8mSH/icon.png\",\"downloads\":292390000,\"follows\":37300,"
            + "\"date_modified\":\"2026-10-28T10:12:45.123456Z\",\"client_side\":\"optional\",\"server_side\":\"optional\","
            + "\"display_categories\":[\"library\",\"fabric\"],\"categories\":[\"library\",\"fabric\",\"utility\"],"
            + "\"versions\":[\"1.20.1\",\"1.21.4\"]},"
            + "{\"project_id\":\"x\"}],\"offset\":0,\"limit\":20,\"total_hits\":77773}";

    @Test
    public void aSearchAnswerIsReadIntoCards() throws Exception {
        SearchPage page = SearchPage.parse(SEARCH);
        assertEquals(2, page.hits.size());
        assertEquals(77773, page.totalHits);
        assertEquals(20, page.limit);
        ProjectHit hit = page.hits.get(0);
        assertEquals("P7dR8mSH", hit.id);
        assertEquals("fabric-api", hit.slug);
        assertEquals("Fabric API", hit.title);
        assertEquals("modmuss50", hit.author);
        assertEquals("https://cdn.modrinth.com/data/P7dR8mSH/icon.png", hit.iconUrl);
        assertEquals(292_390_000L, hit.downloads);
        assertEquals(37_300L, hit.follows);
        assertEquals(Arrays.asList("library", "fabric"), hit.tags);
        assertEquals(Arrays.asList("1.20.1", "1.21.4"), hit.gameVersions);
        assertEquals("Client or server", hit.environment());
    }

    @Test
    public void tagsReadLikeModrinthsCard() throws Exception {
        ProjectHit hit = SearchPage.parse(SEARCH).hits.get(0);
        // Category first, then the loader: "Library", then "Fabric".
        assertEquals(Arrays.asList("library", "fabric"), hit.orderedTags());
    }

    @Test
    public void aSparseHitStillGetsATitle() throws Exception {
        ProjectHit sparse = SearchPage.parse(SEARCH).hits.get(1);
        assertEquals("x", sparse.id);
        assertEquals("Unnamed project", sparse.title);
        assertEquals("", sparse.iconUrl);
        assertEquals(0L, sparse.downloads);
    }

    @Test
    public void totalFallsBackToTheHitCount() throws Exception {
        SearchPage page = SearchPage.parse("{\"hits\":[{\"project_id\":\"a\"},{\"project_id\":\"b\"}]}");
        assertEquals(2, page.totalHits);
    }

    @Test
    public void garbageAnswersBecomeIOExceptions() {
        for (String body : new String[]{"", "not json", "[1,2]", "{\"hits\":"}) {
            try {
                SearchPage.parse(body);
                fail("expected an IOException for: " + body);
            } catch (IOException expected) {
                // fine
            }
        }
    }

    @Test
    public void wrongTypedFieldsDoNotCrash() throws Exception {
        SearchPage page = SearchPage.parse("{\"hits\":[{\"project_id\":5,\"title\":{\"a\":1},\"downloads\":\"lots\","
                + "\"display_categories\":\"nope\",\"versions\":null}],\"total_hits\":\"many\"}");
        assertEquals(1, page.hits.size());
        assertEquals(0L, page.hits.get(0).downloads);
        assertTrue(page.hits.get(0).tags.isEmpty());
    }

    @Test
    public void serversCarryTheirAddress() throws Exception {
        String body = "{\"hits\":[{\"project_id\":\"s1\",\"name\":\"Cool Server\",\"summary\":\"Play with us\","
                + "\"project_types\":[\"minecraft_java_server\"],\"minecraft_java_server\":{\"address\":\"play.cool.net\"}},"
                + "{\"project_id\":\"s2\",\"title\":\"Nested\",\"minecraft_java_server\":{\"ping\":{\"data\":{\"ip\":\"mc.nested.org:25570\"}}}}],"
                + "\"total_hits\":2}";
        SearchPage page = SearchPage.parse(body);
        assertEquals("Cool Server", page.hits.get(0).title);
        assertEquals("Play with us", page.hits.get(0).description);
        assertEquals("minecraft_java_server", page.hits.get(0).type);
        assertEquals("play.cool.net", page.hits.get(0).serverAddress);
        assertEquals("mc.nested.org:25570", page.hits.get(1).serverAddress);
    }

    private static final String VERSION = "{\"id\":\"v1\",\"project_id\":\"P7dR8mSH\",\"name\":\"Fabric API 0.119\","
            + "\"version_number\":\"0.119.0+1.21.4\",\"version_type\":\"release\",\"date_published\":\"2026-10-01T00:00:00Z\","
            + "\"game_versions\":[\"1.21.4\"],\"loaders\":[\"fabric\"],"
            + "\"dependencies\":[{\"project_id\":\"lib\",\"version_id\":null,\"dependency_type\":\"required\"},"
            + "{\"project_id\":\"opt\",\"dependency_type\":\"optional\"}],"
            + "\"files\":[{\"url\":\"https://cdn/x-sources.jar\",\"filename\":\"x-sources.jar\",\"primary\":false,\"size\":5,\"hashes\":{\"sha1\":\"aa\"}},"
            + "{\"url\":\"https://cdn/x.jar\",\"filename\":\"x.jar\",\"primary\":true,\"size\":1234,\"hashes\":{\"sha1\":\"bb\"}}]}";

    @Test
    public void aVersionPicksItsPrimaryFile() {
        ProjectVersion version = ProjectVersion.fromJson(JsonParser.parseString(VERSION).getAsJsonObject());
        assertEquals("v1", version.id);
        assertEquals("https://cdn/x.jar", version.url);
        assertEquals("x.jar", version.filename);
        assertEquals("bb", version.sha1);
        assertEquals(1234L, version.size);
        assertTrue(version.hasFile());
        assertEquals(2, version.dependencies.size());
        assertTrue(version.dependencies.get(0).isRequired());
        assertFalse(version.dependencies.get(1).isRequired());
        assertEquals("", version.dependencies.get(0).versionId);
        assertEquals("1.21.4", version.gameVersionText());
    }

    @Test
    public void aVersionWithoutPrimaryUsesTheFirstFile() {
        ProjectVersion version = ProjectVersion.fromJson(JsonParser.parseString(
                "{\"id\":\"v\",\"files\":[{\"url\":\"u1\",\"filename\":\"a.zip\"},{\"url\":\"u2\",\"filename\":\"b.zip\"}]}").getAsJsonObject());
        assertEquals("u1", version.url);
        assertEquals("", version.sha1);
    }

    @Test
    public void aVersionWithoutFilesHasNoFile() {
        ProjectVersion version = ProjectVersion.fromJson(JsonParser.parseString("{\"id\":\"v\",\"files\":[]}").getAsJsonObject());
        assertFalse(version.hasFile());
    }

    @Test
    public void gameVersionRangeText() {
        ProjectVersion version = new ProjectVersion();
        assertEquals("", version.gameVersionText());
        version.gameVersions.addAll(Arrays.asList("1.21", "1.21.1", "1.21.4"));
        assertEquals("1.21 \u2013 1.21.4", version.gameVersionText());
    }

    @Test
    public void preferredVersionIsTheNewestRelease() {
        ProjectVersion beta = new ProjectVersion();
        beta.id = "beta";
        beta.type = "beta";
        ProjectVersion release = new ProjectVersion();
        release.id = "rel";
        release.type = "release";
        ProjectVersion olderRelease = new ProjectVersion();
        olderRelease.id = "old";
        olderRelease.type = "release";
        assertEquals("rel", InstallPlan.preferred(Arrays.asList(beta, release, olderRelease)).id);
        assertEquals("beta", InstallPlan.preferred(Arrays.asList(beta)).id);
        assertEquals(null, InstallPlan.preferred(Arrays.<ProjectVersion>asList()));
    }
}
