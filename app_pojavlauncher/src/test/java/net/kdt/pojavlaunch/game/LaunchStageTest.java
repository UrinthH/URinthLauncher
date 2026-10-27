package net.kdt.pojavlaunch.game;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LaunchStageTest {
    @Test
    public void anEmptyLogMeansTheLauncherIsStillPreparing() {
        assertEquals(LaunchStage.PREPARING, LaunchStage.fromLog(null));
        assertEquals(LaunchStage.PREPARING, LaunchStage.fromLog(""));
        assertEquals(LaunchStage.PREPARING, LaunchStage.fromLog("--------- Starting game with Launcher Debug!"));
    }

    @Test
    public void theLauncherOptionsLineMeansTheVmIsStarting() {
        assertEquals(LaunchStage.STARTING_JAVA,
                LaunchStage.fromLog("[Options] options.txt as the launcher found it"));
        assertEquals(LaunchStage.STARTING_JAVA, LaunchStage.fromLog("[0.012s][info][gc] Using G1"));
    }

    @Test
    public void minecraftLogLinesMeanTheGameIsLoading() {
        assertEquals(LaunchStage.LOADING_GAME,
                LaunchStage.fromLog("[main/INFO]: Setting user: Steve"));
        assertEquals(LaunchStage.LOADING_GAME,
                LaunchStage.fromLog("[main/INFO]: Loading Minecraft 1.21.1 with Fabric Loader 0.16"));
    }

    @Test
    public void theBackendLibraryLineMeansTheWindowIsBeingCreated() {
        assertEquals(LaunchStage.CREATING_WINDOW,
                LaunchStage.fromLog("[Render thread/INFO]: Backend library: LWJGL version 3.3.3"));
    }

    @Test
    public void resourceReloadingIsTheLastStage() {
        assertEquals(LaunchStage.LOADING_RESOURCES,
                LaunchStage.fromLog("[Render thread/INFO]: Reloading ResourceManager: vanilla"));
    }

    @Test
    public void theFurthestStageWinsWhenSeveralAreInTheTail() {
        String tail = "[Options] x\n[main/INFO]: Setting user: A\n"
                + "[Render thread/INFO]: Backend library: LWJGL version 3.3.3\n"
                + "[Render thread/INFO]: Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas";
        assertEquals(LaunchStage.LOADING_RESOURCES, LaunchStage.fromLog(tail));
    }

    @Test
    public void elapsedIsMinutesAndSeconds() {
        assertEquals("0:00", LaunchStage.elapsed(0));
        assertEquals("0:09", LaunchStage.elapsed(9_999));
        assertEquals("1:23", LaunchStage.elapsed(83_000));
        assertEquals("0:00", LaunchStage.elapsed(-5));
    }
}
