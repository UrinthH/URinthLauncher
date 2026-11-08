package dev.urinth.find;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;

/**
 * Remembers, once a second, where the players the game can see are, so /find can say where somebody was when
 * they have walked out of view and nobody can be asked (see {@link Sightings}). Forgotten on every new
 * connection. Only the game thread touches it.
 */
final class FindSightings {
    private static final int EVERY_TICKS = 20;

    private static final Sightings SIGHTINGS = new Sightings();
    private static ClientPacketListener connection;
    private static int ticks;

    private FindSightings() {}

    /** Called every game tick. */
    static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            SIGHTINGS.clear();
            connection = null;
            return;
        }
        if (minecraft.player.connection != connection) {
            SIGHTINGS.clear();
            connection = minecraft.player.connection;
            ticks = 0;
        }
        if (++ticks % EVERY_TICKS != 0) return;
        String dimension = minecraft.level.dimension().location().toString();
        long now = nowMs();
        for (Player player : minecraft.level.players()) {
            if (player == minecraft.player) continue;
            SIGHTINGS.record(new Located(player.getGameProfile().getName(), player.getX(), player.getY(), player.getZ(),
                    dimension), now);
        }
    }

    /** Everybody remembered, with how long ago each was seen. */
    static List<Sightings.Remembered> remembered() {
        return SIGHTINGS.all(nowMs());
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }
}
