package dev.urinth.recordable.enc;

import java.io.IOException;

/**
 * Where captured pictures end up: a way to turn a {@link Frame} into a video file. Used only from the encoder thread, never from the
 * render thread.
 */
public interface VideoOut {
    /**
     * Takes one picture.
     *
     * @return false if the picture had to be dropped because the encoder had no room for it
     */
    boolean accept(Frame frame) throws IOException;

    /** Called while no picture is waiting, so the encoder can hand out what it finished. */
    void idle() throws IOException;

    /** Writes everything that is left and closes the file. */
    void finish() throws IOException;

    /** Closes without waiting for anything (after an error). Must not throw. */
    void abort();

    /** Bytes in the file so far. */
    long bytes();

    /** Length of the video so far in milliseconds. */
    long millis();

    /** One line for the log. */
    String describe();
}
