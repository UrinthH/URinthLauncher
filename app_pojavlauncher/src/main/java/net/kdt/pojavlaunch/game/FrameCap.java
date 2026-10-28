package net.kdt.pojavlaunch.game;

import net.kdt.pojavlaunch.Logger;

import java.io.File;

/**
 * Hands the frame ceiling to the renderer (LTW) while the game runs.
 *
 * <p>Performance Mode with the Performance renderer turns vsync off; this is what keeps that from
 * running the phone flat out. The ceiling is a number in a small file that LTW checks twice a
 * second ({@code LTW_FRAME_CAP_FILE}). It is recomputed when the phone's heat level changes and
 * when a voice chat mod is found, see {@link FrameCapPolicy}. It never touches options.txt, the
 * resolution, or the game's own frame limit setting: if that is lower, it still wins.
 *
 * <p>Does nothing unless {@link #configure} was called, which only happens in Performance Mode
 * with the Performance renderer.
 */
public final class FrameCap {
    private static File sFile;
    private static int sLast = -1;

    private FrameCap() {}

    /** Name the file LTW reads. Call before the game starts. */
    public static synchronized void configure(File file) {
        sFile = file;
        sLast = -1;
    }

    /** Work out the ceiling from the current conditions and, if it changed, tell the renderer. */
    public static synchronized void update() {
        if (sFile == null) return;
        int cap = FrameCapPolicy.capFor(ThermalGuard.currentHeatLevel(), VoiceChatAudio.isModPresent());
        if (cap == sLast) return;
        if (FrameCapPolicy.write(sFile, cap)) {
            sLast = cap;
            try {
                Logger.appendToLog("[PerformanceMode] frame ceiling " + (cap == 0 ? "none" : cap + " fps")
                        + (VoiceChatAudio.isModPresent() ? " (voice chat mod installed)" : ""));
            } catch (Throwable ignored) {
                // The native logger is not available yet or already gone.
            }
        }
    }
}
