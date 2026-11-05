package dev.urinth.sunflare;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Cheap soft blob shadows under living entities (mobs, the player). These are projected ovals drawn on the ground,
 * slightly offset below the entity. They are NOT real shadow maps; they are always circles, they do not change shape
 * based on the sun's angle, and they do not appear on walls. What they do is ground entities visually (the floating
 * feeling is gone) at a very small GPU cost: one quad per nearby entity, no texture, one draw call.
 *
 * <p>The shadow fades as the entity rises above the ground (jumping, flying) and fades at night (URinth Lighting
 * makes the ambient darker; a black circle on already-dark ground would look odd). It is drawn with depth testing
 * and polygon offset so it sits flush on the surface without z-fighting.
 *
 * <p>Only living entities within {@link #RANGE} blocks of the camera are covered; the player is included if
 * they are in first-person view (third-person already has a shadow from the skin).
 */
final class BlobShadow {

    /** Maximum horizontal distance (blocks) from the camera that earns a blob shadow. */
    private static final float RANGE = 24f;
    /** Maximum height above the ground the entity may be before the shadow disappears (blocks). */
    private static final float MAX_HEIGHT = 10f;
    /** Maximum alpha of the shadow circle at ground level, in full sun. */
    private static final float MAX_ALPHA = 0.38f;
    /** Radius of the oval at the foot of a full-sized mob (blocks). The player's is slightly smaller. */
    private static final float BASE_RADIUS = 0.36f;
    /** How far below the entity foot to push the quad so it does not fight the block surface. */
    private static final float Y_OFFSET = 0.005f;
    /** Polygon-offset units to push the blob behind whatever is already drawn on the surface. */
    private static final float POLY_OFFSET = -1.5f;
    /** Maximum drop cast: how far down from the entity foot to look for the ground. */
    private static final float DROP_CHECK = MAX_HEIGHT + 1f;
    /** Number of ring vertices per oval (TRIANGLE_FAN: 1 centre + SEGMENTS+1 ring = SEGMENTS+2 vertices per blob). */
    private static final int SEGMENTS = 16;

    private boolean failed;

    void render(WorldRenderContext ctx) {
        if (failed) return;
        try {
            draw(ctx);
        } catch (Throwable t) {
            failed = true;
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.polygonOffset(0f, 0f);
            RenderSystem.disablePolygonOffset();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            SunFlareMod.LOG.error("[SunFlare] blob shadows turned themselves off after an error", t);
        }
    }

    private void draw(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        // Only draw during the day overworld — night is already dark from the lighting pack
        long dayTime = level.getDayTime();
        double[] dir = new double[3];
        SunMath.sunDirection(dayTime, dir);
        float daylight = (float) GodRayMath.daylight(dir[1], SunMath.nightFactor(dayTime), level.getRainLevel(1.0f));
        if (daylight < 0.05f) return;

        Camera camera = ctx.camera();
        Vec3 camPos = camera.getPosition();

        // Gather nearby living entities
        AABB searchBox = new AABB(camPos.x - RANGE, camPos.y - RANGE, camPos.z - RANGE,
                camPos.x + RANGE, camPos.y + RANGE, camPos.z + RANGE);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, searchBox, e -> {
            if (!e.isAlive()) return false;
            // Skip the local player in first-person: Minecraft itself does not render their body, so a shadow alone looks odd
            if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return false;
            return true;
        });

        if (entities.isEmpty()) return;

        // The position_color shader multiplies: ProjMat * ModelViewMat * Position.
        // During world render ModelViewMat has the camera rotation, so positions here must be camera-relative.

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.polygonOffset(POLY_OFFSET, POLY_OFFSET);
        RenderSystem.enablePolygonOffset();
        RenderSystem.disableCull();
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // Each entity gets its own TRIANGLE_FAN draw call so the fan topology is correct per blob.
        // Cost: one draw call + one ray-cast per entity in range (at most ~20 on a phone's render distance).
        for (LivingEntity entity : entities) {
            // getX/Y/Z returns current-tick feet position; sub-tick interpolation would need xOld/yOld/zOld, but
            // the error is at most one tick (50 ms) of movement and not visible on a blob shadow
            double ex = entity.getX();
            double ey = entity.getY(); // feet position
            double ez = entity.getZ();

            // Ray-cast straight down to find the ground (up to DROP_CHECK blocks below the feet)
            Vec3 from = new Vec3(ex, ey + 0.1, ez);
            Vec3 to = new Vec3(ex, ey - DROP_CHECK, ez);
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if (hit.getType() == HitResult.Type.MISS) continue;

            double groundY = hit.getLocation().y;
            double heightAbove = ey - groundY;
            if (heightAbove > MAX_HEIGHT || heightAbove < -0.5) continue;

            // Shadow shrinks and fades as the entity rises
            float heightFactor = 1f - (float) (heightAbove / MAX_HEIGHT);
            float alpha = MAX_ALPHA * heightFactor * heightFactor * daylight;
            if (alpha < 0.01f) continue;

            float radius = BASE_RADIUS * (entity instanceof Player ? 0.85f : 1.0f);
            // Scale radius down slightly with height (the shadow contracts as the entity floats up)
            radius *= (0.6f + 0.4f * heightFactor);

            // Draw positions are relative to the camera; ModelViewMat in the vertex shader handles the rest
            double bx = ex - camPos.x;
            double by = groundY + Y_OFFSET - camPos.y;
            double bz = ez - camPos.z;

            // Shadow colour: very dark grey-blue (not pure black so it blends naturally)
            float sr = 0.03f, sg = 0.03f, sb = 0.05f;

            // One TRIANGLE_FAN: centre vertex (opaque), then the ring (transparent at the edge)
            BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            addVertex(buf, bx, by, bz, sr, sg, sb, alpha);
            for (int i = 0; i <= SEGMENTS; i++) {
                double angle = i * (2.0 * Math.PI / SEGMENTS);
                double vx = bx + Math.cos(angle) * radius;
                double vz = bz + Math.sin(angle) * radius;
                addVertex(buf, vx, by, vz, sr, sg, sb, 0f);
            }
            MeshData mesh = buf.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.enableCull();
        RenderSystem.polygonOffset(0f, 0f);
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void addVertex(BufferBuilder buf, double wx, double wy, double wz,
                                   float r, float g, float b, float a) {
        // The position_color shader does: gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0)
        // ModelViewMat during world render is already set up with the camera rotation/translation.
        // Positions fed here are camera-relative (entity - camPos), which is what ModelViewMat expects.
        buf.addVertex((float) wx, (float) wy, (float) wz).setColor(r, g, b, a);
    }
}
