package dev.urinth.sunflare;

/**
 * One piece of the flare: an ellipse placed relative to the sun, with its own brightness profile and colour.
 * A few pieces are a glow minus a second, offset glow (a crescent); that second part is {@link #sub}.
 */
final class FlareElement {
    /** Where an ellipse sits and how big it is. */
    static final class Geo {
        final double sx;
        final double sy;
        final double offX;
        final double offY;
        final boolean atSun;
        final boolean yMirrored;
        final boolean usesFlareScale;

        Geo(double sx, double sy, double offX, double offY, boolean atSun, boolean yMirrored, boolean usesFlareScale) {
            this.sx = sx;
            this.sy = sy;
            this.offX = offX;
            this.offY = offY;
            this.atSun = atSun;
            this.yMirrored = yMirrored;
            this.usesFlareScale = usesFlareScale;
        }

        /** On the sun itself. */
        static Geo sun(double sx, double sy, boolean usesFlareScale) {
            return new Geo(sx, sy, 0, 0, true, false, usesFlareScale);
        }

        /** On the line through the sun and the screen centre. offset 0 is the mirror point of the sun. */
        static Geo ghost(double s, double offset) {
            return new Geo(s, s, offset, offset, false, true, true);
        }

        static Geo ghost(double sx, double sy, double offset) {
            return new Geo(sx, sy, offset, offset, false, true, true);
        }

        double centerU(double lx) {
            return atSun ? lx : (1.0 - lx) * (offX + 1.0) - offX * 0.5;
        }

        double centerV(double ly) {
            if (atSun) return ly;
            return yMirrored ? (1.0 - ly) * (offY + 1.0) - offY * 0.5 : ly * (offY + 1.0) - offY * 0.5;
        }

        double radiusU(double radius, double aspect, double flareScale) {
            return radius / (aspect * sx * (usesFlareScale ? flareScale : 1.0));
        }

        double radiusV(double radius, double flareScale) {
            return radius / (sy * (usesFlareScale ? flareScale : 1.0));
        }
    }

    final String name;
    final FlareProfile main;
    final Geo mainGeo;
    final FlareProfile sub;
    final Geo subGeo;
    /** True for the pair whose sunmask is applied after the subtraction. */
    final boolean smOutside;
    final double red;
    final double green;
    final double blue;
    /** Elements 8 and 9 only show when the sun is near the left or right screen edge. */
    final boolean edgeMaskX;

    FlareElement(String name, FlareProfile main, Geo mainGeo, FlareProfile sub, Geo subGeo, boolean smOutside,
                 double red, double green, double blue, boolean edgeMaskX) {
        this.name = name;
        this.main = main;
        this.mainGeo = mainGeo;
        this.sub = sub;
        this.subGeo = subGeo;
        this.smOutside = smOutside;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.edgeMaskX = edgeMaskX;
    }

    boolean isPair() {
        return sub != null;
    }
}
