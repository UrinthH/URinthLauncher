package dev.urinth.recordable.enc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class Nv12Test {
    private static void near(int expected, int actual, int tolerance, String what) {
        assertTrue(Math.abs(expected - actual) <= tolerance, what + ": expected " + expected + " but was " + actual);
    }

    @Test
    void blackWhiteAndGreyUseTheLimitedRange() {
        assertEquals(16, Nv12.luma(0, 0, 0));
        assertEquals(235, Nv12.luma(255, 255, 255));
        near(126, Nv12.luma(128, 128, 128), 1, "mid grey");
        near(128, Nv12.cb(128, 128, 128), 1, "grey cb");
        near(128, Nv12.cr(128, 128, 128), 1, "grey cr");
        near(128, Nv12.cb(255, 255, 255), 1, "white cb");
        near(128, Nv12.cr(0, 0, 0), 1, "black cr");
    }

    @Test
    void primaryColoursFollowBt709() {
        near(63, Nv12.luma(255, 0, 0), 1, "red y");
        near(102, Nv12.cb(255, 0, 0), 1, "red cb");
        near(240, Nv12.cr(255, 0, 0), 1, "red cr");
        near(32, Nv12.luma(0, 0, 255), 1, "blue y");
        near(240, Nv12.cb(0, 0, 255), 1, "blue cb");
        near(118, Nv12.cr(0, 0, 255), 1, "blue cr");
    }

    @Test
    void theFirstRowOfAnOpenGlPictureIsTheBottomOfTheVideo() {
        // 2x2 picture as OpenGL delivers it: bottom row first. Bottom row red, top row blue.
        byte[] rgba = {
                (byte) 255, 0, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255,          // bottom row: red red
                0, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255, (byte) 255};         // top row: blue blue
        byte[] out = new byte[2 * 2 * 3 / 2];
        Nv12.fromRgbaBottomUp(rgba, 2, 2, out);
        near(32, out[0] & 0xFF, 1, "top row of the video is blue");
        near(32, out[1] & 0xFF, 1, "top row of the video is blue");
        near(63, out[2] & 0xFF, 1, "bottom row of the video is red");
        near(63, out[3] & 0xFF, 1, "bottom row of the video is red");
        // chroma is the average of the four pixels: (127, 0, 127)
        near(Nv12.cb(127, 0, 127), out[4] & 0xFF, 1, "cb");
        near(Nv12.cr(127, 0, 127), out[5] & 0xFF, 1, "cr");
    }

    @Test
    void swappingChromaExchangesEveryPairAndLeavesBrightnessAlone() {
        byte[] nv12 = {1, 2, 3, 4, 100, (byte) 200};          // 2x2 picture: four brightness samples, then one blue/red pair
        Nv12.swapChroma(nv12, 2, 2);
        assertEquals(1, nv12[0]);
        assertEquals(4, nv12[3]);
        assertEquals((byte) 200, nv12[4]);
        assertEquals(100, nv12[5]);
    }
}
