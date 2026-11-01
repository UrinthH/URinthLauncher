package dev.urinth.sunflare;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the 13 flare discs in one batch on top of the finished frame.
 *
 * Cost per frame: 13 quads and one draw call. Visibility is decided by a few block ray casts a few times a second,
 * not by reading the depth buffer back from the GPU.
 */
final class FlareRenderer {
    private static final int TEX_SIZE = 64;
    private static final long RAY_INTERVAL_NANOS = 60_000_000L;
    private static final double RAY_LENGTH = 128.0;
    private static final double SPREAD = 0.04;

    private final FlareConfig config;
    private final FlareFade fade = new FlareFade(10.0);
    private final double[] dir = new double[3];
    private final double[] sun = new double[2];
    private final double[] center = new double[2];

    private ResourceLocation texture;
    private boolean failed;
    private long lastNanos;
    private long lastRay;
    private double rayTarget;

    FlareRenderer(FlareConfig config) {
        this.config = config;
    }

    void render(GuiGraphics g, DeltaTracker tracker) {
        if (failed || config.mode() == FlareConfig.OFF) return;
        try {
            draw(g, tracker);
        } catch (Throwable t) {
            failed = true;
            SunFlareMod.LOG.error("[SunFlare] turned itself off after an error", t);
        }
    }

    private void draw(GuiGraphics g, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0.016 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;

        Camera camera = mc.gameRenderer.getMainCamera();
        boolean usable = level.dimension() == Level.OVERWORLD && camera.isInitialized()
                && camera.getFluidInCamera() == FogType.NONE;
        SunMath.sunDirection(level.getDayTime(), dir);
        usable &= dir[1] > -0.02;

        if (usable && now - lastRay > RAY_INTERVAL_NANOS) {
            lastRay = now;
            rayTarget = sunVisible(level, mc, camera);
        }
        double rain = level.getRainLevel(tracker.getGameTimeDeltaPartialTick(false));
        double target = usable ? rayTarget * SunMath.clamp(1.0 - rain / 0.3, 0.0, 1.0) : 0.0;
        double vis = fade.update(target, dt);
        if (vis <= 0.0) return;

        int w = g.guiWidth();
        int h = g.guiHeight();
        if (w <= 0 || h <= 0) return;
        double fov = mc.options.fov().get();
        if (!SunMath.project(dir, camera.getYRot(), camera.getXRot(), fov, (double) w / h, sun)) return;
        double scale = vis * SunMath.edgeFade(sun[0], sun[1]) * config.strength();
        if (scale <= 0.0) return;

        ensureTexture(mc);
        g.flush();

        Matrix4f matrix = g.pose().last().pose();
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < FlareLayers.COUNT; i++) {
            FlareLayers.center(i, sun[0], sun[1], center);
            float cx = (float) (center[0] * w);
            float cy = (float) ((1.0 - center[1]) * h);
            float r = (float) (FlareLayers.SIZE[i] * h);
            if (cx + r < 0 || cx - r > w || cy + r < 0 || cy - r > h) continue;
            double[] c = FlareLayers.COLOR[i];
            float k = (float) (c[3] * scale);
            float red = Math.min(1f, (float) c[0] * k);
            float green = Math.min(1f, (float) c[1] * k);
            float blue = Math.min(1f, (float) c[2] * k);
            buf.addVertex(matrix, cx - r, cy - r, 0f).setUv(0f, 0f).setColor(red, green, blue, 1f);
            buf.addVertex(matrix, cx - r, cy + r, 0f).setUv(0f, 1f).setColor(red, green, blue, 1f);
            buf.addVertex(matrix, cx + r, cy + r, 0f).setUv(1f, 1f).setColor(red, green, blue, 1f);
            buf.addVertex(matrix, cx + r, cy - r, 0f).setUv(1f, 0f).setColor(red, green, blue, 1f);
        }
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Fraction (0..1) of five rays towards the sun that reach the sky. Leaves and stone block, glass does not. */
    private double sunVisible(ClientLevel level, Minecraft mc, Camera camera) {
        Vec3 from = camera.getPosition();
        double[][] offsets = {{0, 0, 0}, {SPREAD, 0, 0}, {-SPREAD, 0, 0}, {0, SPREAD, 0}, {0, -SPREAD, 0}};
        int clear = 0;
        for (double[] o : offsets) {
            Vec3 to = from.add((dir[0] + o[0]) * RAY_LENGTH, (dir[1] + o[1]) * RAY_LENGTH, (dir[2] + o[2]) * RAY_LENGTH);
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) clear++;
        }
        return clear / (double) offsets.length;
    }

    /** A grey disc: brightness = the shader's smoothstep falloff. Alpha stays 1 so the GUI shader never discards pixels. */
    private void ensureTexture(Minecraft mc) {
        if (texture != null) return;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                double dx = (x + 0.5) / TEX_SIZE * 2.0 - 1.0;
                double dy = (y + 0.5) / TEX_SIZE * 2.0 - 1.0;
                double r = Math.min(1.0, Math.sqrt(dx * dx + dy * dy));
                int v = (int) Math.round(255.0 * FlareLayers.profile(r));
                image.setPixel(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        DynamicTexture dyn = new DynamicTexture(image);
        dyn.setFilter(true, false);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("urinth_sunflare", "flare");
        mc.getTextureManager().register(id, dyn);
        texture = id;
    }
}
