package dev.urinth.recordable.enc;

/**
 * Colour conversion on the CPU: the backup for the graphics-chip conversion, and the reference the on-device self test compares with.
 * BT.709, limited range ("TV range"), chroma averaged over 2x2 pixels.
 */
public final class Nv12 {
    private static final int SHIFT = 16;
    private static final int ROUND = 1 << (SHIFT - 1);
    private static final double KY = 219.0 / 255.0;
    private static final double KC = 224.0 / 255.0;
    private static final int YR = fix(KY * 0.2126);
    private static final int YG = fix(KY * 0.7152);
    private static final int YB = fix(KY * 0.0722);
    private static final int UR = fix(-KC * 0.2126 / 1.8556);
    private static final int UG = fix(-KC * 0.7152 / 1.8556);
    private static final int UB = fix(KC * 0.9278 / 1.8556);
    private static final int VR = fix(KC * 0.7874 / 1.5748);
    private static final int VG = fix(-KC * 0.7152 / 1.5748);
    private static final int VB = fix(-KC * 0.0722 / 1.5748);

    private Nv12() {
    }

    private static int fix(double v) {
        return (int) Math.round(v * (1 << SHIFT));
    }

    public static int luma(int r, int g, int b) {
        return 16 + ((YR * r + YG * g + YB * b + ROUND) >> SHIFT);
    }

    public static int cb(int r, int g, int b) {
        return 128 + ((UR * r + UG * g + UB * b + ROUND) >> SHIFT);
    }

    public static int cr(int r, int g, int b) {
        return 128 + ((VR * r + VG * g + VB * b + ROUND) >> SHIFT);
    }

    /**
     * Converts a picture as OpenGL delivers it (RGBA, first row is the bottom of the picture) to NV12 with the top row first.
     *
     * @param out must hold at least w*h*3/2 bytes
     */
    public static void fromRgbaBottomUp(byte[] rgba, int w, int h, byte[] out) {
        int uv = w * h;
        for (int y = 0; y < h; y += 2) {
            int top = (h - 1 - y) * w * 4;          // picture row y
            int bot = (h - 2 - y) * w * 4;          // picture row y + 1
            int yo0 = y * w;
            int yo1 = yo0 + w;
            int uo = uv + (y >> 1) * w;
            for (int x = 0; x < w; x += 2) {
                int p00 = top + x * 4;
                int p01 = p00 + 4;
                int p10 = bot + x * 4;
                int p11 = p10 + 4;
                int r00 = rgba[p00] & 0xFF, g00 = rgba[p00 + 1] & 0xFF, b00 = rgba[p00 + 2] & 0xFF;
                int r01 = rgba[p01] & 0xFF, g01 = rgba[p01 + 1] & 0xFF, b01 = rgba[p01 + 2] & 0xFF;
                int r10 = rgba[p10] & 0xFF, g10 = rgba[p10 + 1] & 0xFF, b10 = rgba[p10 + 2] & 0xFF;
                int r11 = rgba[p11] & 0xFF, g11 = rgba[p11 + 1] & 0xFF, b11 = rgba[p11 + 2] & 0xFF;
                out[yo0 + x] = (byte) luma(r00, g00, b00);
                out[yo0 + x + 1] = (byte) luma(r01, g01, b01);
                out[yo1 + x] = (byte) luma(r10, g10, b10);
                out[yo1 + x + 1] = (byte) luma(r11, g11, b11);
                int r = (r00 + r01 + r10 + r11 + 2) >> 2;
                int g = (g00 + g01 + g10 + g11 + 2) >> 2;
                int b = (b00 + b01 + b10 + b11 + 2) >> 2;
                out[uo + x] = (byte) cb(r, g, b);
                out[uo + x + 1] = (byte) cr(r, g, b);
            }
        }
    }

    /** Swaps the two colour values of every chroma pair of an NV12 picture (U,V to V,U). The cure for encoders that read NV12 as NV21. */
    public static void swapChroma(byte[] nv12, int w, int h) {
        int end = w * h * 3 / 2;
        for (int i = w * h; i + 1 < end; i += 2) {
            byte t = nv12[i];
            nv12[i] = nv12[i + 1];
            nv12[i + 1] = t;
        }
    }
}
