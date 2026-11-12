package dev.urinth.boost;

/**
 * Everything the mod decides, with no game code in it: the mode, the level that follows from it, the four hook
 * decisions and the counters. The game side feeds it the clock and the frame rate once per tick and asks it
 * yes or no questions from the hooks; so all of it is covered by plain unit tests.
 *
 * The hooks run very often (every entity, every particle), so the first thing each of them does is read the
 * current {@link Limits} once and return at once when the level is 0.
 */
public final class BoostRuntime {
    /** One frame rate reading per this long. */
    public static final long SAMPLE_MS = 1_000;
    /** After joining a world the frame rate means little (chunks are loading); readings are ignored this long. */
    public static final long WARMUP_MS = 8_000;

    private volatile Limits limits = Limits.forLevel(BoostLevel.OFF);
    private volatile BoostMode mode;
    private volatile String problem;

    private final Governor governor;
    private final Bench bench = new Bench();
    private final BoostStats stats = new BoostStats();

    private int usedThisTick;
    private long lastSampleAt = Long.MIN_VALUE / 4;
    private long joinedAt = Long.MIN_VALUE / 4;
    private boolean wasInWorld;
    private int lastFps;
    private Bench.Result lastBench;

    public BoostRuntime(BoostMode mode, int targetFps) {
        this.mode = mode;
        this.governor = new Governor(BoostConfig.clampTarget(targetFps));
        applyLevel();
    }

    // ---- what the player sees and sets ----

    public BoostMode mode() {
        return mode;
    }

    public void setMode(BoostMode newMode) {
        bench.cancel();
        governor.reset();
        mode = newMode;
        applyLevel();
    }

    public int target() {
        return governor.target();
    }

    public void setTarget(int fps) {
        governor.setTarget(BoostConfig.clampTarget(fps));
    }

    public int level() {
        return limits.level;
    }

    public Limits limits() {
        return limits;
    }

    public int lastFps() {
        return lastFps;
    }

    public String governorNote() {
        return governor.note();
    }

    public BoostStats stats() {
        return stats;
    }

    public Bench bench() {
        return bench;
    }

    /** The result of the last finished /uboost bench, or null. */
    public Bench.Result lastBench() {
        return lastBench;
    }

    /** Why the mod switched itself off, or null while it is working. */
    public String problem() {
        return problem;
    }

    /**
     * Switches everything off for good until the game is restarted, after something in the mod went wrong. The mod
     * must never be the reason a game crashes, so a hook that fails ends up here instead.
     */
    public void disable(String reason) {
        if (problem == null) problem = reason;
        bench.cancel();
        limits = Limits.forLevel(BoostLevel.OFF);
    }

    /** Starts the bench test. False when it cannot run (the mod switched itself off). */
    public boolean startBench() {
        if (problem != null) return false;
        bench.start();
        applyLevel();
        return true;
    }

    // ---- the clock ----

    /** Call at the start of every game tick: the particle allowance starts again. */
    public void newTick() {
        usedThisTick = 0;
    }

    /**
     * Call at the end of every game tick.
     *
     * @param nowMs a clock in milliseconds that only goes forward
     * @param fps the frame rate the game reports (it renews it once a second)
     * @param inWorld false on the title screen and while joining or leaving
     */
    public void tick(long nowMs, int fps, boolean inWorld) {
        if (problem != null) return;
        if (!inWorld) {
            wasInWorld = false;
            if (bench.active()) bench.cancel();
            governor.reset();
            applyLevel();
            return;
        }
        if (!wasInWorld) {
            wasInWorld = true;
            joinedAt = nowMs;
        }
        if (nowMs - lastSampleAt < SAMPLE_MS) return;
        lastSampleAt = nowMs;
        lastFps = fps;
        if (nowMs - joinedAt < WARMUP_MS) {
            applyLevel();
            return;
        }
        if (bench.active()) {
            bench.sample(fps);
            if (!bench.active()) lastBench = bench.result();
        } else if (mode.isAuto()) {
            governor.sample(nowMs, fps);
        }
        applyLevel();
    }

    private void applyLevel() {
        int level;
        if (problem != null) {
            level = BoostLevel.OFF;
        } else if (bench.active()) {
            level = bench.level();
        } else if (mode.isAuto()) {
            level = governor.level();
        } else {
            level = mode.fixedLevel();
        }
        if (level != limits.level) limits = Limits.forLevel(level);
    }

    // ---- the hooks: numbers in, decision out ----

    /** A new particle is about to be added, {@code distSq} blocks squared from the player. True: drop it. */
    public boolean dropParticle(double distSq) {
        stats.call(BoostStats.PARTICLE);
        Limits l = limits;
        if (l.level == BoostLevel.OFF) return false;
        boolean drop;
        if (distSq > l.particleDistSq) {
            drop = true;
        } else if (l.particlesPerTick != Limits.UNLIMITED && usedThisTick >= l.particlesPerTick) {
            drop = true;
        } else {
            usedThisTick++;
            drop = false;
        }
        if (drop) stats.act(BoostStats.PARTICLE);
        return drop;
    }

    /** An entity is about to be drawn. True: leave it out. Players and everything unlisted are always drawn. */
    public boolean hideEntity(EntityKind kind, double distSq) {
        stats.call(BoostStats.ENTITY);
        Limits l = limits;
        if (l.level == BoostLevel.OFF || kind == EntityKind.KEEP) return false;
        double limit;
        switch (kind) {
            case ITEM:
                limit = l.itemDistSq;
                break;
            case ARMOR_STAND:
                limit = l.standDistSq;
                break;
            case MOB:
                limit = l.mobDistSq;
                break;
            default:
                return false;
        }
        boolean hide = distSq > limit;
        if (hide) stats.act(BoostStats.ENTITY);
        return hide;
    }

    /** A name tag is about to be drawn. True: leave it out. */
    public boolean hideNameTag(double distSq) {
        stats.call(BoostStats.NAME_TAG);
        Limits l = limits;
        if (l.level == BoostLevel.OFF) return false;
        boolean hide = distSq > l.nameTagDistSq;
        if (hide) stats.act(BoostStats.NAME_TAG);
        return hide;
    }

    /** The armor, held items and so on of a living thing are about to be drawn. True: leave them out. */
    public boolean hideLayers(double distSq) {
        stats.call(BoostStats.LAYERS);
        Limits l = limits;
        if (l.level == BoostLevel.OFF) return false;
        boolean hide = distSq > l.layerDistSq;
        if (hide) stats.act(BoostStats.LAYERS);
        return hide;
    }
}
