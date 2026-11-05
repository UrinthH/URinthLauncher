#version 150

uniform float AmbientLightFactor;
uniform float SkyFactor;
uniform float BlockFactor;
uniform int UseBrightLightmap;
uniform vec3 SkyLightColor;
uniform float NightVisionFactor;
uniform float DarknessScale;
uniform float DarkenWorldFactor;
uniform float BrightnessFactor;

in vec2 texCoord;

out vec4 fragColor;

float get_brightness(float level) {
    float curved_level = level / (4.0 - 3.0 * level);
    return mix(curved_level, 1.0, AmbientLightFactor);
}

vec3 notGamma(vec3 x) {
    vec3 nx = 1.0 - x;
    return 1.0 - nx * nx * nx * nx;
}

void main() {
    float block_brightness = get_brightness(floor(texCoord.x * 16) / 15) * BlockFactor;
    float sky_brightness = get_brightness(floor(texCoord.y * 16) / 15) * SkyFactor;

    // cubic nonsense, dips to yellowish in the middle, white when fully saturated
    vec3 color = vec3(
        block_brightness,
        block_brightness * ((block_brightness * 0.6 + 0.4) * 0.6 + 0.4),
        block_brightness * (block_brightness * block_brightness * 0.6 + 0.4)
    );

    if (UseBrightLightmap != 0) {
        color = mix(color, vec3(0.99, 1.12, 1.0), 0.25);
        color = clamp(color, 0.0, 1.0);
    } else {
        color += SkyLightColor * sky_brightness;
        color = mix(color, vec3(0.75), 0.04);

        vec3 darkened_color = color * vec3(0.7, 0.6, 0.6);
        color = mix(color, darkened_color, DarkenWorldFactor);
    }

    if (NightVisionFactor > 0.0) {
        // scale up uniformly until 1.0 is hit by one of the colors
        float max_component = max(color.r, max(color.g, color.b));
        if (max_component < 1.0) {
            vec3 bright_color = color / max_component;
            color = mix(color, bright_color, NightVisionFactor);
        }
    }

    if (UseBrightLightmap == 0) {
        color = clamp(color - vec3(DarknessScale), 0.0, 1.0);
    }

    vec3 notGamma = notGamma(color);
    color = mix(color, notGamma, BrightnessFactor);
    color = mix(color, vec3(0.75), 0.04);
    color = clamp(color, 0.0, 1.0);

    // URinth Lighting (overworld only): this picture is 16 x 16 pixels and is made once per tick, so it costs nothing per frame.
    // Light that comes from the sky gets warm where the sun reaches and cool and darker in shadow; torch light is left alone.
    // Under leaves and roofs the sky light is low, so the shade zone grows and the colour shifts cool/blue.
    if (UseBrightLightmap == 0) {
        float sunlit = smoothstep(0.55, 1.0, sky_brightness);
        // shade is strong when sky_brightness is low (under leaves, roofs, indoors); 0.6 threshold makes it kick in earlier
        float shade = 1.0 - smoothstep(0.0, 0.60, sky_brightness);
        // how much of this pixel's light comes from the sky vs a torch (block light)
        float skyShare = 1.0 - clamp(block_brightness * 1.5, 0.0, 1.0);
        vec3 warm = vec3(1.05, 1.0, 0.92);
        // cooler / bluer in deep shade: noticeable contrast between sunlit and covered areas
        vec3 cool = vec3(0.80, 0.90, 1.14);
        vec3 tintColor = mix(vec3(1.0), warm, sunlit * 0.85) * mix(vec3(1.0), cool, shade * 0.95);
        color *= mix(vec3(1.0), tintColor, skyShare);
        // steeper brightness curve in shaded sky-lit areas so covered spots look noticeably darker
        // exponent 1.35 is stronger than the old 1.22; full-sun pixels are at 1.0 so they stay bright
        vec3 steep = pow(clamp(color, 0.0, 1.0), vec3(1.35));
        // apply the steep curve more in shade (0.80 * skyShare) so torchlight indoors stays warm and readable
        color = mix(color, steep, 0.80 * skyShare + 0.25 * (1.0 - skyShare));
        color = clamp(color, 0.0, 1.0);
    }

    fragColor = vec4(color, 1.0);
}
