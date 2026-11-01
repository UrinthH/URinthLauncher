package dev.urinth.sunflare;

/**
 * Where is the sun on the screen? Plain maths, no Minecraft classes, so it can be unit tested.
 *
 * Minecraft moves the sun on a fixed east-west arc, so its screen position follows from the time of day and the
 * camera rotation alone. No depth buffer is read, which keeps the cost close to zero on a phone GPU.
 */
public final class SunMath {
    private SunMath() {}

    /** The vanilla "sky angle" (0..1, 0 = noon, 0.5 = midnight) for a world day time in ticks. */
    public static double skyAngle(long dayTime) {
        double d = frac(dayTime / 24000.0 - 0.25);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (d * 2.0 + e) / 3.0;
    }

    /** Unit vector from the player to the sun in world space (x east, y up, z south). */
    public static void sunDirection(long dayTime, double[] out) {
        double theta = skyAngle(dayTime) * 2.0 * Math.PI;
        out[0] = -Math.sin(theta);
        out[1] = Math.cos(theta);
        out[2] = 0.0;
    }

    /**
     * Projects a world direction to screen space for a camera with the given Minecraft yaw/pitch (degrees).
     *
     * @param out receives {u, v} in 0..1 with v pointing up, like the shader pack's texcoord
     * @return false when the direction is behind the camera
     */
    public static boolean project(double[] dir, double yawDeg, double pitchDeg, double fovDeg, double aspect, double[] out) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        // up = forward x left, left has no y part
        double ux = fy * lz;
        double uy = fz * lx - fx * lz;
        double uz = -fy * lx;

        double forward = dir[0] * fx + dir[1] * fy + dir[2] * fz;
        if (forward < 0.05) return false;
        double left = dir[0] * lx + dir[2] * lz;
        double up = dir[0] * ux + dir[1] * uy + dir[2] * uz;

        double t = Math.tan(Math.toRadians(fovDeg) / 2.0);
        double ndcX = -left / (forward * t * aspect);
        double ndcY = up / (forward * t);
        out[0] = ndcX * 0.5 + 0.5;
        out[1] = ndcY * 0.5 + 0.5;
        return true;
    }

    /** The pack's "night" (0 = day, 1 = night) for a world day time, as in its shaders.properties. */
    public static double nightFactor(long dayTime) {
        double worldTime = Math.floorMod(dayTime, 24000L);
        double ticks = Math.abs(((worldTime / 1000.0 + 6.0) % 24.0) - 12.0);
        return clamp(ticks - 6.7, 0.0, 1.0);
    }

    /** The shader fades the flare out in the last 20% towards any screen edge. */
    public static double edgeFade(double u, double v) {
        double shortest = Math.min(Math.min(u, 1.0 - u), Math.min(v, 1.0 - v));
        return smoothstep(0.0, 0.2, clamp(shortest, 0.0, 0.2));
    }

    public static double smoothstep(double e0, double e1, double x) {
        double t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : (x > hi ? hi : x);
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }
}
