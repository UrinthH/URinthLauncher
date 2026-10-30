package net.kdt.pojavlaunch.content.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import net.kdt.pojavlaunch.content.ContentInfo;
import net.kdt.pojavlaunch.content.ContentItem;
import net.kdt.pojavlaunch.content.ContentKind;
import net.kdt.pojavlaunch.content.ContentReader;
import net.kdt.pojavlaunch.downloads.FileInstaller;
import net.kdt.pojavlaunch.downloads.IconMath;
import net.kdt.pojavlaunch.downloads.ModrinthClient;
import net.kdt.pojavlaunch.downloads.ProjectVersion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads each file's own name, version, description and icon (off the main thread, two at a time,
 * remembered while the launcher runs). For mods and packs that carry no icon of their own, the
 * file's SHA-1 is looked up at Modrinth, which names the project, and the project's icon is used.
 */
final class ContentLoader {
    interface Callback {
        void loaded(ContentInfo info, Bitmap icon);
    }

    /** Lets a screen stop the background work it started when it is closed. */
    static final class Cancel {
        volatile boolean cancelled;
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final ExecutorService ONLINE = Executors.newSingleThreadExecutor();
    private static final int ICON_PX = 128;
    private static final int MAX_INFO = 600;

    private static final Map<String, ContentInfo> INFO = Collections.synchronizedMap(new LinkedHashMap<String, ContentInfo>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ContentInfo> eldest) {
            return size() > MAX_INFO;
        }
    });
    private static final LruCache<String, Bitmap> ICONS = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    /** cache key of a file to the address of its project's icon at Modrinth. */
    private static final Map<String, String> REMOTE_ICONS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> REMOTE_CHECKED = new ConcurrentHashMap<>();

    private ContentLoader() {}

    static String remoteIcon(ContentItem item) {
        return REMOTE_ICONS.get(item.cacheKey());
    }

    /** The name written inside the file, if it has been read already; "" otherwise. */
    static String cachedName(ContentItem item) {
        ContentInfo info = INFO.get(item.cacheKey());
        return info == null ? "" : info.name;
    }

    static Bitmap cachedIcon(ContentItem item) {
        return ICONS.get(item.cacheKey());
    }

    /** Answers on the main thread, at once when the file was read before. */
    static void load(final ContentItem item, final ContentKind kind, final Callback callback) {
        final String key = item.cacheKey();
        ContentInfo known = INFO.get(key);
        if (known != null) {
            callback.loaded(known, ICONS.get(key));
            return;
        }
        POOL.execute(() -> {
            final ContentInfo info = read(item, kind);
            final Bitmap icon = ICONS.get(key);
            MAIN.post(() -> callback.loaded(info, icon));
        });
    }

    private static ContentInfo read(ContentItem item, ContentKind kind) {
        String key = item.cacheKey();
        ContentInfo cached = INFO.get(key);
        if (cached != null) return cached;
        ContentInfo info = ContentReader.read(item.file, kind);
        if (info.hasIcon()) {
            Bitmap bitmap = decode(info.icon);
            if (bitmap != null) ICONS.put(key, bitmap);
            info.icon = null; // the bytes are not kept, the small picture is
        }
        INFO.put(key, info);
        return info;
    }

    private static Bitmap decode(byte[] data) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = IconMath.sampleSize(bounds.outWidth, bounds.outHeight, ICON_PX);
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * For files without an icon of their own, asks Modrinth who they are (by SHA-1) and remembers
     * the project's icon address. {@code changed} runs on the main thread whenever new icons were found.
     */
    static void findOnlineIcons(final List<ContentItem> items, final ContentKind kind, final Cancel cancel, final Runnable changed) {
        ONLINE.execute(() -> {
            try {
                Map<String, ContentItem> byHash = new HashMap<>();
                for (ContentItem item : items) {
                    if (cancel.cancelled) return;
                    if (item.directory || REMOTE_CHECKED.containsKey(item.cacheKey())) continue;
                    read(item, kind);
                    if (ICONS.get(item.cacheKey()) != null) continue;
                    try {
                        byHash.put(FileInstaller.sha1(item.file).toLowerCase(Locale.ROOT), item);
                    } catch (IOException unreadable) {
                        // this file cannot be read; the others still can
                    }
                }
                if (byHash.isEmpty()) return;
                ModrinthClient client = new ModrinthClient();
                List<String> hashes = new ArrayList<>(byHash.keySet());
                Map<String, String> iconByProject = new HashMap<>();
                for (int from = 0; from < hashes.size(); from += 50) {
                    if (cancel.cancelled) return;
                    List<String> chunk = hashes.subList(from, Math.min(hashes.size(), from + 50));
                    Map<String, ProjectVersion> known = client.versionsByHashes(chunk);
                    boolean any = false;
                    for (String hash : chunk) {
                        ContentItem item = byHash.get(hash);
                        if (item == null) continue;
                        REMOTE_CHECKED.put(item.cacheKey(), Boolean.TRUE);
                        ProjectVersion version = known.get(hash);
                        if (version == null || version.projectId.isEmpty()) continue;
                        String icon = iconByProject.get(version.projectId);
                        if (icon == null) {
                            try {
                                icon = client.project(version.projectId).iconUrl;
                            } catch (IOException e) {
                                icon = ""; // this one project could not be read; the others go on
                            }
                            iconByProject.put(version.projectId, icon == null ? "" : icon);
                        }
                        if (icon != null && !icon.isEmpty()) {
                            REMOTE_ICONS.put(item.cacheKey(), icon);
                            any = true;
                        }
                    }
                    if (any) MAIN.post(changed);
                }
            } catch (IOException | RuntimeException e) {
                // No network or Modrinth is busy: the icons simply stay as placeholders.
            }
        });
    }
}
