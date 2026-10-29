package net.kdt.pojavlaunch.downloads;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Saves a release's file into a folder. The file is first written under a temporary name and only
 * renamed once its SHA-1 matches what Modrinth published, so a cut connection or a damaged
 * download never leaves a broken file where the game would try to load it.
 */
public final class FileInstaller {
    private FileInstaller() {}

    public interface Progress {
        /** Bytes of the current file so far, and its size (0 if unknown). */
        void onBytes(long done, long total);

        boolean isCancelled();
    }

    public static final class Result {
        public final File file;
        /** True when the same file was already in the folder, so nothing was downloaded. */
        public final boolean alreadyInstalled;

        Result(File file, boolean alreadyInstalled) {
            this.file = file;
            this.alreadyInstalled = alreadyInstalled;
        }
    }

    public static Result install(ProjectVersion version, File folder, Progress progress) throws IOException {
        String name = safeName(version.filename, version.id);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not create the folder " + folder.getName());
        File destination = new File(folder, name);
        if (destination.isFile() && !version.sha1.isEmpty() && version.sha1.equalsIgnoreCase(sha1(destination))) {
            return new Result(destination, true);
        }
        File temporary = new File(folder, name + ".part");
        HttpURLConnection connection = ModrinthClient.open(version.url);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("The download of " + name + " failed (HTTP " + code + ")");
            long total = connection.getContentLengthLong() > 0 ? connection.getContentLengthLong() : version.size;
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024];
                long done = 0;
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (progress != null && progress.isCancelled()) throw new IOException("Cancelled");
                    out.write(buffer, 0, count);
                    done += count;
                    if (progress != null) progress.onBytes(done, total);
                }
                out.getFD().sync();
            }
            if (!version.sha1.isEmpty() && !version.sha1.equalsIgnoreCase(sha1(temporary))) {
                throw new IOException("The downloaded file " + name + " is damaged (its checksum does not match). Try again.");
            }
            if (destination.exists() && !destination.delete()) {
                throw new IOException("Could not replace the existing file " + name);
            }
            if (!temporary.renameTo(destination)) throw new IOException("Could not finish saving " + name);
            return new Result(destination, false);
        } finally {
            connection.disconnect();
            if (temporary.exists()) temporary.delete(); // gone after a rename, left over after a failure
        }
    }

    /** A file name with no folders in it, so a hostile name cannot write outside the target folder. */
    static String safeName(String filename, String fallbackId) {
        String name = filename == null ? "" : filename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replace("\u0000", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "download-" + (fallbackId == null || fallbackId.isEmpty() ? "file" : fallbackId.replaceAll("[^A-Za-z0-9_-]", "")) + ".jar";
        }
        return name;
    }

    static String sha1(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
