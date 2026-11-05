package dev.urinth.sunflare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The built-in lighting pack must stay a drop-in replacement for the game's lightmap shader: same inputs, same output. */
class LightingPackTest {
    private static String read(String path) throws IOException {
        try (InputStream in = LightingPackTest.class.getClassLoader().getResourceAsStream(path)) {
            assertTrue(in != null, path + " is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void packHasTheRightFormatForThisGameVersion() throws IOException {
        String mcmeta = read("resourcepacks/lighting/pack.mcmeta");
        assertTrue(mcmeta.contains("\"pack_format\": 46"), "1.21.4 resource packs are format 46");
    }

    @Test
    void lightmapShaderKeepsEveryUniformAndTheOutputOfTheGamesOwn() throws IOException {
        String fsh = read("resourcepacks/lighting/assets/minecraft/shaders/core/lightmap.fsh");
        assertTrue(fsh.startsWith("#version 150"));
        for (String u : new String[] {"AmbientLightFactor", "SkyFactor", "BlockFactor", "UseBrightLightmap", "SkyLightColor",
            "NightVisionFactor", "DarknessScale", "DarkenWorldFactor", "BrightnessFactor"}) {
            assertTrue(fsh.contains("uniform ") && fsh.contains(" " + u + ";"), "uniform " + u);
        }
        assertTrue(fsh.contains("in vec2 texCoord;"));
        assertTrue(fsh.contains("out vec4 fragColor;"));
        assertTrue(fsh.contains("fragColor = vec4(color, 1.0);"));
        int open = 0, close = 0;
        for (char c : fsh.toCharArray()) {
            if (c == '{') open++;
            if (c == '}') close++;
        }
        assertEquals(open, close, "braces balance");
        assertTrue(fsh.contains("URinth Lighting"));
    }
}
