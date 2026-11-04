package dev.urinth.sunflare;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Screen-space god rays, in the way of the "Godrays" of Sildur's Vibrant shaders. The sky is the light source: wherever the
 * depth buffer says "nothing here, just sky", light comes through. That sky picture is made small, then drawn several times,
 * each time shrunk a bit more towards the sun, and the layers are added up. A pixel therefore collects the sky along the line
 * from itself to the sun, so every gap between leaves and trunks becomes a soft ray pointing at the sun and every tree throws a
 * shadow ray away from it.
 *
 * It needs no shader of its own, only blending, so it cannot break the game's shader loading:
 * <ol>
 *   <li>the alpha channel of the main picture is cleared, then a quad at the far plane is drawn with the depth test and only the
 *       alpha channel writable: alpha becomes 1 exactly where the depth buffer still holds sky (colours are untouched),</li>
 *   <li>that alpha is copied into a small picture (about 1/6 of the window), averaging a few taps,</li>
 *   <li>the small picture is drawn into a second small one once per layer, shrunk towards the sun, tinted with the colour of the
 *       sun, and added (the blend uses the sky alpha as the amount),</li>
 *   <li>that result is smeared once more towards the sun into a third small picture, which multiplies the number of sample
 *       distances and so removes the steps between layers,</li>
 *   <li>a soft falloff around the sun is multiplied in, and the result is added to the screen in one full-window quad, except
 *       over open sky (so the sky does not get brighter, only the trees and the ground show rays).</li>
 * </ol>
 * When the sun is high above the picture, the rays come from a point just outside the screen edge in its direction, so they stay
 * while the player looks at the horizon. Nothing is drawn when the sun is behind the player, at night, in rain, in water or in
 * other dimensions, so then it costs nothing.
 * The number of layers follows the frame-rate guard ({@link QualityGovernor}), so the picture stays near the target frame rate.
 */
final class GodRays {
    private static final int FALLOFF_SIZE = 128;
    private static final double SKY_DEPTH = 0.99999;

    private final FlareConfig config;
    private final QualityGovernor governor;
    private final double[] dir = new double[3];
    private final double[] tint = new double[3];
    private final double[] origin = new double[3];
    private final double[] scale = new double[GodRayMath.MAX_LAYERS];
    private final double[] weight = new double[GodRayMath.MAX_LAYERS];
    private final double[] bandScale = new double[GodRayMath.BAND_LAYERS];
    private final double[] bandWeight = new double[GodRayMath.BAND_LAYERS];
    private final float[] view = new float[16];
    private final float[] proj = new float[16];
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();
    private final Vector4f corner = new Vector4f();

    private TextureTarget small;
    private TextureTarget rays;
    private TextureTarget rays2;
    private int smallWidth;
    private int smallHeight;
    private DynamicTexture falloff;
    private ResourceLocation falloffId;
    private long lastFrame;
    private double presence;
    private boolean failed;

    GodRays(FlareConfig config) {
        this.config = config;
        this.governor = new QualityGovernor(config.targetFps());
    }

    /** Quality of the effects now (0.3..1), shared with the other effects so one guard looks after the whole frame. */
    double quality() {
        return governor.quality();
    }

    void render(WorldRenderContext ctx) {
        // keep watching the frame time even while nothing is drawn, so the guard knows what a plain frame costs
        long now = System.nanoTime();
        double ms = lastFrame == 0 ? 0.0 : (now - lastFrame) / 1e6;
        double dt = Math.min(0.1, ms / 1000.0);
        lastFrame = now;
        governor.setTargetFps(config.targetFps());
        if (ms > 0.0) governor.frame(ms);
        if (failed || config.beams() == FlareConfig.BEAMS_OFF) return;
        try {
            draw(ctx, dt);
        } catch (Throwable t) {
            failed = true;
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            SunFlareMod.LOG.error("[SunFlare] god rays turned themselves off after an error", t);
        }
    }

    private void draw(WorldRenderContext ctx, double dt) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        boolean usable = level.dimension() == Level.OVERWORLD && !ctx.advancedTranslucency()
                && ctx.camera().getFluidInCamera() == FogType.NONE;
        long dayTime = level.getDayTime();
        SunMath.sunDirection(dayTime, dir);
        double target = usable ? GodRayMath.daylight(dir[1], SunMath.nightFactor(dayTime), level.getRainLevel(1.0f)) : 0.0;
        presence += (target - presence) * (1.0 - Math.exp(-dt * 2.0));
        if (presence < 0.01) return;

        ctx.positionMatrix().get(view);
        projection.set(RenderSystem.getProjectionMatrix());
        projection.get(proj);
        double vx = view[0] * dir[0] + view[4] * dir[1] + view[8] * dir[2];
        double vy = view[1] * dir[0] + view[5] * dir[1] + view[9] * dir[2];
        double vz = view[2] * dir[0] + view[6] * dir[1] + view[10] * dir[2];
        // where the rays come from: the sun's place on the screen, or (when the player looks at the horizon and the sun is high
        // above the picture) a point just outside the edge in the sun's direction, so the rays do not vanish
        GodRayMath.origin(vx, vy, vz, proj[0], proj[5], origin);
        double su = origin[0];
        double sv = origin[1];
        double vis = origin[2];
        if (vis < 0.01) return; // the sun is behind the player: nothing to smear the sky towards

        double quality = governor.quality();
        int layers = GodRayMath.layerCount(config.godRayLayers(), quality);
        GodRayMath.layers(layers, scale, weight);
        GodRayMath.bands(bandScale, bandWeight);
        SunTint.colour(dir[1], tint);
        double density = config.godRayDensity();
        double amount = presence * vis * (0.7 + 0.3 * quality);

        // what the GPU will do to a vertex is ProjMat * ModelViewMat; undo that to place quads straight in screen space
        inverse.set(projection).mul(RenderSystem.getModelViewMatrix()).invert();

        RenderTarget main = mc.getMainRenderTarget();
        int windowW = mc.getWindow().getWidth();
        int windowH = mc.getWindow().getHeight();
        ensureTargets(windowW, windowH, config.godRayDivisor());
        ensureFalloff(mc);

        main.bindWrite(true);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // 1. alpha of the main picture = 1 where the depth buffer holds sky
        RenderSystem.colorMask(false, false, false, true);
        GlStateManager._clearColor(0f, 0f, 0f, 0f);
        GlStateManager._clear(16384);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(buf, -1f, -1f, 1f, 1f, SKY_DEPTH);
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();

        // 2. that alpha, made small (four taps, each a quarter)
        RenderSystem.colorMask(true, true, true, true); // a clear obeys the colour mask, and this one must clear everything
        main.setFilterMode(9729);
        small.clear();
        small.bindWrite(true);
        RenderSystem.colorMask(false, false, false, true); // the copy writes only the alpha channel
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, main.getColorTextureId());
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float tx = 1.5f / windowW;
        float ty = 1.5f / windowH;
        for (int i = 0; i < 4; i++) {
            float ox = (i & 1) == 0 ? -tx : tx;
            float oy = (i & 2) == 0 ? -ty : ty;
            quadUv(buf, 0f + ox, 0f + oy, 1f + ox, 1f + oy, 1f, 1f, 1f, 0.25f);
        }
        mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.colorMask(true, true, true, true);
        main.setFilterMode(9728);

        // 3. the layers, shrunk towards the sun, tinted, added by the amount of sky in them. Done in two passes, the second on the
        // result of the first: pass A has fine layers for one band of distances, pass B one layer per band, so the scales multiply
        // into evenly spaced sample distances along the whole reach and no steps show.
        rays.clear();
        rays.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, small.getColorTextureId());
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float fu = (float) su;
        float fv = (float) sv;
        for (int k = 0; k < layers; k++) {
            float s = (float) scale[k];
            float w = (float) (weight[k] * density);
            float r = Math.min(1f, (float) (tint[0] * w));
            float g = Math.min(1f, (float) (tint[1] * w));
            float b = Math.min(1f, (float) (tint[2] * w));
            float u0 = fu + (0f - fu) * s, u1 = fu + (1f - fu) * s;
            float v0 = fv + (0f - fv) * s, v1 = fv + (1f - fv) * s;
            quadUv(buf, u0, v0, u1, v1, r, g, b, 1f);
        }
        mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        // 3b. pass B: the first result, once per band further out (the colours are already tinted)
        rays2.clear();
        rays2.bindWrite(true);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, rays.getColorTextureId());
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int j = 0; j < GodRayMath.BAND_LAYERS; j++) {
            float s = (float) bandScale[j];
            float w = (float) bandWeight[j];
            float u0 = fu + (0f - fu) * s, u1 = fu + (1f - fu) * s;
            float v0 = fv + (0f - fv) * s, v1 = fv + (1f - fv) * s;
            quadUv(buf, u0, v0, u1, v1, w, w, w, 1f);
        }
        mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        // brightest near the sun, fading with distance from it
        // (colour only: the alpha stays, because the game's shader throws away pixels whose alpha is small)
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.SRC_COLOR,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, falloffId);
        double aspect = (double) windowW / windowH;
        float cx = (float) (su * 2.0 - 1.0);
        float cy = (float) (sv * 2.0 - 1.0);
        float hy = (float) (GodRayMath.FALLOFF_RADIUS * 2.0);
        float hx = (float) (GodRayMath.FALLOFF_RADIUS * 2.0 / aspect);
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        quadUv(buf, cx - hx, cy - hy, cx + hx, cy + hy, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
        mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        // 4. add to the screen, but not over open sky: the alpha of the main picture still says where the sky is, so the sky itself
        // does not get brighter (that was the glare around the sun) and the rays show on trees, hills and the ground
        main.bindWrite(true);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_ALPHA, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, rays2.getColorTextureId());
        float a = (float) Math.min(1.0, amount);
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        quadUv(buf, -1f, -1f, 1f, 1f, 0f, 0f, 1f, 1f, a, a, a, 1f);
        mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        // leave the main picture the way the game expects: alpha 1 everywhere, normal blending, depth test on
        RenderSystem.colorMask(false, false, false, true);
        GlStateManager._clearColor(0f, 0f, 0f, 1f);
        GlStateManager._clear(16384);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    /** A full-target quad in clip space (x0..x1, y0..y1), placed through the inverse of the GPU's matrices at depth z. */
    private void quad(BufferBuilder buf, float x0, float y0, float x1, float y1, double z) {
        vertex(buf, x0, y0, z, 1f, 1f, 1f, 1f);
        vertex(buf, x0, y1, z, 1f, 1f, 1f, 1f);
        vertex(buf, x1, y1, z, 1f, 1f, 1f, 1f);
        vertex(buf, x1, y0, z, 1f, 1f, 1f, 1f);
    }

    private void vertex(BufferBuilder buf, float x, float y, double z, float r, float g, float b, float a) {
        corner.set(x, y, (float) (z * 2.0 - 1.0), 1f);
        inverse.transform(corner);
        float w = corner.w;
        buf.addVertex(corner.x / w, corner.y / w, corner.z / w).setColor(r, g, b, a);
    }

    private void vertexUv(BufferBuilder buf, float x, float y, float u, float v, float r, float gr, float b, float a) {
        corner.set(x, y, 0f, 1f);
        inverse.transform(corner);
        float w = corner.w;
        buf.addVertex(corner.x / w, corner.y / w, corner.z / w).setUv(u, v).setColor(r, gr, b, a);
    }

    /** Clip-space rectangle showing the texture rectangle (u0, v0)-(u1, v1) corner to corner. */
    private void quadUv(BufferBuilder buf, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                        float r, float g, float b, float a) {
        vertexUv(buf, x0, y0, u0, v0, r, g, b, a);
        vertexUv(buf, x0, y1, u0, v1, r, g, b, a);
        vertexUv(buf, x1, y1, u1, v1, r, g, b, a);
        vertexUv(buf, x1, y0, u1, v0, r, g, b, a);
    }

    /** Covers the whole target (0..1 on both axes in texture space) with a single colour. */
    private void quadUv(BufferBuilder buf, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        quadUv(buf, -1f, -1f, 1f, 1f, u0, v0, u1, v1, r, g, b, a);
    }

    private void ensureTargets(int windowW, int windowH, int divisor) {
        int w = Math.max(64, windowW / divisor);
        int h = Math.max(36, windowH / divisor);
        if (small != null && smallWidth == w && smallHeight == h) return;
        if (small != null) small.destroyBuffers();
        if (rays != null) rays.destroyBuffers();
        if (rays2 != null) rays2.destroyBuffers();
        small = new TextureTarget(w, h, false);
        small.setClearColor(1f, 1f, 1f, 0f); // white, so the layers can take their colour from the vertices; alpha is the sky
        small.setFilterMode(9729);
        rays = new TextureTarget(w, h, false);
        rays.setClearColor(0f, 0f, 0f, 0f);
        rays.setFilterMode(9729);
        rays2 = new TextureTarget(w, h, false);
        rays2.setClearColor(0f, 0f, 0f, 0f);
        rays2.setFilterMode(9729);
        smallWidth = w;
        smallHeight = h;
    }

    private void ensureFalloff(Minecraft mc) {
        if (falloff != null) return;
        byte[] px = GodRayMath.falloffTexture(FALLOFF_SIZE);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, FALLOFF_SIZE, FALLOFF_SIZE, false);
        for (int y = 0; y < FALLOFF_SIZE; y++) {
            for (int x = 0; x < FALLOFF_SIZE; x++) {
                int v = px[y * FALLOFF_SIZE + x] & 0xFF;
                image.setPixel(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        falloff = new DynamicTexture(image);
        falloff.setFilter(true, false);
        falloffId = ResourceLocation.fromNamespaceAndPath("urinth_sunflare", "godray_falloff");
        mc.getTextureManager().register(falloffId, falloff);
    }
}
