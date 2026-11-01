package dev.urinth.sunflare;

/**
 * Texture layout. Every single element gets a 64x64 cell holding its brightness curve as a grey disc (0..255, scaled by
 * the curve's peak). Crescent pairs get cells in a second, small texture that is re-baked every frame.
 */
final class FlareAtlas {
    private FlareAtlas() {}

    static final int CELL = 64;
    static final int COLS = 8;
    static final int ROWS = 4;
    static final int WIDTH = CELL * COLS;
    static final int HEIGHT = CELL * ROWS;
    static final int PAIR_COLS = 2;
    static final int PAIR_SIZE = CELL * PAIR_COLS;

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
            out[0] = c * CELL / (double) PAIR_SIZE;
            out[1] = r * CELL / (double) PAIR_SIZE;
            out[2] = (c + 1) * CELL / (double) PAIR_SIZE;
            out[3] = (r + 1) * CELL / (double) PAIR_SIZE;
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
                    double v = FlareLut.lookup(p, Math.sqrt(dx * dx + dy * dy)) / peak;
                    px[(oy + y) * WIDTH + ox + x] = (byte) Math.round(SunMath.clamp(v, 0.0, 1.0) * 255.0);
                }
            }
        }
        return px;
    }
}
