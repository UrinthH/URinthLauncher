package dev.urinth.boost.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.urinth.boost.BoostGame;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every name tag (and the score under a player's name) is handed to the renderer by this one method. Far ones can
 * be skipped. (Minecraft 1.21.11 calls it submitNameTag; 1.21.4 called it renderNameTag and passed the text in.)
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void urinth$limitNameTags(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                      CameraRenderState camera, CallbackInfo ci) {
        if (BoostGame.hideNameTag(state)) ci.cancel();
    }
}
