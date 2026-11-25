package dev.urinth.sunflare.mixin;

import dev.urinth.sunflare.CloudFar;
import dev.urinth.sunflare.SunFlareMod;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Sodium draws the clouds in CloudRenderer.render and hands the camera's projection matrix straight to the draw call, which cuts off
 * everything beyond the camera's far plane (4 times the render distance: 128 blocks at render distance 2, where the clouds are 192
 * blocks up). The first matrix of that method is the projection; this replaces it, for that one call, with a copy whose far plane
 * reaches {@link CloudFar#REACH} blocks when the camera's is shorter. The game's own matrix is never changed, so nothing else is
 * drawn any farther. The target and the method are looked up by name, so without Sodium (or with a Sodium that changed this) the
 * game just draws the clouds as it did.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer")
public abstract class SodiumCloudFarMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false, require = 0)
    private Matrix4f urinth$longerCloudReach(Matrix4f projection) {
        if (projection == null || !CloudFar.enabled()) return projection;
        float m22 = projection.m22();
        float m32 = projection.m32();
        if (!CloudFar.isPerspective(m22, projection.m23(), m32, projection.m33())) return projection;
        float far = CloudFar.farOf(m22, m32);
        float wanted = CloudFar.farFor(far);
        String line = CloudFar.report(far, wanted);
        if (line != null) SunFlareMod.note(line);
        if (!(wanted > far)) return projection;
        float near = CloudFar.nearOf(m22, m32);
        return new Matrix4f(projection).m22(CloudFar.m22For(near, wanted)).m32(CloudFar.m32For(near, wanted));
    }
}
