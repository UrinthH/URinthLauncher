package dev.urinth.sunflare;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
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
 * Draws the sun lens flare of the CilokGaming pack (see {@link FlareSet}) on top of the finished frame.
 *
 * Cost per frame: at most about 27 textured quads in two draw calls and one full-screen quad for the slight darkening.
 * Visibility comes from a few block ray casts a few times a second, not from reading the depth buffer back from the GPU.
 */
final class FlareRenderer {
    private static final long RAY_INTERVAL_NANOS = 60_000_000L;
    private static final double RAY_LENGTH = 128.0;
    private static final double SPREAD = 0.04;

    private final FlareConfig config;
    private final FlareFade fade = new FlareFade(10.0);
    private final FlareFrame frame = new FlareFrame();
    private final PairBaker baker = new PairBaker();
    private final double[] dir = new double[3];
    private final double[] sun = new double[2];
    private final double[] rect = new double[4];
    private final double[] pairLevel = new double[FlareSet.COUNT];

    private DynamicTexture atlas;
    private DynamicTexture pairs;
    private ResourceLocation atlasId;
    private ResourceLocation pairsId;
    private byte[] pairBytes;
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
        long dayTime = level.getDayTime();
        SunMath.sunDirection(dayTime, dir);
        usable &= dir[1] > -0.02;

        if (usable && now - lastRay > RAY_INTERVAL_NANOS) {
            lastRay = now;
            rayTarget = sunVisible(level, mc, camera);
        }
        double vis = fade.update(usable ? rayTarget : 0.0, dt);
        if (vis <= 0.0) return;

        int w = g.guiWidth();
        int h = g.guiHeight();
        if (w <= 0 || h <= 0) return;
        double aspect = (double) w / h;
        double fov = mc.options.fov().get();
        if (!SunMath.project(dir, camera.getYRot(), camera.getXRot(), fov, aspect, sun)) return;
        double mask = vis * SunMath.edgeFade(sun[0], sun[1]);
        if (mask <= 0.0) return;

        double night = SunMath.nightFactor(dayTime);
        double rain = level.getRainLevel(tracker.getGameTimeDeltaPartialTick(false));
        frame.compute(sun[0], sun[1], aspect, config.power(), mask, night, rain);
        if (frame.count == 0 && frame.dim <= 0.0) return;

        ensureTextures(mc);
        g.flush();
        Matrix4f matrix = g.pose().last().pose();

        double dim = frame.dim * (1.0 - night);
        if (dim > 0.002) drawDim(matrix, w, h, (float) dim);
        drawFlares(matrix, w, h, aspect);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** The pack darkens the scene a little when the sun is near the middle of the screen. */
    private void drawDim(Matrix4f matrix, int w, int h, float alpha) {
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.addVertex(matrix, 0f, 0f, 0f).setColor(0f, 0f, 0f, alpha);
        buf.addVertex(matrix, 0f, h, 0f).setColor(0f, 0f, 0f, alpha);
        buf.addVertex(matrix, w, h, 0f).setColor(0f, 0f, 0f, alpha);
        buf.addVertex(matrix, w, 0f, 0f).setColor(0f, 0f, 0f, alpha);
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private void drawFlares(Matrix4f matrix, int w, int h, double aspect) {
        // Bake this frame's crescents first (they depend on where the sun is).
        boolean anyPair = false;
        double[] level = pairLevel;
        java.util.Arrays.fill(level, 0.0);
        for (int i = 0; i < frame.count; i++) {
            FlareFrame.Draw d = frame.draws[i];
            if (!d.pair) continue;
            level[i] = baker.bake(FlareSet.ELEMENTS[d.element], d, sun[0], sun[1], aspect, frame.sm, frame.flareScale, pairBytes);
            anyPair |= level[i] > 0.0;
        }
        if (anyPair) uploadPairs();

        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

        drawBatch(matrix, w, h, atlasId, false, level);
        if (anyPair) drawBatch(matrix, w, h, pairsId, true, level);
    }

    private void drawBatch(Matrix4f matrix, int w, int h, ResourceLocation texture, boolean pairBatch, double[] level) {
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < frame.count; i++) {
            FlareFrame.Draw d = frame.draws[i];
            if (d.pair != pairBatch) continue;
            FlareElement e = FlareSet.ELEMENTS[d.element];
            double red = d.red;
            double green = d.green;
            double blue = d.blue;
            if (pairBatch) {
                if (level[i] <= 0.0) continue;
                red = e.red * level[i];
                green = e.green * level[i];
                blue = e.blue * level[i];
            }
            float cx = (float) (d.cx * w);
            float cy = (float) ((1.0 - d.cy) * h);
            float rx = (float) (d.rx * w);
            float ry = (float) (d.ry * h);
            if (cx + rx < 0 || cx - rx > w || cy + ry < 0 || cy - ry > h) continue;
            FlareAtlas.uvRect(d.element, rect);
            float u0 = (float) rect[0];
            float v0 = (float) rect[1];
            float u1 = (float) rect[2];
            float v1 = (float) rect[3];
            float r = Math.min(1f, (float) red);
            float gr = Math.min(1f, (float) green);
            float b = Math.min(1f, (float) blue);
            buf.addVertex(matrix, cx - rx, cy - ry, 0f).setUv(u0, v0).setColor(r, gr, b, 1f);
            buf.addVertex(matrix, cx - rx, cy + ry, 0f).setUv(u0, v1).setColor(r, gr, b, 1f);
            buf.addVertex(matrix, cx + rx, cy + ry, 0f).setUv(u1, v1).setColor(r, gr, b, 1f);
            buf.addVertex(matrix, cx + rx, cy - ry, 0f).setUv(u1, v0).setColor(r, gr, b, 1f);
        }
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
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

    /**
     * Grey textures with alpha always 255, so the GUI shader never throws pixels away. The brightness lives in the vertex
     * colour, and the blend is plain addition.
     */
    private void ensureTextures(Minecraft mc) {
        if (atlas != null) return;
        byte[] singles = FlareAtlas.buildSingles();
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, FlareAtlas.WIDTH, FlareAtlas.HEIGHT, false);
        for (int y = 0; y < FlareAtlas.HEIGHT; y++) {
            for (int x = 0; x < FlareAtlas.WIDTH; x++) {
                int v = singles[y * FlareAtlas.WIDTH + x] & 0xFF;
                image.setPixel(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        atlas = new DynamicTexture(image);
        atlas.setFilter(true, false);
        atlasId = ResourceLocation.fromNamespaceAndPath("urinth_sunflare", "atlas");
        mc.getTextureManager().register(atlasId, atlas);

        pairBytes = new byte[FlareAtlas.PAIR_SIZE * FlareAtlas.PAIR_SIZE];
        NativeImage pairImage = new NativeImage(NativeImage.Format.RGBA, FlareAtlas.PAIR_SIZE, FlareAtlas.PAIR_SIZE, false);
        for (int y = 0; y < FlareAtlas.PAIR_SIZE; y++) {
            for (int x = 0; x < FlareAtlas.PAIR_SIZE; x++) pairImage.setPixel(x, y, 0xFF000000);
        }
        pairs = new DynamicTexture(pairImage);
        pairs.setFilter(true, false);
        pairsId = ResourceLocation.fromNamespaceAndPath("urinth_sunflare", "pairs");
        mc.getTextureManager().register(pairsId, pairs);
    }

    private void uploadPairs() {
        NativeImage image = pairs.getPixels();
        if (image == null) return;
        int n = FlareAtlas.PAIR_SIZE;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int v = pairBytes[y * n + x] & 0xFF;
                image.setPixel(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        pairs.upload();
    }
}
