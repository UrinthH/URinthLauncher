package net.kdt.pojavlaunch.downloads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchQueryTest {
    private static String decoded(String value) throws Exception {
        return java.net.URLDecoder.decode(value, "UTF-8");
    }

    @Test
    public void modsUseTheV2SearchWithEveryModLoaderByDefault() throws Exception {
        SearchQuery query = new SearchQuery(DownloadCategory.MODS);
        String url = query.toUrl();
        assertTrue(url, url.startsWith("https://api.modrinth.com/v2/search?query=&index=relevance&limit=20&offset=0&facets="));
        assertEquals("[[\"project_type:mod\"],[\"categories:fabric\",\"categories:forge\",\"categories:quilt\","
                + "\"categories:neoforge\",\"categories:liteloader\",\"categories:modloader\",\"categories:rift\"]]", query.facets());
    }

    @Test
    public void pagesStartAtTheRightOffset() {
        SearchQuery query = new SearchQuery(DownloadCategory.MODS);
        query.pageSize = 20;
        query.page = 2;
        assertTrue(query.toUrl().contains("&limit=20&offset=40&"));
        query.pageSize = 50;
        query.page = 3;
        assertTrue(query.toUrl().contains("&limit=50&offset=150&"));
    }

    @Test
    public void pageSizeIsKeptInsideModrinthsLimits() {
        SearchQuery query = new SearchQuery(DownloadCategory.MODS);
        query.pageSize = 500;
        assertTrue(query.toUrl().contains("&limit=100&"));
        query.pageSize = 0;
        assertTrue(query.toUrl().contains("&limit=1&"));
        query.page = -4;
        assertTrue(query.toUrl().contains("&offset=0&"));
    }

    @Test
    public void searchTextIsTrimmedAndEncoded() throws Exception {
        SearchQuery query = new SearchQuery(DownloadCategory.MODS);
        query.text = "  sodium & lithium é ";
        String url = query.toUrl();
        assertTrue(url, url.contains("query=sodium%20%26%20lithium%20%C3%A9&"));
        assertEquals("sodium & lithium é", decoded(url.substring(url.indexOf("query=") + 6, url.indexOf("&index"))));
    }

    @Test
    public void aChosenLoaderReplacesTheListOfAll() {
        SearchQuery query = new SearchQuery(DownloadCategory.MODS);
        query.loader = "fabric";
        assertEquals("[[\"project_type:mod\"],[\"categories:fabric\"]]", query.facets());
    }

    @Test
    public void gameVersionAndEnvironmentAreAddedAsOwnGroups() {
        SearchQuery query = new SearchQuery(DownloadCategory.MODS);
        query.loader = "fabric";
        query.gameVersion = "1.21.4";
        query.environment = "client";
        assertEquals("[[\"project_type:mod\"],[\"categories:fabric\"],[\"versions:1.21.4\"],"
                + "[\"client_side:required\",\"client_side:optional\"]]", query.facets());
        query.environment = "server";
        assertTrue(query.facets().endsWith("[\"server_side:required\",\"server_side:optional\"]]"));
    }

    @Test
    public void environmentIsIgnoredWhereItMeansNothing() {
        SearchQuery query = new SearchQuery(DownloadCategory.RESOURCE_PACKS);
        query.environment = "client";
        assertEquals("[[\"project_type:resourcepack\"]]", query.facets());
    }

    @Test
    public void eachTabAsksForItsOwnProjectType() {
        assertEquals("[[\"project_type:resourcepack\"]]", new SearchQuery(DownloadCategory.RESOURCE_PACKS).facets());
        assertEquals("[[\"categories:datapack\"]]", new SearchQuery(DownloadCategory.DATA_PACKS).facets());
        assertEquals("[[\"project_type:shader\"]]", new SearchQuery(DownloadCategory.SHADERS).facets());
        assertEquals("[[\"project_type:modpack\"]]", new SearchQuery(DownloadCategory.MODPACKS).facets());
        SearchQuery plugins = new SearchQuery(DownloadCategory.PLUGINS);
        assertTrue(plugins.facets().startsWith("[[\"categories:bukkit\",\"categories:spigot\",\"categories:paper\""));
        plugins.loader = "paper";
        assertEquals("[[\"categories:paper\"]]", plugins.facets());
    }

    @Test
    public void shadersAndModpacksTakeALoader() {
        SearchQuery shaders = new SearchQuery(DownloadCategory.SHADERS);
        shaders.loader = "iris";
        assertEquals("[[\"project_type:shader\"],[\"categories:iris\"]]", shaders.facets());
        SearchQuery packs = new SearchQuery(DownloadCategory.MODPACKS);
        packs.loader = "neoforge";
        assertEquals("[[\"project_type:modpack\"],[\"categories:neoforge\"]]", packs.facets());
    }

    @Test
    public void serversUseTheV3Search() throws Exception {
        SearchQuery query = new SearchQuery(DownloadCategory.SERVERS);
        assertTrue(query.usesV3());
        String url = query.toUrl();
        assertTrue(url, url.startsWith("https://api.modrinth.com/v3/search?query=&index=relevance&limit=20&offset=0&new_filters="));
        assertFalse(url.contains("facets="));
        assertEquals("project_types = \"minecraft_java_server\"", decoded(url.substring(url.indexOf("new_filters=") + 12)));
    }

    @Test
    public void everythingElseUsesV2() {
        for (DownloadCategory category : DownloadCategory.values()) {
            assertEquals(category.name(), category == DownloadCategory.SERVERS, new SearchQuery(category).usesV3());
        }
    }

    @Test
    public void quotesInAFilterCannotBreakTheFacetText() {
        SearchQuery query = new SearchQuery(DownloadCategory.MODS);
        query.gameVersion = "1.21\"],[\"x";
        assertTrue(query.facets().contains("versions:1.21\\\"],[\\\"x"));
    }

    @Test
    public void sortOrdersUseModrinthsIndexNames() {
        SearchQuery query = new SearchQuery(DownloadCategory.MODS);
        query.sort = SortOrder.DOWNLOADS;
        assertTrue(query.toUrl().contains("&index=downloads&"));
        query.sort = SortOrder.UPDATED;
        assertTrue(query.toUrl().contains("&index=updated&"));
        query.sort = SortOrder.NEWEST;
        assertTrue(query.toUrl().contains("&index=newest&"));
        query.sort = SortOrder.FOLLOWS;
        assertTrue(query.toUrl().contains("&index=follows&"));
    }

    @Test
    public void copyIsIndependent() {
        SearchQuery query = new SearchQuery(DownloadCategory.MODS);
        query.text = "a";
        query.loader = "fabric";
        SearchQuery copy = query.copy();
        copy.text = "b";
        copy.page = 5;
        assertEquals("a", query.text);
        assertEquals(0, query.page);
        assertEquals("fabric", copy.loader);
    }

    @Test
    public void filterDescriptionListsWhatIsActive() {
        SearchQuery query = new SearchQuery(DownloadCategory.MODS);
        assertEquals("", query.describeFilters());
        query.gameVersion = "1.21.4";
        query.loader = "neoforge";
        query.environment = "client";
        assertEquals("Minecraft 1.21.4 · NeoForge · Client", query.describeFilters());
    }

    @Test
    public void tabsKnowTheirInstallFolders() {
        assertEquals("mods", DownloadCategory.MODS.installFolder);
        assertEquals("resourcepacks", DownloadCategory.RESOURCE_PACKS.installFolder);
        assertEquals("shaderpacks", DownloadCategory.SHADERS.installFolder);
        assertEquals("plugins", DownloadCategory.PLUGINS.installFolder);
        assertEquals(null, DownloadCategory.MODPACKS.installFolder);
        assertEquals(null, DownloadCategory.SERVERS.installFolder);
        assertEquals(DownloadCategory.MODS, DownloadCategory.fromKey("nonsense"));
        assertEquals(DownloadCategory.SHADERS, DownloadCategory.fromKey("shaders"));
    }
}
