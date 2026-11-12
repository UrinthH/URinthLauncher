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
    private final PlayerCap playerCap = new PlayerCap();

    private volatile int playersSetting;
    private volatile int playersAround;
    private int usedThisTick;
    private long lastSampleAt = Long.MIN_VALUE / 4;
    private long joinedAt = Long.MIN_VALUE / 4;
    private boolean wasInWorld;
    private int lastFps;
    private Bench.Result lastBench;

    public BoostRuntime(BoostMode mode, int targetFps) {
        this(mode, targetFps, BoostConfig.DEFAULT_PLAYERS);
    }

    public BoostRuntime(BoostMode mode, int targetFps, int playersSetting) {
        this.mode = mode;
        this.playersSetting = BoostConfig.clampPlayers(playersSetting);
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
        playerCap.clear();
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
        playerCap.clear();
    }

    /** Starts the bench test. False when it cannot run (the mod switched itself off). */
    public boolean startBench() {
        if (problem != null) return false;
        bench.start();
        applyLevel();
        return true;
    }

    // ---- other players ----

    /** {@link BoostConfig#PLAYERS_AUTO}, {@link BoostConfig#PLAYERS_OFF} or the number the player chose. */
    public int playersSetting() {
        return playersSetting;
    }

    public void setPlayersSetting(int setting) {
        playersSetting = BoostConfig.clampPlayers(setting);
        playerCap.clear();
    }

    /**
     * True while nothing at all is to be taken away: the mod switched itself off, the mode is off, or this is the
     * "Boost off" half of the bench test. The player limit is then off too, whatever the player chose for it.
     */
    public boolean takesNothingAway() {
        if (problem != null) return true;
        if (bench.active()) return bench.level() == BoostLevel.OFF;
        return mode == BoostMode.OFF;
    }

    /** The most other players drawn right now, the nearest ones; {@link Limits#UNLIMITED} when all are drawn. */
    public int effectivePlayerCap() {
        if (takesNothingAway()) return Limits.UNLIMITED;
        int setting = playersSetting;
        if (setting == BoostConfig.PLAYERS_OFF) return Limits.UNLIMITED;
        if (setting == BoostConfig.PLAYERS_AUTO) return limits.maxPlayers;
        return setting;
    }

    /** True when the game should hand over the distances of the other players this tick. */
    public boolean wantsPlayerRanks() {
        return effectivePlayerCap() != Limits.UNLIMITED;
    }

    /**
     * Once per tick, when {@link #wantsPlayerRanks()}: the entity ids of the other players and how far each one is
     * (squared). The farthest ones beyond the limit are then left out until the next call.
     */
    public void updatePlayers(int[] ids, double[] distSq, int count) {
        playersAround = count;
        playerCap.update(effectivePlayerCap(), ids, distSq, count);
    }

    /** Everybody is drawn again. */
    public void clearPlayers() {
        playerCap.clear();
    }

    /** For the status: how many other players the game has around (nearby ones the server sends). */
    public void setPlayersAround(int count) {
        playersAround = Math.max(0, count);
    }

    public int playersAround() {
        return playersAround;
    }

    /** How many players are being left out right now. */
    public int playersLeftOut() {
        return playerCap.hiddenCount();
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
            playerCap.clear();
            playersAround = 0;
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

    /** Another player is about to be drawn. True: leave him out (he is one of the farthest in a crowd). */
    public boolean hidePlayer(int entityId) {
        stats.call(BoostStats.PLAYER);
        boolean hide = playerCap.isHidden(entityId);
        if (hide) stats.act(BoostStats.PLAYER);
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
