package net.kdt.pojavlaunch.content;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Opens a mod, resource pack or shader and reads what it says about itself and its own icon.
 * Blocking, and a little heavy for a big jar: call it off the main thread.
 */
public final class ContentReader {
    static final long MAX_TEXT_BYTES = 512 * 1024;
    static final long MAX_ICON_BYTES = 1024 * 1024;

    private ContentReader() {}

    public static ContentInfo read(File file, ContentKind kind) {
        ContentInfo info = new ContentInfo();
        info.name = displayNameFromFile(file);
        try {
            if (file.isDirectory()) readFolder(file, kind, info);
            else try (ZipFile zip = new ZipFile(file)) {
                if (kind == ContentKind.MODS) readMod(zip, info);
                else readPack(new ZipSource(zip), kind, info);
            }
        } catch (IOException | RuntimeException e) {
            info.unreadable = true;
        }
        return info;
    }

    /** "Sodium-0.5.8+mc1.20.4.jar.disabled" becomes "Sodium-0.5.8+mc1.20.4". */
    public static String displayNameFromFile(File file) {
        String name = ContentKind.activeName(file.getName());
        int dot = name.lastIndexOf('.');
        if (dot > 0 && !file.isDirectory()) name = name.substring(0, dot);
        return name;
    }

    // ---- mods -------------------------------------------------------------------------------------

    private static void readMod(ZipFile zip, ContentInfo info) throws IOException {
        ModMetadata meta = null;
        String text = text(zip, "fabric.mod.json");
        if (text != null) meta = ModMetadata.fromFabric(text);
        if (meta == null || meta.isEmpty()) {
            text = text(zip, "quilt.mod.json");
            if (text != null) meta = ModMetadata.fromQuilt(text);
        }
        if (meta == null || meta.isEmpty()) {
            text = text(zip, "META-INF/neoforge.mods.toml");
            if (text == null) text = text(zip, "META-INF/mods.toml");
            if (text != null) meta = ModMetadata.fromToml(text);
        }
        if (meta == null || meta.isEmpty()) {
            text = text(zip, "mcmod.info");
            if (text != null) meta = ModMetadata.fromMcmodInfo(text);
        }
        if (meta == null) return;
        if (!meta.name.isEmpty()) info.name = meta.name;
        info.version = meta.version;
        info.description = meta.description;
        if (!meta.iconPath.isEmpty()) info.icon = bytes(zip, meta.iconPath, MAX_ICON_BYTES);
    }

    // ---- packs ------------------------------------------------------------------------------------

    /** Where the pack's files come from: a zip or a folder. */
    private interface Source {
        byte[] get(String path, long limit) throws IOException;
    }

    private static final class ZipSource implements Source {
        private final ZipFile zip;

        ZipSource(ZipFile zip) {
            this.zip = zip;
        }

        @Override
        public byte[] get(String path, long limit) throws IOException {
            return bytes(zip, path, limit);
        }
    }

    private static void readFolder(File folder, ContentKind kind, ContentInfo info) throws IOException {
        readPack((path, limit) -> {
            File file = new File(folder, path);
            if (!file.isFile() || file.length() > limit) return null;
            try (InputStream in = new FileInputStream(file)) {
                return readAll(in, limit);
            }
        }, kind, info);
    }

    private static void readPack(Source source, ContentKind kind, ContentInfo info) throws IOException {
        if (kind == ContentKind.RESOURCE_PACKS) {
            byte[] mcmeta = source.get("pack.mcmeta", MAX_TEXT_BYTES);
            if (mcmeta != null) info.description = PackMetadata.description(new String(mcmeta, StandardCharsets.UTF_8));
        }
        info.icon = source.get("pack.png", MAX_ICON_BYTES);
    }

    // ---- zip helpers ------------------------------------------------------------------------------

    private static String text(ZipFile zip, String path) throws IOException {
        byte[] data = bytes(zip, path, MAX_TEXT_BYTES);
        return data == null ? null : new String(data, StandardCharsets.UTF_8);
    }

    /** The bytes of an entry, or null when it is missing, a folder, or bigger than {@code limit}. */
    static byte[] bytes(ZipFile zip, String path, long limit) throws IOException {
        String clean = path.replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        ZipEntry entry = zip.getEntry(clean);
        if (entry == null || entry.isDirectory()) return null;
        if (entry.getSize() > limit) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            return readAll(in, limit);
        }
    }

    private static byte[] readAll(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        long total = 0;
        while ((count = in.read(buffer)) != -1) {
            total += count;
            if (total > limit) return null; // an entry that lies about its size is dropped
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
}
