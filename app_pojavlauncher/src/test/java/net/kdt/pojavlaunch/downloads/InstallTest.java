package net.kdt.pojavlaunch.downloads;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InstallTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static ProjectVersion version(String id, String url, String filename, byte[] content) throws Exception {
        ProjectVersion version = new ProjectVersion();
        version.id = id;
        version.url = url;
        version.filename = filename;
        version.size = content == null ? 0 : content.length;
        version.sha1 = content == null ? "" : sha1(content);
        return version;
    }

    private static String sha1(byte[] content) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest(content)) hex.append(String.format("%02x", b & 0xff));
        return hex.toString();
    }

    private static final class Recorder implements FileInstaller.Progress {
        long lastDone, lastTotal;
        boolean cancel;

        @Override
        public void onBytes(long done, long total) {
            lastDone = done;
            lastTotal = total;
        }

        @Override
        public boolean isCancelled() {
            return cancel;
        }
    }

    @Test
    public void aFileIsDownloadedVerifiedAndSaved() throws Exception {
        byte[] content = "mod content".getBytes(StandardCharsets.UTF_8);
        try (FakeModrinth server = new FakeModrinth()) {
            server.bytes("/cdn/a.jar", content);
            ProjectVersion version = version("v1", server.base() + "/cdn/a.jar", "a.jar", content);
            File folder = temp.newFolder("profile", "mods");
            Recorder recorder = new Recorder();
            FileInstaller.Result result = FileInstaller.install(version, folder, recorder);
            assertFalse(result.alreadyInstalled);
            assertEquals("a.jar", result.file.getName());
            assertArrayEquals(content, Files.readAllBytes(result.file.toPath()));
            assertEquals(content.length, recorder.lastDone);
            assertEquals(content.length, recorder.lastTotal);
            assertEquals(1, folder.listFiles().length); // no .part file left behind
        }
    }

    @Test
    public void theFolderIsCreatedWhenMissing() throws Exception {
        byte[] content = {1, 2, 3};
        try (FakeModrinth server = new FakeModrinth()) {
            server.bytes("/f", content);
            File folder = new File(temp.getRoot(), "new/deep/mods");
            FileInstaller.install(version("v", server.base() + "/f", "f.jar", content), folder, null);
            assertTrue(new File(folder, "f.jar").isFile());
        }
    }

    @Test
    public void aDamagedDownloadIsRejectedAndLeavesNothing() throws Exception {
        try (FakeModrinth server = new FakeModrinth()) {
            server.bytes("/f", new byte[]{9, 9, 9});
            ProjectVersion version = version("v", server.base() + "/f", "f.jar", new byte[]{1, 2, 3}); // hash of other bytes
            File folder = temp.newFolder("mods");
            try {
                FileInstaller.install(version, folder, null);
                fail();
            } catch (IOException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("damaged"));
            }
            assertEquals(0, folder.listFiles().length);
        }
    }

    @Test
    public void aFailedDownloadKeepsTheOldFileAndAddsNothing() throws Exception {
        try (FakeModrinth server = new FakeModrinth()) {
            server.status("/f", 500);
            File folder = temp.newFolder("mods");
            File old = new File(folder, "f.jar");
            Files.write(old.toPath(), "old".getBytes(StandardCharsets.UTF_8));
            ProjectVersion version = version("v", server.base() + "/f", "f.jar", new byte[]{1});
            try {
                FileInstaller.install(version, folder, null);
                fail();
            } catch (IOException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("500"));
            }
            assertEquals("old", new String(Files.readAllBytes(old.toPath()), StandardCharsets.UTF_8));
            assertEquals(1, folder.listFiles().length);
        }
    }

    @Test
    public void aFileThatIsAlreadyThereWithTheSameHashIsNotDownloadedAgain() throws Exception {
        byte[] content = "same".getBytes(StandardCharsets.UTF_8);
        try (FakeModrinth server = new FakeModrinth()) {
            File folder = temp.newFolder("mods");
            Files.write(new File(folder, "s.jar").toPath(), content);
            FileInstaller.Result result = FileInstaller.install(version("v", server.base() + "/s", "s.jar", content), folder, null);
            assertTrue(result.alreadyInstalled);
            assertTrue(server.requests.isEmpty());
        }
    }

    @Test
    public void aDifferentFileWithTheSameNameIsReplaced() throws Exception {
        byte[] content = "new version".getBytes(StandardCharsets.UTF_8);
        try (FakeModrinth server = new FakeModrinth()) {
            server.bytes("/s", content);
            File folder = temp.newFolder("mods");
            Files.write(new File(folder, "s.jar").toPath(), "old version".getBytes(StandardCharsets.UTF_8));
            FileInstaller.Result result = FileInstaller.install(version("v", server.base() + "/s", "s.jar", content), folder, null);
            assertFalse(result.alreadyInstalled);
            assertArrayEquals(content, Files.readAllBytes(result.file.toPath()));
        }
    }

    @Test
    public void cancellingStopsAndCleansUp() throws Exception {
        byte[] content = new byte[200_000];
        try (FakeModrinth server = new FakeModrinth()) {
            server.bytes("/big", content);
            File folder = temp.newFolder("mods");
            Recorder recorder = new Recorder();
            recorder.cancel = true;
            try {
                FileInstaller.install(version("v", server.base() + "/big", "big.jar", content), folder, recorder);
                fail();
            } catch (IOException e) {
                assertEquals("Cancelled", e.getMessage());
            }
            assertEquals(0, folder.listFiles().length);
        }
    }

    @Test
    public void hostileFileNamesStayInsideTheFolder() {
        assertEquals("evil.jar", FileInstaller.safeName("../../etc/evil.jar", "v"));
        assertEquals("evil.jar", FileInstaller.safeName("C:\\temp\\evil.jar", "v"));
        assertEquals("a.jar", FileInstaller.safeName("  a.jar  ", "v"));
        assertEquals("download-abc123.jar", FileInstaller.safeName("..", "abc123"));
        assertEquals("download-abc.jar", FileInstaller.safeName("", "a/b.c"));
        assertEquals("download-file.jar", FileInstaller.safeName(null, null));
    }

    // ---------- dependency planning ----------

    private ProjectVersion mod(String id, String project, String game, String loader, ProjectVersion.Dependency... deps) throws Exception {
        ProjectVersion version = version(id, "http://x/" + id, id + ".jar", null);
        version.projectId = project;
        version.type = "release";
        version.gameVersions.add(game);
        version.loaders.add(loader);
        for (ProjectVersion.Dependency dependency : deps) version.dependencies.add(dependency);
        return version;
    }

    private static ProjectVersion.Dependency required(String projectId, String versionId) {
        return new ProjectVersion.Dependency(com.google.gson.JsonParser.parseString(
                "{\"project_id\":\"" + projectId + "\",\"version_id\":" + (versionId == null ? "null" : "\"" + versionId + "\"")
                        + ",\"dependency_type\":\"required\"}").getAsJsonObject());
    }

    private static ProjectVersion.Dependency optional(String projectId) {
        return new ProjectVersion.Dependency(com.google.gson.JsonParser.parseString(
                "{\"project_id\":\"" + projectId + "\",\"dependency_type\":\"optional\"}").getAsJsonObject());
    }

    private static String versionsJson(String id, String project, String game, String loader, String deps) {
        return "{\"id\":\"" + id + "\",\"project_id\":\"" + project + "\",\"version_type\":\"release\",\"game_versions\":[\"" + game
                + "\"],\"loaders\":[\"" + loader + "\"],\"dependencies\":[" + deps + "],\"files\":[{\"url\":\"http://x/" + id
                + "\",\"filename\":\"" + id + ".jar\"}]}";
    }

    @Test
    public void requiredModsComeFirstAndOptionalOnesAreSkipped() throws Exception {
        ProfileTarget target = ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", null);
        try (FakeModrinth server = new FakeModrinth()) {
            String filter = "?loaders=" + SearchQuery.encode("[\"fabric\"]") + "&game_versions=" + SearchQuery.encode("[\"1.21.4\"]");
            server.json("/project/fapi/version" + filter, "[" + versionsJson("fapi-1", "fapi", "1.21.4", "fabric", "") + "]");
            ProjectVersion chosen = mod("main-1", "main", "1.21.4", "fabric", required("fapi", null), optional("extra"));
            List<ProjectVersion> plan = InstallPlan.build(new ModrinthClient(server.base()), chosen, target, true);
            List<String> ids = new ArrayList<>();
            for (ProjectVersion version : plan) ids.add(version.id);
            assertEquals(java.util.Arrays.asList("fapi-1", "main-1"), ids);
            assertEquals(1, server.requests.size()); // the optional one was never looked up
        }
    }

    @Test
    public void aDependencyChainIsFollowedDeepestFirst() throws Exception {
        ProfileTarget target = ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", null);
        try (FakeModrinth server = new FakeModrinth()) {
            String filter = "?loaders=" + SearchQuery.encode("[\"fabric\"]") + "&game_versions=" + SearchQuery.encode("[\"1.21.4\"]");
            server.json("/project/b/version" + filter, "[" + versionsJson("b-1", "b", "1.21.4", "fabric",
                    "{\"project_id\":\"c\",\"dependency_type\":\"required\"}") + "]");
            server.json("/project/c/version" + filter, "[" + versionsJson("c-1", "c", "1.21.4", "fabric", "") + "]");
            ProjectVersion chosen = mod("a-1", "a", "1.21.4", "fabric", required("b", null));
            List<ProjectVersion> plan = InstallPlan.build(new ModrinthClient(server.base()), chosen, target, true);
            List<String> ids = new ArrayList<>();
            for (ProjectVersion version : plan) ids.add(version.id);
            assertEquals(java.util.Arrays.asList("c-1", "b-1", "a-1"), ids);
        }
    }

    @Test
    public void aCycleDoesNotLoopForever() throws Exception {
        ProfileTarget target = ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", null);
        try (FakeModrinth server = new FakeModrinth()) {
            String filter = "?loaders=" + SearchQuery.encode("[\"fabric\"]") + "&game_versions=" + SearchQuery.encode("[\"1.21.4\"]");
            server.json("/project/b/version" + filter, "[" + versionsJson("b-1", "b", "1.21.4", "fabric",
                    "{\"project_id\":\"a\",\"version_id\":\"a-1\",\"dependency_type\":\"required\"}") + "]");
            server.json("/version/a-1", versionsJson("a-1", "a", "1.21.4", "fabric", ""));
            ProjectVersion chosen = mod("a-1", "a", "1.21.4", "fabric", required("b", null));
            List<ProjectVersion> plan = InstallPlan.build(new ModrinthClient(server.base()), chosen, target, true);
            assertEquals(2, plan.size());
            assertEquals("a-1", plan.get(plan.size() - 1).id);
        }
    }

    @Test
    public void aMissingRequiredModStopsBeforeAnythingIsInstalled() throws Exception {
        ProfileTarget target = ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", null);
        try (FakeModrinth server = new FakeModrinth()) {
            String filter = "?loaders=" + SearchQuery.encode("[\"fabric\"]") + "&game_versions=" + SearchQuery.encode("[\"1.21.4\"]");
            server.json("/project/lib/version" + filter, "[]");
            ProjectVersion chosen = mod("a-1", "a", "1.21.4", "fabric", required("lib", null));
            try {
                InstallPlan.build(new ModrinthClient(server.base()), chosen, target, true);
                fail();
            } catch (IOException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("lib"));
                assertTrue(e.getMessage(), e.getMessage().contains("Fabric 1.21.4"));
            }
        }
    }

    @Test
    public void withoutDependenciesOnlyTheChosenOneIsPlanned() throws Exception {
        ProfileTarget target = ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", null);
        ProjectVersion chosen = mod("a-1", "a", "1.21.4", "fabric", required("b", null));
        List<ProjectVersion> plan = InstallPlan.build(new ModrinthClient("http://127.0.0.1:1"), chosen, target, false);
        assertEquals(1, plan.size());
    }

    // ---------- the whole install ----------

    private static class Quiet implements InstallRunner.Listener {
        final List<String> statuses = new ArrayList<>();

        @Override
        public void onStatus(String text) {
            statuses.add(text);
        }

        @Override
        public void onFileProgress(int index, int count, long done, long total) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    private static String versionFile(String id, String project, String url, String filename, byte[] content, String deps) throws Exception {
        return "{\"id\":\"" + id + "\",\"project_id\":\"" + project + "\",\"version_type\":\"release\",\"game_versions\":[\"1.21.4\"],"
                + "\"loaders\":[\"fabric\"],\"dependencies\":[" + deps + "],\"files\":[{\"url\":\"" + url + "\",\"filename\":\""
                + filename + "\",\"primary\":true,\"size\":" + content.length + ",\"hashes\":{\"sha1\":\"" + sha1(content) + "\"}}]}";
    }

    @Test
    public void installingAModAddsItsMissingRequirementsAndReplacesItsOlderRelease() throws Exception {
        byte[] oldMain = "main 1.0".getBytes(StandardCharsets.UTF_8);
        byte[] newMain = "main 2.0".getBytes(StandardCharsets.UTF_8);
        byte[] fabricApi = "fabric api".getBytes(StandardCharsets.UTF_8);
        byte[] library = "library".getBytes(StandardCharsets.UTF_8);
        byte[] unknown = "somebody's own mod".getBytes(StandardCharsets.UTF_8);
        try (FakeModrinth server = new FakeModrinth()) {
            File mods = temp.newFolder("profile", "mods");
            File oldMainFile = new File(mods, "main-1.0.jar");
            File fabricApiFile = new File(mods, "fabric-api-0.100.jar");
            File unknownFile = new File(mods, "my-own-mod.jar");
            Files.write(oldMainFile.toPath(), oldMain);
            Files.write(fabricApiFile.toPath(), fabricApi);
            Files.write(unknownFile.toPath(), unknown);
            // Modrinth knows the first two files; the third is not on Modrinth.
            server.json("/version_files", "{\"" + sha1(oldMain) + "\":" + versionFile("main-old", "main", "u", "main-1.0.jar", oldMain, "") + ","
                    + "\"" + sha1(fabricApi) + "\":" + versionFile("fapi-old", "fapi", "u", "fabric-api-0.100.jar", fabricApi, "") + "}");
            // The new release needs Fabric API (already there) and a library (missing).
            server.bytes("/cdn/main-2.0.jar", newMain);
            server.bytes("/cdn/library.jar", library);
            String filter = "?loaders=" + SearchQuery.encode("[\"fabric\"]") + "&game_versions=" + SearchQuery.encode("[\"1.21.4\"]");
            server.json("/project/lib/version" + filter, "[" + versionFile("lib-1", "lib", server.base() + "/cdn/library.jar", "library.jar", library, "") + "]");
            ProjectVersion chosen = ProjectVersion.fromJson(com.google.gson.JsonParser.parseString(versionFile("main-new", "main",
                    server.base() + "/cdn/main-2.0.jar", "main-2.0.jar", newMain,
                    "{\"project_id\":\"fapi\",\"dependency_type\":\"required\"},{\"project_id\":\"lib\",\"dependency_type\":\"required\"}")).getAsJsonObject());

            InstallRunner.Outcome outcome = InstallRunner.run(new ModrinthClient(server.base()), chosen, DownloadCategory.MODS,
                    ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", null), mods, true, new Quiet());

            assertEquals(1, outcome.dependenciesAdded);
            assertEquals("main-2.0.jar", outcome.main.getName());
            assertTrue(new File(mods, "library.jar").isFile());
            assertTrue(new File(mods, "main-2.0.jar").isFile());
            assertFalse("older release of the same mod must go", oldMainFile.exists());
            assertEquals(1, outcome.replaced.size());
            assertTrue("fabric api was already there and stays", fabricApiFile.isFile());
            assertTrue("files Modrinth does not know are never touched", unknownFile.isFile());
            assertFalse("fabric api must not be downloaded again", new File(mods, "fabric-api-0.119.jar").exists());
            assertEquals(4, mods.listFiles().length);
        }
    }

    @Test
    public void installingWithoutRequirementsSkipsTheLookups() throws Exception {
        byte[] content = "c".getBytes(StandardCharsets.UTF_8);
        try (FakeModrinth server = new FakeModrinth()) {
            File mods = temp.newFolder("mods2");
            server.bytes("/cdn/a.jar", content);
            ProjectVersion chosen = ProjectVersion.fromJson(com.google.gson.JsonParser.parseString(versionFile("a", "p",
                    server.base() + "/cdn/a.jar", "a.jar", content, "{\"project_id\":\"x\",\"dependency_type\":\"required\"}")).getAsJsonObject());
            InstallRunner.Outcome outcome = InstallRunner.run(new ModrinthClient(server.base()), chosen, DownloadCategory.MODS,
                    ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", null), mods, false, new Quiet());
            assertEquals(0, outcome.dependenciesAdded);
            assertTrue(new File(mods, "a.jar").isFile());
            assertTrue(server.requests.isEmpty() || !server.requests.contains("/project/x/version"));
        }
    }

    @Test
    public void resourcePacksAreJustSaved() throws Exception {
        byte[] content = "zip".getBytes(StandardCharsets.UTF_8);
        try (FakeModrinth server = new FakeModrinth()) {
            File packs = new File(temp.getRoot(), "resourcepacks");
            server.bytes("/cdn/pack.zip", content);
            ProjectVersion chosen = ProjectVersion.fromJson(com.google.gson.JsonParser.parseString(versionFile("pk", "pack",
                    server.base() + "/cdn/pack.zip", "Faithful 64x.zip", content, "")).getAsJsonObject());
            InstallRunner.Outcome outcome = InstallRunner.run(new ModrinthClient(server.base()), chosen, DownloadCategory.RESOURCE_PACKS,
                    ProfileTarget.parse("1.21.4", null), packs, true, new Quiet());
            assertEquals("Faithful 64x.zip", outcome.main.getName());
            assertFalse(server.requests.contains("/version_files"));
        }
    }

    @Test
    public void aCancelledInstallStopsBeforeDownloading() throws Exception {
        byte[] content = "c".getBytes(StandardCharsets.UTF_8);
        try (FakeModrinth server = new FakeModrinth()) {
            File mods = temp.newFolder("mods3");
            ProjectVersion chosen = ProjectVersion.fromJson(com.google.gson.JsonParser.parseString(versionFile("a", "p",
                    server.base() + "/cdn/a.jar", "a.jar", content, "")).getAsJsonObject());
            InstallRunner.Listener cancelled = new Quiet() {
                @Override
                public boolean isCancelled() {
                    return true;
                }
            };
            try {
                InstallRunner.run(new ModrinthClient(server.base()), chosen, DownloadCategory.RESOURCE_PACKS,
                        ProfileTarget.parse("1.21.4", null), mods, false, cancelled);
                fail();
            } catch (IOException e) {
                assertEquals("Cancelled", e.getMessage());
            }
            assertEquals(0, mods.listFiles().length);
        }
    }

    @Test
    public void anUnreachableHashCheckStopsTheInstallInsteadOfRiskingDuplicates() throws Exception {
        byte[] content = "c".getBytes(StandardCharsets.UTF_8);
        try (FakeModrinth server = new FakeModrinth()) {
            File mods = temp.newFolder("mods4");
            Files.write(new File(mods, "x.jar").toPath(), "x".getBytes(StandardCharsets.UTF_8));
            server.status("/version_files", 500);
            ProjectVersion chosen = ProjectVersion.fromJson(com.google.gson.JsonParser.parseString(versionFile("a", "p",
                    server.base() + "/cdn/a.jar", "a.jar", content, "")).getAsJsonObject());
            try {
                InstallRunner.run(new ModrinthClient(server.base()), chosen, DownloadCategory.MODS,
                        ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", null), mods, true, new Quiet());
                fail();
            } catch (IOException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("500"));
            }
            assertFalse(new File(mods, "a.jar").exists());
        }
    }
}
