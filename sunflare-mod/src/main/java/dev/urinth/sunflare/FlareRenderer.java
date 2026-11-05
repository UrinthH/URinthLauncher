package dev.urinth.sunflare;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
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
 * The flare is made of big, soft, overlapping layers. Blending them all at full resolution costs several screens of pixels,
 * which is too much for a phone GPU. So the big layers are blended into a quarter-resolution picture, and that picture is
 * copied onto the screen once, only where the flare is bright enough to matter. Small sharp elements (the glints) are drawn
 * straight to the screen. Visibility comes from a few block ray casts a few times a second, not from reading the depth buffer
 * back from the GPU.
 */
final class FlareRenderer {
    private static final long RAY_INTERVAL_NANOS = 120_000_000L;
    private static final double RAY_LENGTH = 128.0;
    private static final double SPREAD = 0.04;
    /** The small picture is this many times smaller than the window in each direction. */
    private static final int DIVISOR = 4;

    private final FlareConfig config;
    private final FlareFade fade = new FlareFade(10.0);
    private final FlareFrame frame = new FlareFrame();
    private final PairBaker baker = new PairBaker();
    private final double[] dir = new double[3];
    private final double[] sun = new double[2];
    private final double[] rect = new double[4];
    private final double[] pairLevel = new double[FlareSet.COUNT];
    private final FlareCoverage coverage = new FlareCoverage();
    private final double[] pairLast = new double[FlareSet.COUNT];
    private final boolean[] pairFresh = new boolean[FlareSet.COUNT];
    private final boolean[] pairSeen = new boolean[FlareSet.COUNT];
    private long frameNo;
    private boolean reached;
    private boolean coverageValid;
    private RenderTarget lowRes;
    private int lowWidth;
    private int lowHeight;
    private boolean bufferFailed;

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
            reached = false;
            draw(g, tracker);
            if (!reached) {
                java.util.Arrays.fill(pairFresh, false);
                coverageValid = false;
            }
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
        boolean real = config.realStyle();
        double forward = SunMath.projectWide(dir, camera.getYRot(), camera.getXRot(), fov, aspect, sun);
        boolean ahead = forward >= 0.05;
        double edge = ahead ? SunMath.edgeFade(sun[0], sun[1]) : 0.0;
        double reach = ahead ? SunMath.glowReach(sun[0], sun[1]) : 0.0;
        if (!real && edge * vis <= 0.0) return; // the pack's flare needs the sun on the screen
        // The Real style does not need the sun in view: its glow carries on just past the screen edge and a faint warm veil
        // takes over when a low sun is out of view (even behind the player). Only the reflections need the sun on screen.

        double night = SunMath.nightFactor(dayTime);
        double rain = level.getRainLevel(tracker.getGameTimeDeltaPartialTick(false));
        frame.real = real;
        frame.compute(sun[0], sun[1], aspect, config.power(), vis, edge, reach, night, rain, dir[1]);
        if (frame.count == 0) return;

        ensureTextures(mc);
        g.flush();
        Matrix4f matrix = g.pose().last().pose();

        drawFlares(mc, matrix, w, h, aspect);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void drawFlares(Minecraft mc, Matrix4f matrix, int w, int h, double aspect) {
        reached = true;
        // Bake this frame's crescents first (they depend on where the sun is).
        boolean anyPair = false;
        boolean baked = false;
        double[] level = pairLevel;
        java.util.Arrays.fill(level, 0.0);
        boolean anyBig = false;
        frameNo++;
        java.util.Arrays.fill(pairSeen, false);
        for (int i = 0; i < frame.count; i++) {
            FlareFrame.Draw d = frame.draws[i];
            anyBig |= !d.small;
            if (!d.pair) continue;
            pairSeen[d.element] = true;
            // Each crescent is re-baked every other frame (a one frame lag in a soft crescent cannot be seen),
            // and straight away the first time it appears.
            boolean myTurn = (FlareAtlas.pairSlot(d.element) & 1) == (frameNo & 1);
            if (myTurn || !pairFresh[d.element]) {
                pairLast[d.element] = baker.bake(FlareSet.ELEMENTS[d.element], d, sun[0], sun[1], aspect, frame.sm, frame.flareScale, pairBytes);
                pairFresh[d.element] = true;
                baked = true;
            }
            level[i] = pairLast[d.element];
            anyPair |= level[i] > 0.0;
        }
        // a crescent that was not drawn this frame must be baked again the moment it comes back
        for (int e = 0; e < FlareSet.COUNT; e++) {
            if (FlareSet.ELEMENTS[e].isPair() && !pairSeen[e]) pairFresh[e] = false;
        }
        if (baked) uploadPairs();

        boolean bigDone = !anyBig;
        if (anyBig && !bufferFailed) {
            try {
                bigDone = drawBigThroughBuffer(mc, matrix, w, h, anyPair, level);
            } catch (Throwable t) {
                bufferFailed = true;
                SunFlareMod.LOG.error("[SunFlare] the small buffer failed, drawing at full resolution instead", t);
                mc.getMainRenderTarget().bindWrite(true);
            }
        }

        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        if (!bigDone) drawBatches(matrix, w, h, false, anyPair, level);
        drawBatches(matrix, w, h, true, anyPair, level);
    }

    /** The big layers, blended at a fraction of the resolution, then copied onto the screen once. */
    private boolean drawBigThroughBuffer(Minecraft mc, Matrix4f matrix, int w, int h, boolean anyPair, double[] level) {
        int lw = Math.max(32, mc.getWindow().getWidth() / DIVISOR);
        int lh = Math.max(32, mc.getWindow().getHeight() / DIVISOR);
        if (lowRes == null || lowWidth != lw || lowHeight != lh) {
            if (lowRes != null) lowRes.destroyBuffers();
            lowRes = new TextureTarget(lw, lh, false);
            lowRes.setClearColor(0f, 0f, 0f, 0f);
            lowRes.setFilterMode(9729); // GL_LINEAR, so the small picture is smoothly enlarged
            lowWidth = lw;
            lowHeight = lh;
        }

        lowRes.clear();
        lowRes.bindWrite(true);
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        drawBatches(matrix, w, h, false, anyPair, level);
        mc.getMainRenderTarget().bindWrite(true);

        // the mask has a tile of margin, so it only needs refreshing every other frame
        if (!coverageValid || (frameNo & 1) == 0) {
            coverage.compute(frame);
            coverageValid = true;
        }
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, lowRes.getColorTextureId());
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int gy = 0; gy < FlareCoverage.GY; gy++) {
            int gx = 0;
            while (gx < FlareCoverage.GX) {
                if (!coverage.cells[gy * FlareCoverage.GX + gx]) {
                    gx++;
                    continue;
                }
                int start = gx;
                while (gx < FlareCoverage.GX && coverage.cells[gy * FlareCoverage.GX + gx]) gx++;
                float x0 = (float) w * start / FlareCoverage.GX;
                float x1 = (float) w * gx / FlareCoverage.GX;
                float y0 = (float) h * gy / FlareCoverage.GY;
                float y1 = (float) h * (gy + 1) / FlareCoverage.GY;
                float u0 = (float) start / FlareCoverage.GX;
                float u1 = (float) gx / FlareCoverage.GX;
                float vTop = 1f - (float) gy / FlareCoverage.GY;
                float vBottom = 1f - (float) (gy + 1) / FlareCoverage.GY;
                buf.addVertex(matrix, x0, y0, 0f).setUv(u0, vTop).setColor(1f, 1f, 1f, 1f);
                buf.addVertex(matrix, x0, y1, 0f).setUv(u0, vBottom).setColor(1f, 1f, 1f, 1f);
                buf.addVertex(matrix, x1, y1, 0f).setUv(u1, vBottom).setColor(1f, 1f, 1f, 1f);
                buf.addVertex(matrix, x1, y0, 0f).setUv(u1, vTop).setColor(1f, 1f, 1f, 1f);
            }
        }
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        return true;
    }

    private void drawBatches(Matrix4f matrix, int w, int h, boolean small, boolean anyPair, double[] level) {
        drawBatch(matrix, w, h, atlasId, false, small, level);
        if (anyPair) drawBatch(matrix, w, h, pairsId, true, small, level);
    }

    private void drawBatch(Matrix4f matrix, int w, int h, ResourceLocation texture, boolean pairBatch, boolean small, double[] level) {
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < frame.count; i++) {
            FlareFrame.Draw d = frame.draws[i];
            if (d.pair != pairBatch || d.small != small) continue;
            FlareElement e = FlareSet.ELEMENTS[d.element];
            double red = d.red;
            double green = d.green;
            double blue = d.blue;
            if (pairBatch) {
                if (level[i] <= 0.0) continue;
                red = d.baseR * level[i];
                green = d.baseG * level[i];
                blue = d.baseB * level[i];
            }
            float cx = (float) (d.cx * w);
            float cy = (float) ((1.0 - d.cy) * h);
            float rx = (float) (d.rx * d.support * w);
            float ry = (float) (d.ry * d.support * h);
            if (cx + rx < 0 || cx - rx > w || cy + ry < 0 || cy - ry > h) continue;
            FlareAtlas.uvRect(d.element, rect);
            double um = (rect[0] + rect[2]) / 2;
            double vm = (rect[1] + rect[3]) / 2;
            float u0 = (float) (um - (um - rect[0]) * d.support);
            float u1 = (float) (um + (rect[2] - um) * d.support);
            float v0 = (float) (vm - (vm - rect[1]) * d.support);
            float v1 = (float) (vm + (rect[3] - vm) * d.support);
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

    /** Fraction (0..1) of three rays towards the sun that reach the sky. Leaves and stone block, glass does not. */
    private double sunVisible(ClientLevel level, Minecraft mc, Camera camera) {
        Vec3 from = camera.getPosition();
        double[][] offsets = {{0, 0, 0}, {SPREAD, SPREAD, 0}, {-SPREAD, -SPREAD, 0}};
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
