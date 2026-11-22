package dev.urinth.recordable.mux;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes H.264 video into a fragmented MP4 file.
 *
 * <p>Why fragmented: a normal MP4 keeps its index at the end of the file, so a recording that is cut short (the phone kills the game
 * because it is low on memory, the battery dies) is unplayable. A fragmented file stores the index along with the video in small pieces,
 * so everything written so far stays playable no matter how the recording ended. When the recording is closed properly the total
 * duration is added to the header.
 *
 * <p>Not thread-safe: use it from one thread (the encoder's writer thread).
 */
public final class Fmp4Writer implements Closeable {
    /** Ticks per second of the video track. */
    public static final int TIMESCALE = 90000;
    private static final int MOVIE_TIMESCALE = 1000;
    private static final long MAC_EPOCH_OFFSET = 2082844800L;   // seconds between 1904-01-01 and 1970-01-01
    private static final int MAX_FRAGMENT_SAMPLES = 300;
    private static final int MAX_FRAGMENT_BYTES = 4 << 20;

    private static final class Pending {
        final byte[] data;
        final long ticks;                                      // decode time, from the first frame
        final boolean key;
        long duration;

        Pending(byte[] data, long ticks, boolean key) {
            this.data = data;
            this.ticks = ticks;
            this.key = key;
        }
    }

    private final FileChannel ch;
    private final int width;
    private final int height;
    private final int nominalFps;
    private final H264.ParamSets ps = new H264.ParamSets();
    private final List<Pending> pending = new ArrayList<>();
    private int pendingBytes;
    private boolean headerWritten;
    private boolean sawKey;
    private boolean closed;
    private long filePos;
    private int sequence = 1;
    private long baseUs = Long.MIN_VALUE;
    private long lastTicks = -1;
    private long fragmentStartTicks;                           // decode time of the first sample of the next fragment
    private long endTicks;                                     // decode time right after the last written sample
    private long samples;
    private int mvhdDurationPos;
    private int tkhdDurationPos;
    private int mdhdDurationPos;
    private int mehdDurationPos;

    /**
     * @param nominalFps used only as the duration of a lone last sample, when the real spacing is unknown
     */
    public Fmp4Writer(Path file, int width, int height, int nominalFps) throws IOException {
        this.width = width;
        this.height = height;
        this.nominalFps = Math.max(1, nominalFps);
        this.ch = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** Bytes written to the file so far (not counting samples still waiting for the next keyframe). */
    public long bytesWritten() {
        return filePos;
    }

    /** Number of video frames accepted so far. */
    public long sampleCount() {
        return samples;
    }

    /** Length of the video in milliseconds, up to the last frame that was accepted. */
    public long durationMs() {
        return (Math.max(endTicks, lastTicks) * 1000L) / TIMESCALE;
    }

    /**
     * Adds one encoder output buffer. It may hold a keyframe with SPS and PPS in front, a plain frame, or only the parameter sets; the
     * latter gives no sample.
     *
     * @param ptsUs presentation time of the frame, in microseconds
     * @return false if the buffer only held parameter sets or was skipped because no keyframe has been seen yet
     */
    public boolean write(byte[] annexB, int off, int len, long ptsUs) throws IOException {
        if (closed) {
            throw new IOException("writer is closed");
        }
        H264.Sample s = H264.toAvcc(annexB, off, len, ps);
        if (s.data.length == 0) {
            return false;
        }
        if (!sawKey) {
            if (!s.keyframe) {
                return false;                                  // a stream has to start with a keyframe
            }
            sawKey = true;
        }
        if (baseUs == Long.MIN_VALUE) {
            baseUs = ptsUs;
        }
        long ticks = Math.max(0L, ptsUs - baseUs) * 9L / 100L;   // microseconds to 1/90000 s
        if (ticks <= lastTicks) {
            ticks = lastTicks + 1;                             // the timeline must keep moving forward
        }
        if (!pending.isEmpty()) {
            pending.get(pending.size() - 1).duration = ticks - lastTicks;
            if (s.keyframe || pending.size() >= MAX_FRAGMENT_SAMPLES || pendingBytes >= MAX_FRAGMENT_BYTES) {
                flushFragment();
            }
        }
        pending.add(new Pending(s.data, ticks, s.keyframe));
        pendingBytes += s.data.length;
        lastTicks = ticks;
        samples++;
        return true;
    }

    private void flushFragment() throws IOException {
        if (pending.isEmpty()) {
            return;
        }
        if (!headerWritten) {
            if (!ps.complete()) {
                throw new IOException("the encoder did not provide SPS/PPS");
            }
            writeHeader();
        }
        int count = pending.size();
        int payload = 0;
        for (Pending p : pending) {
            payload += p.data.length;
        }
        ByteSink b = new ByteSink();
        int moof = b.begin("moof");
        int mfhd = b.beginFull("mfhd", 0, 0);
        b.u32(sequence++);
        b.end(mfhd);
        int traf = b.begin("traf");
        int tfhd = b.beginFull("tfhd", 0, 0x020000);           // offsets are relative to the start of the moof box
        b.u32(1);
        b.end(tfhd);
        int tfdt = b.beginFull("tfdt", 1, 0);
        b.u64(fragmentStartTicks);
        b.end(tfdt);
        int trun = b.beginFull("trun", 0, 0x000701);           // data offset, sample duration, sample size, sample flags
        b.u32(count);
        int dataOffsetPos = b.size();
        b.u32(0);
        long ticks = 0;
        for (Pending p : pending) {
            b.u32(p.duration);
            b.u32(p.data.length);
            b.u32(p.key ? 0x02000000L : 0x01010000L);
            ticks += p.duration;
        }
        b.end(trun);
        b.end(traf);
        b.end(moof);
        b.patch32(dataOffsetPos, b.size() + 8L);               // the first sample follows the 8-byte mdat header

        ByteSink mdat = new ByteSink();
        mdat.u32(8L + payload);
        mdat.fourcc("mdat");
        writeFully(ByteBuffer.wrap(b.toArray()));
        writeFully(ByteBuffer.wrap(mdat.toArray()));
        for (Pending p : pending) {
            writeFully(ByteBuffer.wrap(p.data));
        }
        fragmentStartTicks += ticks;
        endTicks = fragmentStartTicks;
        pending.clear();
        pendingBytes = 0;
    }

    private void writeFully(ByteBuffer bb) throws IOException {
        while (bb.hasRemaining()) {
            filePos += ch.write(bb);
        }
    }

    private void writeHeader() throws IOException {
        long now = System.currentTimeMillis() / 1000L + MAC_EPOCH_OFFSET;
        ByteSink b = new ByteSink();

        int ftyp = b.begin("ftyp");
        b.fourcc("isom").u32(512).fourcc("isom").fourcc("iso6").fourcc("avc1").fourcc("mp41");
        b.end(ftyp);

        int moov = b.begin("moov");

        int mvhd = b.beginFull("mvhd", 0, 0);
        b.u32(now).u32(now).u32(MOVIE_TIMESCALE);
        mvhdDurationPos = b.size();
        b.u32(0);
        b.u32(0x00010000).u16(0x0100).u16(0).u32(0).u32(0);    // rate 1.0, volume 1.0, reserved
        matrix(b);
        b.zeros(24);                                           // pre_defined
        b.u32(2);                                              // next track id
        b.end(mvhd);

        int trak = b.begin("trak");
        int tkhd = b.beginFull("tkhd", 0, 3);                  // enabled, in movie
        b.u32(now).u32(now).u32(1).u32(0);
        tkhdDurationPos = b.size();
        b.u32(0);
        b.u32(0).u32(0).u16(0).u16(0).u16(0).u16(0);           // reserved, layer, alternate group, volume, reserved
        matrix(b);
        b.u32((long) width << 16).u32((long) height << 16);
        b.end(tkhd);

        int mdia = b.begin("mdia");
        int mdhd = b.beginFull("mdhd", 0, 0);
        b.u32(now).u32(now).u32(TIMESCALE);
        mdhdDurationPos = b.size();
        b.u32(0);
        b.u16(0x55C4).u16(0);                                  // language "und"
        b.end(mdhd);
        int hdlr = b.beginFull("hdlr", 0, 0);
        b.u32(0).fourcc("vide").zeros(12);
        b.bytes("VideoHandler".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).u8(0);
        b.end(hdlr);

        int minf = b.begin("minf");
        int vmhd = b.beginFull("vmhd", 0, 1);
        b.u16(0).u16(0).u16(0).u16(0);
        b.end(vmhd);
        int dinf = b.begin("dinf");
        int dref = b.beginFull("dref", 0, 0);
        b.u32(1);
        int url = b.beginFull("url ", 0, 1);                   // media data is in this file
        b.end(url);
        b.end(dref);
        b.end(dinf);

        int stbl = b.begin("stbl");
        int stsd = b.beginFull("stsd", 0, 0);
        b.u32(1);
        int avc1 = b.begin("avc1");
        b.zeros(6).u16(1);                                     // data reference index
        b.u16(0).u16(0).zeros(12);                             // pre_defined, reserved, pre_defined
        b.u16(width).u16(height);
        b.u32(0x00480000).u32(0x00480000).u32(0);              // 72 dpi, reserved
        b.u16(1);                                              // frames per sample
        byte[] name = "Record-able Lite".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        b.u8(name.length).bytes(name).zeros(31 - name.length);
        b.u16(0x0018).u16(0xFFFF);                             // depth, pre_defined
        int avcC = b.begin("avcC");
        b.bytes(H264.avcC(ps));
        b.end(avcC);
        int colr = b.begin("colr");                            // tell players the picture is BT.709, limited range
        b.fourcc("nclx").u16(1).u16(1).u16(1).u8(0);
        b.end(colr);
        b.end(avc1);
        b.end(stsd);
        emptyTable(b, "stts", 0);
        emptyTable(b, "stsc", 0);
        int stsz = b.beginFull("stsz", 0, 0);
        b.u32(0).u32(0);
        b.end(stsz);
        emptyTable(b, "stco", 0);
        b.end(stbl);
        b.end(minf);
        b.end(mdia);
        b.end(trak);

        int mvex = b.begin("mvex");
        int mehd = b.beginFull("mehd", 0, 0);
        mehdDurationPos = b.size();
        b.u32(0);
        b.end(mehd);
        int trex = b.beginFull("trex", 0, 0);
        b.u32(1).u32(1).u32(0).u32(0).u32(0x01010000L);
        b.end(trex);
        b.end(mvex);

        b.end(moov);
        // The positions above are relative to this buffer, and the buffer goes to the very start of the file.
        writeFully(ByteBuffer.wrap(b.toArray()));
        headerWritten = true;
    }

    private static void matrix(ByteSink b) {
        b.u32(0x00010000).u32(0).u32(0);
        b.u32(0).u32(0x00010000).u32(0);
        b.u32(0).u32(0).u32(0x40000000L);
    }

    private static void emptyTable(ByteSink b, String type, int entries) {
        int p = b.beginFull(type, 0, 0);
        b.u32(entries);
        b.end(p);
    }

    /** Writes the frames that are still waiting, fills in the total duration and closes the file. */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (!pending.isEmpty()) {
                Pending last = pending.get(pending.size() - 1);
                if (last.duration <= 0) {
                    long d = pending.size() > 1 ? pending.get(pending.size() - 2).duration : 0;
                    last.duration = d > 0 ? d : TIMESCALE / nominalFps;
                }
                flushFragment();
            }
            if (headerWritten) {
                long ms = endTicks * 1000L / TIMESCALE;
                patch(mvhdDurationPos, ms);
                patch(tkhdDurationPos, ms);
                patch(mdhdDurationPos, endTicks);
                patch(mehdDurationPos, ms);
            }
        } finally {
            ch.close();
        }
    }

    private void patch(int pos, long value) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt((int) Math.min(value, 0xFFFFFFFFL));
        bb.flip();
        ch.write(bb, pos);
    }

    /** True once at least one fragment with a keyframe is on disk or queued, meaning the file will play. */
    public boolean hasVideo() {
        return sawKey;
    }
}
