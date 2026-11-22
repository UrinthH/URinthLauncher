package dev.urinth.recordable.gl;

import dev.urinth.recordable.enc.Frame;
import dev.urinth.recordable.enc.FramePool;
import dev.urinth.recordable.enc.Nv12;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GLCapabilities;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Takes the picture of the game for the recorder with as little work for the game as possible.
 *
 * <p>Every captured frame goes through four steps, and only the first three cost the game anything:
 * <ol>
 *   <li>The graphics chip shrinks the finished frame to the video size ({@code glBlitFramebuffer}).</li>
 *   <li>Two tiny shader passes turn it into NV12, the format the phone's video encoder wants (this also flips it upright).</li>
 *   <li>The result is copied to a pixel buffer on the graphics chip; this call returns at once.</li>
 *   <li>A few frames later, when a fence says the copy is done, the bytes are copied into a reusable buffer for the encoder thread.</li>
 * </ol>
 * Nothing waits for the graphics chip, and nothing is allocated per frame. All OpenGL state that is touched is saved and put back, so
 * the game and other mods never notice.
 *
 * <p>If the shader path does not work on a device (it is tested once when it starts), {@link Mode#RGBA} reads the shrunken picture
 * without conversion and the encoder side converts it on the CPU.
 *
 * <p>Use only from the render thread.
 */
public final class GpuCapture implements AutoCloseable {
    public enum Mode { NV12, RGBA }

    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_NEAREST = 0x2600;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    private static final int GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_READ_FRAMEBUFFER_BINDING = 0x8CAA;
    private static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_COLOR_BUFFER_BIT = 0x4000;
    private static final int GL_VERSION = 0x1F02;
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_ARRAY_BUFFER_BINDING = 0x8894;
    private static final int GL_STATIC_DRAW = 0x88E4;
    private static final int GL_STREAM_READ = 0x88E1;
    private static final int GL_PIXEL_PACK_BUFFER = 0x88EB;
    private static final int GL_PIXEL_PACK_BUFFER_BINDING = 0x88ED;
    private static final int GL_VERTEX_SHADER = 0x8B31;
    private static final int GL_FRAGMENT_SHADER = 0x8B30;
    private static final int GL_COMPILE_STATUS = 0x8B81;
    private static final int GL_LINK_STATUS = 0x8B82;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_VERTEX_ARRAY_BINDING = 0x85B5;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_DEPTH_TEST = 0x0B71;
    private static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_DITHER = 0x0BD0;
    private static final int GL_COLOR_WRITEMASK = 0x0C23;
    private static final int GL_VIEWPORT = 0x0BA2;
    private static final int GL_ACTIVE_TEXTURE = 0x84E0;
    private static final int GL_TEXTURE0 = 0x84C0;
    private static final int GL_TEXTURE_BINDING_2D = 0x8069;
    private static final int GL_MAP_READ_BIT = 0x0001;
    private static final int GL_READ_ONLY = 0x88B8;
    private static final int GL_SYNC_GPU_COMMANDS_COMPLETE = 0x9117;
    private static final int GL_TIMEOUT_EXPIRED = 0x911B;
    private static final int GL_WAIT_FAILED = 0x911D;

    /** Functions without which nothing works. */
    private static final String[] REQUIRED = {
            "glBlitFramebuffer", "glGenFramebuffers", "glBindFramebuffer", "glFramebufferTexture2D", "glCheckFramebufferStatus",
            "glGenVertexArrays", "glBindVertexArray", "glGenBuffers", "glBindBuffer", "glBufferData", "glReadPixels", "glTexImage2D",
            "glDrawArrays", "glCreateShader", "glLinkProgram", "glUseProgram", "glGetIntegerv", "glUnmapBuffer"
    };

    private static final String VERTEX_BODY = "in vec2 a_pos;\nvoid main() { gl_Position = vec4(a_pos, 0.0, 1.0); }\n";

    private static final String FRAGMENT_Y = ""
            + "uniform sampler2D u_tex;\n"
            + "uniform int u_height;\n"
            + "out vec4 fragColor;\n"
            + "float luma(vec3 c) { return (16.0 + 219.0 * dot(c, vec3(0.2126, 0.7152, 0.0722))) / 255.0; }\n"
            + "void main() {\n"
            + "    ivec2 p = ivec2(gl_FragCoord.xy);\n"
            + "    int sy = u_height - 1 - p.y;\n"
            + "    int sx = p.x * 4;\n"
            + "    fragColor = vec4(luma(texelFetch(u_tex, ivec2(sx, sy), 0).rgb),\n"
            + "                     luma(texelFetch(u_tex, ivec2(sx + 1, sy), 0).rgb),\n"
            + "                     luma(texelFetch(u_tex, ivec2(sx + 2, sy), 0).rgb),\n"
            + "                     luma(texelFetch(u_tex, ivec2(sx + 3, sy), 0).rgb));\n"
            + "}\n";

    private static final String FRAGMENT_UV = ""
            + "uniform sampler2D u_tex;\n"
            + "uniform int u_height;\n"
            + "out vec4 fragColor;\n"
            + "vec2 chroma(vec3 c) {\n"
            + "    float y = dot(c, vec3(0.2126, 0.7152, 0.0722));\n"
            + "    return (128.0 + 224.0 * vec2((c.b - y) / 1.8556, (c.r - y) / 1.5748)) / 255.0;\n"
            + "}\n"
            + "vec2 block(int cx, int cy) {\n"
            + "    int sx = cx * 2;\n"
            + "    int sy = u_height - 1 - cy * 2;\n"
            + "    vec3 a = texelFetch(u_tex, ivec2(sx, sy), 0).rgb;\n"
            + "    vec3 b = texelFetch(u_tex, ivec2(sx + 1, sy), 0).rgb;\n"
            + "    vec3 c = texelFetch(u_tex, ivec2(sx, sy - 1), 0).rgb;\n"
            + "    vec3 d = texelFetch(u_tex, ivec2(sx + 1, sy - 1), 0).rgb;\n"
            + "    return chroma((a + b + c + d) * 0.25);\n"
            + "}\n"
            + "void main() {\n"
            + "    ivec2 p = ivec2(gl_FragCoord.xy);\n"
            + "    int row = p.y - u_height;\n"
            + "    vec2 first = block(p.x * 2, row);\n"
            + "    vec2 second = block(p.x * 2 + 1, row);\n"
            + "    fragColor = vec4(first.x, first.y, second.x, second.y);\n"
            + "}\n";

    private final int outW;
    private final int outH;
    private final Mode requested;
    private final int ringSize;

    private Mode mode;
    private String problem = "";
    private String details = "";
    private boolean ready;

    private int midTex;
    private int midFbo;
    private int outTex;
    private int outFbo;
    private int vao;
    private int vbo;
    private int progY;
    private int progUv;
    private int[] pbo;
    private long[] fence;
    private long[] ptsUs;
    private long[] issuedAtPoll;
    private int head;
    private int size;
    private long pollCalls;
    private int pboBytes;
    private boolean useFences;
    private boolean mapRange;
    private boolean swapChroma;

    private long issued;
    private long delivered;
    private long skipped;

    private final int[] saved = new int[24];
    private final int[] tmp4 = new int[4];

    public GpuCapture(int outW, int outH, Mode requested, int ringSize) {
        if (outW % 16 != 0 || outH % 16 != 0) {
            throw new IllegalArgumentException("size must be a multiple of 16: " + outW + "x" + outH);
        }
        this.outW = outW;
        this.outH = outH;
        this.requested = requested;
        this.ringSize = ringSize;
    }

    /** Writes the two chroma values of each pair in the other order. Call before {@link #init}. */
    public void setSwapChroma(boolean swap) {
        this.swapChroma = swap;
    }

    /** Size of the video for a source of the given size and a wished height; both sides come out as multiples of 16. */
    public static int[] outputSize(int srcW, int srcH, int wantedHeight) {
        int h = Math.min(wantedHeight, Math.max(16, srcH));
        h = Math.max(16, (h + 8) / 16 * 16);
        long w = Math.round((double) srcW * h / Math.max(1, srcH));
        int ww = (int) Math.max(16, (w + 8) / 16 * 16);
        return new int[]{ww, h};
    }

    public Mode mode() {
        return mode;
    }

    public String problem() {
        return problem;
    }

    /** Short description of what was chosen, for the log. */
    public String details() {
        return details;
    }

    public boolean isReady() {
        return ready;
    }

    public int width() {
        return outW;
    }

    public int height() {
        return outH;
    }

    /** Bytes of one frame as delivered by {@link #poll}. */
    public int frameBytes() {
        return pboBytes;
    }

    /** Frames that were started but not collected yet. */
    public int inFlight() {
        return size;
    }

    public long issuedCount() {
        return issued;
    }

    public long deliveredCount() {
        return delivered;
    }

    public long skippedCount() {
        return skipped;
    }

    // ------------------------------------------------------------------------------------------------------------------------------

    private boolean checkCapabilities() {
        GLCapabilities caps;
        try {
            caps = GL.getCapabilities();
        } catch (Throwable t) {
            problem = "OpenGL is not available: " + t;
            return false;
        }
        for (String name : REQUIRED) {
            if (functionAddress(caps, name) == 0) {
                problem = "the graphics driver lacks " + name;
                return false;
            }
        }
        useFences = functionAddress(caps, "glFenceSync") != 0 && functionAddress(caps, "glClientWaitSync") != 0
                && functionAddress(caps, "glDeleteSync") != 0;
        mapRange = functionAddress(caps, "glMapBufferRange") != 0;
        if (!mapRange && functionAddress(caps, "glMapBuffer") == 0) {
            problem = "the graphics driver cannot map buffers";
            return false;
        }
        return true;
    }

    /** The driver's address of a GL function, or 1 when it cannot be told (a LWJGL without that field). */
    private static long functionAddress(GLCapabilities caps, String name) {
        try {
            Field f = GLCapabilities.class.getField(name);
            return f.getLong(caps);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1;
        }
    }

    /**
     * Creates the graphics objects. Returns false (and says why in {@link #problem}) if the capture cannot work on this device.
     * In {@link Mode#NV12} a failing shader or a failing self test is not final: the capture is then set up in {@link Mode#RGBA}.
     */
    public boolean init() {
        if (!checkCapabilities()) {
            return false;
        }
        saveState();
        try {
            pboBytes = requested == Mode.NV12 ? outW * outH * 3 / 2 : outW * outH * 4;
            mode = requested;
            if (!createTargets()) {
                return false;
            }
            createGeometry();
            if (mode == Mode.NV12) {
                String why = prepareShaders();
                if (why != null) {
                    problem = why;
                    details = "shader path unusable (" + why + "), using blit only";
                    mode = Mode.RGBA;
                    releaseShaderObjects();
                    pboBytes = outW * outH * 4;
                }
            }
            createPixelBuffers();
            ready = true;
            if (mode == requested) {
                problem = "";
            }
            details = (details.isEmpty() ? "" : details + "; ") + "mode " + mode + ", " + outW + "x" + outH
                    + (useFences ? ", fences" : ", no fences") + (mapRange ? "" : ", glMapBuffer");
            return true;
        } catch (Throwable t) {
            problem = "capture setup failed: " + t;
            release();
            return false;
        } finally {
            restoreState();
        }
    }

    private boolean createTargets() {
        midTex = GL11C.glGenTextures();
        allocateTexture(midTex, outW, outH);
        midFbo = makeFbo(midTex);
        if (midFbo == 0) {
            return false;
        }
        if (mode == Mode.NV12) {
            outTex = GL11C.glGenTextures();
            allocateTexture(outTex, outW / 4, outH * 3 / 2);
            outFbo = makeFbo(outTex);
            if (outFbo == 0) {
                problem = "";
                mode = Mode.RGBA;
                details = "NV12 target is not renderable, using blit only";
                pboBytes = outW * outH * 4;
            }
        }
        return true;
    }

    private void allocateTexture(int tex, int w, int h) {
        GL13C.glActiveTexture(GL_TEXTURE0);
        GL11C.glBindTexture(GL_TEXTURE_2D, tex);
        GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11C.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
    }

    private int makeFbo(int tex) {
        int fbo = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        GL30C.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
        int status = GL30C.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            problem = "framebuffer incomplete (0x" + Integer.toHexString(status) + ")";
            GL30C.glDeleteFramebuffers(fbo);
            return 0;
        }
        return fbo;
    }

    /** One triangle that covers the whole target; the vertex array remembers how to read it. */
    private void createGeometry() {
        int previousArrayBuffer = GL11C.glGetInteger(GL_ARRAY_BUFFER_BINDING);
        vao = GL30C.glGenVertexArrays();
        GL30C.glBindVertexArray(vao);
        vbo = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL_ARRAY_BUFFER, vbo);
        GL15C.glBufferData(GL_ARRAY_BUFFER, new float[]{-1f, -1f, 3f, -1f, -1f, 3f}, GL_STATIC_DRAW);
        GL20C.glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L);
        GL20C.glEnableVertexAttribArray(0);
        GL30C.glBindVertexArray(0);
        GL15C.glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer);
    }

    private void createPixelBuffers() {
        pbo = new int[ringSize];
        fence = new long[ringSize];
        ptsUs = new long[ringSize];
        issuedAtPoll = new long[ringSize];
        for (int i = 0; i < ringSize; i++) {
            pbo[i] = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo[i]);
            GL15C.glBufferData(GL_PIXEL_PACK_BUFFER, (long) pboBytes, GL_STREAM_READ);
        }
        GL15C.glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
    }

    // ---- shaders --------------------------------------------------------------------------------------------------------------

    /** Compiles the conversion shaders and checks their output against the CPU converter. Returns null on success, else the reason. */
    private String prepareShaders() {
        String[] desktop = {"#version 150\n", "desktop GLSL 150"};
        String[] gles = {"#version 300 es\nprecision highp float;\nprecision highp int;\n", "GLSL ES 300"};
        // Try the dialect that matches the context first: an OpenGL ES driver has no business with "#version 150".
        String[][] dialects = isEsContext() ? new String[][]{gles, desktop} : new String[][]{desktop, gles};
        StringBuilder why = new StringBuilder();
        for (String[] d : dialects) {
            problem = "";
            releaseShaderObjects();
            int vs = compile(GL_VERTEX_SHADER, d[0] + VERTEX_BODY);
            int fy = vs == 0 ? 0 : compile(GL_FRAGMENT_SHADER, d[0] + FRAGMENT_Y);
            String uvSource = swapChroma
                    ? FRAGMENT_UV.replace("vec4(first.x, first.y, second.x, second.y)", "vec4(first.y, first.x, second.y, second.x)")
                    : FRAGMENT_UV;
            int fu = fy == 0 ? 0 : compile(GL_FRAGMENT_SHADER, d[0] + uvSource);
            if (fu != 0) {
                progY = link(vs, fy);
                progUv = progY == 0 ? 0 : link(vs, fu);
            }
            if (vs != 0) {
                GL20C.glDeleteShader(vs);
            }
            if (fy != 0) {
                GL20C.glDeleteShader(fy);
            }
            if (fu != 0) {
                GL20C.glDeleteShader(fu);
            }
            if (progY != 0 && progUv != 0) {
                String test = selfTest();
                if (test == null) {
                    details = "shaders: " + d[1];
                    return null;
                }
                problem = test;
            }
            why.append(d[1]).append(": ").append(problem.isEmpty() ? "failed" : problem).append("; ");
        }
        return why.toString();
    }

    private static boolean isEsContext() {
        try {
            String v = GL11C.glGetString(GL_VERSION);
            return v != null && v.startsWith("OpenGL ES");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private int compile(int type, String source) {
        int sh = GL20C.glCreateShader(type);
        GL20C.glShaderSource(sh, source);
        GL20C.glCompileShader(sh);
        if (GL20C.glGetShaderi(sh, GL_COMPILE_STATUS) == 0) {
            problem = "compile: " + trim(GL20C.glGetShaderInfoLog(sh));
            GL20C.glDeleteShader(sh);
            return 0;
        }
        return sh;
    }

    private int link(int vs, int fs) {
        int p = GL20C.glCreateProgram();
        GL20C.glAttachShader(p, vs);
        GL20C.glAttachShader(p, fs);
        GL20C.glBindAttribLocation(p, 0, "a_pos");
        GL20C.glLinkProgram(p);
        if (GL20C.glGetProgrami(p, GL_LINK_STATUS) == 0) {
            problem = "link: " + trim(GL20C.glGetProgramInfoLog(p));
            GL20C.glDeleteProgram(p);
            return 0;
        }
        GL20C.glUseProgram(p);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(p, "u_tex"), 0);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(p, "u_height"), outH);
        return p;
    }

    private static String trim(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim().replace('\n', ' ');
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    private void releaseShaderObjects() {
        if (progY != 0) {
            GL20C.glDeleteProgram(progY);
            progY = 0;
        }
        if (progUv != 0) {
            GL20C.glDeleteProgram(progUv);
            progUv = 0;
        }
    }

    /**
     * Runs a known picture through the real shaders and compares the result with the CPU converter. A graphics driver that handles the
     * shaders differently than expected (wrong rounding, wrong row order, wrong layout) is found here, once, instead of silently
     * producing a broken video.
     */
    private String selfTest() {
        int w = outW;
        int h = outH;
        byte[] rgba = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int o = (y * w + x) * 4;
                rgba[o] = (byte) ((x * 7 + y * 3) & 0xFF);
                rgba[o + 1] = (byte) ((x * 5 + y * 11 + 40) & 0xFF);
                rgba[o + 2] = (byte) ((x * 2 + y * 13 + 90) & 0xFF);
                rgba[o + 3] = (byte) 0xFF;
            }
        }
        ByteBuffer src = ByteBuffer.allocateDirect(rgba.length).order(ByteOrder.nativeOrder());
        src.put(rgba).flip();
        GL13C.glActiveTexture(GL_TEXTURE0);
        GL11C.glBindTexture(GL_TEXTURE_2D, midTex);
        GL11C.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, src);

        GL15C.glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        drawPasses();
        int bytes = w * h * 3 / 2;
        ByteBuffer dst = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        GL30C.glBindFramebuffer(GL_FRAMEBUFFER, outFbo);
        GL11C.glReadPixels(0, 0, w / 4, h * 3 / 2, GL_RGBA, GL_UNSIGNED_BYTE, dst);
        byte[] got = new byte[bytes];
        dst.get(got);
        byte[] ref = new byte[bytes];
        Nv12.fromRgbaBottomUp(rgba, w, h, ref);
        if (swapChroma) {
            Nv12.swapChroma(ref, w, h);
        }
        int off = 0;
        int worst = 0;
        int firstBad = -1;
        for (int i = 0; i < bytes; i++) {
            int d = Math.abs((got[i] & 0xFF) - (ref[i] & 0xFF));
            worst = Math.max(worst, d);
            if (d > 3) {
                off++;
                if (firstBad < 0) {
                    firstBad = i;
                }
            }
        }
        if (off > bytes / 200 || worst > 12) {
            return "self test failed: " + off + " of " + bytes + " bytes off by more than 3 (worst " + worst + ", first at byte "
                    + firstBad + ")";
        }
        return null;
    }

    /** The two conversion passes, from {@code midTex} into {@code outFbo}. State must be saved by the caller. */
    private void drawPasses() {
        GL30C.glBindFramebuffer(GL_FRAMEBUFFER, outFbo);
        disableFixedFunction();
        GL30C.glBindVertexArray(vao);
        GL13C.glActiveTexture(GL_TEXTURE0);
        GL11C.glBindTexture(GL_TEXTURE_2D, midTex);
        GL20C.glUseProgram(progY);
        GL11C.glViewport(0, 0, outW / 4, outH);
        GL11C.glDrawArrays(GL_TRIANGLES, 0, 3);
        GL20C.glUseProgram(progUv);
        GL11C.glViewport(0, outH, outW / 4, outH / 2);
        GL11C.glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    // ---- per frame ------------------------------------------------------------------------------------------------------------

    /**
     * Starts the capture of one frame. The call returns as soon as the commands are queued.
     *
     * @param srcFbo the framebuffer that holds the finished picture (the game's main render target)
     * @return false if the picture was skipped because every buffer of the ring is still in use
     */
    public boolean issue(int srcFbo, int srcW, int srcH, long recordingTimeUs) {
        if (!ready || size == ringSize || srcW <= 0 || srcH <= 0) {
            skipped++;
            return false;
        }
        saveState();
        try {
            GL30C.glBindFramebuffer(GL_READ_FRAMEBUFFER, srcFbo);
            GL30C.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, midFbo);
            disableFixedFunction();
            GL30C.glBlitFramebuffer(0, 0, srcW, srcH, 0, 0, outW, outH, GL_COLOR_BUFFER_BIT, GL_LINEAR);
            int readW;
            int readH;
            if (mode == Mode.NV12) {
                drawPasses();
                readW = outW / 4;
                readH = outH * 3 / 2;
            } else {
                GL30C.glBindFramebuffer(GL_FRAMEBUFFER, midFbo);
                readW = outW;
                readH = outH;
            }
            int slot = (head + size) % ringSize;
            GL15C.glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo[slot]);
            GL11C.glReadPixels(0, 0, readW, readH, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            if (useFences) {
                fence[slot] = GL32C.glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
                GL11C.glFlush();
            }
            ptsUs[slot] = recordingTimeUs;
            issuedAtPoll[slot] = pollCalls;
            size++;
            issued++;
            return true;
        } finally {
            restoreState();
        }
    }

    /**
     * Collects the frames whose copy is finished and hands them to the encoder thread through the pool. Call it every frame while
     * recording; it is cheap when nothing is ready.
     *
     * @return the number of frames delivered
     */
    public int poll(FramePool pool) {
        pollCalls++;
        int count = 0;
        while (size > 0) {
            int slot = head;
            if (useFences) {
                int r = GL32C.glClientWaitSync(fence[slot], 0, 0L);
                if (r == GL_TIMEOUT_EXPIRED) {
                    break;
                }
                if (r == GL_WAIT_FAILED) {
                    useFences = false;                           // fences do not work here; fall back to waiting a few frames
                    problem = "glClientWaitSync failed; waiting three frames instead";
                    GL32C.glDeleteSync(fence[slot]);
                    fence[slot] = 0;
                    issuedAtPoll[slot] = pollCalls;
                    break;
                }
                GL32C.glDeleteSync(fence[slot]);
                fence[slot] = 0;
            } else if (pollCalls - issuedAtPoll[slot] < 3) {
                break;
            }
            Frame f = pool.acquire();
            if (f != null && f.data.length >= pboBytes) {
                if (copyOut(slot, f)) {
                    f.length = pboBytes;
                    f.ptsUs = ptsUs[slot];
                    pool.submit(f);
                    delivered++;
                    count++;
                } else {
                    pool.release(f);
                }
            } else if (f != null) {
                pool.release(f);
            }
            head = (head + 1) % ringSize;
            size--;
        }
        return count;
    }

    private boolean copyOut(int slot, Frame f) {
        int previous = GL11C.glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        GL15C.glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo[slot]);
        try {
            ByteBuffer mapped = mapRange
                    ? GL30C.glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0L, (long) pboBytes, GL_MAP_READ_BIT)
                    : GL15C.glMapBuffer(GL_PIXEL_PACK_BUFFER, GL_READ_ONLY);
            if (mapped == null || mapped.capacity() < pboBytes) {
                problem = "could not map the pixel buffer";
                return false;
            }
            mapped.get(f.data, 0, pboBytes);
            return GL15C.glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
        } finally {
            GL15C.glBindBuffer(GL_PIXEL_PACK_BUFFER, previous);
        }
    }

    // ---- state ----------------------------------------------------------------------------------------------------------------

    private void saveState() {
        saved[0] = GL11C.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        saved[1] = GL11C.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        GL11C.glGetIntegerv(GL_VIEWPORT, tmp4);
        saved[2] = tmp4[0];
        saved[3] = tmp4[1];
        saved[4] = tmp4[2];
        saved[5] = tmp4[3];
        saved[6] = GL11C.glGetInteger(GL_CURRENT_PROGRAM);
        saved[7] = GL11C.glGetInteger(GL_VERTEX_ARRAY_BINDING);
        saved[8] = GL11C.glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        saved[9] = GL11C.glGetInteger(GL_ACTIVE_TEXTURE);
        if (saved[9] != GL_TEXTURE0) {
            GL13C.glActiveTexture(GL_TEXTURE0);
        }
        saved[10] = GL11C.glGetInteger(GL_TEXTURE_BINDING_2D);
        saved[11] = GL11C.glIsEnabled(GL_BLEND) ? 1 : 0;
        saved[12] = GL11C.glIsEnabled(GL_DEPTH_TEST) ? 1 : 0;
        saved[13] = GL11C.glIsEnabled(GL_CULL_FACE) ? 1 : 0;
        saved[14] = GL11C.glIsEnabled(GL_SCISSOR_TEST) ? 1 : 0;
        saved[15] = GL11C.glIsEnabled(GL_DITHER) ? 1 : 0;
        GL11C.glGetIntegerv(GL_COLOR_WRITEMASK, tmp4);
        saved[16] = tmp4[0];
        saved[17] = tmp4[1];
        saved[18] = tmp4[2];
        saved[19] = tmp4[3];
    }

    private void disableFixedFunction() {
        if (saved[11] != 0) {
            GL11C.glDisable(GL_BLEND);
        }
        if (saved[12] != 0) {
            GL11C.glDisable(GL_DEPTH_TEST);
        }
        if (saved[13] != 0) {
            GL11C.glDisable(GL_CULL_FACE);
        }
        if (saved[14] != 0) {
            GL11C.glDisable(GL_SCISSOR_TEST);
        }
        if (saved[15] != 0) {
            GL11C.glDisable(GL_DITHER);                          // dithering would add noise to the 8-bit values
        }
        GL11C.glColorMask(true, true, true, true);
    }

    private void restoreState() {
        GL30C.glBindFramebuffer(GL_READ_FRAMEBUFFER, saved[0]);
        GL30C.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, saved[1]);
        GL11C.glViewport(saved[2], saved[3], saved[4], saved[5]);
        GL20C.glUseProgram(saved[6]);
        GL30C.glBindVertexArray(saved[7]);
        GL15C.glBindBuffer(GL_PIXEL_PACK_BUFFER, saved[8]);
        GL13C.glActiveTexture(GL_TEXTURE0);
        GL11C.glBindTexture(GL_TEXTURE_2D, saved[10]);
        GL13C.glActiveTexture(saved[9]);
        restoreFlag(GL_BLEND, saved[11]);
        restoreFlag(GL_DEPTH_TEST, saved[12]);
        restoreFlag(GL_CULL_FACE, saved[13]);
        restoreFlag(GL_SCISSOR_TEST, saved[14]);
        restoreFlag(GL_DITHER, saved[15]);
        GL11C.glColorMask(saved[16] != 0, saved[17] != 0, saved[18] != 0, saved[19] != 0);
    }

    private static void restoreFlag(int cap, int was) {
        if (was != 0) {
            GL11C.glEnable(cap);
        }
    }

    // ---- cleanup --------------------------------------------------------------------------------------------------------------

    private void release() {
        releaseShaderObjects();
        if (pbo != null) {
            for (int i = 0; i < pbo.length; i++) {
                if (useFences && fence != null && fence[i] != 0) {
                    GL32C.glDeleteSync(fence[i]);
                    fence[i] = 0;
                }
                if (pbo[i] != 0) {
                    GL15C.glDeleteBuffers(pbo[i]);
                    pbo[i] = 0;
                }
            }
        }
        if (vbo != 0) {
            GL15C.glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vao != 0) {
            GL30C.glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (midFbo != 0) {
            GL30C.glDeleteFramebuffers(midFbo);
            midFbo = 0;
        }
        if (outFbo != 0) {
            GL30C.glDeleteFramebuffers(outFbo);
            outFbo = 0;
        }
        if (midTex != 0) {
            GL11C.glDeleteTextures(midTex);
            midTex = 0;
        }
        if (outTex != 0) {
            GL11C.glDeleteTextures(outTex);
            outTex = 0;
        }
        ready = false;
        size = 0;
        head = 0;
    }

    /** Frees the graphics objects. Must run on the render thread. */
    @Override
    public void close() {
        if (midTex == 0 && pbo == null) {
            return;
        }
        try {
            saveState();
            try {
                release();
            } finally {
                restoreState();
            }
        } catch (RuntimeException e) {
            // the context may already be gone while the game shuts down
        }
        pbo = null;
    }
}
