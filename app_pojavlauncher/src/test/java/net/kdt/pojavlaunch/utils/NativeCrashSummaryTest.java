package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

/**
 * Reading the crash dump Android keeps for a native crash. The dumps here are built by hand with
 * the field numbers of AOSP's tombstone.proto, which is what the class under test reads.
 */
public class NativeCrashSummaryTest {

    /** Just enough of a protobuf writer to build the messages below. */
    private static final class Msg {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Msg varint(int field, long value) {
            writeVarint((long) field << 3);
            writeVarint(value);
            return this;
        }

        Msg string(int field, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeVarint(((long) field << 3) | 2);
            writeVarint(bytes.length);
            out.write(bytes, 0, bytes.length);
            return this;
        }

        Msg message(int field, Msg inner) {
            byte[] bytes = inner.toBytes();
            writeVarint(((long) field << 3) | 2);
            writeVarint(bytes.length);
            out.write(bytes, 0, bytes.length);
            return this;
        }

        byte[] toBytes() {
            return out.toByteArray();
        }

        private void writeVarint(long value) {
            while ((value & ~0x7FL) != 0) {
                out.write((int) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
            out.write((int) value);
        }
    }

    private static Msg frame(long relativePc, String function, long offset, String file) {
        Msg frame = new Msg().varint(1, relativePc);
        if (function != null) frame.string(4, function).varint(5, offset);
        return frame.string(6, file);
    }

    /** One entry of the tombstone's map of threads, keyed by thread id. */
    private static Msg threadEntry(int id, String name, Msg... frames) {
        Msg thread = new Msg().varint(1, id).string(2, name);
        for (Msg frame : frames) thread.message(4, frame);
        return new Msg().varint(1, id).message(2, thread);
    }

    private static byte[] tombstone(int crashedThreadId, Msg... threads) {
        Msg signal = new Msg().varint(1, 11).string(2, "SIGSEGV").varint(3, 1).string(4, "SEGV_MAPERR");
        Msg tombstone = new Msg()
                .string(3, "some revision")   // fields the reader does not care about must be skipped
                .varint(5, 4242)
                .varint(6, crashedThreadId)
                .string(9, "com.example.app:game")
                .message(10, signal)
                .string(14, "something went wrong");
        for (Msg thread : threads) tombstone.message(16, thread);
        return tombstone.toBytes();
    }

    private static byte[] typicalDump() {
        return tombstone(100,
                threadEntry(99, "Other thread", frame(1, "never_shown", 0, "/system/lib64/libother.so")),
                threadEntry(100, "Render thread",
                        frame(0x1234, "render_frame", 8, "/data/app/lib/arm64/libfoo.so"),
                        frame(0x20, "abort", 168, "/apex/com.android.runtime/lib64/bionic/libc.so")));
    }

    @Test
    public void namesTheSignalTheAbortMessageAndTheProcess() {
        String summary = NativeCrashSummary.summarize(typicalDump());

        assertNotNull(summary);
        assertTrue(summary, summary.contains("Signal: SIGSEGV (SEGV_MAPERR)"));
        assertTrue(summary, summary.contains("Abort message: something went wrong"));
        assertTrue(summary, summary.contains("Process: com.example.app:game"));
    }

    @Test
    public void showsTheStackOfTheCrashingThreadOnly() {
        String summary = NativeCrashSummary.summarize(typicalDump());

        assertNotNull(summary);
        assertTrue(summary, summary.contains("Crashed thread: 100 \"Render thread\""));
        assertTrue(summary, summary.contains("#0 /data/app/lib/arm64/libfoo.so (render_frame+8)"));
        assertTrue(summary, summary.contains("#1 /apex/com.android.runtime/lib64/bionic/libc.so (abort+168)"));
        assertFalse(summary, summary.contains("Other thread"));
        assertFalse(summary, summary.contains("never_shown"));
    }

    @Test
    public void aFrameWithoutASymbolShowsItsOffsetInTheLibrary() {
        String summary = NativeCrashSummary.summarize(tombstone(7,
                threadEntry(7, "main", frame(0xabc, null, 0, "/data/app/lib/arm64/libbar.so"))));

        assertNotNull(summary);
        assertTrue(summary, summary.contains("#0 /data/app/lib/arm64/libbar.so +0xabc"));
    }

    @Test
    public void stopsAfterTheFirstFewFrames() {
        Msg[] frames = new Msg[50];
        for (int i = 0; i < frames.length; i++) frames[i] = frame(i, "fn" + i, 0, "/lib/libdeep.so");
        String summary = NativeCrashSummary.summarize(tombstone(1, threadEntry(1, "deep", frames)));

        assertNotNull(summary);
        assertTrue(summary, summary.contains("#13 "));
        assertFalse(summary, summary.contains("#14 "));
    }

    @Test
    public void whenTheCrashingThreadIsMissingItListsTheLibrariesInTheDump() {
        String summary = NativeCrashSummary.summarize(tombstone(999,
                threadEntry(1, "unrelated", frame(1, "fn", 0, "/data/app/lib/arm64/libwhatever.so"))));

        assertNotNull(summary);
        assertTrue(summary, summary.contains("Signal: SIGSEGV"));
        assertTrue(summary, summary.contains("Libraries in the dump:"));
        assertTrue(summary, summary.contains("libwhatever.so"));
    }

    @Test
    public void aTextTombstoneFallsBackToItsReadableLines() {
        String text = "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***\n"
                + "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0000000000000010\n"
                + "    #00 pc 000000000001b2c4  /data/app/lib/arm64/libpojavexec.so\n";
        String summary = NativeCrashSummary.summarize(text.getBytes(StandardCharsets.UTF_8));

        assertNotNull(summary);
        assertTrue(summary, summary.contains("signal 11 (SIGSEGV)"));
        assertTrue(summary, summary.contains("libpojavexec.so"));
    }

    @Test
    public void nothingReadableGivesNull() {
        assertNull(NativeCrashSummary.summarize(null));
        assertNull(NativeCrashSummary.summarize(new byte[0]));
        byte[] noise = new byte[64];
        Arrays.fill(noise, (byte) 0xFF);
        assertNull(NativeCrashSummary.summarize(noise));
    }

    @Test
    public void aDumpCutOffAnywhereNeverThrows() {
        byte[] dump = typicalDump();
        for (int length = 0; length <= dump.length; length++) {
            // Any exception fails the test; what comes back (null or a few lines) does not matter.
            NativeCrashSummary.summarize(Arrays.copyOf(dump, length));
        }
    }

    @Test
    public void aDamagedDumpNeverThrows() {
        byte[] original = typicalDump();
        Random random = new Random(1234);
        for (int round = 0; round < 2000; round++) {
            byte[] damaged = original.clone();
            int hits = 1 + random.nextInt(4);
            for (int i = 0; i < hits; i++) damaged[random.nextInt(damaged.length)] = (byte) random.nextInt(256);
            NativeCrashSummary.summarize(damaged);
        }
    }
}
