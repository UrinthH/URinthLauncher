package net.kdt.pojavlaunch.content;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ContentTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File zip(File file, String... namesAndTexts) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < namesAndTexts.length; i += 2) {
                out.putNextEntry(new ZipEntry(namesAndTexts[i]));
                out.write(namesAndTexts[i + 1].getBytes(StandardCharsets.ISO_8859_1));
                out.closeEntry();
            }
        }
        return file;
    }

    // ---- kinds and names

    @Test
    public void kindsAcceptTheirOwnFilesSwitchedOnOrOff() throws IOException {
        File jar = temp.newFile("sodium.jar");
        File off = temp.newFile("lithium.jar.disabled");
        File text = temp.newFile("readme.txt");
        File hidden = temp.newFile(".DS_Store");
        File pack = temp.newFile("Faithful.zip");
        File folder = temp.newFolder("Unpacked");
        assertTrue(ContentKind.MODS.accepts(jar));
        assertTrue(ContentKind.MODS.accepts(off));
        assertFalse(ContentKind.MODS.accepts(text));
        assertFalse(ContentKind.MODS.accepts(hidden));
        assertFalse(ContentKind.MODS.accepts(pack));
        assertFalse(ContentKind.MODS.accepts(folder));
        assertTrue(ContentKind.RESOURCE_PACKS.accepts(pack));
        assertTrue(ContentKind.RESOURCE_PACKS.accepts(folder));
        assertTrue(ContentKind.SHADERS.accepts(folder));
        assertFalse(ContentKind.SHADERS.accepts(jar));
        assertEquals("shaderpacks", ContentKind.SHADERS.folder);
        assertEquals(ContentKind.RESOURCE_PACKS, ContentKind.fromKey("resourcepacks"));
        assertEquals(ContentKind.MODS, ContentKind.fromKey("nonsense"));
    }

    @Test
    public void disabledNames() {
        assertEquals("a.jar", ContentKind.activeName("a.jar.disabled"));
        assertEquals("a.jar", ContentKind.activeName("a.jar.DISABLED"));
        assertEquals("a.jar", ContentKind.activeName("a.jar"));
        assertTrue(ContentKind.isDisabledName("a.jar.disabled"));
        assertFalse(ContentKind.isDisabledName("a.jar"));
        assertEquals("Sodium-0.5", ContentReader.displayNameFromFile(new File("Sodium-0.5.jar.disabled")).replace(".8", ""));
    }

    @Test
    public void listIsSortedAndMissingFolderIsEmpty() throws IOException {
        File game = temp.newFolder("game");
        assertTrue(ContentList.scan(game, ContentKind.MODS).isEmpty());
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        new File(mods, "b-mod.jar").createNewFile();
        new File(mods, "A-mod.jar.disabled").createNewFile();
        new File(mods, "notes.txt").createNewFile();
        List<ContentItem> items = ContentList.scan(game, ContentKind.MODS);
        assertEquals(2, items.size());
        assertEquals("A-mod.jar", items.get(0).fileName());
        assertFalse(items.get(0).enabled);
        assertTrue(items.get(1).enabled);
        assertEquals(2, ContentList.count(game, ContentKind.MODS));
    }

    @Test
    public void sizeTextAndCacheKey() throws IOException {
        assertEquals("812 B", ContentItem.sizeText(812));
        assertEquals("48 KB", ContentItem.sizeText(48 * 1024 + 5));
        assertEquals("3.3 MB", ContentItem.sizeText(3_500_000));
        assertEquals("25 MB", ContentItem.sizeText(25L * 1024 * 1024));
        File folder = temp.newFolder("keys");
        File on = new File(folder, "a.jar");
        assertTrue(on.createNewFile());
        String before = new ContentItem(on, 0).cacheKey();
        File off = ContentActions.setEnabled(new ContentItem(on, 0), false);
        assertEquals(before, new ContentItem(off, 0).cacheKey());
    }

    // ---- actions

    @Test
    public void switchingRenamesAndBack() throws IOException {
        File folder = temp.newFolder("mods");
        File jar = new File(folder, "sodium.jar");
        assertTrue(jar.createNewFile());
        File off = ContentActions.setEnabled(new ContentItem(jar, 0), false);
        assertEquals("sodium.jar.disabled", off.getName());
        assertTrue(off.isFile());
        assertFalse(jar.exists());
        File on = ContentActions.setEnabled(new ContentItem(off, 0), true);
        assertEquals("sodium.jar", on.getName());
        assertTrue(on.isFile());
    }

    @Test
    public void switchingNeverOverwritesAndNeverTouchesFolders() throws IOException {
        File folder = temp.newFolder("mods");
        File jar = new File(folder, "a.jar");
        File old = new File(folder, "a.jar.disabled");
        assertTrue(jar.createNewFile());
        assertTrue(old.createNewFile());
        try {
            ContentActions.setEnabled(new ContentItem(jar, 0), false);
            fail("must not replace a file");
        } catch (IOException expected) {
            assertTrue(jar.exists() && old.exists());
        }
        File dir = new File(folder, "Pack");
        assertTrue(dir.mkdirs());
        try {
            ContentActions.setEnabled(new ContentItem(dir, 0), false);
            fail("folders cannot be switched");
        } catch (IOException expected) {
            assertTrue(dir.isDirectory());
        }
    }

    @Test
    public void deleteRemovesFilesAndFoldersButOnlyInsideTheFolder() throws IOException {
        File folder = temp.newFolder("resourcepacks");
        File file = new File(folder, "a.zip");
        assertTrue(file.createNewFile());
        ContentActions.delete(folder, new ContentItem(file, 1));
        assertFalse(file.exists());

        File dir = new File(folder, "Unpacked");
        assertTrue(new File(dir, "assets/minecraft").mkdirs());
        assertTrue(new File(dir, "assets/minecraft/x.png").createNewFile());
        ContentActions.delete(folder, new ContentItem(dir, 0));
        assertFalse(dir.exists());

        File outside = temp.newFile("precious.jar");
        try {
            ContentActions.delete(folder, new ContentItem(outside, 1));
            fail("must refuse a file outside the folder");
        } catch (IOException expected) {
            assertTrue(outside.exists());
        }
    }

    // ---- metadata

    @Test
    public void fabricMetadataPicksTheBiggestIcon() {
        String json = "{\"id\":\"sodium\",\"name\":\"Sodium\",\"version\":\"0.5.8\",\"description\":\"A renderer.\","
                + "\"icon\":{\"32\":\"a32.png\",\"128\":\"a128.png\",\"64\":\"a64.png\"}}";
        ModMetadata meta = ModMetadata.fromFabric(json);
        assertEquals("Sodium", meta.name);
        assertEquals("0.5.8", meta.version);
        assertEquals("A renderer.", meta.description);
        assertEquals("a128.png", meta.iconPath);
        assertEquals("icon.png", ModMetadata.fromFabric("{\"id\":\"x\",\"icon\":\"icon.png\"}").iconPath);
        assertEquals("x", ModMetadata.fromFabric("{\"id\":\"x\"}").name);
        assertTrue(ModMetadata.fromFabric("not json").isEmpty());
        assertTrue(ModMetadata.fromFabric(null).isEmpty());
    }

    @Test
    public void quiltMetadata() {
        String json = "{\"quilt_loader\":{\"id\":\"qsl\",\"version\":\"1.2\",\"metadata\":{\"name\":\"QSL\",\"description\":\"Library\",\"icon\":\"q.png\"}}}";
        ModMetadata meta = ModMetadata.fromQuilt(json);
        assertEquals("QSL", meta.name);
        assertEquals("1.2", meta.version);
        assertEquals("q.png", meta.iconPath);
    }

    @Test
    public void tomlMetadataReadsTheModBlockOnly() {
        String toml = "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\nlicense=\"MIT\"\n\n"
                + "[[mods]] #defines a mod\n".replace(" #defines a mod", "") 
                + "modId=\"jei\"\nversion=\"${file.jarVersion}\"\ndisplayName=\"Just Enough Items\" # the name\n"
                + "logoFile=\"jei.png\"\ndescription='''Item and recipe viewer.\nSecond line.'''\n\n"
                + "[[dependencies.jei]]\nmodId=\"forge\"\ndisplayName=\"Not this one\"\n";
        ModMetadata meta = ModMetadata.fromToml(toml);
        assertEquals("Just Enough Items", meta.name);
        assertEquals("", meta.version); // an unfilled ${...} placeholder is not a version
        assertEquals("jei.png", meta.iconPath);
        assertTrue(meta.description.startsWith("Item and recipe viewer."));
        assertTrue(ModMetadata.fromToml("nothing here").isEmpty());
    }

    @Test
    public void mcmodInfoBothShapes() {
        ModMetadata a = ModMetadata.fromMcmodInfo("[{\"modid\":\"old\",\"name\":\"Old Mod\",\"version\":\"1\",\"logoFile\":\"l.png\"}]");
        assertEquals("Old Mod", a.name);
        assertEquals("l.png", a.iconPath);
        ModMetadata b = ModMetadata.fromMcmodInfo("{\"modList\":[{\"modid\":\"id-only\"}]}");
        assertEquals("id-only", b.name);
        assertTrue(ModMetadata.fromMcmodInfo("garbage").isEmpty());
    }

    @Test
    public void packDescriptionAsPlainText() {
        assertEquals("Hello World", PackMetadata.description("{\"pack\":{\"description\":\"\\u00A7aHello \\u00A7lWorld\"}}"));
        assertEquals("One Two", PackMetadata.description("{\"pack\":{\"description\":[{\"text\":\"One \"},\"Two\"]}}"));
        assertEquals("Top Extra", PackMetadata.description("{\"pack\":{\"description\":{\"text\":\"Top \",\"extra\":[{\"text\":\"Extra\"}]}}}"));
        assertEquals("", PackMetadata.description("{}"));
        assertEquals("", PackMetadata.description("oops"));
        assertEquals("", PackMetadata.description(null));
    }

    // ---- reading files

    @Test
    public void readsAFabricModAndItsIcon() throws IOException {
        File jar = zip(temp.newFile("sodium.jar"),
                "fabric.mod.json", "{\"id\":\"sodium\",\"name\":\"Sodium\",\"version\":\"1.0\",\"icon\":\"assets/sodium/icon.png\"}",
                "assets/sodium/icon.png", "PNGDATA");
        ContentInfo info = ContentReader.read(jar, ContentKind.MODS);
        assertEquals("Sodium", info.name);
        assertEquals("1.0", info.version);
        assertTrue(info.hasIcon());
        assertArrayEquals("PNGDATA".getBytes(StandardCharsets.ISO_8859_1), info.icon);
        assertFalse(info.unreadable);
    }

    @Test
    public void aModWithoutMetadataFallsBackToItsFileName() throws IOException {
        File jar = zip(temp.newFile("Mystery-1.2.jar.disabled"), "some/Class.class", "x");
        ContentInfo info = ContentReader.read(jar, ContentKind.MODS);
        assertEquals("Mystery-1.2", info.name);
        assertFalse(info.hasIcon());
    }

    @Test
    public void aBrokenFileIsMarkedNotThrown() throws IOException {
        File jar = temp.newFile("broken.jar");
        try (FileOutputStream out = new FileOutputStream(jar)) {
            out.write("this is not a zip".getBytes(StandardCharsets.ISO_8859_1));
        }
        ContentInfo info = ContentReader.read(jar, ContentKind.MODS);
        assertTrue(info.unreadable);
        assertEquals("broken", info.name);
    }

    @Test
    public void readsAResourcePackFromAZipAndFromAFolder() throws IOException {
        File pack = zip(temp.newFile("Faithful.zip"),
                "pack.mcmeta", "{\"pack\":{\"pack_format\":15,\"description\":\"Sharp and clean\"}}", "pack.png", "ICON");
        ContentInfo zipInfo = ContentReader.read(pack, ContentKind.RESOURCE_PACKS);
        assertEquals("Faithful", zipInfo.name);
        assertEquals("Sharp and clean", zipInfo.description);
        assertTrue(zipInfo.hasIcon());

        File folder = temp.newFolder("Unpacked");
        try (FileOutputStream out = new FileOutputStream(new File(folder, "pack.mcmeta"))) {
            out.write("{\"pack\":{\"description\":\"From a folder\"}}".getBytes(StandardCharsets.UTF_8));
        }
        ContentInfo folderInfo = ContentReader.read(folder, ContentKind.RESOURCE_PACKS);
        assertEquals("From a folder", folderInfo.description);
        assertFalse(folderInfo.hasIcon());
    }

    @Test
    public void entriesBiggerThanTheLimitAreIgnored() throws IOException {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 1024 * 1024 + 10; i++) big.append('x');
        File jar = zip(temp.newFile("huge.jar"),
                "fabric.mod.json", "{\"name\":\"Huge\",\"icon\":\"big.png\"}", "big.png", big.toString());
        ContentInfo info = ContentReader.read(jar, ContentKind.MODS);
        assertEquals("Huge", info.name);
        assertNull(info.icon);
    }
}
