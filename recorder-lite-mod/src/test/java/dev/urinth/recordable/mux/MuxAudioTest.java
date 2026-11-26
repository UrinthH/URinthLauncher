package dev.urinth.recordable.mux;

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

/** Checks the sound track of the fragmented MP4 (the structure; real decoders were used to check the content while developing). */
final class MuxAudioTest {
    private static final byte[] SPS = {0x67, 0x64, 0x00, 0x1F, (byte) 0xAC, (byte) 0xD9, 0x40, 0x50, 0x05, (byte) 0xBB, 0x01, 0x10};
    private static final byte[] PPS = {0x68, (byte) 0xEB, (byte) 0xE3, (byte) 0xCB, 0x22, (byte) 0xC0};
    private static final byte[] SC4 = {0, 0, 0, 1};
    private static final byte[] SC3 = {0, 0, 1};
    private static final byte[] ASC = {0x11, (byte) 0x90};
    private static final long BASE = 1_000_000;

    private static byte[] nal(int header, int seed, int payload) {
        byte[] n = new byte[1 + payload];
        n[0] = (byte) header;
        for (int i = 1; i < n.length; i++) {
            n[i] = (byte) (0x10 + (seed * 31 + i * 7) % 0xE0);
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

    /** A fake AAC frame: its first byte says which one it is, its length varies. */
    private static byte[] aac(int k) {
        byte[] f = new byte[200 + (k % 7) * 13];
        java.util.Arrays.fill(f, (byte) (k % 250 + 1));
        return f;
    }

    private static byte[] video(int i) {
        return i % 30 == 0 ? join(SC4, SPS, SC4, PPS, SC3, nal(0x65, i, 40)) : join(SC3, nal(0x41, i, 40));
    }

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

    private static long u64(byte[] d, int p) {
        return (u32(d, p) << 32) | u32(d, p + 4);
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

    private static List<Box> children(byte[] d, Box parent, String type) {
        List<Box> out = new ArrayList<>();
        for (Box b : boxes(d, parent.body, parent.start + parent.size)) {
            if (b.type.equals(type)) {
                out.add(b);
            }
        }
        return out;
    }

    private static boolean contains(byte[] d, int from, int to, byte[] t) {
        outer:
        for (int i = from; i + t.length <= to; i++) {
            for (int k = 0; k < t.length; k++) {
                if (d[i + k] != t[k]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean contains(byte[] d, int from, int to, String text) {
        return contains(d, from, to, text.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** What one traf says. */
    private static final class Run {
        long trackId;
        long decodeTime;
        long count;
        long dataOffset;
        long[] sizes;
    }

    private static Run run(byte[] d, Box traf) {
        Run r = new Run();
        Box tfhd = child(d, traf, "tfhd");
        r.trackId = u32(d, tfhd.body + 4);
        Box tfdt = child(d, traf, "tfdt");
        r.decodeTime = u64(d, tfdt.body + 4);
        Box trun = child(d, traf, "trun");
        long flags = u32(d, trun.body) & 0xFFFFFF;
        r.count = u32(d, trun.body + 4);
        r.dataOffset = u32(d, trun.body + 8);
        int per = ((flags & 0x100) != 0 ? 4 : 0) + ((flags & 0x200) != 0 ? 4 : 0) + ((flags & 0x400) != 0 ? 4 : 0);
        r.sizes = new long[(int) r.count];
        int p = trun.body + 12;
        for (int i = 0; i < r.count; i++) {
            int q = p + i * per;
            if ((flags & 0x100) != 0) {
                q += 4;
            }
            r.sizes[i] = u32(d, q);
        }
        return r;
    }

    /** Writes three seconds of video (a keyframe each second) with sound; the sound starts a little before the first picture. */
    private static long writeRecording(Path file, boolean withConfig) throws Exception {
        long accepted = 0;
        try (Fmp4Writer w = new Fmp4Writer(file, 640, 360, 30)) {
            w.enableAudio(48000, 2);
            if (withConfig) {
                w.setAudioConfig(ASC);
            }
            int k = 0;
            long audioStart = BASE - 100_000;
            for (; k < 10; k++) {                               // sound that arrives before the first picture
                w.writeAudio(aac(k), 0, aac(k).length, audioStart + k * 21_333L);
            }
            for (int i = 0; i < 90; i++) {
                long pts = BASE + i * 33_333L;
                byte[] v = video(i);
                assertTrue(w.write(v, 0, v.length, pts));
                while (audioStart + k * 21_333L <= pts + 50_000 && audioStart + k * 21_333L < BASE + 3_000_000) {
                    w.writeAudio(aac(k), 0, aac(k).length, audioStart + k * 21_333L);
                    k++;
                }
            }
            while (audioStart + k * 21_333L < BASE + 3_000_000) {
                w.writeAudio(aac(k), 0, aac(k).length, audioStart + k * 21_333L);
                k++;
            }
            accepted = w.audioFrameCount();
        }
        return accepted;
    }

    @Test
    void theFileHasASoundTrackWithTheConfigurationInItsDescriptor() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Path file = dir.resolve("a.mp4");
            writeRecording(file, true);
            byte[] d = Files.readAllBytes(file);
            List<Box> top = boxes(d, 0, d.length);
            assertEquals("ftyp", top.get(0).type);
            assertEquals("moov", top.get(1).type);
            assertEquals(d.length, top.get(top.size() - 1).start + top.get(top.size() - 1).size, "the boxes cover the whole file");
            Box moov = top.get(1);
            List<Box> traks = children(d, moov, "trak");
            assertEquals(2, traks.size());
            Box mvhd = child(d, moov, "mvhd");
            assertEquals(3, u32(d, mvhd.start + mvhd.size - 4), "next track id");
            Box audio = traks.get(1);
            Box tkhd = child(d, audio, "tkhd");
            assertEquals(2, u32(d, tkhd.body + 12), "track id");
            Box mdia = child(d, audio, "mdia");
            Box mdhd = child(d, mdia, "mdhd");
            assertEquals(48000, u32(d, mdhd.body + 12), "sound timescale");
            long samples = u32(d, mdhd.body + 16);
            assertTrue(Math.abs(samples - 3 * 48000) < 4000, "sound length " + samples + " ticks");
            assertTrue(contains(d, mdia.start, mdia.start + mdia.size, "soun"));
            assertTrue(contains(d, mdia.start, mdia.start + mdia.size, "smhd"));
            assertTrue(contains(d, mdia.start, mdia.start + mdia.size, "mp4a"));
            assertTrue(contains(d, mdia.start, mdia.start + mdia.size, "esds"));
            assertTrue(contains(d, mdia.start, mdia.start + mdia.size, new byte[]{0x05, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x02, 0x11,
                    (byte) 0x90}), "the AudioSpecificConfig sits in its descriptor");
            Box mvex = child(d, moov, "mvex");
            assertEquals(2, children(d, mvex, "trex").size());
            Box mvhdDur = child(d, moov, "mvhd");
            long ms = u32(d, mvhdDur.body + 16);
            assertTrue(Math.abs(ms - 3000) <= 100, "movie length " + ms + " ms");
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    @Test
    void everyFragmentCarriesTheSoundOfItsTimeAndTheOffsetsPointAtTheRightBytes() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Path file = dir.resolve("b.mp4");
            long accepted = writeRecording(file, true);
            byte[] d = Files.readAllBytes(file);
            List<Box> top = boxes(d, 0, d.length);
            long audioSeen = 0;
            long expectedTick = -1;
            int fragments = 0;
            long firstAudioTick = -1;
            for (int i = 2; i < top.size(); i += 2) {
                Box moof = top.get(i);
                Box mdat = top.get(i + 1);
                assertEquals("moof", moof.type);
                assertEquals("mdat", mdat.type);
                fragments++;
                long videoBytes = 0;
                List<Box> trafs = children(d, moof, "traf");
                for (Box traf : trafs) {
                    Run r = run(d, traf);
                    long pos = moof.start + r.dataOffset;
                    assertTrue(pos >= mdat.body && pos <= mdat.start + mdat.size, "data offset of track " + r.trackId);
                    if (r.trackId == 1) {
                        assertEquals(mdat.body, pos, "video comes first in the mdat");
                        for (long s : r.sizes) {
                            videoBytes += s;
                        }
                    } else {
                        assertEquals(2, r.trackId);
                        if (expectedTick >= 0) {
                            assertEquals(expectedTick, r.decodeTime, "sound fragments follow each other without gaps");
                        } else {
                            firstAudioTick = r.decodeTime;
                        }
                        long off = pos;
                        for (int k = 0; k < r.count; k++) {
                            long idx = audioSeen + k + 5;       // the first five frames lay before the first picture and were dropped
                            assertEquals((byte) (idx % 250 + 1), d[(int) off], "frame " + idx + " is where the offsets say");
                            assertEquals(200 + (idx % 7) * 13, r.sizes[k], "size of frame " + idx);
                            off += r.sizes[k];
                        }
                        audioSeen += r.count;
                        expectedTick = r.decodeTime + r.count * 1024;
                        assertEquals(mdat.start + mdat.size, off, "the sound ends where the mdat ends");
                    }
                }
                assertTrue(videoBytes > 0, "every fragment has video");
            }
            assertEquals(accepted, audioSeen, "every accepted sound frame is in the file");
            assertTrue(fragments >= 3, "fragments " + fragments);
            // the first frame that was not dropped starts 6.7 ms after the first picture: 320 ticks of 1/48000 s
            assertTrue(Math.abs(firstAudioTick - 320) <= 2, "the sound starts at tick " + firstAudioTick);
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    @Test
    void withoutASoundConfigurationTheFileHasNoSoundTrack() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Path file = dir.resolve("c.mp4");
            writeRecording(file, false);
            byte[] d = Files.readAllBytes(file);
            List<Box> top = boxes(d, 0, d.length);
            Box moov = top.get(1);
            assertEquals(1, children(d, moov, "trak").size());
            assertEquals(1, children(d, child(d, moov, "mvex"), "trex").size());
            for (int i = 2; i < top.size(); i += 2) {
                assertEquals(1, children(d, top.get(i), "traf").size(), "video only");
            }
            assertFalse(contains(d, 0, d.length, "mp4a"));
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    @Test
    void soundThatEndsAfterTheLastPictureIsWrittenWhenTheFileIsClosed() throws Exception {
        Path dir = TestFiles.tempDir();
        try {
            Path file = dir.resolve("d.mp4");
            try (Fmp4Writer w = new Fmp4Writer(file, 640, 360, 30)) {
                w.enableAudio(48000, 2);
                w.setAudioConfig(ASC);
                for (int i = 0; i < 30; i++) {
                    byte[] v = video(i);
                    w.write(v, 0, v.length, BASE + i * 33_333L);
                }
                for (int k = 0; k < 100; k++) {                 // 2.1 s of sound for one second of video
                    w.writeAudio(aac(k), 0, aac(k).length, BASE + k * 21_333L);
                }
            }
            byte[] d = Files.readAllBytes(file);
            List<Box> top = boxes(d, 0, d.length);
            Box last = top.get(top.size() - 2);
            assertEquals("moof", last.type);
            List<Box> trafs = children(d, last, "traf");
            assertEquals(1, trafs.size());
            assertEquals(2, run(d, trafs.get(0)).trackId, "the last fragment is sound only");
            Box moov = top.get(1);
            Box mvhd = child(d, moov, "mvhd");
            long ms = u32(d, mvhd.body + 16);
            assertTrue(ms > 2000 && ms < 2200, "the movie is as long as the longer track: " + ms + " ms");
        } finally {
            TestFiles.deleteTree(dir);
        }
    }

    @Test
    void aSoundConfigurationIsComputedForCommonRates() {
        assertEquals(0x11, Fmp4Writer.aacLcConfig(48000, 2)[0] & 0xFF);
        assertEquals(0x90, Fmp4Writer.aacLcConfig(48000, 2)[1] & 0xFF);
        assertEquals(0x12, Fmp4Writer.aacLcConfig(44100, 2)[0] & 0xFF);
        assertEquals(0x10, Fmp4Writer.aacLcConfig(44100, 2)[1] & 0xFF);
        assertEquals(0x11, Fmp4Writer.aacLcConfig(48000, 1)[0] & 0xFF);
        assertEquals(0x88, Fmp4Writer.aacLcConfig(48000, 1)[1] & 0xFF);
    }
}
