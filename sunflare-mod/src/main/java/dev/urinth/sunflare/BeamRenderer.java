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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Sun beams: shafts of light that really pass through gaps in leaves and other blocks. They are found by casting rays through
 * the world ({@link BeamField}) and drawn as soft ribbons in the world after everything else, with the depth buffer on, so
 * leaves and walls in front hide them and they move correctly when the player walks around.
 *
 * Cost control: a few rays per tick (the number adapts to how long they take), at most a fixed share of the screen covered
 * ({@link BeamGeometry}), nothing drawn at night, in rain or in other dimensions, and nothing at all when set to Off.
 */
final class BeamRenderer {
    private static final double MAX_TICK_MILLIS = 1.5;
    private static final double MIN_TICK_MILLIS = 0.6;

    private final FlareConfig config;
    private final BeamField field = new BeamField();
    private final BeamGeometry geometry = new BeamGeometry();
    private final BeamGeometry.Params params = new BeamGeometry.Params();
    private final double[] dir = new double[3];
    private final double[] sunScreen = new double[2];
    private final float[] view = new float[16];
    private final float[] proj = new float[16];
    private final float[] transform = new float[16];
    private final Matrix4f inverse = new Matrix4f();
    private final Matrix4f combined = new Matrix4f();

    private ClientLevel castLevel;
    private Entity castEntity;
    private final BeamField.Occluder occluder = (sx, sy, sz, dx, dy, dz, maxLen) -> {
        Vec3 from = new Vec3(sx, sy, sz);
        Vec3 to = new Vec3(sx + dx * maxLen, sy + dy * maxLen, sz + dz * maxLen);
        BlockHitResult hit = castLevel.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, castEntity));
        return hit.getType() == HitResult.Type.MISS ? -1.0 : hit.getLocation().distanceTo(from);
    };

    /** 0..1 daylight/weather factor for the sun beams (0 at night, in rain, below the horizon). */
    private double target;
    private double presence;
    private double warmth;
    private int raysPerTick;
    private int tickNo;
    private long lastFrameNanos;
    private boolean failed;
    private boolean checked;
    private boolean wasActive;

    BeamRenderer(FlareConfig config) {
        this.config = config;
    }

    /** Once per client tick: re-cast a few rays and refresh the shaft list. */
    void tick(Minecraft mc) {
        if (failed) return;
        try {
            ClientLevel level = mc.level;
            if (config.beams() == FlareConfig.BEAMS_OFF || level == null || mc.player == null
                    || level.dimension() != Level.OVERWORLD) {
                target = 0.0;
                if (wasActive) {
                    field.clear();
                    wasActive = false;
                }
                return;
            }
            long dayTime = level.getDayTime();
            SunMath.sunDirection(dayTime, dir);
            double night = SunMath.nightFactor(dayTime);
            double rain = SunMath.clamp(level.getRainLevel(1.0f), 0.0, 1.0);
            target = (1.0 - night) * (1.0 - rain) * SunMath.smoothstep(-0.02, 0.12, dir[1]);
            warmth = 1.0 - SunMath.clamp(dir[1], 0.0, 1.0);
            if (target <= 0.001) return;

            Vec3 eye = mc.player.getEyePosition();
            castLevel = level;
            castEntity = mc.player;
            field.setSun(dir);
            if (raysPerTick <= 0 || raysPerTick > config.beamRays()) raysPerTick = config.beamRays();
            long t0 = System.nanoTime();
            field.step(occluder, eye.x, eye.y, eye.z, raysPerTick);
            double ms = (System.nanoTime() - t0) / 1e6;
            // keep the ray work between about 0.6 and 1.5 ms per tick, whatever the phone does
            if (ms > MAX_TICK_MILLIS && raysPerTick > 8) raysPerTick = Math.max(8, raysPerTick - 4);
            else if (ms < MIN_TICK_MILLIS && raysPerTick < config.beamRays()) raysPerTick++;
            if ((++tickNo % 3) == 0) field.extract(eye.x, eye.y, eye.z);
            wasActive = true;
        } catch (Throwable t) {
            failed = true;
            SunFlareMod.LOG.error("[SunFlare] sun beams turned themselves off after an error", t);
        }
    }

    /** At the end of world rendering: draw the shafts into the main picture, depth tested. */
    void render(WorldRenderContext ctx) {
        if (failed || config.beams() == FlareConfig.BEAMS_OFF) return;
        try {
            draw(ctx);
        } catch (Throwable t) {
            failed = true;
            SunFlareMod.LOG.error("[SunFlare] sun beams turned themselves off after an error", t);
        }
    }

    private void draw(WorldRenderContext ctx) {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? 0.016 : Math.min(0.1, (now - lastFrameNanos) / 1e9);
        lastFrameNanos = now;
        presence += (target - presence) * (1.0 - Math.exp(-dt * 1.5));
        if (presence < 0.01 || field.count() == 0) return;
        if (ctx.advancedTranslucency()) return; // "Fabulous" graphics composite things differently; stay out of it

        Minecraft mc = Minecraft.getInstance();
        Camera camera = ctx.camera();
        Vec3 cam = camera.getPosition();
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        ctx.positionMatrix().get(view);
        RenderSystem.getProjectionMatrix().get(proj);
        // the GPU will multiply what we give it by the model-view matrix, so undo that and apply the camera rotation instead
        inverse.set(modelView).invert();
        combined.set(inverse).mul(ctx.positionMatrix());
        combined.get(transform);

        if (!checked) checkMatrices(mc, camera);

        params.screenW = mc.getWindow().getWidth();
        params.screenH = mc.getWindow().getHeight();
        params.strength = config.beamStrength() * presence;
        params.areaFraction = config.beamArea();
        // low sun: warm and orange; high sun: nearly white
        params.red = 1.0;
        params.green = 0.95 - 0.16 * warmth;
        params.blue = 0.86 - 0.34 * warmth;
        params.sun[0] = dir[0];
        params.sun[1] = dir[1];
        params.sun[2] = dir[2];
        int n = geometry.build(field, cam.x, cam.y, cam.z, view, proj, transform, params);
        if (n == 0) return;

        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] v = geometry.vertices;
        for (int i = 0; i < n; i++) {
            int o = i * BeamGeometry.FLOATS_PER_VERTEX;
            buf.addVertex(v[o], v[o + 1], v[o + 2]).setColor(v[o + 3], v[o + 4], v[o + 5], 1f);
        }
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * One-off self check, written to the log: the sun's screen position from the camera matrices should agree with the one the
     * lens flare uses (computed from yaw, pitch and field of view). If they do not, the log says so.
     */
    private void checkMatrices(Minecraft mc, Camera camera) {
        double vx = view[0] * dir[0] + view[4] * dir[1] + view[8] * dir[2];
        double vy = view[1] * dir[0] + view[5] * dir[1] + view[9] * dir[2];
        double vz = view[2] * dir[0] + view[6] * dir[1] + view[10] * dir[2];
        if (-vz < 0.3) return; // sun not in front of the camera right now, try again later
        double nx = proj[0] * vx / -vz;
        double ny = proj[5] * vy / -vz;
        if (Math.abs(nx) > 0.8 || Math.abs(ny) > 0.8) return;
        double aspect = (double) mc.getWindow().getWidth() / mc.getWindow().getHeight();
        if (!SunMath.project(dir, camera.getYRot(), camera.getXRot(), mc.options.fov().get(), aspect, sunScreen)) return;
        double ex = sunScreen[0] * 2.0 - 1.0;
        double ey = sunScreen[1] * 2.0 - 1.0;
        double err = Math.hypot(nx - ex, ny - ey);
        checked = true;
        if (err < 0.12) {
            SunFlareMod.LOG.info("[SunFlare] beams: camera matrices agree with the flare's sun position (error {})", String.format("%.3f", err));
        } else {
            SunFlareMod.LOG.warn("[SunFlare] beams: camera matrices disagree with the flare's sun position (error {}, beams ndc {},{} flare ndc {},{}); sprint/speed field of view changes can cause a small difference",
                String.format("%.3f", err), String.format("%.2f", nx), String.format("%.2f", ny), String.format("%.2f", ex), String.format("%.2f", ey));
        }
    }
}
