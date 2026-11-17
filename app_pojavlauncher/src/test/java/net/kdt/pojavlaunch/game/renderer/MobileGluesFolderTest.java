package net.kdt.pojavlaunch.game.renderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class MobileGluesFolderTest {
    private static File freshDir() throws IOException {
        File dir = Files.createTempDirectory("mgfolder").toFile();
        dir.deleteOnExit();
        return dir;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** A path that can never become a folder: its parent is a plain file. */
    private static File impossible() throws IOException {
        File parent = freshDir();
        File plain = new File(parent, "plain");
        assertTrue(plain.createNewFile());
        return new File(plain, "mobileglues");
    }

    @Test
    public void theFirstUsableCandidateIsMadeAndReturned() throws IOException {
        File wanted = new File(freshDir(), "a/b/mobileglues");
        File chosen = MobileGluesFolder.pick(wanted, freshDir());
        assertEquals(wanted, chosen);
        assertTrue(chosen.isDirectory());
    }

    @Test
    public void unusableAndMissingCandidatesAreSkipped() throws IOException {
        File good = new File(freshDir(), "mobileglues");
        assertEquals(good, MobileGluesFolder.pick((File) null, impossible(), good));
    }

    @Test
    public void whenNoCandidateWorksTheAnswerIsNull() throws IOException {
        assertNull(MobileGluesFolder.pick());
        assertNull(MobileGluesFolder.pick((File) null, impossible()));
    }

    @Test
    public void theConfigIsWrittenAndReplacedWithoutLeavingATemporaryFile() throws IOException {
        File dir = freshDir();
        File written = MobileGluesFolder.writeConfig(dir, "{\"a\": 1}\n");
        assertEquals("config.json", written.getName());
        assertEquals("{\"a\": 1}\n", read(written));
        MobileGluesFolder.writeConfig(dir, "{\"b\": 2}\n");
        assertEquals("{\"b\": 2}\n", read(written));
        assertFalse(new File(dir, "config.json.tmp").exists());
    }

    @Test
    public void aFolderThatCannotBeWrittenIntoIsAnErrorTheCallerHandles() throws IOException {
        boolean thrown = false;
        try {
            MobileGluesFolder.writeConfig(new File(freshDir(), "missing"), "{}");
        } catch (IOException expected) {
            thrown = true;
        }
        assertTrue(thrown);
    }
}
