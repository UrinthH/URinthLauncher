package net.kdt.pojavlaunch.game;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * What ceiling (frames per second) the game gets in Performance Mode, from how hot the phone is
 * and whether voice chat is running. 0 would mean no ceiling; there is none now, because even a
 * cool phone is held back by the other work the game does at the same time (the integrated
 * server, chunk building, voice chat), and a render thread that never rests takes the CPU
 * time those need.
 *
 * <p>With vsync off the game would otherwise draw flat out, which is what heats a phone up and
 * then makes it throttle, and that is what players feel as stutter and freezes. Frames beyond
 * what the screen can show are never seen, so lowering the ceiling as heat builds costs
 * nothing visible. Voice chat has its own lower ceiling, because the recording and playback
 * threads need CPU time too and a game that uses every core makes the voice cut out.
 *
 * <p>Plain Java with no Android classes, so it is covered by JVM unit tests.
 */
public final class FrameCapPolicy {
    /** Heat levels, as {@link ThermalGuard} counts them: 0 cool, 1 warm, 2 hot, 3 critical. */
    private static final int[] BY_HEAT = {120, 90, 75, 60};
    private static final int[] BY_HEAT_WITH_VOICE_CHAT = {90, 75, 60, 60};

    private FrameCapPolicy() {}

    public static int capFor(int heatLevel, boolean voiceChat) {
        int level = Math.max(0, Math.min(heatLevel, BY_HEAT.length - 1));
        int cap = voiceChat ? BY_HEAT_WITH_VOICE_CHAT[level] : BY_HEAT[level];
        return cap;
    }

    /** Writes the number so the renderer never sees half of it: to a side file, then renamed. */
    public static boolean write(File target, int cap) {
        File temp = new File(target.getPath() + ".tmp");
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write((cap + "\n").getBytes(StandardCharsets.US_ASCII));
            }
            return temp.renameTo(target);
        } catch (IOException e) {
            return false;
        }
    }
}
