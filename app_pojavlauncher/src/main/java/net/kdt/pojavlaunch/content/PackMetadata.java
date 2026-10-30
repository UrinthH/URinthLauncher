package net.kdt.pojavlaunch.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The description a resource pack gives in its pack.mcmeta, as plain text. */
public final class PackMetadata {
    private PackMetadata() {}

    public static String description(String mcmeta) {
        if (mcmeta == null) return "";
        try {
            JsonElement root = JsonParser.parseString(mcmeta.startsWith("﻿") ? mcmeta.substring(1) : mcmeta);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("pack")) return "";
            JsonElement pack = root.getAsJsonObject().get("pack");
            if (!pack.isJsonObject() || !pack.getAsJsonObject().has("description")) return "";
            StringBuilder text = new StringBuilder();
            collect(pack.getAsJsonObject().get("description"), text, 0);
            return clean(text.toString());
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Text components: a string, an object with "text" and "extra", or a list of those. */
    private static void collect(JsonElement element, StringBuilder out, int depth) {
        if (element == null || depth > 8) return;
        if (element.isJsonPrimitive()) {
            out.append(element.getAsString());
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement part : array) collect(part, out, depth + 1);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("text")) collect(object.get("text"), out, depth + 1);
            if (object.has("extra")) collect(object.get("extra"), out, depth + 1);
        }
    }

    /** Drops the section-sign colour codes ("§a") and tidies the white space. */
    static String clean(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                i++; // the code letter after it
                continue;
            }
            out.append(c == '\n' || c == '\r' ? ' ' : c);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }
}
