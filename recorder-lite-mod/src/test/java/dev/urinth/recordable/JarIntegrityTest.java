package dev.urinth.recordable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/**
 * Looks into the finished jar (after remapping), because the things that went wrong with earlier mods were all about the jar and not
 * about the code: a jar that was not remapped for the game, a missing native helper, a version that was never filled in.
 */
final class JarIntegrityTest {
    private static final String[] REQUIRED = {
            "fabric.mod.json",
            "assets/recordable_lite/lang/en_us.json",
            "natives/linux-aarch64/libreclite.so",
            "natives/linux-x86_64/libreclite.so",
            "dev/urinth/recordable/RecordableLite.class",
            "dev/urinth/recordable/RecorderSession.class",
            "dev/urinth/recordable/Governor.class",
            "dev/urinth/recordable/Settings.class",
            "dev/urinth/recordable/SettingsScreen.class",
            "dev/urinth/recordable/gl/GpuCapture.class",
            "dev/urinth/recordable/nat/NdkEncoder.class",
            "dev/urinth/recordable/nat/Shim.class",
            "dev/urinth/recordable/mux/Fmp4Writer.class",
            "dev/urinth/recordable/mux/AviWriter.class",
            "dev/urinth/recordable/enc/H264Out.class",
            "dev/urinth/recordable/enc/MjpegOut.class",
            // the game's sound: the hook into the game's sound engine and everything it uses
            "recordable_lite.mixins.json",
            "dev/urinth/recordable/NativeFolders.class",
            "dev/urinth/recordable/mixin/LibraryMixin.class",
            "dev/urinth/recordable/audio/GameAudio.class",
            "dev/urinth/recordable/audio/AudioPump.class",
            "dev/urinth/recordable/audio/AudioRecorder.class",
            "dev/urinth/recordable/audio/AudioTap.class",
            "dev/urinth/recordable/audio/AudioTimeline.class",
            "dev/urinth/recordable/audio/AAudioOutput.class",
            "dev/urinth/recordable/audio/OpenAlBridge.class",
            "dev/urinth/recordable/nat/AAudioLib.class",
            "dev/urinth/recordable/nat/NdkAudioEncoder.class"};

    private static byte[] read(ZipFile zip, String name) throws IOException {
        ZipEntry e = zip.getEntry(name);
        assertNotNull(e, name + " is in the jar");
        try (InputStream in = zip.getInputStream(e)) {
            return in.readAllBytes();
        }
    }

    private static boolean contains(byte[] data, String text) {
        byte[] t = text.getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i + t.length <= data.length; i++) {
            for (int k = 0; k < t.length; k++) {
                if (data[i + k] != t[k]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static long le(byte[] d, int p, int bytes) {
        long v = 0;
        for (int i = bytes - 1; i >= 0; i--) {
            v = (v << 8) | (d[p + i] & 0xFFL);
        }
        return v;
    }

    @Test
    void theJarHoldsEverythingTheGameNeeds() throws Exception {
        String path = System.getProperty("urinth.jar");
        if (path == null || path.isEmpty()) {
            return;                                             // started without the build (an IDE): nothing to look at
        }
        Path jar = Path.of(path);
        assertTrue(Files.isRegularFile(jar), "the jar exists: " + jar);
        assertTrue(Files.size(jar) < 400_000, "the jar stays small, it is " + Files.size(jar) + " bytes");
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String name : REQUIRED) {
                assertNotNull(zip.getEntry(name), name + " is in the jar");
            }
            String meta = new String(read(zip, "fabric.mod.json"), StandardCharsets.UTF_8);
            assertTrue(meta.contains("\"id\": \"recordable_lite\""), "mod id");
            assertTrue(meta.contains("dev.urinth.recordable.RecordableLite"), "entry point");
            assertFalse(meta.contains("${version}"), "the version was filled in");
            assertTrue(meta.contains("recordable_lite.mixins.json"), "the game hook configuration is listed in fabric.mod.json");

            // The sound hook: it must be optional (a game that does not fit it still starts) and, in the remapped jar, it must name
            // the game's classes and methods the way the running game knows them.
            String mixins = new String(read(zip, "recordable_lite.mixins.json"), StandardCharsets.UTF_8).replaceAll("\\s+", "");
            assertTrue(mixins.contains("\"package\":\"dev.urinth.recordable.mixin\""), "mixin package");
            assertTrue(mixins.contains("LibraryMixin"), "the sound engine hook is listed");
            assertTrue(mixins.contains("\"required\":false"), "the hook configuration is not required");
            assertTrue(mixins.contains("\"defaultRequire\":0"), "hooks that do not fit are skipped, not fatal");
            if (!Boolean.getBoolean("urinth.unremapped")) {
                // Loom rewrites the names inside the hook's annotations in the class file itself (there is no separate refmap file):
                // the game runs with its intermediary names, so the hook must name the sound library and its methods that way.
                String hook = new String(read(zip, "dev/urinth/recordable/mixin/LibraryMixin.class"), StandardCharsets.ISO_8859_1);
                assertTrue(hook.contains("Lnet/minecraft/class_"), "the hook targets the game's sound library by its runtime name");
                assertTrue(hook.contains("method_"), "the hook names the game's methods by their runtime names");
                assertTrue(hook.contains("Lorg/lwjgl/openal/ALC10;alcCreateContext"), "the hook finds the OpenAL context call (an LWJGL name, not remapped)");
                assertFalse(hook.contains("com/mojang/blaze3d/audio/Library"), "no development name of the sound library is left in the hook");
                assertFalse(hook.contains("openDeviceOrFallback"), "no development name of the sound library's methods is left in the hook");
            }

            String lang = new String(read(zip, "assets/recordable_lite/lang/en_us.json"), StandardCharsets.UTF_8);
            for (String key : new String[]{"key.categories.recordable_lite", "key.recordable_lite.toggle", "key.recordable_lite.pause",
                    "key.recordable_lite.settings"}) {
                assertTrue(lang.contains("\"" + key + "\""), "the key text for " + key);
            }

            Enumeration<? extends ZipEntry> all = zip.entries();
            int classes = 0;
            while (all.hasMoreElements()) {
                ZipEntry e = all.nextElement();
                if (e.getName().endsWith(".class")) {
                    classes++;
                    byte[] c = read(zip, e.getName());
                    assertEquals(0xCAFEBABEL, le(new byte[]{c[3], c[2], c[1], c[0]}, 0, 4), "class file magic of " + e.getName());
                    int major = ((c[6] & 0xFF) << 8) | (c[7] & 0xFF);
                    assertTrue(major <= 65, e.getName() + " needs a newer Java than 21 (class version " + major + ")");
                }
            }
            assertTrue(classes >= 20, "class count " + classes);

            // The game runs with intermediary names; a jar that still has the readable Minecraft names fails with NoClassDefFoundError.
            if (!Boolean.getBoolean("urinth.unremapped")) {
                byte[] main = read(zip, "dev/urinth/recordable/RecordableLite.class");
                assertTrue(contains(main, "net/minecraft/class_"), "RecordableLite refers to Minecraft by intermediary names");
                assertFalse(contains(main, "net/minecraft/client/Minecraft"), "no readable Minecraft class names are left");
            }

            checkNative(read(zip, "natives/linux-aarch64/libreclite.so"), 183, true);
            checkNative(read(zip, "natives/linux-x86_64/libreclite.so"), 62, false);
        }
    }

    /** A 64-bit little-endian ELF library for the expected processor, with no dependencies of its own. */
    private static void checkNative(byte[] elf, int machine, boolean sixteenKbPages) {
        assertTrue(elf.length > 64, "elf size");
        assertEquals(0x7F, elf[0] & 0xFF);
        assertEquals('E', elf[1]);
        assertEquals('L', elf[2]);
        assertEquals('F', elf[3]);
        assertEquals(2, elf[4], "64-bit");
        assertEquals(1, elf[5], "little endian");
        assertEquals(3, le(elf, 16, 2), "shared object");
        assertEquals(machine, le(elf, 18, 2), "processor");
        long phoff = le(elf, 32, 8);
        int phentsize = (int) le(elf, 54, 2);
        int phnum = (int) le(elf, 56, 2);
        assertTrue(phnum > 0 && phoff > 0, "has program headers");
        for (int i = 0; i < phnum; i++) {
            int h = (int) (phoff + (long) i * phentsize);
            long type = le(elf, h, 4);
            assertTrue(type != 3, "no interpreter");
            if (type == 1 && sixteenKbPages) {
                assertTrue(le(elf, h + 48, 8) >= 16384, "load segment " + i + " is aligned for 16 KB pages");
            }
            if (type == 2) {
                int dyn = (int) le(elf, h + 8, 8);
                int end = dyn + (int) le(elf, h + 32, 8);
                for (int p = dyn; p + 16 <= end; p += 16) {
                    long tag = le(elf, p, 8);
                    if (tag == 0) {
                        break;
                    }
                    assertTrue(tag != 1, "the helper must not depend on other libraries");
                }
            }
        }
    }
}
