package net.kdt.pojavlaunch.downloads.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import net.kdt.pojavlaunch.downloads.IconMath;
import net.kdt.pojavlaunch.downloads.ModrinthClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads project icons from the web into image views, with a memory cache and a disk cache.
 * Icons are fetched off the main thread; a view that was given another address in the meantime
 * (because its row was reused) never shows the older icon.
 */
public final class IconLoader {
    private IconLoader() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(
            Math.max(2 * 1024 * 1024, (int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 16))) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    /**
     * Shows the icon of {@code url} in {@code view}, rounded by {@code cornerDp}. {@code placeholder}
     * stays until the picture is there (and stays if it cannot be loaded).
     */
    public static void load(final ImageView view, final String url, final int sizePx, final float cornerDp, Drawable placeholder) {
        final String wanted = url == null ? "" : url;
        view.setTag(wanted);
        view.setImageDrawable(placeholder);
        if (!IconMath.isWebAddress(wanted)) return;
        final Context context = view.getContext().getApplicationContext();
        final String key = wanted + "#" + sizePx;
        Bitmap cached = MEMORY.get(key);
        if (cached != null) {
            show(view, wanted, cached, cornerDp);
            return;
        }
        POOL.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = fetch(context, wanted, sizePx);
            } catch (IOException | RuntimeException | OutOfMemoryError ignored) {
                // The placeholder stays.
            }
            if (bitmap == null) return;
            MEMORY.put(key, bitmap);
            final Bitmap result = bitmap;
            MAIN.post(() -> show(view, wanted, result, cornerDp));
        });
    }

    private static void show(ImageView view, String wanted, Bitmap bitmap, float cornerDp) {
        if (!wanted.equals(view.getTag())) return; // the row now shows something else
        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(view.getResources(), bitmap);
        drawable.setCornerRadius(Mr.dp(view.getContext(), cornerDp));
        drawable.setAntiAlias(true);
        view.setImageDrawable(drawable);
    }

    private static Bitmap fetch(Context context, String url, int sizePx) throws IOException {
        File folder = new File(context.getCacheDir(), "modrinth_icons");
        File file = new File(folder, IconMath.cacheName(url));
        byte[] data;
        if (file.isFile() && file.length() > 0) {
            data = readAll(new FileInputStream(file), IconMath.MAX_CACHED_BYTES);
        } else {
            HttpURLConnection connection = ModrinthClient.open(url);
            try {
                connection.setRequestProperty("Accept", "image/*");
                if (connection.getResponseCode() / 100 != 2) return null;
                data = readAll(connection.getInputStream(), 8L * 1024 * 1024);
            } finally {
                connection.disconnect();
            }
            if (data.length <= IconMath.MAX_CACHED_BYTES && (folder.isDirectory() || folder.mkdirs())) {
                File temporary = new File(folder, file.getName() + ".part");
                try (FileOutputStream out = new FileOutputStream(temporary)) {
                    out.write(data);
                }
                if (!temporary.renameTo(file)) temporary.delete();
            }
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = IconMath.sampleSize(bounds.outWidth, bounds.outHeight, sizePx);
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (bitmap == null) file.delete(); // a damaged cache file must not stay
        return bitmap;
    }

    private static byte[] readAll(InputStream in, long limit) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            long total = 0;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > limit) throw new IOException("Icon too large");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }
}
