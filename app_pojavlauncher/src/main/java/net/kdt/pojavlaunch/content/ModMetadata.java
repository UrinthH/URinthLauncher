package net.kdt.pojavlaunch.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/** Reads a mod's name, version, description and icon path from the files mods carry inside their jar. */
public final class ModMetadata {
    public String name = "", version = "", description = "", iconPath = "";

    private ModMetadata() {}

    /** fabric.mod.json */
    public static ModMetadata fromFabric(String json) {
        ModMetadata meta = new ModMetadata();
        JsonObject root = object(json);
        if (root == null) return meta;
        meta.name = string(root, "name");
        if (meta.name.isEmpty()) meta.name = string(root, "id");
        meta.version = string(root, "version");
        meta.description = string(root, "description");
        meta.iconPath = icon(root.get("icon"));
        return meta;
    }

    /** quilt.mod.json */
    public static ModMetadata fromQuilt(String json) {
        ModMetadata meta = new ModMetadata();
        JsonObject root = object(json);
        if (root == null || !root.has("quilt_loader") || !root.get("quilt_loader").isJsonObject()) return meta;
        JsonObject loader = root.getAsJsonObject("quilt_loader");
        meta.version = string(loader, "version");
        if (loader.has("metadata") && loader.get("metadata").isJsonObject()) {
            JsonObject metadata = loader.getAsJsonObject("metadata");
            meta.name = string(metadata, "name");
            meta.description = string(metadata, "description");
            meta.iconPath = icon(metadata.get("icon"));
        }
        if (meta.name.isEmpty()) meta.name = string(loader, "id");
        return meta;
    }

    /** The first [[mods]] block of META-INF/mods.toml or neoforge.mods.toml. */
    public static ModMetadata fromToml(String toml) {
        ModMetadata meta = new ModMetadata();
        if (toml == null) return meta;
        String[] lines = toml.replace("\r", "").split("\n");
        boolean inMods = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("[")) {
                if (inMods) break; // the next section starts: the mod's own block is over
                inMods = line.equals("[[mods]]");
                continue;
            }
            if (!inMods || line.isEmpty() || line.startsWith("#")) continue;
            int equals = line.indexOf('=');
            if (equals < 0) continue;
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            if (value.startsWith("'''") || value.startsWith("\"\"\"")) {
                String quote = value.substring(0, 3);
                StringBuilder text = new StringBuilder(value.substring(3));
                while (text.indexOf(quote) < 0 && i + 1 < lines.length) text.append('\n').append(lines[++i]);
                int end = text.indexOf(quote);
                value = (end >= 0 ? text.substring(0, end) : text.toString()).trim();
            } else {
                value = unquote(value);
            }
            switch (key) {
                case "displayName": meta.name = value; break;
                case "modId": if (meta.name.isEmpty()) meta.name = value; break;
                case "version": if (!value.contains("${")) meta.version = value; break;
                case "description": meta.description = value; break;
                case "logoFile": meta.iconPath = value; break;
                default: break;
            }
        }
        return meta;
    }

    /** mcmod.info of older Forge mods: a list, or an object with "modList". */
    public static ModMetadata fromMcmodInfo(String json) {
        ModMetadata meta = new ModMetadata();
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray list = null;
            if (root.isJsonArray()) list = root.getAsJsonArray();
            else if (root.isJsonObject() && root.getAsJsonObject().has("modList") && root.getAsJsonObject().get("modList").isJsonArray())
                list = root.getAsJsonObject().getAsJsonArray("modList");
            if (list == null || list.size() == 0 || !list.get(0).isJsonObject()) return meta;
            JsonObject first = list.get(0).getAsJsonObject();
            meta.name = string(first, "name");
            if (meta.name.isEmpty()) meta.name = string(first, "modid");
            meta.version = string(first, "version");
            meta.description = string(first, "description");
            meta.iconPath = string(first, "logoFile");
        } catch (RuntimeException e) {
            // not readable: leave it empty
        }
        return meta;
    }

    public boolean isEmpty() {
        return name.isEmpty() && version.isEmpty() && description.isEmpty() && iconPath.isEmpty();
    }

    private static JsonObject object(String json) {
        if (json == null) return null;
        try {
            // Mods sometimes ship a byte order mark or a trailing comma: gson is lenient about the second.
            JsonElement root = JsonParser.parseString(json.startsWith("﻿") ? json.substring(1) : json);
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return "";
        return element.getAsString().trim();
    }

    /** "icon" is a path, or an object of size to path; the biggest size wins. */
    private static String icon(JsonElement element) {
        if (element == null) return "";
        if (element.isJsonPrimitive()) return element.getAsString().trim();
        if (!element.isJsonObject()) return "";
        String best = "";
        int bestSize = -1;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            int size;
            try {
                size = Integer.parseInt(entry.getKey().trim());
            } catch (NumberFormatException e) {
                size = 0;
            }
            if (size > bestSize) {
                bestSize = size;
                best = entry.getValue().getAsString().trim();
            }
        }
        return best;
    }

    private static String unquote(String value) {
        int comment = value.startsWith("\"") || value.startsWith("'") ? -1 : value.indexOf('#');
        if (comment >= 0) value = value.substring(0, comment).trim();
        if (value.length() >= 2) {
            char first = value.charAt(0), last = value.charAt(value.length() - 1);
            if ((first == '"' || first == '\'') && last == first) return value.substring(1, value.length() - 1);
            // a comment after a quoted value
            if (first == '"' || first == '\'') {
                int close = value.indexOf(first, 1);
                if (close > 0) return value.substring(1, close);
            }
        }
        return value;
    }
}
