package dev.urinth.boost.mixin;

import dev.urinth.boost.BoostGame;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game asks this once per entity per frame whether the entity is to be drawn. When the answer is already no,
 * nothing changes. Otherwise a far dropped item, armor stand or small mob can be answered with no instead.
 * This is only the answer to "draw it?": the entity still exists and can be hit, picked up and so on.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void urinth$limitEntities(Entity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && BoostGame.hideEntity(entity, cameraX, cameraY, cameraZ)) {
            cir.setReturnValue(false);
        }
    }
}
