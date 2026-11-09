package dev.urinth.optimizer.mixin;

import dev.urinth.optimizer.Hooks;
import dev.urinth.optimizer.ParticleGate;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.level.ParticleStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fewer particles while the frame rate is low: the game's own "Decreased" level (a third of the particles are dropped) is used in
 * place of "All". The player's particle setting is not changed, and "Decreased" or "Minimal" are left as they are.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "calculateParticleLevel", at = @At("RETURN"), cancellable = true)
    private void urinth$fewerParticles(boolean decreased, CallbackInfoReturnable<ParticleStatus> cir) {
        if (!Hooks.usable("particles")) return;
        try {
            Hooks.particlesSeen();
            if (cir.getReturnValue() == ParticleStatus.ALL && ParticleGate.reduce()) {
                cir.setReturnValue(ParticleStatus.DECREASED);
            }
        } catch (Throwable t) {
            Hooks.failed("particles", t);
        }
    }
}
