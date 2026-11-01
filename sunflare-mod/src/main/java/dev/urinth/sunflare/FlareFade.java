package dev.urinth.sunflare;

/** Smooths how visible the flare is, so it never pops in or out when the sun is covered or uncovered. */
public final class FlareFade {
    private final double perSecond;
    private double value;

    public FlareFade(double perSecond) {
        this.perSecond = perSecond;
    }

    /** Moves towards {@code target} (0..1) and returns the new value. */
    public double update(double target, double dtSeconds) {
        double k = SunMath.clamp(perSecond * dtSeconds, 0.0, 1.0);
        value += (SunMath.clamp(target, 0.0, 1.0) - value) * k;
        if (value < 0.002) value = 0.0;
        return value;
    }

    public double value() {
        return value;
    }
}
