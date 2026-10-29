package net.kdt.pojavlaunch.downloads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class ModrinthClientTest {
    @Test
    public void searchReadsTheAnswerAndSendsAnIdentifyingUserAgent() throws Exception {
        try (FakeModrinth server = new FakeModrinth()) {
            SearchQuery query = new SearchQuery(DownloadCategory.MODS);
            query.text = "sodium";
            String path = query.toUrl("").toString();
            server.json(path, "{\"hits\":[{\"project_id\":\"AANobbMI\",\"title\":\"Sodium\",\"author\":\"jellysquid3\",\"downloads\":5}],\"total_hits\":1}");
            SearchPage page = new ModrinthClient(server.base()).search(query);
            // The query goes to the base given, with the same path and parameters.
            assertEquals(path, server.requests.get(0));
            assertEquals(1, page.hits.size());
            assertEquals("Sodium", page.hits.get(0).title);
            assertTrue(server.userAgents.get(0), server.userAgents.get(0).contains("URinthLauncher"));
        }
    }

    @Test
    public void errorCodesBecomeReadableMessages() throws Exception {
        try (FakeModrinth server = new FakeModrinth()) {
            server.status("/slow", 429);
            server.status("/broken", 500);
            ModrinthClient client = new ModrinthClient(server.base());
            try {
                client.get(server.base() + "/slow");
                fail();
            } catch (IOException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("slow down"));
            }
            try {
                client.get(server.base() + "/broken");
                fail();
            } catch (IOException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("500"));
            }
            try {
                client.get(server.base() + "/missing");
                fail();
            } catch (IOException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("404"));
            }
        }
    }

    @Test
    public void projectVersionsAskForTheLoadersAndGameVersionsAndKeepOnlyOnesWithFiles() throws Exception {
        try (FakeModrinth server = new FakeModrinth()) {
            String path = "/project/AANobbMI/version?loaders=" + SearchQuery.encode("[\"fabric\",\"quilt\"]")
                    + "&game_versions=" + SearchQuery.encode("[\"1.21.4\"]");
            server.json(path, "[{\"id\":\"a\",\"files\":[{\"url\":\"u\",\"filename\":\"a.jar\"}]},{\"id\":\"nofile\",\"files\":[]}]");
            List<ProjectVersion> versions = new ModrinthClient(server.base())
                    .projectVersions("AANobbMI", new String[]{"fabric", "quilt"}, new String[]{"1.21.4"});
            assertEquals(1, versions.size());
            assertEquals("a", versions.get(0).id);
            assertEquals(path, server.requests.get(0));
        }
    }

    @Test
    public void projectVersionsWithoutFiltersHaveNoQuery() throws Exception {
        try (FakeModrinth server = new FakeModrinth()) {
            server.json("/project/abc/version", "[]");
            assertTrue(new ModrinthClient(server.base()).projectVersions("abc", null, null).isEmpty());
            assertEquals("/project/abc/version", server.requests.get(0));
        }
    }

    @Test
    public void gameVersionListKeepsOnlyReleases() throws Exception {
        try (FakeModrinth server = new FakeModrinth()) {
            server.json("/tag/game_version", "[{\"version\":\"1.21.5-pre1\",\"version_type\":\"snapshot\"},"
                    + "{\"version\":\"1.21.4\",\"version_type\":\"release\"},{\"version\":\"1.21.3\",\"version_type\":\"release\"}]");
            assertEquals(java.util.Arrays.asList("1.21.4", "1.21.3"), new ModrinthClient(server.base()).releaseGameVersions());
        }
    }

    @Test
    public void aVersionByIdIsParsed() throws Exception {
        try (FakeModrinth server = new FakeModrinth()) {
            server.json("/version/v9", "{\"id\":\"v9\",\"project_id\":\"p\",\"files\":[{\"url\":\"u\",\"filename\":\"f.jar\"}]}");
            ProjectVersion version = new ModrinthClient(server.base()).version("v9");
            assertEquals("v9", version.id);
            assertEquals("f.jar", version.filename);
        }
    }

    @Test
    public void unreadableAnswersAreIOExceptions() throws Exception {
        try (FakeModrinth server = new FakeModrinth()) {
            server.json("/version/bad", "<html>nope</html>");
            server.json("/project/bad/version", "{\"not\":\"an array\"}");
            ModrinthClient client = new ModrinthClient(server.base());
            try {
                client.version("bad");
                fail();
            } catch (IOException expected) {
                // fine
            }
            try {
                client.projectVersions("bad", null, null);
                fail();
            } catch (IOException expected) {
                // fine
            }
        }
    }

    @Test
    public void jsonArrayEscapesQuotes() {
        assertEquals("[\"a\",\"b\\\"c\"]", ModrinthClient.jsonArray(new String[]{"a", "b\"c"}));
        assertEquals("[]", ModrinthClient.jsonArray(new String[0]));
    }

    @Test
    public void serversAreSearchedUnderV3AndEverythingElseUnderV2() throws Exception {
        try (FakeModrinth server = new FakeModrinth()) {
            SearchQuery servers = new SearchQuery(DownloadCategory.SERVERS);
            server.json("/v3" + servers.toUrl("").toString(), "{\"hits\":[{\"project_id\":\"s\",\"name\":\"Srv\"}],\"total_hits\":1}");
            assertEquals("Srv", new ModrinthClient(server.base()).search(servers).hits.get(0).title);
            assertTrue(server.requests.get(0).startsWith("/v3/search?"));
            assertEquals("https://api.modrinth.com/v2/search?x", new ModrinthClient().searchRoot(false) + "/search?x");
            assertEquals("https://api.modrinth.com/v3/search?x", new ModrinthClient().searchRoot(true) + "/search?x");
        }
    }
}
