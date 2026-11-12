package dev.urinth.boost.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.urinth.boost.BoostGame;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every name tag (and the score under a player's name) is drawn by this one method. Far ones can be skipped. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
            + "Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void urinth$limitNameTags(EntityRenderState state, Component name, PoseStack poseStack,
                                      MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (BoostGame.hideNameTag(state)) ci.cancel();
    }
}
