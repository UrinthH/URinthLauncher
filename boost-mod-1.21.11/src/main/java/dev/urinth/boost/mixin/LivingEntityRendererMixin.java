package dev.urinth.boost.mixin;

import dev.urinth.boost.BoostGame;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Asked once per living thing per frame: "draw its armor, held items and capes?". For people-shaped things that are
 * far away the answer can be no; the body itself is still drawn.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void urinth$limitLayers(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && BoostGame.hideLayers(state)) {
            cir.setReturnValue(false);
        }
    }
}
