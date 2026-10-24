package net.kdt.pojavlaunch.home;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Short text for an installation on the home screen. */
public final class InstallationLabels {
    private static final Pattern NUMBER_GROUP = Pattern.compile("\\d+(?:\\.\\d+){1,2}");

    private InstallationLabels() {}

    /**
     * Second line for an installation in a list: the Minecraft version, plus the full version id
     * when that says more ("1.21.4 - fabric-loader-0.17.3-1.21.4"). Null when there is no id.
     */
    public static String subtitle(String versionId) {
        String minecraft = minecraftVersion(versionId);
        if (minecraft == null) return null;
        String id = versionId.trim();
        return minecraft.equals(id) ? id : minecraft + " \u00b7 " + id;
    }

    /** The name to show for an installation: its name, else its version id, else a fixed fallback. */
    public static String displayName(String name, String versionId, String fallback) {
        if (name != null && !name.trim().isEmpty()) return name.trim();
        if (versionId != null && !versionId.trim().isEmpty()) return versionId.trim();
        return fallback;
    }

    /**
     * The Minecraft version inside a version id, so the play button can say which one it will
     * start: "fabric-loader-0.17.3-1.21.4" gives "1.21.4", "1.20.1-forge-47.2.0" gives "1.20.1",
     * "1.21.4-OptiFine_HD_U_J1" gives "1.21.4". When there is no such number (a snapshot such as
     * "24w36a", or NeoForge's "neoforge-21.1.5"), the whole version id is returned instead, so the
     * player always sees something that identifies the version. Null only when there is no id.
     */
    public static String minecraftVersion(String versionId) {
        if (versionId == null || versionId.trim().isEmpty()) return null;
        String id = versionId.trim();
        Matcher matcher = NUMBER_GROUP.matcher(id);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (candidate.startsWith("1.")) return candidate;
        }
        return id;
    }
}
