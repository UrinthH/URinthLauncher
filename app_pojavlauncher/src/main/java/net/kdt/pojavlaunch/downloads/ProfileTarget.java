package net.kdt.pojavlaunch.downloads;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Minecraft version and mod loader of a launcher profile, worked out from its version id
 * (for example {@code fabric-loader-0.17.3-1.21.4} is Fabric on 1.21.4).
 */
public final class ProfileTarget {
    /** Minecraft version such as "1.21.4" or "24w10a", or "" when it cannot be told. */
    public final String gameVersion;
    /** "fabric", "quilt", "forge", "neoforge", "optifine", or "" for plain Minecraft. */
    public final String loader;

    private static final Pattern RELEASE = Pattern.compile("(?<![\\d.])1\\.\\d{1,2}(?:\\.\\d{1,2})?(?![\\d.])");
    private static final Pattern SNAPSHOT = Pattern.compile("(?<![\\w])\\d{2}w\\d{2}[a-z](?![\\w])");
    private static final Pattern NEOFORGE = Pattern.compile("neoforge-(\\d{1,2})\\.(\\d{1,2})(?:\\.\\d+)?.*");

    public ProfileTarget(String gameVersion, String loader) {
        this.gameVersion = gameVersion == null ? "" : gameVersion;
        this.loader = loader == null ? "" : loader;
    }

    /**
     * @param versionId   the profile's version id
     * @param inheritsFrom the "inheritsFrom" entry of that version's JSON (the plain Minecraft
     *                    version it builds on), or null when it is not known
     */
    public static ProfileTarget parse(String versionId, String inheritsFrom) {
        String id = versionId == null ? "" : versionId.trim();
        String lower = id.toLowerCase(java.util.Locale.ROOT);
        String loader = "";
        if (lower.startsWith("fabric-loader-") || lower.contains("fabric")) loader = "fabric";
        else if (lower.startsWith("quilt-loader-") || lower.contains("quilt")) loader = "quilt";
        else if (lower.contains("neoforge")) loader = "neoforge";
        else if (lower.contains("forge")) loader = "forge";
        else if (lower.contains("optifine")) loader = "optifine";

        String game = "";
        if (inheritsFrom != null && !inheritsFrom.trim().isEmpty()) {
            game = inheritsFrom.trim();
        } else if ("fabric".equals(loader) || "quilt".equals(loader)) {
            int dash = id.lastIndexOf('-');
            String last = dash >= 0 ? id.substring(dash + 1) : id;
            if (RELEASE.matcher(last).matches() || SNAPSHOT.matcher(last).matches()) game = last;
        }
        if (game.isEmpty() && "neoforge".equals(loader)) {
            Matcher neo = NEOFORGE.matcher(lower);
            if (neo.matches()) {
                String minor = neo.group(2);
                game = "0".equals(minor) ? "1." + neo.group(1) : "1." + neo.group(1) + "." + minor;
            }
        }
        if (game.isEmpty()) {
            Matcher release = RELEASE.matcher(id);
            if (release.find()) game = release.group();
        }
        if (game.isEmpty()) {
            Matcher snapshot = SNAPSHOT.matcher(id);
            if (snapshot.find()) game = snapshot.group();
        }
        return new ProfileTarget(game, loader);
    }

    /** Loader names to ask Modrinth for: Quilt also runs Fabric mods. Empty for plain Minecraft. */
    public String[] modLoaders() {
        switch (loader) {
            case "fabric": return new String[]{"fabric"};
            case "quilt": return new String[]{"quilt", "fabric"};
            case "forge": return new String[]{"forge"};
            case "neoforge": return new String[]{"neoforge"};
            default: return new String[0];
        }
    }

    public boolean canRunMods() {
        return modLoaders().length > 0;
    }

    public String describe() {
        String name = loader.isEmpty() ? "Minecraft" : Formatters.tagLabel(loader);
        return gameVersion.isEmpty() ? name : name + " " + gameVersion;
    }
}
