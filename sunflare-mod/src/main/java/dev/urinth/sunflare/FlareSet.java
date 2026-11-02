package dev.urinth.sunflare;

import static dev.urinth.sunflare.FlareElement.Geo;

/**
 * Every element of the flare, with the numbers taken from the pack's lib/LensFlare.glsl.
 * Colours already include the pack's global flare multipliers (red 0.8, green 1.0, blue 1.5).
 */
final class FlareSet {
    private FlareSet() {}

    private static final double MR = 0.8;
    private static final double MG = 1.0;
    private static final double MB = 1.5;
    private static final double PI = 3.1415;

    private static FlareElement single(String name, FlareProfile p, Geo g, double r, double gr, double b) {
        return new FlareElement(name, p, g, null, null, false, r * MR, gr * MG, b * MB, false);
    }

    /** The same element, but with a six-bladed aperture outline instead of a round one. */
    private static FlareElement hex(FlareElement e) {
        return e.withShape(FlareElement.Shape.HEX);
    }

    /** An element with its colour given as is (the pack's red/green/blue flare multipliers are not applied). */
    private static FlareElement plain(String name, FlareProfile p, Geo g, double r, double gr, double b, FlareElement.Shape shape) {
        return new FlareElement(name, p, g, null, null, false, r, gr, b, false, shape);
    }

    private static FlareElement edge(String name, FlareProfile p, Geo g, double r, double gr, double b) {
        return new FlareElement(name, p, g, null, null, false, r * MR, gr * MG, b * MB, true);
    }

    private static FlareElement pair(String name, FlareProfile p, Geo g, FlareProfile s, Geo sg, boolean smOutside,
                                     double r, double gr, double b) {
        return new FlareElement(name, p, g, s, sg, smOutside, r * MR, gr * MG, b * MB, false);
    }

    static final FlareElement[] ELEMENTS = {
        single("1", FlareProfile.linear(0.5, 1.8, 12.0), Geo.sun(1.7, 1.7, true), 0.7, 0.4, 0.2),
        single("1B", FlareProfile.linear(0.5, 1.8, 6.0), Geo.sun(0.5, 0.5, true), 0.7, 0.2, 0.0),
        single("2", FlareProfile.linear(0.3, 1.8, 1.0), Geo.sun(0.2, 1.0, false), 1.8, 0.6, 0.0),
        single("2B", FlareProfile.linear(0.3, 1.8, 1.0), Geo.sun(0.2, 4.0, false), 1.2, 0.5, 0.0),
        pair("3", FlareProfile.sinFill(10.0, 1.1, 0.7, true), Geo.ghost(2.0, -0.5),
                FlareProfile.sinFill(2.0, 0.9, 1.0, true), Geo.ghost(1.4, -0.65), false, 0.0, 0.3, 1.0),
        pair("3C", FlareProfile.sinFill(10.0, 1.1, 1.4, false), Geo.ghost(3.2, 0.0),
                FlareProfile.sinFill(1.4, 0.9, 2.7, false), Geo.ghost(2.1, -0.05), true, 0.4, 0.7, 1.0),
        hex(single("4", FlareProfile.sinFill(3.0, 1.1, 0.3, true), Geo.ghost(4.5, -0.1), 0.6, 0.0, 0.8)),
        hex(single("4B", FlareProfile.sinFill(2.0, 1.1, 0.4, true), Geo.ghost(7.5, 0.0), 0.4, 0.0, 0.8)),
        hex(single("4C", FlareProfile.sinFill(2.0, 1.1, 2.0, true), Geo.ghost(37.5, -0.3), 0.2, 0.6, 0.8)),
        hex(single("4D", FlareProfile.sinFill(2.0, 1.1, 1.0, true), Geo.ghost(67.5, -0.35), 0.2, 0.2, 0.8)),
        hex(single("4E", FlareProfile.sinFill(3.0, 1.1, 1.0, true), Geo.ghost(60.5, -0.3393), 0.2, 0.2, 0.6)),
        hex(single("4F", FlareProfile.sinFill(3.0, 1.1, 3.0, true), Geo.ghost(20.5, -0.4713), 0.6, 0.1, 0.1)),
        single("5", FlareProfile.fill(1.0, 1.9, 13.4), Geo.ghost(3.2, -2.0), 0.9, 0.4, 0.3),
        single("5B", FlareProfile.fill(2.0, 1.4, 1.4), Geo.ghost(0.5, 3.5, -1.9), 0.9, 0.3, 0.0),
        single("6", FlareProfile.sinPow(5.0, 1.6, 0.2, PI), Geo.ghost(1.2, -1.9), 0.6, 0.0, 0.0),
        single("6B", FlareProfile.sinPow(5.0, 1.6, 0.2, PI), Geo.ghost(1.1, -1.9), 0.0, 0.4, 0.0),
        single("6C", FlareProfile.sinPow(5.0, 1.8, 0.2, PI), Geo.ghost(0.9, -1.9), 0.0, 0.0, 0.4),
        single("7", FlareProfile.sinPow(10.0, 1.9, 0.2, PI), Geo.ghost(0.4, 2.6), 1.0, 0.0, 0.0),
        single("7D", FlareProfile.sinPow(10.0, 1.9, 0.1, PI), Geo.ghost(0.39, 2.6), 0.0, 0.6, 0.0),
        single("7B", FlareProfile.sinPow(2.0, 1.9, 0.1, PI * 0.5), Geo.ghost(0.2, 2.9), 1.0, 0.0, 0.0),
        edge("8", FlareProfile.fill(12.0, 1.4, 0.5), new Geo(0.3, 40.5, 1.0, 1.0, false, false, true), 0.0, 0.3, 0.8),
        edge("9", FlareProfile.fill(2.0, 1.4, 1.9), new Geo(0.2, 5.5, 1.0, 0.0, false, true, true), 0.2, 0.4, 0.9),
        pair("10", FlareProfile.sinFill(1.1, 1.1, 1.9, true), Geo.ghost(6.0, -0.7),
                FlareProfile.sinFill(1.0, 0.9, 1.5, true), Geo.ghost(5.1, -0.77), false, 0.8, 0.2, 0.0),
        pair("10C", FlareProfile.sinFill(1.1, 1.1, 1.9, true), Geo.ghost(6.0, -0.6),
                FlareProfile.sinFill(1.0, 0.9, 1.5, true), Geo.ghost(5.1, -0.67), false, 0.0, 0.2, 0.9),
        single("11", FlareProfile.fillPow(2.0, 2.9, 1.1), Geo.ghost(1.5, -0.523), 1.0, 0.2, 0.0),
        single("12", FlareProfile.fillPow(2.0, 2.9, 0.5), Geo.ghost(2.5, -0.323), 0.8, 0.2, 1.0),
        single("13", FlareProfile.fillPow(2.0, 2.9, 0.5), Geo.ghost(1.0, 0.138), 0.0, 0.2, 1.0),
        // Added for realism (not in the pack). Both sit on the sun and keep their size when the sun is near the centre.
        // S: diffraction spikes of a six-bladed aperture, a warm white that fades with distance. Drawn with the big layers,
        // so it costs next to nothing; at 35% lens power its brightest part is about a third of full.
        plain("S", FlareProfile.linear(0.5, 1.6, 3.6), Geo.sun(0.85, 0.85, false), 1.0, 0.92, 0.78, FlareElement.Shape.STAR),
        // C: a tight hot core, so the sun itself blooms instead of staying a hard square.
        plain("C", FlareProfile.linear(0.5, 2.2, 9.0), Geo.sun(4.0, 4.0, false), 1.0, 0.95, 0.85, FlareElement.Shape.ROUND),
        // H: a faint rainbow halo around the sun, like the dispersion ring of a real lens. Three rings of slightly different size
        // (red smallest, blue largest) overlap to give the colour fringe. They keep their size when the sun is near the centre.
        plain("HR", FlareProfile.sinPow(8.0, 1.4, 0.20, PI), Geo.sun(1.50, 1.50, false), 1.0, 0.0, 0.0, FlareElement.Shape.ROUND),
        plain("HG", FlareProfile.sinPow(8.0, 1.4, 0.20, PI), Geo.sun(1.42, 1.42, false), 0.0, 1.0, 0.0, FlareElement.Shape.ROUND),
        plain("HB", FlareProfile.sinPow(8.0, 1.4, 0.20, PI), Geo.sun(1.34, 1.34, false), 0.0, 0.0, 1.0, FlareElement.Shape.ROUND),
    };

    static final int COUNT = ELEMENTS.length;
}
