package net.kdt.pojavlaunch.content;

import java.io.File;
import java.io.IOException;

/** Switching a file on or off and deleting it, never outside the folder it lives in. */
public final class ContentActions {
    private ContentActions() {}

    /**
     * Switches a single file on or off by adding or removing ".disabled" on its name. The game
     * only loads files with their real ending, so a renamed file is left out of the game and
     * stays on the phone. Returns the file under its new name.
     */
    public static File setEnabled(ContentItem item, boolean enabled) throws IOException {
        if (!item.canSwitch()) throw new IOException("A folder cannot be switched off. Delete it instead.");
        if (item.enabled == enabled) return item.file;
        File parent = item.file.getParentFile();
        String name = item.file.getName();
        File target = new File(parent, enabled ? ContentKind.activeName(name) : name + ContentKind.DISABLED_SUFFIX);
        if (target.exists()) throw new IOException(target.getName() + " already exists.");
        if (!item.file.renameTo(target)) throw new IOException("Could not rename " + name);
        return target;
    }

    /** Deletes the file, or the folder with everything in it. Refuses anything outside {@code folder}. */
    public static void delete(File folder, ContentItem item) throws IOException {
        File parent = item.file.getCanonicalFile().getParentFile();
        if (parent == null || !parent.equals(folder.getCanonicalFile())) {
            throw new IOException("That file is not in " + folder.getName());
        }
        // A symbolic link is removed as a link; what it points at is never entered.
        if (isLink(item.file)) {
            if (!item.file.delete()) throw new IOException("Could not delete " + item.file.getName());
            return;
        }
        deleteTree(item.file);
    }

    private static void deleteTree(File file) throws IOException {
        if (file.isDirectory() && !isLink(file)) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        if (!file.delete() && file.exists()) throw new IOException("Could not delete " + file.getName());
    }

    private static boolean isLink(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent == null) return false;
        return !file.getCanonicalFile().equals(new File(parent.getCanonicalFile(), file.getName()));
    }
}
