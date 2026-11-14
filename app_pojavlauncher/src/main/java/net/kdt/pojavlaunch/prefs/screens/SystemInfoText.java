package net.kdt.pojavlaunch.prefs.screens;

/**
 * The text that "Copy system info" puts on the clipboard, for bug reports. It is always in English,
 * whatever language the launcher is shown in, so that anyone reading the report can follow it.
 */
final class SystemInfoText {
    private SystemInfoText() {}

    /**
     * @param vulkan the Vulkan version the phone reports, or null when it reports none
     * @param api "OpenGL" or "Vulkan": what the renderer in use draws with
     */
    static String build(String launcher, String device, String android, String abi,
                        int totalRamMb, int gameRamMb, String gpu, int glesMajor,
                        String vulkan, String renderer, String api, boolean performanceMode) {
        StringBuilder text = new StringBuilder();
        line(text, "Launcher", launcher);
        line(text, "Device", device);
        line(text, "Android", android);
        line(text, "CPU ABI", abi);
        line(text, "RAM", totalRamMb + " MB in the phone, " + gameRamMb + " MB set for the game");
        line(text, "Graphics chip", gpu);
        line(text, "OpenGL ES", glesMajor > 0 ? String.valueOf(glesMajor) : "unknown");
        line(text, "Vulkan", vulkan == null || vulkan.trim().isEmpty() ? "not reported" : vulkan);
        line(text, "Renderer", renderer);
        line(text, "Graphics API", api);
        line(text, "Performance Mode", performanceMode ? "on" : "off");
        return text.toString();
    }

    private static void line(StringBuilder text, String label, String value) {
        text.append(label).append(": ").append(clean(value)).append('\n');
    }

    private static String clean(String value) {
        if (value == null) return "unknown";
        String trimmed = value.replace('\n', ' ').replace('\r', ' ').trim();
        return trimmed.isEmpty() ? "unknown" : trimmed;
    }
}
