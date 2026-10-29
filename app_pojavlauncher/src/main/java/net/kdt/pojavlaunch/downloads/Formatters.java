package net.kdt.pojavlaunch.downloads;

import java.util.Locale;

/** Number, date and tag wording for the Downloads page, matching how Modrinth words them. */
public final class Formatters {
    private Formatters() {}

    /** 292390000 gives "292.39M", 37300 gives "37.3K", 999 stays "999". */
    public static String compactNumber(long value) {
        if (value < 0) value = 0;
        if (value < 1000) return Long.toString(value);
        double scaled;
        String suffix;
        if (value >= 1_000_000_000L) { scaled = value / 1e9; suffix = "B"; }
        else if (value >= 1_000_000L) { scaled = value / 1e6; suffix = "M"; }
        else { scaled = value / 1e3; suffix = "K"; }
        long hundredths = Math.round(scaled * 100.0);
        // 999_999 rounds to 1000.00K; show it as 1M instead of the odd 1000K.
        if (hundredths >= 100000 && "K".equals(suffix)) { hundredths = 100; suffix = "M"; }
        else if (hundredths >= 100000 && "M".equals(suffix)) { hundredths = 100; suffix = "B"; }
        String text = String.format(Locale.ROOT, "%d.%02d", hundredths / 100, hundredths % 100);
        if (text.endsWith(".00")) text = text.substring(0, text.length() - 3);
        else if (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text + suffix;
    }

    /**
     * Milliseconds since 1970 (UTC) for the start of an ISO 8601 time such as
     * {@code 2026-10-28T10:12:45.123456Z}, or -1 if it cannot be read. Written out by hand because
     * java.time is not available on the oldest Android versions the launcher supports.
     */
    public static long parseIsoMillis(String iso) {
        if (iso == null || iso.length() < 10) return -1;
        try {
            int year = Integer.parseInt(iso.substring(0, 4));
            int month = Integer.parseInt(iso.substring(5, 7));
            int day = Integer.parseInt(iso.substring(8, 10));
            if (iso.charAt(4) != '-' || iso.charAt(7) != '-' || month < 1 || month > 12 || day < 1 || day > 31) return -1;
            int hour = 0, minute = 0, second = 0;
            if (iso.length() >= 19 && (iso.charAt(10) == 'T' || iso.charAt(10) == ' ')) {
                hour = Integer.parseInt(iso.substring(11, 13));
                minute = Integer.parseInt(iso.substring(14, 16));
                second = Integer.parseInt(iso.substring(17, 19));
            }
            long days = daysFromCivil(year, month, day);
            return ((days * 24 + hour) * 60 + minute) * 60_000L + second * 1000L;
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return -1;
        }
    }

    /** Days since 1970-01-01 of a calendar date (Howard Hinnant's algorithm). */
    static long daysFromCivil(int year, int month, int day) {
        long y = month <= 2 ? year - 1 : year;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yearOfEra = y - era * 400;
        long monthIndex = month > 2 ? month - 3 : month + 9;
        long dayOfYear = (153 * monthIndex + 2) / 5 + day - 1;
        long dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return era * 146097 + dayOfEra - 719468;
    }

    /** "Yesterday", "3 days ago", "2 months ago" for how long before {@code nowMillis} the time was. */
    public static String relativeTime(String iso, long nowMillis) {
        long then = parseIsoMillis(iso);
        if (then < 0) return "";
        long seconds = (nowMillis - then) / 1000;
        if (seconds < 60) return "Just now";
        long minutes = seconds / 60;
        if (minutes < 60) return plural(minutes, "minute");
        long hours = minutes / 60;
        if (hours < 24) return plural(hours, "hour");
        long days = hours / 24;
        if (days < 2) return "Yesterday";
        if (days < 7) return plural(days, "day");
        if (days < 30) return plural(days / 7, "week");
        if (days < 365) return plural(days / 30, "month");
        return plural(days / 365, "year");
    }

    private static String plural(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s") + " ago";
    }

    /** "neoforge" to "NeoForge", "client_side" to "Client side", "worldgen" to "Worldgen". */
    public static String tagLabel(String id) {
        if (id == null || id.isEmpty()) return "";
        switch (id.toLowerCase(Locale.ROOT)) {
            case "neoforge": return "NeoForge";
            case "liteloader": return "LiteLoader";
            case "modloader": return "ModLoader";
            case "bungeecord": return "BungeeCord";
            case "optifine": return "OptiFine";
            case "datapack": return "Data Pack";
            case "gui": return "GUI";
            case "mod": return "Mod";
            case "utility": return "Utility";
            default:
                String spaced = id.replace('_', ' ').replace('-', ' ');
                return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
        }
    }

    /** Modrinth's loader names, which are also tags. */
    public static boolean isLoader(String id) {
        if (id == null) return false;
        switch (id.toLowerCase(Locale.ROOT)) {
            case "fabric": case "forge": case "neoforge": case "quilt": case "liteloader": case "modloader": case "rift":
            case "iris": case "optifine": case "canvas": case "vanilla": case "datapack": case "minecraft":
            case "bukkit": case "spigot": case "paper": case "purpur": case "folia": case "sponge":
            case "velocity": case "bungeecord": case "waterfall": case "bta-babric": case "java-agent": case "nilloader":
            case "legacy-fabric": case "ornithe":
                return true;
            default:
                return false;
        }
    }

    /** "Client or server", "Client", "Server" or "" from Modrinth's client_side / server_side values. */
    public static String environmentLabel(String clientSide, String serverSide) {
        boolean client = clientSide != null && ("required".equals(clientSide) || "optional".equals(clientSide));
        boolean server = serverSide != null && ("required".equals(serverSide) || "optional".equals(serverSide));
        if (client && server) return "Client or server";
        if (client) return "Client";
        if (server) return "Server";
        return "";
    }
}
