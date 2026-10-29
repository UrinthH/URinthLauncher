package net.kdt.pojavlaunch.downloads;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfileTargetTest {
    @Test
    public void fabricLoaderIdsGiveTheGameVersion() {
        ProfileTarget target = ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", null);
        assertEquals("1.21.4", target.gameVersion);
        assertEquals("fabric", target.loader);
        assertArrayEquals(new String[]{"fabric"}, target.modLoaders());
        assertEquals("Fabric 1.21.4", target.describe());
    }

    @Test
    public void inheritsFromWinsWhenKnown() {
        ProfileTarget target = ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", "1.21.4");
        assertEquals("1.21.4", target.gameVersion);
        target = ProfileTarget.parse("my-custom-profile-name", "1.20.1");
        assertEquals("1.20.1", target.gameVersion);
        assertEquals("", target.loader);
    }

    @Test
    public void quiltAcceptsFabricMods() {
        ProfileTarget target = ProfileTarget.parse("quilt-loader-0.29.0-1.21.1", null);
        assertEquals("quilt", target.loader);
        assertEquals("1.21.1", target.gameVersion);
        assertArrayEquals(new String[]{"quilt", "fabric"}, target.modLoaders());
    }

    @Test
    public void forgeAndNeoForgeIds() {
        ProfileTarget forge = ProfileTarget.parse("1.20.1-forge-47.2.0", null);
        assertEquals("forge", forge.loader);
        assertEquals("1.20.1", forge.gameVersion);
        ProfileTarget neo = ProfileTarget.parse("neoforge-21.4.100", null);
        assertEquals("neoforge", neo.loader);
        assertEquals("1.21.4", neo.gameVersion);
        ProfileTarget neoZero = ProfileTarget.parse("neoforge-21.0.5", null);
        assertEquals("1.21", neoZero.gameVersion);
        assertArrayEquals(new String[]{"neoforge"}, neo.modLoaders());
    }

    @Test
    public void plainMinecraftHasNoModLoader() {
        ProfileTarget release = ProfileTarget.parse("1.21.4", null);
        assertEquals("1.21.4", release.gameVersion);
        assertFalse(release.canRunMods());
        assertEquals("Minecraft 1.21.4", release.describe());
        ProfileTarget snapshot = ProfileTarget.parse("24w10a", null);
        assertEquals("24w10a", snapshot.gameVersion);
        ProfileTarget optifine = ProfileTarget.parse("1.20.1-OptiFine_HD_U_I6", null);
        assertEquals("optifine", optifine.loader);
        assertEquals("1.20.1", optifine.gameVersion);
        assertFalse(optifine.canRunMods());
    }

    @Test
    public void unknownIdsDoNotCrash() {
        ProfileTarget nothing = ProfileTarget.parse(null, null);
        assertEquals("", nothing.gameVersion);
        assertEquals("", nothing.loader);
        assertEquals("Minecraft", nothing.describe());
        assertEquals("", ProfileTarget.parse("latest_release", null).gameVersion);
    }

    @Test
    public void releasesAreMatchedByWholeNumbersOnly() {
        // "0.17.3" must not be read as a Minecraft version, nor 21.4.100 inside a longer number.
        assertEquals("1.21.4", ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", null).gameVersion);
        assertEquals("", ProfileTarget.parse("build-11.2.3333", null).gameVersion);
    }

    @Test
    public void compatibilityUsesGameVersionAndLoaderForMods() {
        ProfileTarget target = ProfileTarget.parse("fabric-loader-0.17.3-1.21.4", null);
        ProjectVersion good = new ProjectVersion();
        good.gameVersions.add("1.21.4");
        good.loaders.add("fabric");
        assertTrue(good.isCompatibleWith(target, DownloadCategory.MODS));
        ProjectVersion wrongLoader = new ProjectVersion();
        wrongLoader.gameVersions.add("1.21.4");
        wrongLoader.loaders.add("forge");
        assertFalse(wrongLoader.isCompatibleWith(target, DownloadCategory.MODS));
        ProjectVersion wrongGame = new ProjectVersion();
        wrongGame.gameVersions.add("1.20.1");
        wrongGame.loaders.add("fabric");
        assertFalse(wrongGame.isCompatibleWith(target, DownloadCategory.MODS));
        // Resource packs have no loader.
        ProjectVersion pack = new ProjectVersion();
        pack.gameVersions.add("1.21.4");
        assertTrue(pack.isCompatibleWith(target, DownloadCategory.RESOURCE_PACKS));
    }
}
