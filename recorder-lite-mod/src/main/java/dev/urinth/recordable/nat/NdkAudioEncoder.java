package dev.urinth.recordable.nat;

import dev.urinth.recordable.mux.Fmp4Writer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The phone's AAC encoder (almost always a software one, which is fine: AAC at 48 kHz costs very little), driven through the NDK media
 * codec functions. 16-bit stereo PCM goes in, AAC-LC frames come out, one frame (1024 samples) per output buffer.
 */
public final class NdkAudioEncoder extends NdkCodec {
    private static final String MIME = "audio/mp4a-latm";
    private static final int AAC_LC = 2;

    private final int rate;
    private final int channels;
    private final int bitrate;
    private final List<String> tried = new ArrayList<>();

    private NdkAudioEncoder(Ndk ndk, int rate, int channels, int bitrate) {
        super(ndk);
        this.rate = rate;
        this.channels = channels;
        this.bitrate = bitrate;
    }

    /** Creates, configures and starts an AAC encoder. Throws if the phone cannot do it. */
    public static NdkAudioEncoder open(int sampleRate, int channels, int bitrate) throws IOException {
        Ndk ndk = Ndk.open("libmediandk.so");
        NdkAudioEncoder e = new NdkAudioEncoder(ndk, sampleRate, channels, bitrate);
        try {
            e.search();
            return e;
        } catch (IOException | RuntimeException x) {
            e.close();
            throw x;
        }
    }

    public String attempts() {
        return String.join("; ", tried);
    }

    public String describe() {
        return codecName + " AAC-LC " + rate + " Hz " + (channels == 2 ? "stereo" : channels + " ch") + " " + bitrate / 1000 + " kbps";
    }

    /** The AudioSpecificConfig that goes into the MP4 file if the encoder does not give its own. */
    public byte[] defaultConfig() {
        return Fmp4Writer.aacLcConfig(rate, channels);
    }

    private void search() throws IOException {
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean full = attempt == 0;
            String label = full ? "AAC full settings" : "AAC basic settings";
            long c = createCodec(null, MIME);
            if (c == 0) {
                tried.add(label + ": the phone has no AAC encoder");
                break;
            }
            long fmt = newFormat();
            int st;
            try {
                setString(fmt, "mime", MIME);
                setInt(fmt, "sample-rate", rate);
                setInt(fmt, "channel-count", channels);
                setInt(fmt, "bitrate", bitrate);
                if (full) {
                    setInt(fmt, "aac-profile", AAC_LC);
                }
                st = configureEncoder(c, fmt);
            } finally {
                deleteFormat(fmt);
            }
            String name = nameOf(c);
            if (st != 0) {
                tried.add(label + ": " + (name.isEmpty() ? "" : name + " ") + "configure failed (" + st + ")");
                deleteCodec(c);
                continue;
            }
            codec = c;
            codecName = name.isEmpty() ? "unknown" : name;
            try {
                startCodec();
            } catch (IOException x) {
                tried.add(label + ": " + codecName + " " + x.getMessage());
                deleteCodec(codec);
                codec = 0;
                continue;
            }
            tried.add(label + ": " + codecName + " works");
            return;
        }
        throw new IOException(tried.isEmpty() ? "no AAC encoder could be started" : String.join("; ", tried));
    }

    /**
     * Gives 16-bit interleaved PCM to the encoder.
     *
     * @param timeoutUs how long to wait for a free input buffer (0 = do not wait)
     * @return the number of bytes taken; 0 if the encoder had no room (try again later); a buffer smaller than the data takes a part
     */
    public int submit(byte[] pcm, int off, int len, long ptsUs, long timeoutUs) throws IOException {
        if (!dequeueInput(timeoutUs)) {
            return 0;
        }
        int frameBytes = 2 * channels;
        int n = (int) Math.min((long) len, inputCap);
        n -= n % frameBytes;
        if (n <= 0) {
            throw new IOException("the audio encoder's input buffer is too small (" + inputCap + " bytes)");
        }
        Mem.put(inputPtr, pcm, off, n);
        queueInput(n, ptsUs, 0);
        return n;
    }
}
