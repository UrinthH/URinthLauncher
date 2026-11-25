package dev.urinth.recordable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks the folder the videos go to. The best place on a phone is the Movies folder of the shared storage, because the gallery and video
 * editors such as CapCut look there; a file in the game's own folder is out of their reach. Every candidate is tested with a real file
 * before it is used, and the game's own "recordings" folder is the last resort, so a recording never fails because of the folder.
 */
final class OutputFolder {
    static final String NAME = "Record-able";
    private static final String[] ROOTS = {"/storage/emulated/0", "/sdcard", "/storage/self/primary"};
    private static final String[] MEDIA_FOLDERS = {"Movies", "DCIM", "Download"};

    private OutputFolder() {
    }

    /** Places in the phone's shared storage that could hold the videos, best first. Empty when this is no phone. */
    static List<Path> galleryCandidates() {
        List<Path> list = new ArrayList<>();
        for (String media : MEDIA_FOLDERS) {
            for (String root : ROOTS) {
                Path r = Path.of(root);
                if (Files.isDirectory(r)) {
                    list.add(r.resolve(media).resolve(NAME));
                }
            }
        }
        return list;
    }

    /**
     * @param gallery whether the shared storage should be tried first
     * @param candidates the shared storage folders to try, in order
     * @param notes receives one line for every folder that could not be used
     * @return a folder that exists and accepts files, or null if even the game's own folder does not
     */
    static Path choose(boolean gallery, Path gameDir, List<Path> candidates, List<String> notes) {
        if (gallery) {
            for (Path c : candidates) {
                String problem = tryUse(c);
                if (problem == null) {
                    return c;
                }
                notes.add("cannot save to " + c + " (" + problem + ")");
            }
        }
        Path local = gameDir.resolve("recordings");
        String problem = tryUse(local);
        if (problem != null) {
            notes.add("cannot save to " + local + " (" + problem + ")");
            return null;
        }
        return local;
    }

    /** True if the folder is in the phone's shared storage, where the gallery can see it. */
    static boolean isShared(Path dir) {
        String p = dir.toAbsolutePath().toString();
        for (String root : ROOTS) {
            if (p.startsWith(root + "/")) {
                return true;
            }
        }
        return false;
    }

    /** Creates the folder if needed and writes and deletes a small test file. Returns null on success, else the reason. */
    static String tryUse(Path dir) {
        try {
            Files.createDirectories(dir);
            Path probe = dir.resolve(".record-able-test.mp4");
            Files.write(probe, new byte[]{0});
            Files.deleteIfExists(probe);
            return null;
        } catch (IOException | RuntimeException e) {
            String m = e.getMessage();
            return e.getClass().getSimpleName() + (m == null || m.isEmpty() ? "" : ": " + m);
        }
    }
}
