package dev.urinth.boost.mixin;

import dev.urinth.boost.BoostGame;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every new particle goes through ParticleEngine.add, whether it came from a potion swirl, a hit, a block that
 * broke or an explosion. A particle that is not added is never moved, never drawn and costs nothing afterwards.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void urinth$limitParticles(Particle particle, CallbackInfo ci) {
        if (BoostGame.dropParticle(particle)) ci.cancel();
    }
}
