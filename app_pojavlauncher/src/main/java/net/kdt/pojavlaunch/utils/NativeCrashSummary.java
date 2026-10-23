package net.kdt.pojavlaunch.utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the crash dump Android keeps for a native crash into a few readable lines: the signal, the
 * abort message and the top of the crashing thread's stack.
 *
 * The dump is a "tombstone" protobuf (Android 12 and newer). Only a handful of its fields matter,
 * so they are read by hand instead of pulling in generated classes. Field numbers are those of
 * AOSP's system/core/debuggerd/proto/tombstone.proto. Nothing here trusts the data: a truncated or
 * unexpected dump gives less text, never an exception. If none of the expected fields are found
 * (an older or different format), the readable strings in the dump are listed instead.
 *
 * This class deliberately depends on nothing from Android, so it can be tested on its own.
 */
public final class NativeCrashSummary {
    private static final int MAX_FRAMES = 14;
    private static final int MAX_TEXT = 240;
    private static final int MAX_RAW_STRINGS = 60;
    private static final int MAX_LIBRARY_LINES = 25;
    private static final int MIN_RAW_STRING = 6;

    // Tombstone
    private static final int TOMBSTONE_TID = 6;
    private static final int TOMBSTONE_COMMAND_LINE = 9;
    private static final int TOMBSTONE_SIGNAL_INFO = 10;
    private static final int TOMBSTONE_ABORT_MESSAGE = 14;
    private static final int TOMBSTONE_THREADS = 16;
    // Signal
    private static final int SIGNAL_NAME = 2;
    private static final int SIGNAL_CODE_NAME = 4;
    // map<uint32, Thread> entry
    private static final int ENTRY_KEY = 1;
    private static final int ENTRY_VALUE = 2;
    // Thread
    private static final int THREAD_NAME = 2;
    private static final int THREAD_BACKTRACE = 4;
    // BacktraceFrame
    private static final int FRAME_REL_PC = 1;
    private static final int FRAME_FUNCTION_NAME = 4;
    private static final int FRAME_FUNCTION_OFFSET = 5;
    private static final int FRAME_FILE_NAME = 6;

    private NativeCrashSummary() {}

    /**
     * @param dump the raw bytes of the crash dump
     * @return a short readable summary, or null when nothing readable could be found
     */
    public static String summarize(byte[] dump) {
        if (dump == null || dump.length == 0) return null;
        String structured = null;
        try {
            structured = structuredSummary(dump);
        } catch (RuntimeException unexpected) {
            // Fall through to the plain strings below.
        }
        if (structured != null) return structured;
        return rawStrings(dump);
    }

    // ---------------------------------------------------------------- structured reading

    private static String structuredSummary(byte[] dump) {
        String signalName = null;
        String signalCode = null;
        String abortMessage = null;
        String commandLine = null;
        long crashedThreadId = -1;
        List<int[]> threadEntries = new ArrayList<>();

        Reader top = new Reader(dump, 0, dump.length);
        try {
            while (top.hasMore()) {
                int tag = (int) top.varint();
                int field = tag >>> 3;
                int wire = tag & 7;
                if (wire == 2) {
                    int length = top.length();
                    int start = top.pos;
                    int end = start + length;
                    if (field == TOMBSTONE_SIGNAL_INFO) {
                        String[] signal = readSignal(dump, start, end);
                        signalName = signal[0];
                        signalCode = signal[1];
                    } else if (field == TOMBSTONE_ABORT_MESSAGE) {
                        abortMessage = text(dump, start, length);
                    } else if (field == TOMBSTONE_COMMAND_LINE) {
                        if (commandLine == null) commandLine = text(dump, start, length);
                    } else if (field == TOMBSTONE_THREADS) {
                        threadEntries.add(new int[] { start, end });
                    }
                    top.pos = end;
                } else if (wire == 0 && field == TOMBSTONE_TID) {
                    crashedThreadId = top.varint();
                } else {
                    top.skip(wire);
                }
            }
        } catch (RuntimeException stoppedMakingSense) {
            // Keep whatever was read before the data stopped making sense.
        }

        String threadName = null;
        List<String> frames = new ArrayList<>();
        int[] crashedThread = findThread(dump, threadEntries, crashedThreadId);
        if (crashedThread != null) {
            Reader thread = new Reader(dump, crashedThread[0], crashedThread[1]);
            try {
                while (thread.hasMore() && frames.size() < MAX_FRAMES) {
                    int tag = (int) thread.varint();
                    int field = tag >>> 3;
                    int wire = tag & 7;
                    if (wire == 2) {
                        int length = thread.length();
                        int start = thread.pos;
                        int end = start + length;
                        if (field == THREAD_NAME) {
                            threadName = text(dump, start, length);
                        } else if (field == THREAD_BACKTRACE) {
                            frames.add(describeFrame(dump, start, end, frames.size()));
                        }
                        thread.pos = end;
                    } else {
                        thread.skip(wire);
                    }
                }
            } catch (RuntimeException stoppedMakingSense) {
                // Use the frames read so far.
            }
        }

        if (signalName == null && abortMessage == null && frames.isEmpty()) return null;

        StringBuilder out = new StringBuilder();
        if (signalName != null) {
            out.append("Signal: ").append(signalName);
            if (signalCode != null && !signalCode.isEmpty()) out.append(" (").append(signalCode).append(')');
            out.append('\n');
        }
        if (abortMessage != null && !abortMessage.isEmpty()) {
            out.append("Abort message: ").append(abortMessage).append('\n');
        }
        if (commandLine != null && !commandLine.isEmpty()) {
            out.append("Process: ").append(commandLine).append('\n');
        }
        if (crashedThread != null) {
            out.append("Crashed thread: ").append(crashedThreadId);
            if (threadName != null && !threadName.isEmpty()) out.append(" \"").append(threadName).append('"');
            out.append('\n');
        }
        for (String frame : frames) out.append(frame).append('\n');
        if (frames.isEmpty()) {
            // The crashing thread could not be picked out, so at least name the libraries that
            // appear in the dump (thread stacks come before the list of every mapped library).
            String libraries = rawStrings(dump, ".so", MAX_LIBRARY_LINES);
            if (libraries != null) out.append("Libraries in the dump:\n").append(libraries);
        }
        return out.toString();
    }

    private static String[] readSignal(byte[] data, int start, int end) {
        String[] result = new String[2];
        Reader signal = new Reader(data, start, end);
        try {
            while (signal.hasMore()) {
                int tag = (int) signal.varint();
                int field = tag >>> 3;
                int wire = tag & 7;
                if (wire == 2) {
                    int length = signal.length();
                    if (field == SIGNAL_NAME) result[0] = text(data, signal.pos, length);
                    else if (field == SIGNAL_CODE_NAME) result[1] = text(data, signal.pos, length);
                    signal.pos += length;
                } else {
                    signal.skip(wire);
                }
            }
        } catch (RuntimeException stoppedMakingSense) {
            // Use what was read.
        }
        return result;
    }

    /** @return {start, end} of the Thread message whose map key is threadId, or null */
    private static int[] findThread(byte[] data, List<int[]> entries, long threadId) {
        if (threadId < 0) return null;
        for (int[] entry : entries) {
            Reader reader = new Reader(data, entry[0], entry[1]);
            long key = -1;
            int valueStart = -1;
            int valueEnd = -1;
            try {
                while (reader.hasMore()) {
                    int tag = (int) reader.varint();
                    int field = tag >>> 3;
                    int wire = tag & 7;
                    if (field == ENTRY_KEY && wire == 0) {
                        key = reader.varint();
                    } else if (field == ENTRY_VALUE && wire == 2) {
                        int length = reader.length();
                        valueStart = reader.pos;
                        valueEnd = valueStart + length;
                        reader.pos = valueEnd;
                    } else {
                        reader.skip(wire);
                    }
                }
            } catch (RuntimeException stoppedMakingSense) {
                // Judge this entry by what was read.
            }
            if (key == threadId && valueStart >= 0) return new int[] { valueStart, valueEnd };
        }
        return null;
    }

    private static String describeFrame(byte[] data, int start, int end, int index) {
        String function = null;
        String file = null;
        long offset = -1;
        long relativePc = -1;
        Reader frame = new Reader(data, start, end);
        try {
            while (frame.hasMore()) {
                int tag = (int) frame.varint();
                int field = tag >>> 3;
                int wire = tag & 7;
                if (wire == 2) {
                    int length = frame.length();
                    if (field == FRAME_FUNCTION_NAME) function = text(data, frame.pos, length);
                    else if (field == FRAME_FILE_NAME) file = text(data, frame.pos, length);
                    frame.pos += length;
                } else if (wire == 0) {
                    long value = frame.varint();
                    if (field == FRAME_FUNCTION_OFFSET) offset = value;
                    else if (field == FRAME_REL_PC) relativePc = value;
                } else {
                    frame.skip(wire);
                }
            }
        } catch (RuntimeException stoppedMakingSense) {
            // Describe the frame from what was read.
        }
        StringBuilder out = new StringBuilder("#").append(index).append(' ');
        out.append(file == null || file.isEmpty() ? "?" : file);
        if (function != null && !function.isEmpty()) {
            out.append(" (").append(function);
            if (offset >= 0) out.append('+').append(offset);
            out.append(')');
        } else if (relativePc >= 0) {
            out.append(" +0x").append(Long.toHexString(relativePc));
        }
        return out.toString();
    }

    private static String text(byte[] data, int start, int length) {
        String value = new String(data, start, Math.min(length, MAX_TEXT), StandardCharsets.UTF_8);
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    // ---------------------------------------------------------------- fallback

    /**
     * Readable runs of text anywhere in the data. In a protobuf each string is preceded by a tag and
     * a length byte, which can show up as one or two stray characters at the start of a line.
     */
    private static String rawStrings(byte[] data) {
        String lines = rawStrings(data, null, MAX_RAW_STRINGS);
        return lines == null ? null : "(raw text from the crash dump)\n" + lines;
    }

    /**
     * @param mustContain only runs containing this text are kept, or null to keep every run
     * @return one run per line, or null when there were none
     */
    private static String rawStrings(byte[] data, String mustContain, int maxLines) {
        Set<String> found = new LinkedHashSet<>();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i <= data.length && found.size() < maxLines; i++) {
            int value = i < data.length ? data[i] & 0xFF : 0;
            if (value >= 0x20 && value < 0x7F) {
                run.append((char) value);
                continue;
            }
            if (run.length() >= MIN_RAW_STRING
                    && (mustContain == null || run.indexOf(mustContain) >= 0)) {
                found.add(run.length() > MAX_TEXT ? run.substring(0, MAX_TEXT) : run.toString());
            }
            run.setLength(0);
        }
        if (found.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (String line : found) out.append(line).append('\n');
        return out.toString();
    }

    // ---------------------------------------------------------------- protobuf reader

    /** Just enough of the protobuf wire format to walk a message. Throws on malformed input. */
    private static final class Reader {
        private final byte[] data;
        private final int end;
        int pos;

        Reader(byte[] data, int start, int end) {
            this.data = data;
            this.pos = start;
            this.end = end;
        }

        boolean hasMore() {
            return pos < end;
        }

        long varint() {
            long result = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= end) throw new IllegalStateException("truncated varint");
                int b = data[pos++] & 0xFF;
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return result;
            }
            throw new IllegalStateException("varint too long");
        }

        /** Reads the length prefix of a length-delimited value; pos ends up at its first byte. */
        int length() {
            long length = varint();
            if (length < 0 || length > end - pos) throw new IllegalStateException("bad length");
            return (int) length;
        }

        void skip(int wireType) {
            switch (wireType) {
                case 0:
                    varint();
                    break;
                case 1:
                    advance(8);
                    break;
                case 2:
                    advance(length());
                    break;
                case 5:
                    advance(4);
                    break;
                default:
                    throw new IllegalStateException("unsupported wire type " + wireType);
            }
        }

        private void advance(int count) {
            if (count < 0 || count > end - pos) throw new IllegalStateException("truncated field");
            pos += count;
        }
    }
}
