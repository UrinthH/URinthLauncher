package dev.urinth.sunflare;

/**
 * Works out, for one frame, which flare elements to draw and where. Plain maths: no Minecraft classes.
 * All screen positions are in 0..1 with v pointing up.
 */
final class FlareFrame {
    /** One quad to draw. */
    static final class Draw {
        int element;
        double cx;
        double cy;
        double rx;
        double ry;
        double red;
        double green;
        double blue;
        /** The element's colour in the active style, before the brightness is applied (crescents are scaled by their baked level). */
        double baseR;
        double baseG;
        double baseB;
        /** Set for crescent pairs: this quad uses a texture baked for this frame. */
        boolean pair;
        /** 0..1: only this inner fraction of the ellipse is brighter than the output can show. The quad is cut to it. */
        double support;
        /** Small, sharp elements are drawn straight to the screen; only the big soft ones go through the small buffer. */
        boolean small;
    }

    /** Below about one level of the 8-bit output an element is not worth drawing, and neither is its faint tail. */
    static final double MIN_AMPLITUDE = 1.2 / 255.0;
    /** Quads smaller than this share of the screen are drawn at full resolution. */
    static final double SMALL_AREA = 0.05;

    /** Least strength of the veil (compared with a sunset's) while the sun is out of view in daytime. */
    static final double VEIL_FLOOR = 0.45;

    /** True: the photographic style (the Real elements); false: the pack's own. Set before {@link #compute}. */
    boolean real;
    private final double[] tint = new double[3];
    private final double[] sunColour = new double[3];
    final Draw[] draws = new Draw[FlareSet.COUNT];
    int count;
    /** The pack's sunmask for this frame. */
    double sm;
    /** How much the whole picture is darkened (0..1), as the pack does when the sun is near the screen centre. */
    double dim;
    double flareScale;

    FlareFrame() {
        for (int i = 0; i < draws.length; i++) draws[i] = new Draw();
    }

    /** The pack's way: one visibility for everything. */
    void compute(double lx, double ly, double aspect, double power, double mask, double night, double rain) {
        compute(lx, ly, aspect, power, mask, 1.0, 1.0, night, rain, 0.5);
    }

    /**
     * @param lx,ly     sun position on the screen (0..1, v up). May be outside the screen in the Real style.
     * @param power     the pack's LENS_POWER
     * @param vis       0..1 how much of the sun can be seen from the player (trees, hills and water in the way)
     * @param edge      0..1 fade of the reflections towards the screen edges (see {@link SunMath#edgeFade})
     * @param reach     0..1 how far the glow still reaches into the picture when the sun is off screen (see {@link SunMath#glowReach})
     * @param sunHeight sine of the sun's height above the horizon (1 = overhead), for the colour and the sunset veil
     */
    void compute(double lx, double ly, double aspect, double power, double vis, double edge, double reach, double night, double rain, double sunHeight) {
        count = 0;
        double base = power * (1.0 - night) * (1.0 - SunMath.clamp(rain, 0.0, 1.0));
        sm = base * vis * edge;
        dim = 0.0;
        flareScale = 1.0;
        double smGlow = base * vis * reach;
        // out of view, a low sun still warms the picture a little: the veil takes over from the glow
        // (and whenever the sun is out of view, even behind the player, a gentle warm wash stays: the sun is still there)
        double smVeil = real ? base * vis * (1.0 - reach) * Math.max(VEIL_FLOOR, Math.pow(SunTint.warmth(sunHeight), 1.2)) * 0.5 : 0.0;
        if (real) {
            if (smGlow <= 0.0 && sm <= 0.0 && smVeil <= 0.0) return;
        } else if (sm <= 0.0) {
            return;
        }
        SunTint.colour(sunHeight, sunColour);
        double boost = real ? SunTint.boost(sunHeight) : 1.0;

        double dist = Math.hypot(lx - 0.5, ly - 0.5);
        double centerMask = (1.0 - SunMath.clamp(dist * 2.0, 0.0, 1.0)) * sm;
        dim = centerMask;
        flareScale = 1.0 - centerMask;
        double edgeX = SunMath.clamp(Math.abs(lx - 0.5) * 8.0 - 3.0, 0.0, 1.0);

        int from = real ? FlareSet.PACK_COUNT : 0;
        int to = real ? FlareSet.COUNT : FlareSet.PACK_COUNT;
        for (int i = from; i < to; i++) {
            FlareElement e = FlareSet.ELEMENTS[i];
            double m;
            if (!real) {
                m = sm;
                tint[0] = e.red;
                tint[1] = e.green;
                tint[2] = e.blue;
            } else {
                m = e.role == FlareElement.Role.GLOW ? smGlow : e.role == FlareElement.Role.VEIL ? smVeil : sm;
                FlareSet.colour(e, sunColour, tint);
                for (int c = 0; c < 3; c++) tint[c] *= e.role == FlareElement.Role.GLOW || e.role == FlareElement.Role.VEIL ? boost : 1.0;
            }
            if (m <= 0.0) continue;
            double amp = amplitude(e, m) * (e.edgeMaskX ? edgeX : 1.0);
            double top = Math.max(tint[0], Math.max(tint[1], tint[2])) * amp;
            if (top < MIN_AMPLITUDE) continue;
            double support = FlareLut.support(e.main, MIN_AMPLITUDE / top);
            if (support <= 0.0) continue;
            Draw d = draws[count++];
            d.support = support;
            d.element = i;
            d.pair = e.isPair();
            d.cx = e.mainGeo.centerU(lx);
            d.cy = e.mainGeo.centerV(ly);
            d.rx = e.mainGeo.radiusU(e.main.radius, aspect, flareScale);
            d.ry = e.mainGeo.radiusV(e.main.radius, flareScale);
            d.small = 4.0 * d.rx * d.ry * support * support < SMALL_AREA;
            d.baseR = tint[0];
            d.baseG = tint[1];
            d.baseB = tint[2];
            d.red = tint[0] * amp;
            d.green = tint[1] * amp;
            d.blue = tint[2] * amp;
        }
    }

    /** Brightness at the centre-most point of an element for a given sm (the texture stores the shape, this the level). */
    static double amplitude(FlareElement e, double sm) {
        if (e.isPair()) {
            // the pair is baked per frame, so only an upper bound is needed here
            double a = FlareLut.peak(e.main) * (e.smOutside ? sm : Math.pow(sm, e.main.smExponent()));
            return a;
        }
        return FlareLut.peak(e.main) * Math.pow(sm, e.main.smExponent());
    }
}
