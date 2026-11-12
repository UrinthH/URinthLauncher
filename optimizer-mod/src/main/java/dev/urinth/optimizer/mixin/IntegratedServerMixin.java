package dev.urinth.optimizer.mixin;

import dev.urinth.optimizer.Hooks;
import dev.urinth.optimizer.ViewCap;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The server inside the game keeps its view distance equal to the render distance option: on every tick it reads the option and, if
 * the server's value is different, sets it. The memory guard's last resort (a shorter world while the game is about to run out of
 * memory) has to go through exactly this place, otherwise the game would undo it on the very next tick and log a line 20 times a
 * second. This answers the option's value, or the guard's shorter one while it is active. The option itself is not changed.
 *
 * <p>The game reads two options here: the render distance first, the simulation distance second. Only the first is redirected
 * (ordinal 0); a test checks against the game's own code that the first one really is the render distance.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Redirect(
        method = "tickServer",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I", ordinal = 0),
        require = 0)
    private int urinth$memoryView(int least, int chosen) {
        if (!Hooks.usable("view distance")) return Math.max(least, chosen);
        try {
            Hooks.viewSeen();
            return Math.max(least, ViewCap.apply(chosen));
        } catch (Throwable t) {
            Hooks.failed("view distance", t);
            return Math.max(least, chosen);
        }
    }
}
