package dev.urinth.sunflare;

/**
 * How bright one flare element is, from its centre (rp = 0) to its edge (rp = 1).
 * Each kind is one of the brightness recipes used in the SEUS style flare (lib/LensFlare.glsl of the CilokGaming pack).
 *
 * "sm" is the pack's sunmask: lens power x sun visibility x (1 - night) x (1 - rain).
 */
final class FlareProfile {
    enum Kind {
        /** pow(t * sm, exp) * pw */
        LINEAR,
        /** pow(sin(clamp(t * fill) * pi/2) * sm, exp) * pw. With smInside false the sm is applied later, by the pair. */
        SIN_FILL,
        /** pow(clamp(t * fill) * sm, exp) * pw */
        FILL,
        /** pow(clamp(t * fill), exp) * sm * pw */
        FILL_POW,
        /** sin(pow(clamp(t * fill), exp) * sinK) * sm * pw */
        SIN_POW
    }

    final Kind kind;
    /** The pack measures t = radius - distance. 0.5 for nearly all elements. */
    final double radius;
    final double fill;
    final double exp;
    final double pw;
    final double sinK;
    final boolean smInside;

    private FlareProfile(Kind kind, double radius, double fill, double exp, double pw, double sinK, boolean smInside) {
        this.kind = kind;
        this.radius = radius;
        this.fill = fill;
        this.exp = exp;
        this.pw = pw;
        this.sinK = sinK;
        this.smInside = smInside;
    }

    static FlareProfile linear(double radius, double exp, double pw) {
        return new FlareProfile(Kind.LINEAR, radius, 0, exp, pw, 0, true);
    }

    static FlareProfile sinFill(double fill, double exp, double pw, boolean smInside) {
        return new FlareProfile(Kind.SIN_FILL, 0.5, fill, exp, pw, 0, smInside);
    }

    static FlareProfile fill(double fill, double exp, double pw) {
        return new FlareProfile(Kind.FILL, 0.5, fill, exp, pw, 0, true);
    }

    static FlareProfile fillPow(double fill, double exp, double pw) {
        return new FlareProfile(Kind.FILL_POW, 0.5, fill, exp, pw, 0, true);
    }

    static FlareProfile sinPow(double fill, double exp, double pw, double sinK) {
        return new FlareProfile(Kind.SIN_POW, 0.5, fill, exp, pw, sinK, true);
    }

    /** Exact value for a normalised distance rp (0 = centre, 1 = edge). */
    double value(double rp, double sm) {
        double t = radius * (1.0 - rp);
        if (t <= 0.0) return 0.0;
        switch (kind) {
            case LINEAR:
                return pw * Math.pow(Math.min(t, 10.0) * sm, exp);
            case SIN_FILL: {
                double v = Math.sin(SunMath.clamp(t * fill, 0.0, 1.0) * 1.57075);
                return pw * Math.pow(smInside ? v * sm : v, exp);
            }
            case FILL:
                return pw * Math.pow(SunMath.clamp(t * fill, 0.0, 1.0) * sm, exp);
            case FILL_POW:
                return pw * Math.pow(SunMath.clamp(t * fill, 0.0, 1.0), exp) * sm;
            case SIN_POW:
                return pw * Math.sin(Math.pow(SunMath.clamp(t * fill, 0.0, 1.0), exp) * sinK) * sm;
            default:
                return 0.0;
        }
    }

    /** value(rp, sm) == value(rp, 1) * sm^smExponent() for every rp, which lets one baked texture serve every sm. */
    double smExponent() {
        if (!smInside) return 0.0;
        switch (kind) {
            case LINEAR:
            case SIN_FILL:
            case FILL:
                return exp;
            default:
                return 1.0;
        }
    }
}
