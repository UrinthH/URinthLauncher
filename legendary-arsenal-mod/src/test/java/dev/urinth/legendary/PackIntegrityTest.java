package dev.urinth.legendary;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the FINISHED jar (after remapping, the file that is handed out) and checks that the resource pack inside it is complete and
 * follows the rules the game applies to item models in 1.21.4. Nothing here starts the game; a failure names the exact file.
 */
class PackIntegrityTest {
    private static final String PACK = "resourcepacks/legendary_arsenal/";
    private static final String NS = "urinth_legend";
    private static final Set<String> FACES = Set.of("north", "south", "east", "west", "up", "down");
    private static final Set<Double> ANGLES = Set.of(-45.0, -22.5, 0.0, 22.5, 45.0);
    private static final Set<String> FIRST_PERSON = Set.of("firstperson_righthand", "firstperson_lefthand");

    private static ZipFile jar;
    private static final Set<String> names = new TreeSet<>();

    @BeforeAll
    static void open() throws IOException {
        String path = System.getProperty("urinth.jar");
        assertNotNull(path, "the build did not tell the test where the jar is");
        jar = new ZipFile(path);
        jar.stream().forEach(e -> names.add(e.getName()));
    }

    @AfterAll
    static void close() throws IOException {
        if (jar != null) jar.close();
    }

    private static JsonObject json(String name) throws IOException {
        ZipEntry e = jar.getEntry(name);
        assertNotNull(e, "missing in the jar: " + name);
        try (Reader r = new InputStreamReader(jar.getInputStream(e), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    private static List<String> under(String prefix, String suffix) {
        List<String> out = new ArrayList<>();
        for (String n : names) if (n.startsWith(prefix) && n.endsWith(suffix)) out.add(n);
        return out;
    }

    private static String stripNs(String id) {
        int c = id.indexOf(':');
        return c < 0 ? id : id.substring(c + 1);
    }

    @Test
    void modDescriptionIsComplete() throws IOException {
        JsonObject mod = json("fabric.mod.json");
        assertEquals("legendary_arsenal", mod.get("id").getAsString());
        String version = mod.get("version").getAsString();
        assertFalse(version.contains("$"), "the version was not filled in: " + version);
        JsonObject depends = mod.getAsJsonObject("depends");
        assertTrue(depends.has("fabric-api") && depends.has("minecraft") && depends.has("fabricloader"));
        String entry = mod.getAsJsonObject("entrypoints").getAsJsonArray("client").get(0).getAsString();
        assertTrue(names.contains(entry.replace('.', '/') + ".class"), "the entry point class is not in the jar: " + entry);
        assertTrue(names.contains(mod.get("icon").getAsString()), "the mod icon is not in the jar");
        // The pack has no code of its own: nothing here may need a mixin, and none must be left over.
        assertFalse(mod.has("mixins"), "this mod has no mixins");
    }

    @Test
    void packMetadataIsForMinecraft1214() throws IOException {
        JsonObject meta = json(PACK + "pack.mcmeta").getAsJsonObject("pack");
        assertEquals(46, meta.get("pack_format").getAsInt(), "pack_format 46 is Minecraft 1.21.4");
        assertTrue(names.contains(PACK + "pack.png"), "the pack has no icon");
    }

    @Test
    void everyItemShowsTheLegendaryModelInTheOwnersHandsOnly() throws IOException {
        List<String> items = under(PACK + "assets/minecraft/items/", ".json");
        assertFalse(items.isEmpty(), "the pack has no item definitions");
        for (String item : items) {
            JsonObject model = json(item).getAsJsonObject("model");
            assertEquals("minecraft:select", model.get("type").getAsString(), item);
            assertEquals("minecraft:display_context", model.get("property").getAsString(), item);
            assertTrue(model.has("fallback"), item + ": without a fallback every other view of the item would be invisible");
            JsonArray cases = model.getAsJsonArray("cases");
            assertEquals(1, cases.size(), item);
            JsonObject only = cases.get(0).getAsJsonObject();
            for (JsonElement w : only.getAsJsonArray("when")) {
                assertTrue(FIRST_PERSON.contains(w.getAsString()), item + ": the legendary model must not be used for " + w);
            }
            String target = only.getAsJsonObject("model").get("model").getAsString();
            assertTrue(target.startsWith(NS + ":item/"), item + " points at " + target);
            assertTrue(names.contains(PACK + "assets/" + NS + "/models/" + stripNs(target) + ".json"), item + ": no model file for " + target);
        }
    }

    @Test
    void everyModelObeysTheGamesRules() throws IOException {
        List<String> models = under(PACK + "assets/" + NS + "/models/item/", ".json");
        assertFalse(models.isEmpty());
        for (String file : models) {
            JsonObject m = json(file);
            JsonObject textures = m.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> t : textures.entrySet()) {
                String png = PACK + "assets/" + NS + "/textures/" + stripNs(t.getValue().getAsString()) + ".png";
                assertTrue(names.contains(png), file + ": the picture " + png + " is missing");
                BufferedImage img;
                try (InputStream in = jar.getInputStream(jar.getEntry(png))) {
                    img = ImageIO.read(in);
                }
                assertNotNull(img, png + " is not a readable PNG");
                // A square picture is required: the game cuts a picture without animation data into squares of its short side.
                assertEquals(img.getWidth(), img.getHeight(), png + " must be square");
                assertTrue(img.getWidth() >= 64 && img.getWidth() <= 1024, png + " has an unexpected size " + img.getWidth());
            }
            JsonObject display = m.getAsJsonObject("display");
            assertNotNull(display, file + ": no display transform");
            for (String ctx : display.keySet()) {
                assertTrue(FIRST_PERSON.contains(ctx), file + ": display for " + ctx + " would change other views");
            }
            JsonArray elements = m.getAsJsonArray("elements");
            assertTrue(elements.size() > 0 && elements.size() < 2500, file + ": " + elements.size() + " elements");
            for (JsonElement el : elements) {
                JsonObject e = el.getAsJsonObject();
                for (String key : new String[] {"from", "to"}) {
                    for (JsonElement v : e.getAsJsonArray(key)) {
                        double d = v.getAsDouble();
                        assertTrue(d >= -16 && d <= 32, file + ": " + key + " " + d + " is outside -16..32");
                    }
                }
                if (e.has("rotation")) {
                    double a = e.getAsJsonObject("rotation").get("angle").getAsDouble();
                    assertTrue(ANGLES.contains(a), file + ": rotation angle " + a + " is not allowed");
                }
                if (e.has("light_emission")) {
                    int l = e.get("light_emission").getAsInt();
                    assertTrue(l >= 0 && l <= 15, file + ": light_emission " + l);
                }
                for (Map.Entry<String, JsonElement> f : e.getAsJsonObject("faces").entrySet()) {
                    assertTrue(FACES.contains(f.getKey()), file + ": unknown face " + f.getKey());
                    JsonObject face = f.getValue().getAsJsonObject();
                    String tex = face.get("texture").getAsString();
                    assertTrue(tex.startsWith("#") && textures.has(tex.substring(1)), file + ": face uses an undefined texture " + tex);
                    JsonArray uv = face.getAsJsonArray("uv");
                    assertEquals(4, uv.size(), file);
                    for (JsonElement u : uv) {
                        double d = u.getAsDouble();
                        assertTrue(d >= 0 && d <= 16, file + ": uv " + d + " is outside 0..16");
                    }
                }
            }
        }
    }

    @Test
    void everyPictureIsUsedAndNothingElseIsInThePack() {
        Set<String> used = new TreeSet<>();
        List<String> models = under(PACK + "assets/" + NS + "/models/item/", ".json");
        for (String file : models) {
            try {
                for (Map.Entry<String, JsonElement> t : json(file).getAsJsonObject("textures").entrySet()) {
                    used.add(PACK + "assets/" + NS + "/textures/" + stripNs(t.getValue().getAsString()) + ".png");
                }
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        for (String png : under(PACK + "assets/" + NS + "/textures/", ".png")) {
            assertTrue(used.contains(png), "no model uses " + png);
        }
        for (String n : names) {
            if (!n.startsWith(PACK) || n.endsWith("/")) continue;
            assertTrue(n.endsWith(".json") || n.endsWith(".png") || n.endsWith(".mcmeta"), "unexpected file in the pack: " + n);
            assertFalse(n.contains("__pycache__") || n.contains(".DS_Store"), "junk in the pack: " + n);
        }
    }
}
