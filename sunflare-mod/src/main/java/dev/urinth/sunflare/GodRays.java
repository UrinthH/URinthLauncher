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
 * depth buffer says "nothing here, just sky", light comes through. That sky picture is made small and smeared towards the sun,
 * so every gap between leaves and trunks becomes a soft ray pointing at the sun and every tree throws a shadow ray away from it.
 *
 * A phone GPU pays for every pixel it touches, so the work is arranged to touch few of them:
 * only a box around the sun is handled at full window size (the rays fade to nothing at its edge anyway, and it shrinks a
 * little when the frame-rate guard asks for it), and the smearing is done on a tiny picture with a few passes that each double
 * the number of samples ({@link GodRayMath}), instead of dozens of blended layers.
 *
 * It needs no shader of its own, only blending, so it cannot break the game's shader loading:
 * <ol>
 *   <li>inside the box, the alpha channel of the main picture is cleared, then a quad at the far plane is drawn with the depth test
 *       and only the alpha channel writable: alpha becomes 1 exactly where the depth buffer still holds sky (colours are untouched),</li>
 *   <li>that alpha is copied into a small picture (1/6 of the window), then into a smaller one (the sky as a grey value),</li>
 *   <li>the smaller picture is smeared towards the sun: 4 to 6 fine passes, each adding the picture to a copy of itself shrunk
 *       towards the sun (every pass doubles the samples along each ray), then one pass that adds it to copies of itself one
 *       band further out each, so the samples tile the whole way to the sun with steps finer than the blur,</li>
 *   <li>a soft falloff around the sun is multiplied in, and the result, tinted with the colour of the sun, is added to the screen
 *       inside the box, except over open sky (so the sky does not get brighter, only trees and ground show rays). That same
 *       draw puts the alpha channel back to 1.</li>
 * </ol>
 * When the sun is high above the picture, the rays come from a point just outside the screen edge in its direction, so they stay
 * while the player looks at the horizon. With the sun behind the player the rays come from the opposite point of the sky
 * (weaker), so they show whichever way the player looks. Nothing is drawn at night, in rain, in water or in other dimensions,
 * so then it costs nothing.
 * The size of the box and the number of passes follow the frame-rate guard ({@link QualityGovernor}).
 */
final class GodRays {
    private static final int FALLOFF_SIZE = 128;
    private static final int DIVISOR_A = 6;
    private static final double SKY_DEPTH = 0.99999;

    private final FlareConfig config;
    private final QualityGovernor governor;
    private final double[] dir = new double[3];
    private final double[] tint = new double[3];
    private final double[] origin = new double[3];
    private final double[] shrink = new double[GodRayMath.MAX_PASSES];
    private final double[] mix = new double[GodRayMath.MAX_PASSES];
    private final double[] bandScale = new double[GodRayMath.BANDS];
    private final double[] bandWeight = new double[GodRayMath.BANDS];
    private final float[] view = new float[16];
    private final float[] proj = new float[16];
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();
    private final Vector4f corner = new Vector4f();

    /** The sky as alpha, 1/6 of the window. */
    private TextureTarget small;
    /** The sky as a grey value, then the two pictures the smear passes swap between. */
    private TextureTarget work;
    private TextureTarget ping;
    private TextureTarget pong;
    private int smallWidth;
    private int smallHeight;
    private int workWidth;
    private int workHeight;
    private DynamicTexture falloff;
    private ResourceLocation falloffId;
    private long lastFrame;
    private boolean lastDrawn;
    private double presence;
    private boolean failed;

    GodRays(FlareConfig config) {
        this.config = config;
        this.governor = new QualityGovernor(config.targetFps());
    }

    /** Quality of the effects now (0.1..1), shared with the other effects so one guard looks after the whole frame. */
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
        if (ms > 0.0) governor.frame(ms, lastDrawn);
        lastDrawn = false;
        if (failed || config.beams() == FlareConfig.BEAMS_OFF) return;
        try {
            lastDrawn = draw(ctx, dt);
        } catch (Throwable t) {
            failed = true;
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            SunFlareMod.LOG.error("[SunFlare] god rays turned themselves off after an error", t);
        }
    }

    /** @return true when the rays were drawn this frame */
    private boolean draw(WorldRenderContext ctx, double dt) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return false;
        boolean usable = level.dimension() == Level.OVERWORLD && !ctx.advancedTranslucency()
                && ctx.camera().getFluidInCamera() == FogType.NONE;
        long dayTime = level.getDayTime();
        SunMath.sunDirection(dayTime, dir);
        double target = usable ? GodRayMath.daylight(dir[1], SunMath.nightFactor(dayTime), level.getRainLevel(1.0f)) : 0.0;
        presence += (target - presence) * (1.0 - Math.exp(-dt * 2.0));
        if (presence < 0.01) return false;

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
        if (vis < 0.01) return false; // exactly at the side of a far-off point, or too faint to see

        double quality = governor.quality();
        int passes = GodRayMath.passes(config.godRayLayers());
        int bands = GodRayMath.bandCount(quality);
        GodRayMath.smear(passes, shrink, mix);
        GodRayMath.bands(passes, bands, bandScale, bandWeight);
        SunTint.colour(dir[1], tint);
        double density = config.godRayDensity();
        double amount = presence * vis * (0.7 + 0.3 * quality);

        RenderTarget main = mc.getMainRenderTarget();
        int windowW = mc.getWindow().getWidth();
        int windowH = mc.getWindow().getHeight();
        double aspect = (double) windowW / windowH;
        // the box around the point the rays come from that is worth drawing (rays are zero beyond it), cut to the screen and
        // rounded out to whole pixels so the clear, the sky mask and the final add all cover exactly the same pixels
        double radius = GodRayMath.regionRadius(quality);
        int px0 = (int) Math.max(0, Math.floor((su - radius / aspect) * windowW));
        int px1 = (int) Math.min(windowW, Math.ceil((su + radius / aspect) * windowW));
        int py0 = (int) Math.max(0, Math.floor((sv - radius) * windowH));
        int py1 = (int) Math.min(windowH, Math.ceil((sv + radius) * windowH));
        if (px1 - px0 < 8 || py1 - py0 < 8) return false;
        float mx0 = px0 * 2f / windowW - 1f;
        float mx1 = px1 * 2f / windowW - 1f;
        float my0 = py0 * 2f / windowH - 1f;
        float my1 = py1 * 2f / windowH - 1f;
        float tu0 = (float) px0 / windowW;
        float tu1 = (float) px1 / windowW;
        float tv0 = (float) py0 / windowH;
        float tv1 = (float) py1 / windowH;

        // what the GPU will do to a vertex is ProjMat * ModelViewMat; undo that to place quads straight in screen space
        inverse.set(projection).mul(RenderSystem.getModelViewMatrix()).invert();
        ensureTargets(windowW, windowH, config.godRayDivisor());
        ensureFalloff(mc);

        main.bindWrite(true);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // 1. alpha of the main picture inside the box = 1 where the depth buffer holds sky
        RenderSystem.enableScissor(px0, py0, px1 - px0, py1 - py0);
        RenderSystem.colorMask(false, false, false, true);
        GlStateManager._clearColor(0f, 0f, 0f, 0f);
        GlStateManager._clear(16384);
        RenderSystem.disableScissor();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(buf, mx0, my0, mx1, my1, SKY_DEPTH);
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();

        // 2. that alpha, made small (four taps, each a quarter): the box of the screen into the whole small picture
        RenderSystem.colorMask(true, true, true, true); // a clear obeys the colour mask, and this one must clear everything
        main.setFilterMode(9729);
        small.clear();
        small.bindWrite(true);
        RenderSystem.colorMask(false, false, false, true); // the copy writes only the alpha channel
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, main.getColorTextureId());
        // the quarter goes in the shader colour: the game's shader throws away pixels whose own alpha is small
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.25f);
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float tx = 1.5f / windowW;
        float ty = 1.5f / windowH;
        for (int i = 0; i < 4; i++) {
            float ox = (i & 1) == 0 ? -tx : tx;
            float oy = (i & 2) == 0 ? -ty : ty;
            quadUv(buf, -1f, -1f, 1f, 1f, tu0 + ox, tv0 + oy, tu1 + ox, tv1 + oy, 1f, 1f, 1f, 1f);
        }
        mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        main.setFilterMode(9728);

        // 2b. smaller still, and the sky becomes a grey value (the alpha, as the blend factor, times white); alpha stays 1
        RenderSystem.colorMask(true, true, true, true);
        work.clear();
        work.bindWrite(true);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, small.getColorTextureId());
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float sx = 0.5f / smallWidth;
        float sy = 0.5f / smallHeight;
        for (int i = 0; i < 4; i++) {
            float ox = (i & 1) == 0 ? -sx : sx;
            float oy = (i & 2) == 0 ? -sy : sy;
            quadUv(buf, -1f, -1f, 1f, 1f, ox, oy, 1f + ox, 1f + oy, 1f, 1f, 1f, 1f);
        }
        mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // 3. the smear passes. The point the rays come from, in the box's picture coordinates:
        float fu = (float) ((su * windowW - px0) / (px1 - px0));
        float fv = (float) ((sv * windowH - py0) / (py1 - py0));
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        TextureTarget from = work;
        TextureTarget to = ping;
        for (int k = 0; k < passes; k++) {
            to.clear();
            to.bindWrite(true);
            RenderSystem.setShaderTexture(0, from.getColorTextureId());
            float m = (float) mix[k];
            float keep = 1f - m;
            float s = (float) (1.0 - shrink[k]);
            float u0 = fu + (0f - fu) * s, u1 = fu + (1f - fu) * s;
            float v0 = fv + (0f - fv) * s, v1 = fv + (1f - fv) * s;
            buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            quadUv(buf, -1f, -1f, 1f, 1f, 0f, 0f, 1f, 1f, keep, keep, keep, 1f);
            quadUv(buf, -1f, -1f, 1f, 1f, u0, v0, u1, v1, m, m, m, 1f);
            mesh = buf.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
            from = to;
            to = (to == ping) ? pong : ping;
        }

        // 3b. the band pass: the result, once per band further out, so the bands tile the whole way to the sun
        to.clear();
        to.bindWrite(true);
        RenderSystem.setShaderTexture(0, from.getColorTextureId());
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int j = 0; j < bands; j++) {
            float s = (float) bandScale[j];
            float w = (float) bandWeight[j];
            float u0 = fu + (0f - fu) * s, u1 = fu + (1f - fu) * s;
            float v0 = fv + (0f - fv) * s, v1 = fv + (1f - fv) * s;
            quadUv(buf, -1f, -1f, 1f, 1f, u0, v0, u1, v1, w, w, w, 1f);
        }
        mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        from = to;

        // 3c. brightest near the sun, fading to nothing at the edge of the box (colour only: the alpha stays 1, because the
        // game's shader throws away pixels whose alpha is small)
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.SRC_COLOR,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, falloffId);
        float cx = fu * 2f - 1f;
        float cy = fv * 2f - 1f;
        // the falloff texture spans the area of radius R around the point; in this picture that is 2R/(box size) of it
        float hx = (float) (radius * 2.0 * windowH / (px1 - px0));
        float hy = (float) (radius * 2.0 * windowH / (py1 - py0));
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        quadUv(buf, cx - hx, cy - hy, cx + hx, cy + hy, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
        mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        // 4. add to the screen inside the box, but not over open sky: the alpha of the main picture still says where the sky is,
        // so the sky itself does not get brighter (that was the glare around the sun) and the rays show on trees, hills and
        // the ground. The picture's own alpha is 1, and the alpha blend factors write it, so the main picture leaves this
        // draw with alpha 1 again, as the game expects.
        main.bindWrite(true);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_ALPHA, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderTexture(0, from.getColorTextureId());
        float a = (float) Math.min(1.0, amount);
        float gain = (float) density; // may be above 1: the shader multiplies in float before the blend
        RenderSystem.setShaderColor(gain, gain, gain, 1f);
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        quadUv(buf, mx0, my0, mx1, my1, 0f, 0f, 1f, 1f, (float) (tint[0] * a), (float) (tint[1] * a), (float) (tint[2] * a), 1f);
        mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        return true;
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
        int w = Math.max(64, windowW / DIVISOR_A);
        int h = Math.max(36, windowH / DIVISOR_A);
        int w2 = Math.max(32, windowW / Math.max(DIVISOR_A, divisor));
        int h2 = Math.max(18, windowH / Math.max(DIVISOR_A, divisor));
        if (small != null && smallWidth == w && smallHeight == h && workWidth == w2 && workHeight == h2) return;
        if (small != null) small.destroyBuffers();
        if (work != null) work.destroyBuffers();
        if (ping != null) ping.destroyBuffers();
        if (pong != null) pong.destroyBuffers();
        small = new TextureTarget(w, h, false);
        small.setClearColor(1f, 1f, 1f, 0f); // white, so the next step can take the colour from it; alpha is the sky
        small.setFilterMode(9729);
        work = new TextureTarget(w2, h2, false);
        work.setClearColor(0f, 0f, 0f, 1f); // black with alpha 1: the passes only add grey, and the picture's alpha stays 1
        work.setFilterMode(9729);
        ping = new TextureTarget(w2, h2, false);
        ping.setClearColor(0f, 0f, 0f, 1f);
        ping.setFilterMode(9729);
        pong = new TextureTarget(w2, h2, false);
        pong.setClearColor(0f, 0f, 0f, 1f);
        pong.setFilterMode(9729);
        smallWidth = w;
        smallHeight = h;
        workWidth = w2;
        workHeight = h2;
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
