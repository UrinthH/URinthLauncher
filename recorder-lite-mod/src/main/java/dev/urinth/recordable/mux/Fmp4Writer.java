package dev.urinth.recordable.mux;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Writes H.264 video (and, optionally, AAC sound) into a fragmented MP4 file.
 *
 * <p>Why fragmented: a normal MP4 keeps its index at the end of the file, so a recording that is cut short (the phone kills the game
 * because it is low on memory, the battery dies) is unplayable. A fragmented file stores the index along with the video in small pieces,
 * so everything written so far stays playable no matter how the recording ended. When the recording is closed properly the total
 * duration is added to the header.
 *
 * <p>Sound: after {@link #enableAudio} and {@link #setAudioConfig} the file gets a second track. The sound follows the video's clock: the
 * time of the first picture is time zero, sound that was recorded before it is dropped, and the sound frames that belong to a video
 * fragment are written in the same fragment. Without a sound configuration at the time the header is written the file simply has no
 * sound track (the video is never held back for the sound).
 *
 * <p>Not thread-safe: use it from one thread (the encoder's writer thread).
 */
public final class Fmp4Writer implements Closeable {
    /** Ticks per second of the video track. */
    public static final int TIMESCALE = 90000;
    /** Samples in one AAC frame. */
    public static final int AUDIO_FRAME_SAMPLES = 1024;
    private static final int MOVIE_TIMESCALE = 1000;
    private static final long MAC_EPOCH_OFFSET = 2082844800L;   // seconds between 1904-01-01 and 1970-01-01
    private static final int MAX_FRAGMENT_SAMPLES = 300;
    private static final int MAX_FRAGMENT_BYTES = 4 << 20;
    /** Sound frames kept while the first picture is still on its way (a little over four seconds). */
    private static final int AUDIO_HOLD_LIMIT = 200;
    /** Sound frames (about 13 seconds) after which they are written even though no video fragment is due. */
    private static final int AUDIO_ONLY_FLUSH_FRAMES = 600;

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

    /** One AAC frame; {@code value} is its time in microseconds while it is held, and its start tick in the sound track afterwards. */
    private static final class AudioFrame {
        final byte[] data;
        final long value;

        AudioFrame(byte[] data, long value) {
            this.data = data;
            this.value = value;
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

    // sound
    private boolean audioWanted;
    private boolean audioOn;                                   // decided when the header is written
    private int audioRate;
    private int audioChannels;
    private byte[] audioConfig;
    private final List<AudioFrame> audioHeld = new ArrayList<>();
    private final List<AudioFrame> audioPending = new ArrayList<>();
    private long audioNextTick = -1;                           // start tick of the next accepted frame; -1 until the first one
    private long audioWrittenEnd;                              // tick right after the last written sound frame
    private long audioAccepted;
    private int tkhdAudioDurationPos;
    private int mdhdAudioDurationPos;

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

    // ---- sound setup ---------------------------------------------------------------------------------------------------------------

    /** Asks for a sound track. Has to be called before the first video fragment is written. */
    public void enableAudio(int sampleRate, int channels) {
        if (headerWritten || sampleRate <= 0 || channels <= 0) {
            return;
        }
        audioWanted = true;
        audioRate = sampleRate;
        audioChannels = channels;
    }

    /** Sets the AAC "AudioSpecificConfig" (two bytes for plain AAC-LC). Has to come before the first video fragment is written. */
    public void setAudioConfig(byte[] asc) {
        if (!headerWritten && asc != null && asc.length > 0) {
            audioConfig = asc.clone();
        }
    }

    /** The AudioSpecificConfig for AAC-LC at the given rate and channel count (what encoders put out in their config buffer). */
    public static byte[] aacLcConfig(int sampleRate, int channels) {
        int[] rates = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};
        int idx = 4;
        for (int i = 0; i < rates.length; i++) {
            if (rates[i] == sampleRate) {
                idx = i;
                break;
            }
        }
        int objectType = 2;                                    // AAC-LC
        int b0 = (objectType << 3) | (idx >> 1);
        int b1 = ((idx & 1) << 7) | ((Math.max(1, Math.min(7, channels))) << 3);
        return new byte[]{(byte) b0, (byte) b1};
    }

    /** True when sound is part of the file (the header was written with a sound track). */
    public boolean hasAudioTrack() {
        return audioOn;
    }

    /** Number of sound frames written or waiting. */
    public long audioFrameCount() {
        return audioAccepted;
    }

    /** Length of the sound in milliseconds. */
    public long audioDurationMs() {
        long end = Math.max(audioWrittenEnd, audioNextTick);
        return audioRate > 0 && end > 0 ? end * 1000L / audioRate : 0;
    }

    // ---- video ---------------------------------------------------------------------------------------------------------------------

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
            releaseHeldAudio();
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

    // ---- sound ---------------------------------------------------------------------------------------------------------------------

    /**
     * Adds one AAC frame (without any ADTS header). Only the time of the first frame counts: the frames that follow are placed one
     * right after the other, 1024 samples each, which is how the encoder made them.
     *
     * @param ptsUs time of the frame's first sample on the same clock as the video, in microseconds
     * @return false if the frame was not used (no sound track, or it lies before the start of the picture)
     */
    public boolean writeAudio(byte[] data, int off, int len, long ptsUs) throws IOException {
        if (closed) {
            throw new IOException("writer is closed");
        }
        if (!audioWanted || len <= 0 || (headerWritten && !audioOn)) {
            return false;
        }
        byte[] copy = Arrays.copyOfRange(data, off, off + len);
        if (baseUs == Long.MIN_VALUE) {                        // the first picture has not come yet: its time is the zero point
            audioHeld.add(new AudioFrame(copy, ptsUs));
            while (audioHeld.size() > AUDIO_HOLD_LIMIT) {
                audioHeld.remove(0);
            }
            return true;
        }
        return acceptAudio(copy, ptsUs);
    }

    private void releaseHeldAudio() {
        List<AudioFrame> held = new ArrayList<>(audioHeld);
        audioHeld.clear();
        for (AudioFrame f : held) {
            acceptAudio(f.data, f.value);
        }
    }

    private boolean acceptAudio(byte[] data, long ptsUs) {
        if (audioNextTick < 0) {
            if (ptsUs < baseUs) {
                return false;                                  // recorded before the first picture
            }
            audioNextTick = Math.round((ptsUs - baseUs) * (double) audioRate / 1_000_000d);
        }
        audioPending.add(new AudioFrame(data, audioNextTick));
        audioNextTick += AUDIO_FRAME_SAMPLES;
        audioAccepted++;
        return true;
    }

    // ---- fragments -----------------------------------------------------------------------------------------------------------------

    private void flushFragment() throws IOException {
        if (pending.isEmpty()) {
            return;
        }
        ensureHeader();
        writeFragment(true, false);
    }

    private void ensureHeader() throws IOException {
        if (headerWritten) {
            return;
        }
        if (!ps.complete()) {
            throw new IOException("the encoder did not provide SPS/PPS");
        }
        audioOn = audioWanted && audioConfig != null && audioRate > 0;
        if (!audioOn) {
            audioPending.clear();
            audioHeld.clear();
        }
        writeHeader();
    }

    /**
     * Writes one moof/mdat pair.
     *
     * @param video whether the waiting video frames go into it
     * @param allAudio whether all waiting sound frames go into it (otherwise only those that start before the video fragment ends)
     */
    private void writeFragment(boolean video, boolean allAudio) throws IOException {
        int vCount = video ? pending.size() : 0;
        int vPayload = 0;
        long vTicks = 0;
        if (video) {
            for (Pending p : pending) {
                vPayload += p.data.length;
                vTicks += p.duration;
            }
        }
        long videoEndTicks = fragmentStartTicks + vTicks;
        int aCount = 0;
        int aPayload = 0;
        if (audioOn) {
            long limit = allAudio ? Long.MAX_VALUE : (videoEndTicks * audioRate + TIMESCALE - 1) / TIMESCALE;
            for (AudioFrame f : audioPending) {
                if (f.value >= limit) {
                    break;
                }
                aCount++;
                aPayload += f.data.length;
            }
        }
        if (vCount == 0 && aCount == 0) {
            return;
        }

        ByteSink b = new ByteSink();
        int moof = b.begin("moof");
        int mfhd = b.beginFull("mfhd", 0, 0);
        b.u32(sequence++);
        b.end(mfhd);
        int videoOffsetPos = -1;
        int audioOffsetPos = -1;
        if (vCount > 0) {
            int traf = b.begin("traf");
            int tfhd = b.beginFull("tfhd", 0, 0x020000);       // offsets are relative to the start of the moof box
            b.u32(1);
            b.end(tfhd);
            int tfdt = b.beginFull("tfdt", 1, 0);
            b.u64(fragmentStartTicks);
            b.end(tfdt);
            int trun = b.beginFull("trun", 0, 0x000701);       // data offset, sample duration, sample size, sample flags
            b.u32(vCount);
            videoOffsetPos = b.size();
            b.u32(0);
            for (Pending p : pending) {
                b.u32(p.duration);
                b.u32(p.data.length);
                b.u32(p.key ? 0x02000000L : 0x01010000L);
            }
            b.end(trun);
            b.end(traf);
        }
        if (aCount > 0) {
            int traf = b.begin("traf");
            int tfhd = b.beginFull("tfhd", 0, 0x020000);
            b.u32(2);
            b.end(tfhd);
            int tfdt = b.beginFull("tfdt", 1, 0);
            b.u64(audioPending.get(0).value);
            b.end(tfdt);
            int trun = b.beginFull("trun", 0, 0x000301);       // data offset, sample duration, sample size
            b.u32(aCount);
            audioOffsetPos = b.size();
            b.u32(0);
            for (int i = 0; i < aCount; i++) {
                b.u32(AUDIO_FRAME_SAMPLES);
                b.u32(audioPending.get(i).data.length);
            }
            b.end(trun);
            b.end(traf);
        }
        b.end(moof);
        long first = b.size() + 8L;                            // the first sample follows the 8-byte mdat header
        if (videoOffsetPos >= 0) {
            b.patch32(videoOffsetPos, first);
        }
        if (audioOffsetPos >= 0) {
            b.patch32(audioOffsetPos, first + vPayload);
        }

        ByteSink mdat = new ByteSink();
        mdat.u32(8L + vPayload + aPayload);
        mdat.fourcc("mdat");
        writeFully(ByteBuffer.wrap(b.toArray()));
        writeFully(ByteBuffer.wrap(mdat.toArray()));
        if (vCount > 0) {
            for (Pending p : pending) {
                writeFully(ByteBuffer.wrap(p.data));
            }
            fragmentStartTicks += vTicks;
            endTicks = fragmentStartTicks;
            pending.clear();
            pendingBytes = 0;
        }
        if (aCount > 0) {
            for (int i = 0; i < aCount; i++) {
                writeFully(ByteBuffer.wrap(audioPending.get(i).data));
            }
            audioWrittenEnd = audioPending.get(aCount - 1).value + AUDIO_FRAME_SAMPLES;
            audioPending.subList(0, aCount).clear();
        }
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
        b.u32(audioOn ? 3 : 2);                                // next track id
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
        dinf(b);

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
        emptyTables(b);
        b.end(stbl);
        b.end(minf);
        b.end(mdia);
        b.end(trak);

        if (audioOn) {
            int atrak = b.begin("trak");
            int atkhd = b.beginFull("tkhd", 0, 3);
            b.u32(now).u32(now).u32(2).u32(0);
            tkhdAudioDurationPos = b.size();
            b.u32(0);
            b.u32(0).u32(0).u16(0).u16(1).u16(0x0100).u16(0);  // reserved, layer, alternate group, volume 1.0, reserved
            matrix(b);
            b.u32(0).u32(0);                                   // no picture size
            b.end(atkhd);

            int amdia = b.begin("mdia");
            int amdhd = b.beginFull("mdhd", 0, 0);
            b.u32(now).u32(now).u32(audioRate);
            mdhdAudioDurationPos = b.size();
            b.u32(0);
            b.u16(0x55C4).u16(0);
            b.end(amdhd);
            int ahdlr = b.beginFull("hdlr", 0, 0);
            b.u32(0).fourcc("soun").zeros(12);
            b.bytes("SoundHandler".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).u8(0);
            b.end(ahdlr);

            int aminf = b.begin("minf");
            int smhd = b.beginFull("smhd", 0, 0);
            b.u16(0).u16(0);                                   // balance, reserved
            b.end(smhd);
            dinf(b);
            int astbl = b.begin("stbl");
            int astsd = b.beginFull("stsd", 0, 0);
            b.u32(1);
            int mp4a = b.begin("mp4a");
            b.zeros(6).u16(1);                                 // data reference index
            b.u32(0).u32(0);                                   // sound version 0, reserved
            b.u16(audioChannels).u16(16).u16(0).u16(0);        // channels, bits per sample, pre_defined, reserved
            b.u32((long) audioRate << 16);
            int esds = b.beginFull("esds", 0, 0);
            descriptor(b, 0x03, 3 + (5 + 13 + 5 + audioConfig.length) + (5 + 1));       // ES_Descriptor
            b.u16(2).u8(0);                                    // ES_ID, flags
            descriptor(b, 0x04, 13 + 5 + audioConfig.length);  // DecoderConfigDescriptor
            b.u8(0x40).u8(0x15);                               // MPEG-4 audio, audio stream
            b.u8(0).u16(0);                                    // buffer size
            b.u32(0).u32(0);                                   // max and average bit rate (unknown)
            descriptor(b, 0x05, audioConfig.length);           // DecoderSpecificInfo
            b.bytes(audioConfig);
            descriptor(b, 0x06, 1);                            // SLConfigDescriptor
            b.u8(2);
            b.end(esds);
            b.end(mp4a);
            b.end(astsd);
            emptyTables(b);
            b.end(astbl);
            b.end(aminf);
            b.end(amdia);
            b.end(atrak);
        }

        int mvex = b.begin("mvex");
        int mehd = b.beginFull("mehd", 0, 0);
        mehdDurationPos = b.size();
        b.u32(0);
        b.end(mehd);
        int trex = b.beginFull("trex", 0, 0);
        b.u32(1).u32(1).u32(0).u32(0).u32(0x01010000L);
        b.end(trex);
        if (audioOn) {
            int atrex = b.beginFull("trex", 0, 0);
            b.u32(2).u32(1).u32(0).u32(0).u32(0x02000000L);
            b.end(atrex);
        }
        b.end(mvex);

        b.end(moov);
        // The positions above are relative to this buffer, and the buffer goes to the very start of the file.
        writeFully(ByteBuffer.wrap(b.toArray()));
        headerWritten = true;
    }

    /** An MPEG-4 descriptor header: the tag, then the length in the four-byte form that every player accepts. */
    private static void descriptor(ByteSink b, int tag, int length) {
        b.u8(tag);
        b.u8(0x80 | ((length >> 21) & 0x7F));
        b.u8(0x80 | ((length >> 14) & 0x7F));
        b.u8(0x80 | ((length >> 7) & 0x7F));
        b.u8(length & 0x7F);
    }

    private static void dinf(ByteSink b) {
        int dinf = b.begin("dinf");
        int dref = b.beginFull("dref", 0, 0);
        b.u32(1);
        int url = b.beginFull("url ", 0, 1);                   // media data is in this file
        b.end(url);
        b.end(dref);
        b.end(dinf);
    }

    private static void emptyTables(ByteSink b) {
        emptyTable(b, "stts", 0);
        emptyTable(b, "stsc", 0);
        int stsz = b.beginFull("stsz", 0, 0);
        b.u32(0).u32(0);
        b.end(stsz);
        emptyTable(b, "stco", 0);
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
            if (headerWritten && audioOn && !audioPending.isEmpty()) {
                writeFragment(false, true);
            }
            if (headerWritten) {
                long videoMs = endTicks * 1000L / TIMESCALE;
                long audioMs = audioOn ? audioWrittenEnd * 1000L / audioRate : 0;
                long movieMs = Math.max(videoMs, audioMs);
                patch(mvhdDurationPos, movieMs);
                patch(tkhdDurationPos, videoMs);
                patch(mdhdDurationPos, endTicks);
                patch(mehdDurationPos, movieMs);
                if (audioOn) {
                    patch(tkhdAudioDurationPos, audioMs);
                    patch(mdhdAudioDurationPos, audioWrittenEnd);
                }
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
