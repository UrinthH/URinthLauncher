package net.kdt.pojavlaunch.downloads;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** The arithmetic behind the project icons: file names in the disk cache and how far to shrink. */
public final class IconMath {
    private IconMath() {}

    /** Largest icon file kept; anything bigger is shown from memory only, never written. */
    public static final long MAX_CACHED_BYTES = 2L * 1024 * 1024;

    /** Name of the disk cache file for an icon address: the SHA-1 of the address, so any address is safe. */
    public static String cacheName(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(url.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // SHA-1 and UTF-8 always exist
        }
    }

    /**
     * Power of two to shrink an image by while decoding, so the result is still at least
     * {@code targetPx} on its short side (sharp enough for the box it is shown in).
     */
    public static int sampleSize(int width, int height, int targetPx) {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1;
        int shortSide = Math.min(width, height);
        int sample = 1;
        while (shortSide / (sample * 2) >= targetPx) sample *= 2;
        return sample;
    }

    /** Only web addresses are fetched; anything else (file:, content:) is refused. */
    public static boolean isWebAddress(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }
}
