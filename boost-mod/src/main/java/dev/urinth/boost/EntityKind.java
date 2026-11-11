package dev.urinth.boost;

/**
 * The few kinds of entity that may be left undrawn when they are far away. Anything else, and every player, is
 * {@link #KEEP}: it is drawn whatever the level.
 */
public enum EntityKind {
    KEEP,
    /** A dropped item or an experience orb. */
    ITEM,
    ARMOR_STAND,
    /** A mob that is not a player. */
    MOB
}
