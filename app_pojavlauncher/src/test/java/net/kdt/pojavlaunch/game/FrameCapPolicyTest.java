package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;

public class FrameCapPolicyTest {
    @Test
    public void coolPhoneWithoutVoiceChatIsNotCapped() {
        assertEquals(0, FrameCapPolicy.capFor(0, false));
    }

    @Test
    public void ceilingFallsAsThePhoneHeatsUp() {
        assertEquals(120, FrameCapPolicy.capFor(1, false));
        assertEquals(90, FrameCapPolicy.capFor(2, false));
        assertEquals(60, FrameCapPolicy.capFor(3, false));
    }

    @Test
    public void voiceChatIsAlwaysCappedAndNeverHigherThanWithout() {
        for (int heat = 0; heat <= 3; heat++) {
            int with = FrameCapPolicy.capFor(heat, true);
            int without = FrameCapPolicy.capFor(heat, false);
            assertTrue("voice chat must always have a ceiling", with > 0);
            if (without > 0) assertTrue(with <= without);
        }
    }

    @Test
    public void ceilingNeverRisesWithHeat() {
        for (boolean voice : new boolean[] {false, true}) {
            int previous = Integer.MAX_VALUE;
            for (int heat = 0; heat <= 3; heat++) {
                int cap = FrameCapPolicy.capFor(heat, voice);
                int effective = cap == 0 ? Integer.MAX_VALUE : cap;
                assertTrue(effective <= previous);
                previous = effective;
            }
        }
    }

    @Test
    public void outOfRangeHeatIsClamped() {
        assertEquals(FrameCapPolicy.capFor(0, false), FrameCapPolicy.capFor(-4, false));
        assertEquals(FrameCapPolicy.capFor(3, true), FrameCapPolicy.capFor(99, true));
    }

    @Test
    public void writeLeavesJustTheNumberAndNoTempFile() throws IOException {
        File dir = Files.createTempDirectory("capfile").toFile();
        File target = new File(dir, "sub/cap.txt");
        try {
            assertTrue(FrameCapPolicy.write(target, 90));
            assertEquals("90\n", new String(Files.readAllBytes(target.toPath())));
            assertTrue(FrameCapPolicy.write(target, 0));
            assertEquals("0\n", new String(Files.readAllBytes(target.toPath())));
            assertTrue(!new File(target.getPath() + ".tmp").exists());
        } finally {
            new File(dir, "sub/cap.txt").delete();
            new File(dir, "sub").delete();
            dir.delete();
        }
    }
}
