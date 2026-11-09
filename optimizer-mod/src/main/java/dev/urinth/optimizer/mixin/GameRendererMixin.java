package dev.urinth.optimizer.mixin;

import dev.urinth.optimizer.Hooks;
import dev.urinth.optimizer.SoftDistance;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The distance the fog is drawn at. The level renderer asks for it once per frame and hands it to both the terrain fog and the sky
 * fog, so this one place moves the fog in while the frame rate is low. The player's render distance, the far plane of the camera
 * and the chunk data are not touched.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getRenderDistance", at = @At("RETURN"), cancellable = true)
    private void urinth$softFog(CallbackInfoReturnable<Float> cir) {
        try {
            Hooks.fogSeen();
            float vanilla = cir.getReturnValueF();
            float soft = SoftDistance.fogFor(vanilla);
            if (soft < vanilla) cir.setReturnValue(soft);
        } catch (Throwable t) {
            Hooks.failed("fog", t);
        }
    }
}
