package dev.urinth.recordable.mux;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.urinth.recordable.TestFiles;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the structure of the files the two writers produce (real players were used to check the content while developing). */
final class MuxTest {
    private static final byte[] SPS = {0x67, 0x64, 0x00, 0x1F, (byte) 0xAC, (byte) 0xD9, 0x40, 0x50, 0x05, (byte) 0xBB, 0x01, 0x10};
    private static final byte[] PPS = {0x68, (byte) 0xEB, (byte) 0xE3, (byte) 0xCB, 0x22, (byte) 0xC0};

    // ---- H.264 helpers ----------------------------------------------------------------------------------------------------------

    private static byte[] nal(int header, int seed, int payload) {
        byte[] n = new byte[1 + payload];
        n[0] = (byte) header;
        for (int i = 1; i < n.length; i++) {
            n[i] = (byte) (0x10 + (seed * 31 + i * 7) % 0xE0);   // never 0, so no accidental start code
        }
        return n;
    }

    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            o.write(p, 0, p.length);
        }
        return o.toByteArray();
    }

    private static final byte[] SC4 = {0, 0, 0, 1};
    private static final byte[] SC3 = {0, 0, 1};

    @Test
    void nalUnitsAreFoundWithThreeAndFourByteStartCodes() {
        byte[] a = nal(0x65, 1, 20);
        byte[] b = nal(0x41, 2, 30);
        byte[] stream = join(SC4, SPS, SC4, PPS, SC3, a, SC4, b, new byte[]{0, 0});   // trailing zeros belong to no unit
        int[] r = H264.nalRanges(stream, 0, stream.length);
        assertEquals(8, r.length);
        assertEquals(SPS.length, r[1] - r[0]);
        assertEquals(PPS.length, r[3] - r[2]);
        assertEquals(a.length, r[5] - r[4]);
        assertEquals(b.length, r[7] - r[6]);
        assertEquals(7, H264.nalType(stream[r[0]]));
        assertEquals(8, H264.nalType(stream[r[2]]));
        assertEquals(5, H264.nalType(stream[r[4]]));
        assertEquals(1, H264.nalType(stream[r[6]]));
    }

    @Test
    void avccConversionKeepsSlicesCollectsParameterSetsAndDropsDelimiters() {
        byte[] idr = nal(0x65, 3, 25);
        byte[] aud = {0x09, (byte) 0xF0};
        byte[] stream = join(SC4, aud, SC4, SPS, SC4, PPS, SC4, idr);
        H264.ParamSets ps = new H264.ParamSets();
        H264.Sample s = H264.toAvcc(stream, 0, stream.length, ps);
        assertTrue(s.keyframe);
        assertTrue(ps.complete());
        assertArrayEquals(SPS, ps.sps);
        assertArrayEquals(PPS, ps.pps);
        assertEquals(4 + idr.length, s.data.length);
        assertEquals(idr.length, ((s.data[0] & 0xFF) << 24) | ((s.data[1] & 0xFF) << 16) | ((s.data[2] & 0xFF) << 8) | (s.data[3] & 0xFF));

        byte[] onlyParams = join(SC4, SPS, SC4, PPS);
        assertEquals(0, H264.toAvcc(onlyParams, 0, onlyParams.length, new H264.ParamSets()).data.length);
    }

    @Test
    void theDecoderConfigurationHasTheParameterSetsAndTheHighProfileExtension() {
        H264.ParamSets ps = new H264.ParamSets();
        ps.sps = SPS;
        ps.pps = PPS;
        byte[] c = H264.avcC(ps);
        assertEquals(1, c[0]);
        assertEquals(SPS[1], c[1]);
        assertEquals(SPS[3], c[3]);
        assertEquals((byte) 0xFF, c[4]);                       // 4-byte lengths
        assertEquals((byte) 0xE1, c[5]);                       // one SPS
        assertEquals(6 + 2 + SPS.length + 1 + 2 + PPS.length + 4, c.length);
        ps.sps = SPS.clone();
        ps.sps[1] = 66;                                         // baseline has no extension
        assertEquals(6 + 2 + SPS.length + 1 + 2 + PPS.length, H264.avcC(ps).length);
    }

    // ---- MP4 --------------------------------------------------------------------------------------------------------------------

    private static final class Box {
        final String type;
        final int start;
        final int size;
        final int body;

        Box(String type, int start, int size, int body) {
            this.type = type;
            this.start = start;
            this.size = size;
            this.body = body;
        }
    }

    private static long u32(byte[] d, int p) {
        return ((d[p] & 0xFFL) << 24) | ((d[p + 1] & 0xFFL) << 16) | ((d[p + 2] & 0xFFL) << 8) | (d[p + 3] & 0xFFL);
    }

    private static long u32le(byte[] d, int p) {
        return ((d[p + 3] & 0xFFL) << 24) | ((d[p + 2] & 0xFFL) << 16) | ((d[p + 1] & 0xFFL) << 8) | (d[p] & 0xFFL);
    }

    private static List<Box> boxes(byte[] d, int from, int to) {
        List<Box> out = new ArrayList<>();
        int p = from;
        while (p + 8 <= to) {
            long size = u32(d, p);
            String type = new String(d, p + 4, 4, StandardCharsets.ISO_8859_1);
            if (size < 8 || p + size > to) {
                break;
            }
            out.add(new Box(type, p, (int) size, p + 8));
            p += (int) size;
        }
        return out;
    }

    private static Box child(byte[] d, Box parent, String type) {
        for (Box b : boxes(d, parent.body, parent.start + parent.size)) {
            if (b.type.equals(type)) {
                return b;
            }
        }
        throw new AssertionError("no " + type + " inside " + parent.type);
    }

    private static boolean contains(byte[] d, String text) {
        byte[] t = text.getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i + t.length <= d.length; i++) {
            for (int k = 0; k < t.length; k++) {
                if (d[i + k] != t[k]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    @Test
    void fragmentedMp4HasOneFragmentPerKeyframeAndAPatchedDuration() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Path file = dir.resolve("v.mp4");
            int payload = 40;
            try (Fmp4Writer w = new Fmp4Writer(file, 640, 360, 30)) {
                assertFalse(w.write(join(SC4, nal(0x41, 0, 10)), 0, 15, 0), "a stream cannot start with a plain frame");
                byte[] params = join(SC4, SPS, SC4, PPS);
                assertFalse(w.write(params, 0, params.length, 0), "parameter sets alone are no frame");
                for (int i = 0; i < 90; i++) {
                    byte[] data = i % 30 == 0
                            ? join(SC4, SPS, SC4, PPS, SC3, nal(0x65, i, payload))
                            : join(SC3, nal(0x41, i, payload));
                    assertTrue(w.write(data, 0, data.length, 1_000_000L + i * 33_333L));
                }
                assertEquals(90, w.sampleCount());
            }
            byte[] d = Files.readAllBytes(file);
            List<Box> top = boxes(d, 0, d.length);
            StringBuilder types = new StringBuilder();
            for (Box b : top) {
                types.append(b.type).append(' ');
            }
            assertEquals("ftyp moov moof mdat moof mdat moof mdat ", types.toString());
            int end = top.get(top.size() - 1).start + top.get(top.size() - 1).size;
            assertEquals(d.length, end, "the boxes cover the whole file");

            Box moov = top.get(1);
            Box mvhd = child(d, moov, "mvhd");
            assertEquals(1000, u32(d, mvhd.body + 12), "movie timescale");
            long ms = u32(d, mvhd.body + 16);
            assertTrue(Math.abs(ms - 3000) <= 100, "duration " + ms + " ms");
            Box stsd = child(d, child(d, child(d, child(d, child(d, moov, "trak"), "mdia"), "minf"), "stbl"), "stsd");
            byte[] header = java.util.Arrays.copyOfRange(d, stsd.start, stsd.start + stsd.size);
            assertTrue(contains(header, "avc1") && contains(header, "avcC") && contains(header, "colr") && contains(header, "nclx"));

            for (int f = 0; f < 3; f++) {
                Box moof = top.get(2 + 2 * f);
                Box mdat = top.get(3 + 2 * f);
                Box trun = child(d, child(d, moof, "traf"), "trun");
                long count = u32(d, trun.body + 4);
                assertEquals(30, count);
                long dataOffset = u32(d, trun.body + 8);
                assertEquals(mdat.body - moof.start, dataOffset, "samples start right after the mdat header");
                long total = 0;
                for (int i = 0; i < count; i++) {
                    int e = trun.body + 12 + i * 12;
                    total += u32(d, e + 4);
                    assertEquals(4 + 1 + payload, u32(d, e + 4), "sample size");
                    assertEquals(i == 0 ? 0x02000000L : 0x01010000L, u32(d, e + 8), "sample flags of sample " + i);
                }
                assertEquals(mdat.size - 8, total);
            }
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    @Test
    void aFileCutShortStillHoldsEveryFinishedFragment() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Path file = dir.resolve("cut.mp4");
            Fmp4Writer w = new Fmp4Writer(file, 320, 240, 30);
            for (int i = 0; i < 70; i++) {
                byte[] data = i % 30 == 0 ? join(SC4, SPS, SC4, PPS, SC3, nal(0x65, i, 30)) : join(SC3, nal(0x41, i, 30));
                w.write(data, 0, data.length, i * 33_333L);
            }
            long written = w.bytesWritten();                    // two fragments are out, the third is still waiting in memory
            byte[] d = Files.readAllBytes(file.resolveSibling("cut.mp4"));
            byte[] cut = java.util.Arrays.copyOf(d, (int) Math.min(d.length, written - 10));   // the file ends in the middle of a sample
            List<Box> top = boxes(cut, 0, cut.length);
            assertEquals("ftyp", top.get(0).type);
            assertEquals("moov", top.get(1).type);
            assertEquals("moof", top.get(2).type);
            assertEquals("mdat", top.get(3).type, "the first fragment is complete");
            w.close();
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    // ---- AVI --------------------------------------------------------------------------------------------------------------------

    @Test
    void aviHasAnIndexThatPointsAtEveryFrameAndHoldsThePictureDuringGaps() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Path file = dir.resolve("v.avi");
            byte[][] pictures = new byte[4][];
            for (int i = 0; i < pictures.length; i++) {
                pictures[i] = new byte[201 + i];                // odd and even sizes, to test the padding
                java.util.Arrays.fill(pictures[i], (byte) (i + 1));
            }
            try (AviWriter w = new AviWriter(file, 160, 90, 10)) {
                assertTrue(w.write(pictures[0], 0, pictures[0].length, 5_000_000L));
                assertTrue(w.write(pictures[1], 0, pictures[1].length, 5_100_000L));
                assertTrue(w.write(pictures[2], 0, pictures[2].length, 5_200_000L));
                assertTrue(w.write(pictures[3], 0, pictures[3].length, 5_400_000L), "slot 4, after a gap of one slot");
                assertFalse(w.write(pictures[0], 0, pictures[0].length, 5_410_000L), "same slot as the previous picture");
                assertEquals(5, w.frameCount(), "the gap was filled by repeating a picture");
                assertEquals(500, w.durationMs());
            }
            byte[] d = Files.readAllBytes(file);
            assertEquals("RIFF", new String(d, 0, 4, StandardCharsets.ISO_8859_1));
            assertEquals("AVI ", new String(d, 8, 4, StandardCharsets.ISO_8859_1));
            assertEquals(d.length - 8, u32le(d, 4), "RIFF size");
            assertEquals(5, u32le(d, 12 + 12 + 8 + 16), "avih total frames");
            int movi = -1;
            for (int i = 0; i < 400; i++) {
                if (d[i] == 'm' && d[i + 1] == 'o' && d[i + 2] == 'v' && d[i + 3] == 'i') {
                    movi = i;
                    break;
                }
            }
            assertTrue(movi > 0, "movi list found");
            int idx = -1;
            for (int i = d.length - 8; i > movi; i--) {
                if (d[i] == 'i' && d[i + 1] == 'd' && d[i + 2] == 'x' && d[i + 3] == '1') {
                    idx = i;
                    break;
                }
            }
            assertTrue(idx > 0, "index found");
            assertEquals(5 * 16, u32le(d, idx + 4));
            int[] expectedSize = {201, 202, 203, 203, 204};     // the third picture is held for the empty slot
            for (int i = 0; i < 5; i++) {
                int e = idx + 8 + i * 16;
                assertEquals("00dc", new String(d, e, 4, StandardCharsets.ISO_8859_1));
                int chunk = movi + (int) u32le(d, e + 8);
                assertEquals("00dc", new String(d, chunk, 4, StandardCharsets.ISO_8859_1), "index entry " + i + " points at a chunk");
                assertEquals(expectedSize[i], u32le(d, chunk + 4), "chunk size " + i);
                assertEquals(expectedSize[i], u32le(d, e + 12), "index size " + i);
            }
        } finally {
            TestFiles.deleteTree(dir);
        }
    }
}
