package dev.urinth.recordable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The few choices the player can make. Stored as a small JSON file in the game's config folder. The file is read with a tiny built-in
 * reader (flat object of text, whole numbers and true/false) so a damaged or hand-edited file never breaks the game: anything that does
 * not make sense is replaced by the default.
 */
public final class Settings {
    /** A named quality level. */
    public enum Preset {
        PERFORMANCE("performance", 360, 24, 2500),
        BALANCED("balanced", 480, 30, 4000),
        QUALITY("quality", 720, 30, 8000),
        ULTRA("ultra", 1080, 30, 14000);

        public final String id;
        public final int height;
        public final int fps;
        public final int kbps;

        Preset(String id, int height, int fps, int kbps) {
            this.id = id;
            this.height = height;
            this.fps = fps;
            this.kbps = kbps;
        }

        public Preset next() {
            Preset[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public static Preset byId(String id) {
            for (Preset p : values()) {
                if (p.id.equalsIgnoreCase(id)) {
                    return p;
                }
            }
            return BALANCED;
        }
    }

    public static final int[] FPS_CHOICES = Governor.TIERS;
    /** The settings file format. Files without it (version 1) are migrated on load. */
    public static final int VERSION = 2;
    /** Replay buffer lengths offered by the button; 0 means the buffer is off. */
    public static final int[] REPLAY_CHOICES = {0, 30, 60, 90, 120};
    /** File size limits offered by the button, in megabytes; 0 means no limit besides the free storage. */
    public static final int[] MAX_FILE_CHOICES = {0, 500, 1000, 2000, 4000};
    public static final int CORNERS = 4;

    public Preset preset = Preset.BALANCED;
    /** 0 = take the frame rate from the preset. */
    public int fpsCap = 0;
    /** Use the phone's video chip. Turn off only to test the fallback. */
    public boolean hardwareEncoder = true;
    public boolean showIndicator = true;
    /** Swap the two colour channels of the video. A cure for videos where blue and orange are mixed up on some phones. */
    public boolean swapChroma = false;
    /** Stop recording by itself when the storage has less room left than this many megabytes. */
    public int minFreeMb = 300;
    /** Record the game's sound (needs a restart of the game to take effect, because it changes how the game opens its audio device). */
    public boolean recordSound = true;
    /** Save into the phone's Movies folder (where the gallery and video editors look) when the phone allows it. */
    public boolean saveToGallery = true;
    /** Stop a recording by itself when its file reaches this many megabytes (0 = no limit). */
    public int maxFileMb = 0;
    /** Keep the last this many seconds of play, ready to be saved as a clip (0 = off). Costs some performance while on. */
    public int replaySeconds = 0;
    /** Save a clip by itself on death, dimension change, advancement and kill (needs the replay buffer). */
    public boolean autoClip = false;
    /** Which corner the recording indicator uses: 0 top left, 1 top right, 2 bottom left, 3 bottom right. */
    public int overlayCorner = 0;
    /** Size of the recording indicator in percent. */
    public int overlayScale = 100;
    /** Record the microphone too (the phone must allow the game to use it). */
    public boolean microphone = false;
    /** The version the file was saved with (see {@link #VERSION}). */
    public int version = VERSION;

    /** Folder for the mod's own small files (the crash marker, the unpacked native helper). */
    public static Path configDir(Path gameDirectory) {
        return gameDirectory.resolve("config").resolve("recordable_lite");
    }

    public int height() {
        return preset.height;
    }

    /** The highest frame rate wanted: the player's choice if there is one, else the preset's. */
    public int fps() {
        return fpsCap > 0 ? fpsCap : preset.fps;
    }

    /** Video bitrate in kilobits per second. More frames per second need more bits, but less than in proportion. */
    public int kbps() {
        int f = fps();
        double factor = f <= 30 ? 1.0 : 1.0 + 0.8 * Math.min(f - 30, 60) / 60.0;
        return (int) Math.round(preset.kbps * factor);
    }

    /** Next frame rate in the cycle: the preset's own, then 90, 60, 45, ... down to the lowest, then the preset's again. */
    public void cycleFps() {
        if (fpsCap == 0) {
            fpsCap = FPS_CHOICES[0];
            return;
        }
        for (int i = 0; i < FPS_CHOICES.length; i++) {
            if (FPS_CHOICES[i] == fpsCap) {
                fpsCap = i + 1 < FPS_CHOICES.length ? FPS_CHOICES[i + 1] : 0;
                return;
            }
        }
        fpsCap = 0;
    }

    public void cycleReplay() {
        replaySeconds = next(REPLAY_CHOICES, replaySeconds);
    }

    public void cycleMaxFile() {
        maxFileMb = next(MAX_FILE_CHOICES, maxFileMb);
    }

    public void cycleCorner() {
        overlayCorner = (overlayCorner + 1) % CORNERS;
    }

    public void cycleScale() {
        overlayScale = overlayScale >= 200 ? 50 : overlayScale + 25;
    }

    private static int next(int[] choices, int current) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == current) {
                return choices[(i + 1) % choices.length];
            }
        }
        return choices[0];
    }

    // ---- file ------------------------------------------------------------------------------------------------------------------

    public static Settings load(Path file) {
        Settings s = new Settings();
        try {
            if (Files.isRegularFile(file) && Files.size(file) < 64 * 1024) {
                s.apply(parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
            }
        } catch (IOException | RuntimeException e) {
            // keep the defaults
        }
        return s;
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, toJson().getBytes(StandardCharsets.UTF_8));
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public String toJson() {
        StringBuilder b = new StringBuilder("{\n");
        b.append("  \"version\": ").append(VERSION).append(",\n");
        b.append("  \"preset\": \"").append(preset.id).append("\",\n");
        b.append("  \"fpsCap\": ").append(fpsCap).append(",\n");
        b.append("  \"hardwareEncoder\": ").append(hardwareEncoder).append(",\n");
        b.append("  \"showIndicator\": ").append(showIndicator).append(",\n");
        b.append("  \"swapChroma\": ").append(swapChroma).append(",\n");
        b.append("  \"minFreeMb\": ").append(minFreeMb).append(",\n");
        b.append("  \"recordSound\": ").append(recordSound).append(",\n");
        b.append("  \"saveToGallery\": ").append(saveToGallery).append(",\n");
        b.append("  \"maxFileMb\": ").append(maxFileMb).append(",\n");
        b.append("  \"replaySeconds\": ").append(replaySeconds).append(",\n");
        b.append("  \"autoClip\": ").append(autoClip).append(",\n");
        b.append("  \"overlayCorner\": ").append(overlayCorner).append(",\n");
        b.append("  \"overlayScale\": ").append(overlayScale).append(",\n");
        b.append("  \"microphone\": ").append(microphone).append('\n');
        return b.append("}\n").toString();
    }

    void apply(Map<String, String> m) {
        version = clamp(intOf(m.get("version"), 1), 1, 1000);
        if (m.containsKey("preset")) {
            preset = Preset.byId(m.get("preset"));
        }
        fpsCap = clamp(intOf(m.get("fpsCap"), fpsCap), 0, FPS_CHOICES[0]);
        hardwareEncoder = boolOf(m.get("hardwareEncoder"), hardwareEncoder);
        showIndicator = boolOf(m.get("showIndicator"), showIndicator);
        swapChroma = boolOf(m.get("swapChroma"), swapChroma);
        minFreeMb = clamp(intOf(m.get("minFreeMb"), minFreeMb), 50, 100_000);
        recordSound = boolOf(m.get("recordSound"), recordSound);
        saveToGallery = boolOf(m.get("saveToGallery"), saveToGallery);
        maxFileMb = clamp(intOf(m.get("maxFileMb"), maxFileMb), 0, 100_000);
        replaySeconds = clamp(intOf(m.get("replaySeconds"), replaySeconds), 0, 300);
        autoClip = boolOf(m.get("autoClip"), autoClip);
        overlayCorner = clamp(intOf(m.get("overlayCorner"), overlayCorner), 0, CORNERS - 1);
        overlayScale = clamp(intOf(m.get("overlayScale"), overlayScale), 50, 200);
        microphone = boolOf(m.get("microphone"), microphone);
        if (version < VERSION) {
            // Files from the first release: a crash of the video encoder start-up may have switched the hardware encoder off for good,
            // and the start-up has been rebuilt since, so give it a fresh chance.
            hardwareEncoder = true;
            version = VERSION;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int intOf(String v, int fallback) {
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean boolOf(String v, boolean fallback) {
        if (v == null) {
            return fallback;
        }
        String t = v.trim().toLowerCase(Locale.ROOT);
        if (t.equals("true")) {
            return true;
        }
        if (t.equals("false")) {
            return false;
        }
        return fallback;
    }

    /** Reads a flat JSON object into text values. Throws on anything that is not one. */
    static Map<String, String> parse(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        int i = skip(json, 0);
        if (i >= json.length() || json.charAt(i) != '{') {
            throw new IllegalArgumentException("not an object");
        }
        i = skip(json, i + 1);
        if (i < json.length() && json.charAt(i) == '}') {
            return out;
        }
        while (i < json.length()) {
            if (json.charAt(i) != '"') {
                throw new IllegalArgumentException("key expected at " + i);
            }
            int[] end = new int[1];
            String key = string(json, i, end);
            i = skip(json, end[0]);
            if (i >= json.length() || json.charAt(i) != ':') {
                throw new IllegalArgumentException("colon expected at " + i);
            }
            i = skip(json, i + 1);
            String value;
            if (i < json.length() && json.charAt(i) == '"') {
                value = string(json, i, end);
                i = end[0];
            } else {
                int s = i;
                while (i < json.length() && ",}\n\r\t ".indexOf(json.charAt(i)) < 0) {
                    i++;
                }
                value = json.substring(s, i);
            }
            out.put(key, value);
            i = skip(json, i);
            if (i < json.length() && json.charAt(i) == ',') {
                i = skip(json, i + 1);
            } else if (i < json.length() && json.charAt(i) == '}') {
                return out;
            } else {
                throw new IllegalArgumentException("comma or end expected at " + i);
            }
        }
        throw new IllegalArgumentException("unterminated object");
    }

    private static int skip(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String string(String s, int start, int[] end) {
        StringBuilder b = new StringBuilder();
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') {
                end[0] = i;
                return b.toString();
            }
            if (c == '\\' && i < s.length()) {
                char n = s.charAt(i++);
                b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else {
                b.append(c);
            }
        }
        throw new IllegalArgumentException("unterminated string");
    }
}
