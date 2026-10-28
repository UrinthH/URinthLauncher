package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VoiceChatMicModeTest {
    @Test
    public void unknownOrMissingMeansCommunication() {
        assertEquals(VoiceChatMicMode.COMMUNICATION, VoiceChatMicMode.normalize(null));
        assertEquals(VoiceChatMicMode.COMMUNICATION, VoiceChatMicMode.normalize(""));
        assertEquals(VoiceChatMicMode.COMMUNICATION, VoiceChatMicMode.normalize("loud"));
    }

    @Test
    public void presetsMatchTheAndroidConstants() {
        assertEquals(4, VoiceChatMicMode.openSlPreset("communication"));
        assertEquals(3, VoiceChatMicMode.openSlPreset("recognition"));
        assertEquals(5, VoiceChatMicMode.openSlPreset("unprocessed"));
        assertEquals(4, VoiceChatMicMode.openSlPreset("nonsense"));
        assertEquals(7, VoiceChatMicMode.aaudioPreset("communication"));
        assertEquals(6, VoiceChatMicMode.aaudioPreset("recognition"));
        assertEquals(9, VoiceChatMicMode.aaudioPreset("unprocessed"));
    }

    @Test
    public void onlyCommunicationUsesCallMode() {
        assertTrue(VoiceChatMicMode.usesCallMode("communication"));
        assertTrue(VoiceChatMicMode.usesCallMode(null));
        assertFalse(VoiceChatMicMode.usesCallMode("recognition"));
        assertFalse(VoiceChatMicMode.usesCallMode("unprocessed"));
    }
}
