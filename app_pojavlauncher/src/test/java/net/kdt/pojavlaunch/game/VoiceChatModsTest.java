package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;

public class VoiceChatModsTest {
    @Test
    public void knownVoiceChatJarsAreFound() {
        assertTrue(VoiceChatMods.isVoiceChatJar("voicechat-fabric-1.21.1-2.5.26.jar"));
        assertTrue(VoiceChatMods.isVoiceChatJar("Simple-Voice-Chat-Forge.JAR"));
        assertTrue(VoiceChatMods.isVoiceChatJar("voice-chat-neoforge-1.20.jar"));
        assertTrue(VoiceChatMods.isVoiceChatJar("plasmovoice-fabric-1.20.1.jar"));
        assertTrue(VoiceChatMods.isVoiceChatJar("PlasmoVoice-forge.jar"));
    }

    @Test
    public void otherModsAreNot() {
        assertFalse(VoiceChatMods.isVoiceChatJar("sodium-fabric-0.5.jar"));
        assertFalse(VoiceChatMods.isVoiceChatJar("voicechat-fabric.jar.disabled"));
        assertFalse(VoiceChatMods.isVoiceChatJar("notes.txt"));
        assertFalse(VoiceChatMods.isVoiceChatJar(null));
        assertFalse(VoiceChatMods.isVoiceChatJar(""));
    }

    @Test
    public void installedLooksInsideModsFolder() throws IOException {
        File dir = Files.createTempDirectory("vcmods").toFile();
        try {
            assertFalse(VoiceChatMods.isInstalled(dir));
            assertFalse(VoiceChatMods.isInstalled(null));
            File mods = new File(dir, "mods");
            assertTrue(mods.mkdir());
            assertTrue(new File(mods, "sodium.jar").createNewFile());
            assertFalse(VoiceChatMods.isInstalled(dir));
            assertTrue(new File(mods, "voicechat-fabric-2.5.jar").createNewFile());
            assertTrue(VoiceChatMods.isInstalled(dir));
        } finally {
            File[] mods = new File(dir, "mods").listFiles();
            if (mods != null) for (File f : mods) f.delete();
            new File(dir, "mods").delete();
            dir.delete();
        }
    }
}
