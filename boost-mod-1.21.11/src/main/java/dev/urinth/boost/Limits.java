package dev.urinth.boost;

/**
 * What each level takes away, as plain numbers. Everything here is only about what is DRAWN on this screen: no
 * packet is changed, no entity is removed, nothing about the world, combat or hit detection is touched, so a
 * server cannot tell the difference.
 *
 * Distances are in blocks and are stored squared, so the game's hooks never need a square root. Players are never
 * hidden, whatever the level; only their name tags and their armor and held items can be dropped when they are far.
 *
 * The numbers are small on purpose: a server only sends entities that are within about 32 to 64 blocks, so a
 * limit of 100 blocks would never do anything.
 */
public final class Limits {
    /** No limit on the number of particles per game tick. */
    public static final int UNLIMITED = -1;

    private static final double INF = Double.POSITIVE_INFINITY;

    /** The level these limits belong to. */
    public final int level;
    /** How many new particles are let through per game tick. */
    public final int particlesPerTick;
    /** New particles farther from the player than this are dropped (squared). */
    public final double particleDistSq;
    /** Dropped items and experience orbs farther away are not drawn (squared). */
    public final double itemDistSq;
    /** Armor stands (holograms, displays) farther away are not drawn (squared). */
    public final double standDistSq;
    /** Mobs (not players) farther away are not drawn (squared). */
    public final double mobDistSq;
    /** Name tags of entities farther away are not drawn (squared). */
    public final double nameTagDistSq;
    /** Armor, held items, capes and the like of living things farther away are not drawn (squared). */
    public final double layerDistSq;

    private Limits(int level, int particlesPerTick, double particles, double items, double stands, double mobs,
                   double nameTags, double layers) {
        this.level = level;
        this.particlesPerTick = particlesPerTick;
        this.particleDistSq = square(particles);
        this.itemDistSq = square(items);
        this.standDistSq = square(stands);
        this.mobDistSq = square(mobs);
        this.nameTagDistSq = square(nameTags);
        this.layerDistSq = square(layers);
    }

    private static double square(double blocks) {
        return blocks == INF ? INF : blocks * blocks;
    }

    private static final Limits[] TABLE = {
        // Distances in blocks.    level  particles/tick  particles  items  stands  mobs  names  layers
        new Limits(0, UNLIMITED, INF, INF, INF, INF, INF, INF),
        new Limits(1, 300, 48, 40, 48, INF, 56, INF),
        new Limits(2, 120, 32, 24, 32, 48, 40, 40),
        new Limits(3, 48, 20, 14, 24, 32, 28, 22),
    };

    public static Limits forLevel(int level) {
        return TABLE[BoostLevel.clamp(level)];
    }

    /** The distance in blocks a squared distance stands for; infinity stays infinity. For messages. */
    public static double blocks(double distSq) {
        return distSq == INF ? INF : Math.sqrt(distSq);
    }
}
