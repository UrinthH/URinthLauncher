package dev.urinth.sunflare;

/**
 * Texture layout. Every single element gets a 64x64 cell holding its brightness curve as a grey disc (0..255, scaled by
 * the curve's peak). Crescent pairs get cells in a second, small texture that is re-baked every frame.
 */
final class FlareAtlas {
    private FlareAtlas() {}

    static final int CELL = 64;
    static final int COLS = 8;
    static final int ROWS = 5;
    static final int WIDTH = CELL * COLS;
    static final int HEIGHT = CELL * ROWS;
    static final int PAIR_COLS = 2;
    /** Crescents are smooth, so they are baked small: 4 x 32 x 32 values per frame. */
    static final int PAIR_CELL = 32;
    static final int PAIR_SIZE = PAIR_CELL * PAIR_COLS;

    private static final int[] SLOT = new int[FlareSet.COUNT];
    private static final int[] PAIR_SLOT = new int[FlareSet.COUNT];
    static final int SINGLE_COUNT;
    static final int PAIR_COUNT;

    static {
        int s = 0;
        int p = 0;
        for (int i = 0; i < FlareSet.COUNT; i++) {
            if (FlareSet.ELEMENTS[i].isPair()) {
                SLOT[i] = -1;
                PAIR_SLOT[i] = p++;
            } else {
                SLOT[i] = s++;
                PAIR_SLOT[i] = -1;
            }
        }
        SINGLE_COUNT = s;
        PAIR_COUNT = p;
        if (s > COLS * ROWS || p > PAIR_COLS * PAIR_COLS) throw new IllegalStateException("atlas too small");
    }

    static int slot(int element) {
        return SLOT[element];
    }

    /** How many texels across a crescent is baked: the larger ones on screen get more. */
    static int pairTexels(int element) {
        String name = FlareSet.ELEMENTS[element].name;
        if (name.equals("3")) return PAIR_CELL;
        if (name.equals("3C")) return 24;
        return 16;
    }

    static int pairSlot(int element) {
        return PAIR_SLOT[element];
    }

    /** Texture rectangle {u0, v0, u1, v1} for an element, in the singles atlas or the pair texture. */
    static void uvRect(int element, double[] out) {
        if (SLOT[element] >= 0) {
            int c = SLOT[element] % COLS;
            int r = SLOT[element] / COLS;
            out[0] = c * CELL / (double) WIDTH;
            out[1] = r * CELL / (double) HEIGHT;
            out[2] = (c + 1) * CELL / (double) WIDTH;
            out[3] = (r + 1) * CELL / (double) HEIGHT;
        } else {
            int c = PAIR_SLOT[element] % PAIR_COLS;
            int r = PAIR_SLOT[element] / PAIR_COLS;
            out[0] = c * PAIR_CELL / (double) PAIR_SIZE;
            out[1] = r * PAIR_CELL / (double) PAIR_SIZE;
            int n = pairTexels(element);
            out[2] = (c * PAIR_CELL + n) / (double) PAIR_SIZE;
            out[3] = (r * PAIR_CELL + n) / (double) PAIR_SIZE;
        }
    }

    private static final double HEX_K = Math.sqrt(3.0) / 2.0;

    /** Brightness of one texel (dx, dy in -1..1) for an element's shape. */
    static double shaped(FlareElement.Shape shape, FlareProfile p, double dx, double dy) {
        switch (shape) {
            case HEX: {
                // Distance measured to a pointy-top hexagon whose six corners touch the unit circle: the corners are at
                // (0, +-1) and (+-0.866, +-0.5), the flat sides are 0.866 from the centre. d = 1 exactly on its outline.
                double ax = Math.abs(dx);
                double ay = Math.abs(dy);
                return FlareLut.lookup(p, Math.max(ax / HEX_K, ax * 0.5 / HEX_K + ay));
            }
            default:
                return FlareLut.lookup(p, Math.sqrt(dx * dx + dy * dy));
        }
    }

    /** Grey bytes (WIDTH x HEIGHT, row 0 = top) of every single element's curve, each scaled by its own peak. */
    static byte[] buildSingles() {
        byte[] px = new byte[WIDTH * HEIGHT];
        for (int i = 0; i < FlareSet.COUNT; i++) {
            if (SLOT[i] < 0) continue;
            FlareProfile p = FlareSet.ELEMENTS[i].main;
            double peak = FlareLut.peak(p);
            if (peak <= 0.0) continue;
            int ox = (SLOT[i] % COLS) * CELL;
            int oy = (SLOT[i] / COLS) * CELL;
            for (int y = 0; y < CELL; y++) {
                for (int x = 0; x < CELL; x++) {
                    double dx = (x + 0.5) / CELL * 2.0 - 1.0;
                    double dy = (y + 0.5) / CELL * 2.0 - 1.0;
                    double v = shaped(FlareSet.ELEMENTS[i].shape, p, dx, dy) / peak;
                    px[(oy + y) * WIDTH + ox + x] = (byte) Math.round(SunMath.clamp(v, 0.0, 1.0) * 255.0);
                }
            }
        }
        return px;
    }
}
