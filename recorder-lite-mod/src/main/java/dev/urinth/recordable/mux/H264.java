package dev.urinth.recordable.mux;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Small helpers for H.264 byte streams. A hardware encoder hands out "Annex B" data (NAL units separated by 00 00 01 start codes); an MP4
 * file wants "AVCC" data (every NAL unit preceded by its 4-byte length) and keeps the parameter sets (SPS and PPS) in the file header.
 */
public final class H264 {
    public static final int NAL_SLICE = 1;
    public static final int NAL_IDR = 5;
    public static final int NAL_SEI = 6;
    public static final int NAL_SPS = 7;
    public static final int NAL_PPS = 8;
    public static final int NAL_AUD = 9;

    private H264() {
    }

    /** The parameter sets found in a stream. Each one includes its NAL header byte and no start code. */
    public static final class ParamSets {
        public byte[] sps;
        public byte[] pps;

        public boolean complete() {
            return sps != null && pps != null;
        }
    }

    public static int nalType(byte header) {
        return header & 0x1F;
    }

    /**
     * Finds the NAL units of an Annex B stream. Returns pairs of (start, end) positions, end exclusive, of the NAL bytes without the start
     * code and without the zero bytes that belong to the next start code.
     */
    public static int[] nalRanges(byte[] d, int off, int len) {
        int end = off + len;
        int[] out = new int[16];
        int n = 0;
        int i = off;
        int start = -1;
        while (i + 2 < end) {
            if (d[i] == 0 && d[i + 1] == 0 && d[i + 2] == 1) {
                if (start >= 0) {
                    int e = i;
                    while (e > start && d[e - 1] == 0) {
                        e--;                                   // the 4th byte of a 00 00 00 01 start code, or trailing zeros
                    }
                    if (n + 2 > out.length) {
                        out = Arrays.copyOf(out, out.length * 2);
                    }
                    out[n++] = start;
                    out[n++] = e;
                }
                start = i + 3;
                i += 3;
            } else if (d[i + 2] > 1) {
                i += 3;                                        // none of the three bytes can begin a start code
            } else {
                i++;
            }
        }
        if (start >= 0 && start < end) {
            int e = end;
            while (e > start && d[e - 1] == 0) {
                e--;
            }
            if (e > start) {
                if (n + 2 > out.length) {
                    out = Arrays.copyOf(out, out.length * 2);
                }
                out[n++] = start;
                out[n++] = e;
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** Result of {@link #toAvcc}. */
    public static final class Sample {
        public final byte[] data;
        public final boolean keyframe;

        Sample(byte[] data, boolean keyframe) {
            this.data = data;
            this.keyframe = keyframe;
        }
    }

    /**
     * Converts one Annex B access unit to AVCC. SPS and PPS are copied into {@code ps} (first ones seen win) and left out of the sample,
     * access unit delimiters are dropped. The result is empty when the buffer only held parameter sets.
     */
    public static Sample toAvcc(byte[] d, int off, int len, ParamSets ps) {
        int[] r = nalRanges(d, off, len);
        ByteArrayOutputStream out = new ByteArrayOutputStream(len + 16);
        boolean key = false;
        for (int k = 0; k < r.length; k += 2) {
            int s = r[k];
            int e = r[k + 1];
            int type = nalType(d[s]);
            if (type == NAL_SPS) {
                if (ps.sps == null) {
                    ps.sps = Arrays.copyOfRange(d, s, e);
                }
                continue;
            }
            if (type == NAL_PPS) {
                if (ps.pps == null) {
                    ps.pps = Arrays.copyOfRange(d, s, e);
                }
                continue;
            }
            if (type == NAL_AUD) {
                continue;
            }
            if (type == NAL_IDR) {
                key = true;
            }
            int n = e - s;
            out.write(n >>> 24);
            out.write(n >>> 16);
            out.write(n >>> 8);
            out.write(n);
            out.write(d, s, n);
        }
        return new Sample(out.toByteArray(), key);
    }

    /** The decoder configuration record ("avcC" box content) for 4:2:0 8-bit video with 4-byte NAL lengths. */
    public static byte[] avcC(ParamSets ps) {
        byte[] sps = ps.sps;
        byte[] pps = ps.pps;
        int profile = sps[1] & 0xFF;
        boolean high = profile == 100 || profile == 110 || profile == 122 || profile == 144;
        ByteArrayOutputStream o = new ByteArrayOutputStream(sps.length + pps.length + 16);
        o.write(1);                                            // configurationVersion
        o.write(sps[1]);                                       // profile
        o.write(sps[2]);                                       // profile compatibility
        o.write(sps[3]);                                       // level
        o.write(0xFC | 3);                                     // 4-byte NAL lengths
        o.write(0xE0 | 1);                                     // one SPS
        o.write(sps.length >>> 8);
        o.write(sps.length);
        o.write(sps, 0, sps.length);
        o.write(1);                                            // one PPS
        o.write(pps.length >>> 8);
        o.write(pps.length);
        o.write(pps, 0, pps.length);
        if (high) {
            o.write(0xFC | 1);                                 // chroma_format_idc = 4:2:0
            o.write(0xF8);                                     // luma bit depth 8
            o.write(0xF8);                                     // chroma bit depth 8
            o.write(0);                                        // no SPS extensions
        }
        return o.toByteArray();
    }
}
