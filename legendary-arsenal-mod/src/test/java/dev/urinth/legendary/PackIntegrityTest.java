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
    /** Every item the pack covers: five tool kinds in six tiers, and the mace. */
    private static final List<String> TIERS = List.of("wooden", "stone", "iron", "golden", "diamond", "netherite");
    private static final List<String> KINDS = List.of("sword", "axe", "pickaxe", "shovel", "hoe");
    /** What the phone has to keep in its texture atlas for the whole pack (the atlas must stay at 4096 x 2048). */
    private static final long PIXEL_BUDGET = 6_000_000L;

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

    private static String baseName(String file) {
        return file.substring(file.lastIndexOf('/') + 1, file.length() - ".json".length());
    }

    private static double[] vec3(JsonObject d, String key, String where) {
        assertTrue(d.has(key) && d.get(key).isJsonArray() && d.getAsJsonArray(key).size() == 3, where + ": " + key + " needs three numbers");
        JsonArray a = d.getAsJsonArray(key);
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
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
    void thePackCoversEverySwordAxeToolAndTheMace() {
        Set<String> expected = new TreeSet<>();
        for (String tier : TIERS) for (String kind : KINDS) expected.add(tier + "_" + kind);
        expected.add("mace");
        Set<String> items = new TreeSet<>();
        for (String f : under(PACK + "assets/minecraft/items/", ".json")) items.add(baseName(f));
        assertEquals(expected, items, "the pack must replace exactly these items");
        Set<String> models = new TreeSet<>();
        for (String f : under(PACK + "assets/" + NS + "/models/item/", ".json")) models.add(baseName(f));
        assertEquals(expected, models, "every item needs its own model");
    }

    @Test
    void everyItemShowsTheLegendaryModelInTheOwnersHandsOnly() throws IOException {
        List<String> items = under(PACK + "assets/minecraft/items/", ".json");
        assertFalse(items.isEmpty(), "the pack has no item definitions");
        for (String item : items) {
            String id = baseName(item);
            JsonObject model = json(item).getAsJsonObject("model");
            assertEquals("minecraft:select", model.get("type").getAsString(), item);
            assertEquals("minecraft:display_context", model.get("property").getAsString(), item);
            // Every other view of the item (other players, third person, inventory, dropped, frames) must stay the vanilla model.
            assertTrue(model.has("fallback"), item + ": without a fallback every other view of the item would be invisible");
            JsonObject fallback = model.getAsJsonObject("fallback");
            assertEquals("minecraft:model", fallback.get("type").getAsString(), item);
            assertEquals("minecraft:item/" + id, fallback.get("model").getAsString(), item + ": the fallback must be the normal item");
            JsonArray cases = model.getAsJsonArray("cases");
            assertEquals(1, cases.size(), item);
            JsonObject only = cases.get(0).getAsJsonObject();
            Set<String> when = new TreeSet<>();
            for (JsonElement w : only.getAsJsonArray("when")) when.add(w.getAsString());
            assertEquals(new TreeSet<>(FIRST_PERSON), when, item + ": the legendary model is for the two first-person hands and nothing else");
            String target = only.getAsJsonObject("model").get("model").getAsString();
            assertEquals(NS + ":item/" + id, target, item + " points at " + target);
            assertTrue(names.contains(PACK + "assets/" + NS + "/models/" + stripNs(target) + ".json"), item + ": no model file for " + target);
        }
    }

    @Test
    void everyWeaponIsHeldTheNormalMinecraftWay() throws IOException {
        List<String> models = under(PACK + "assets/" + NS + "/models/item/", ".json");
        assertFalse(models.isEmpty());
        for (String file : models) {
            JsonObject display = json(file).getAsJsonObject("display");
            assertNotNull(display, file + ": no display transform");
            for (String ctx : display.keySet()) {
                assertTrue(FIRST_PERSON.contains(ctx), file + ": display for " + ctx + " would change other views");
            }
            assertTrue(display.has("firstperson_righthand") && display.has("firstperson_lefthand"), file + ": both hands need a pose");
            JsonObject right = display.getAsJsonObject("firstperson_righthand");
            JsonObject left = display.getAsJsonObject("firstperson_lefthand");
            double[] rr = vec3(right, "rotation", file + " right"), rt = vec3(right, "translation", file + " right"), rs = vec3(right, "scale", file + " right");
            double[] lr = vec3(left, "rotation", file + " left"), lt = vec3(left, "translation", file + " left"), ls = vec3(left, "scale", file + " left");
            // Like every vanilla handheld item: turned 90 degrees about Y, so the blade points up and forward, not flat at the player's face.
            assertEquals(0.0, rr[0], 1e-6, file + ": no tilt about X");
            assertEquals(-90.0, rr[1], 1e-6, file + ": the right hand holds the weapon turned -90 degrees about Y, like a vanilla sword");
            // The game mirrors the left hand itself (it negates the Y and Z rotation), so the left entry is the mirrored one.
            assertEquals(rr[0], lr[0], 1e-6, file);
            assertEquals(-rr[1], lr[1], 1e-6, file + ": the left hand is the mirror of the right");
            assertEquals(-rr[2], lr[2], 1e-6, file + ": the left hand is the mirror of the right");
            for (int i = 0; i < 3; i++) {
                assertEquals(rt[i], lt[i], 1e-6, file + ": translation is the same for both hands (the game mirrors X)");
                assertEquals(rs[i], ls[i], 1e-6, file + ": scale is the same for both hands");
                assertTrue(Math.abs(rt[i]) <= 80, file + ": translation outside the game's clamp");
                assertTrue(rs[i] > 0 && rs[i] <= 4, file + ": scale outside the game's clamp");
            }
            // A normal-sized weapon: the vanilla hold is 0.68 (0.9 for the mace) and ours are painted a little larger, never huge.
            assertTrue(rs[0] >= 0.5 && rs[0] <= 1.4, file + ": scale " + rs[0] + " would not look like a normal weapon");
            assertTrue(Math.abs(rr[2]) <= 45, file + ": roll " + rr[2] + " is not a normal hold");
        }
    }

    @Test
    void everyModelObeysTheGamesRules() throws IOException {
        List<String> models = under(PACK + "assets/" + NS + "/models/item/", ".json");
        assertFalse(models.isEmpty());
        long pixels = 0;
        for (String file : models) {
            JsonObject m = json(file);
            JsonObject textures = m.getAsJsonObject("textures");
            assertTrue(textures.has("layer0"), file + ": no layer0 picture");
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
                assertTrue(img.getColorModel().hasAlpha(), png + " needs transparency around the weapon");
                // The picture is one weapon on a see-through background, not a solid square.
                long clear = 0, total = (long) img.getWidth() * img.getHeight();
                for (int y = 0; y < img.getHeight(); y += 2) {
                    for (int x = 0; x < img.getWidth(); x += 2) {
                        if ((img.getRGB(x, y) >>> 24) == 0) clear++;
                    }
                }
                assertTrue(clear * 4 > total * 6 / 10, png + " is mostly filled: a weapon picture is mostly transparent");
                if (t.getKey().equals("layer0")) pixels += total;
            }
            JsonArray elements = m.getAsJsonArray("elements");
            // A flat card: a handful of elements at most. The game is drawing this in your hand every frame.
            assertTrue(elements.size() > 0 && elements.size() <= 8, file + ": " + elements.size() + " elements");
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
        assertTrue(pixels <= PIXEL_BUDGET, "the pictures hold " + pixels + " pixels, more than the " + PIXEL_BUDGET + " the phone's texture atlas can take");
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
