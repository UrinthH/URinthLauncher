package dev.urinth.sunflare;

/**
 * Specks of dust floating in the sunlit air around the player: what makes a light beam visible in real life. They drift and
 * twinkle slowly, only exist where the sun really reaches (the ray field says where), and fade in and out instead of popping.
 * Plain maths, unit tested.
 */
final class BeamDust {
    static final int COUNT = 150;
    /** Specks live within this distance of the camera. */
    static final double RADIUS = 14.0;

    final double[] x = new double[COUNT];
    final double[] y = new double[COUNT];
    final double[] z = new double[COUNT];
    final float[] alpha = new float[COUNT];
    final double[] phase = new double[COUNT];
    private final double[] vx = new double[COUNT];
    private final double[] vy = new double[COUNT];
    private final double[] vz = new double[COUNT];
    private final boolean[] alive = new boolean[COUNT];
    private long seed = 0x2545F4914F6CDD1DL;
    private int spawnCursor;

    private double random() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return (seed >>> 11) / (double) (1L << 53);
    }

    /** Moves everything on by dt seconds, fades specks in sunlight in and the others out, and re-seeds lost ones. */
    void update(double dt, double camX, double camY, double camZ, BeamField field, double time) {
        int spawns = 0;
        for (int i = 0; i < COUNT; i++) {
            if (alive[i]) {
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt + Math.sin(time * 0.6 + phase[i]) * 0.02 * dt;
                z[i] += vz[i] * dt;
                double dx = x[i] - camX, dy = y[i] - camY, dz = z[i] - camZ;
                boolean far = dx * dx + dy * dy + dz * dz > RADIUS * RADIUS;
                int lit = far ? 0 : field.lit(x[i], y[i], z[i]);
                float target = lit == 1 ? 1f : 0f;
                alpha[i] += (float) ((target - alpha[i]) * Math.min(1.0, dt * 2.5));
                if (far || (lit != 1 && alpha[i] < 0.02f)) alive[i] = false;
            }
            if (!alive[i] && spawns < 6) {
                // a new speck somewhere around the camera; it only stays if that spot is in the sun
                double a = random() * Math.PI * 2.0;
                double c = random() * 2.0 - 1.0;
                double r = 2.0 + random() * (RADIUS - 2.5);
                double s = Math.sqrt(1.0 - c * c);
                double px = camX + r * s * Math.cos(a), py = camY + r * c * 0.6, pz = camZ + r * s * Math.sin(a);
                spawns++;
                if (field.lit(px, py, pz) == 1) {
                    x[i] = px;
                    y[i] = py;
                    z[i] = pz;
                    vx[i] = (random() - 0.5) * 0.12;
                    vy[i] = (random() - 0.5) * 0.05;
                    vz[i] = (random() - 0.5) * 0.12;
                    phase[i] = random() * 6.283;
                    alpha[i] = 0f;
                    alive[i] = true;
                }
            }
        }
    }

    boolean alive(int i) {
        return alive[i];
    }

    int aliveCount() {
        int n = 0;
        for (boolean b : alive) if (b) n++;
        return n;
    }

    /** Slow twinkle, 0.35..1. */
    double twinkle(int i, double time) {
        return 0.675 + 0.325 * Math.sin(time * (0.7 + 0.5 * (phase[i] % 1.0)) + phase[i] * 3.0);
    }
}
