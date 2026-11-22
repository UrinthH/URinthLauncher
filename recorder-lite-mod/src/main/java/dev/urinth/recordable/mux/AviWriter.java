package dev.urinth.recordable.mux;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Writes Motion-JPEG video into an AVI file. This is only the fallback for phones where the hardware H.264 encoder cannot be used: it
 * plays on a computer (VLC and similar) but many phone galleries do not know the format.
 *
 * <p>AVI has one fixed frame rate. Frames that arrive later than their slot are handled by repeating the previous picture, so the
 * length of the video always matches the real recording time.
 */
public final class AviWriter implements Closeable {
    /** Stops the recording around here; the old AVI format cannot grow past 2 GB. */
    public static final long SIZE_LIMIT = 1_700_000_000L;
    private static final int HEADER_SIZE = 12 + 8 + 4 + 8 + 56 + 12 + 8 + 56 + 8 + 40 + 12;   // up to and including the 'movi' tag

    private final FileChannel ch;
    private final int width;
    private final int height;
    private final int fps;
    private final long moviTag;                                // position of the 'movi' four-character code
    private long pos;
    private long baseUs = Long.MIN_VALUE;
    private long lastSlot = -1;
    private byte[] previous;
    private int previousLen;
    private int frames;
    private int maxFrame;
    private long indexBytes;
    private int[] indexOffset = new int[256];
    private int[] indexSize = new int[256];
    private boolean closed;

    public AviWriter(Path file, int width, int height, int fps) throws IOException {
        this.width = width;
        this.height = height;
        this.fps = Math.max(1, fps);
        this.ch = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        writeHeader();
        this.moviTag = HEADER_SIZE - 4;
    }

    public long bytesWritten() {
        return pos;
    }

    public int frameCount() {
        return frames;
    }

    public long durationMs() {
        return frames * 1000L / fps;
    }

    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void fourcc(ByteBuffer b, String s) {
        for (int i = 0; i < 4; i++) {
            b.put((byte) s.charAt(i));
        }
    }

    private void writeHeader() throws IOException {
        ByteBuffer b = le(HEADER_SIZE);
        fourcc(b, "RIFF");
        b.putInt(0);                                           // patched when closing
        fourcc(b, "AVI ");
        fourcc(b, "LIST");
        b.putInt(4 + 8 + 56 + 12 + 8 + 56 + 8 + 40);           // size of the hdrl list
        fourcc(b, "hdrl");
        fourcc(b, "avih");
        b.putInt(56);
        b.putInt(1_000_000 / fps);                             // microseconds per frame
        b.putInt(0);                                           // max bytes per second, patched
        b.putInt(0);                                           // padding granularity
        b.putInt(0x10);                                        // has index
        b.putInt(0);                                           // total frames, patched
        b.putInt(0);                                           // initial frames
        b.putInt(1);                                           // streams
        b.putInt(1 << 20);                                     // suggested buffer size, patched
        b.putInt(width).putInt(height);
        b.putInt(0).putInt(0).putInt(0).putInt(0);
        fourcc(b, "LIST");
        b.putInt(4 + 8 + 56 + 8 + 40);                         // size of the strl list
        fourcc(b, "strl");
        fourcc(b, "strh");
        b.putInt(56);
        fourcc(b, "vids");
        fourcc(b, "MJPG");
        b.putInt(0);                                           // flags
        b.putShort((short) 0).putShort((short) 0);             // priority, language
        b.putInt(0);                                           // initial frames
        b.putInt(1);                                           // scale
        b.putInt(fps);                                         // rate
        b.putInt(0);                                           // start
        b.putInt(0);                                           // length, patched
        b.putInt(1 << 20);                                     // suggested buffer size, patched
        b.putInt(-1);                                          // quality
        b.putInt(0);                                           // sample size
        b.putShort((short) 0).putShort((short) 0).putShort((short) width).putShort((short) height);
        fourcc(b, "strf");
        b.putInt(40);
        b.putInt(40).putInt(width).putInt(height);
        b.putShort((short) 1).putShort((short) 24);
        fourcc(b, "MJPG");
        b.putInt(width * height * 3);
        b.putInt(0).putInt(0).putInt(0).putInt(0);
        fourcc(b, "LIST");
        b.putInt(0);                                           // size of the movi list, patched
        fourcc(b, "movi");
        b.flip();
        writeFully(b);
    }

    private void writeFully(ByteBuffer bb) throws IOException {
        while (bb.hasRemaining()) {
            pos += ch.write(bb);
        }
    }

    /**
     * Adds one JPEG picture.
     *
     * @param tsUs time since the start of the recording, in microseconds
     * @return false if the picture came too early for its slot and was dropped
     */
    public boolean write(byte[] jpeg, int off, int len, long tsUs) throws IOException {
        if (closed) {
            throw new IOException("writer is closed");
        }
        if (baseUs == Long.MIN_VALUE) {
            baseUs = tsUs;
        }
        long slot = Math.max(0L, tsUs - baseUs) * fps / 1_000_000L;
        if (slot <= lastSlot) {
            return false;
        }
        if (previous != null) {
            long missing = Math.min(slot - lastSlot - 1, 600);
            for (long i = 0; i < missing; i++) {
                chunk(previous, 0, previousLen);               // hold the last picture while no new one arrived
            }
        }
        chunk(jpeg, off, len);
        if (previous == null || previous.length < len) {
            previous = new byte[Math.max(len, 1 << 16)];
        }
        System.arraycopy(jpeg, off, previous, 0, len);
        previousLen = len;
        lastSlot = slot;
        return true;
    }

    private void chunk(byte[] d, int off, int len) throws IOException {
        if (frames == indexOffset.length) {
            indexOffset = Arrays.copyOf(indexOffset, frames * 2);
            indexSize = Arrays.copyOf(indexSize, frames * 2);
        }
        indexOffset[frames] = (int) (pos - moviTag);
        indexSize[frames] = len;
        int padded = len + (len & 1);
        ByteBuffer h = le(8);
        fourcc(h, "00dc");
        h.putInt(len);
        h.flip();
        writeFully(h);
        writeFully(ByteBuffer.wrap(d, off, len));
        if (padded != len) {
            writeFully(ByteBuffer.wrap(new byte[1]));
        }
        frames++;
        maxFrame = Math.max(maxFrame, len);
        indexBytes += 16;
    }

    /** True while there is room for more pictures. */
    public boolean hasRoom() {
        return pos + indexBytes + (maxFrame + 16) * 4L < SIZE_LIMIT;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            long moviEnd = pos;
            ByteBuffer idx = le(8 + frames * 16);
            fourcc(idx, "idx1");
            idx.putInt(frames * 16);
            for (int i = 0; i < frames; i++) {
                fourcc(idx, "00dc");
                idx.putInt(0x10);
                idx.putInt(indexOffset[i]);
                idx.putInt(indexSize[i]);
            }
            idx.flip();
            writeFully(idx);
            patch(4, (int) (pos - 8));
            patch(HEADER_SIZE - 8, (int) (moviEnd - (HEADER_SIZE - 4)));    // movi list size
            long seconds = Math.max(1, (frames + fps - 1) / fps);
            patch(12 + 12 + 8 + 4 + 4, (int) Math.min(Integer.MAX_VALUE, pos / seconds));    // avih: max bytes per second
            patch(12 + 12 + 8 + 16, frames);                                                 // avih: total frames
            patch(12 + 12 + 8 + 28, maxFrame);                                               // avih: suggested buffer size
            int strh = 12 + 12 + 8 + 56 + 12 + 8;
            patch(strh + 32, frames);                                                        // strh: length
            patch(strh + 36, maxFrame);                                                      // strh: suggested buffer size
        } finally {
            ch.close();
        }
    }

    private void patch(long at, int value) throws IOException {
        ByteBuffer b = le(4);
        b.putInt(value).flip();
        ch.write(b, at);
    }
}
