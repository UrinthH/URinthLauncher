package dev.urinth.find;

/** A player who is online and where they are: name, position and the id of their dimension. */
public record Located(String name, double x, double y, double z, String dimension) {
}
