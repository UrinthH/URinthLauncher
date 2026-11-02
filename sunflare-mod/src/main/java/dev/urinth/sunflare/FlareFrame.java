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

    /** True: the photographic style; false: the pack's own colours. Set before {@link #compute}. */
    boolean real;
    private final double[] tint = new double[3];
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

    /**
     * @param lx,ly  sun position on the screen (0..1, v up)
     * @param power  the pack's LENS_POWER
     * @param mask   0..1 visibility of the sun (covered, screen edge, ...)
     */
    void compute(double lx, double ly, double aspect, double power, double mask, double night, double rain) {
        count = 0;
        sm = power * mask * (1.0 - night) * (1.0 - SunMath.clamp(rain, 0.0, 1.0));
        dim = 0.0;
        flareScale = 1.0;
        if (sm <= 0.0) return;

        double dist = Math.hypot(lx - 0.5, ly - 0.5);
        double centerMask = (1.0 - SunMath.clamp(dist * 2.0, 0.0, 1.0)) * sm;
        dim = centerMask;
        flareScale = 1.0 - centerMask;
        double edgeX = SunMath.clamp(Math.abs(lx - 0.5) * 8.0 - 3.0, 0.0, 1.0);

        for (int i = 0; i < FlareSet.COUNT; i++) {
            FlareElement e = FlareSet.ELEMENTS[i];
            FlareSet.colour(e, real, tint);
            double amp = amplitude(e, sm) * (e.edgeMaskX ? edgeX : 1.0);
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
