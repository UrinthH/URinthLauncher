package dev.urinth.optimizer.mixin;

import dev.urinth.optimizer.Hooks;
import dev.urinth.optimizer.ServerCap;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The simulation distance (how far around the players the world is ticked) is set through this method, by the game's options for a
 * world played alone and by server.properties on a server. The limit is applied here; the distance the game asked for is
 * remembered so the limit can move later without the game noticing.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @ModifyVariable(method = "setSimulationDistance", at = @At("HEAD"), argsOnly = true)
    private int urinth$capSimulation(int asked) {
        try {
            int used = ServerCap.apply(asked);
            if (used != asked) Hooks.simulationSeen();
            return used;
        } catch (Throwable t) {
            Hooks.failed("simulation distance", t);
            return asked;
        }
    }
}
