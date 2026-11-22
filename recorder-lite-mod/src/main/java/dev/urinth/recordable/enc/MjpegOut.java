package dev.urinth.recordable.enc;

import dev.urinth.recordable.mux.AviWriter;
import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * The emergency path for phones where the hardware encoder cannot be used: every picture becomes a JPEG inside an AVI file. It plays on
 * computers and in most video players, but it costs the processor much more than the hardware encoder, so it runs at a low frame rate.
 * Pictures must arrive as RGBA with the bottom row first (the capture's {@code RGBA} mode).
 */
public final class MjpegOut implements VideoOut {
    private static final int QUALITY = 70;

    private final AviWriter avi;
    private final int width;
    private final int height;
    private final ByteBuffer rgb;
    private final Collector callback = new Collector();
    private byte[] jpeg = new byte[1 << 17];
    private int jpegLength;
    private boolean closed;

    private MjpegOut(AviWriter avi, int width, int height) {
        this.avi = avi;
        this.width = width;
        this.height = height;
        this.rgb = MemoryUtil.memAlloc(width * height * 4);
    }

    /** Receives the JPEG bytes from the encoder library, in pieces, and gathers them in {@link #jpeg}. */
    private final class Collector extends STBIWriteCallback {
        @Override
        public void invoke(long context, long data, int size) {
            if (jpegLength + size > jpeg.length) {
                jpeg = java.util.Arrays.copyOf(jpeg, Math.max(jpeg.length * 2, jpegLength + size));
            }
            MemoryUtil.memByteBuffer(data, size).get(jpeg, jpegLength, size);
            jpegLength += size;
        }
    }

    public static MjpegOut open(Path file, int width, int height, int fps) throws IOException {
        AviWriter avi = new AviWriter(file, width, height, fps);
        try {
            return new MjpegOut(avi, width, height);
        } catch (RuntimeException | Error e) {
            avi.close();
            throw e;
        }
    }

    @Override
    public boolean accept(Frame f) throws IOException {
        if (!avi.hasRoom()) {
            throw new IOException("the AVI file reached its size limit");
        }
        // OpenGL gives the bottom row first, JPEG wants the top row first
        int row = width * 4;
        rgb.clear();
        for (int y = height - 1; y >= 0; y--) {
            rgb.put(f.data, y * row, row);
        }
        rgb.flip();
        jpegLength = 0;
        if (STBImageWrite.stbi_write_jpg_to_func(callback, 0L, width, height, 4, rgb, QUALITY) == 0 || jpegLength == 0) {
            throw new IOException("JPEG encoding failed");
        }
        return avi.write(jpeg, 0, jpegLength, f.ptsUs);
    }

    @Override
    public void idle() {
        // nothing is buffered anywhere
    }

    @Override
    public void finish() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            avi.close();
        } finally {
            release();
        }
    }

    @Override
    public void abort() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            avi.close();
        } catch (IOException | RuntimeException ignored) {
            // nothing more to do
        }
        release();
    }

    private void release() {
        callback.free();
        MemoryUtil.memFree(rgb);
    }

    @Override
    public long bytes() {
        return avi.bytesWritten();
    }

    @Override
    public long millis() {
        return avi.durationMs();
    }

    @Override
    public String describe() {
        return "Motion-JPEG " + width + "x" + height + " (CPU encoder)";
    }
}
