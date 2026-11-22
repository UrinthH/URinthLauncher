package dev.urinth.recordable.enc;

/** One captured picture on its way from the render thread to the encoder thread. The buffers are reused, never allocated per frame. */
public final class Frame {
    public final byte[] data;
    public int length;
    /** Recording time of the picture in microseconds (pauses do not count). */
    public long ptsUs;

    Frame(int bytes) {
        this.data = new byte[bytes];
    }
}
