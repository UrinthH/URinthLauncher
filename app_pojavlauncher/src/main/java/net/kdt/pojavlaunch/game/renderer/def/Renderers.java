package net.kdt.pojavlaunch.game.renderer.def;

public final class Renderers {
    public static final String LTW_RENDERER = "opengles3_ltw";
    public static final String GL4ES_RENDERER = "opengles2";
    /** Krypton/NG GL4ES wrapper supplied by a compatible renderer plugin. */
    public static final String KRYPTON_RENDERER = "opengles3";
    public static final String ZINK_RENDERER = "vulkan_zink";
    public static final String VIRGL_RENDERER = "gallium_virgl";
    public static final String FREEDRENO_RENDERER = "freedreno_kgsl";
    public static final String PANFROST_RENDERER = "gallium_panfrost";
    public static final String MESA_RENDERER = "mesa_desktop";
    public static final String MESA_RENDERER_EXT = "mesa_desktop_ext";
    public static final String LEGACYZINK_RENDERER = "vulkan_legacyzink";
    public static final String MOBILEGLUES_RENDERER = "mobileglues";

    /**
     * Canonical renderer registry. Keep UI enumeration and registry tests tied to
     * this list so a renderer cannot be implemented but accidentally omitted in Settings.
     */
    private static final String[] ALL_RENDERERS = {
            GL4ES_RENDERER,
            KRYPTON_RENDERER,
            LTW_RENDERER,
            ZINK_RENDERER,
            VIRGL_RENDERER,
            FREEDRENO_RENDERER,
            PANFROST_RENDERER,
            MESA_RENDERER,
            MESA_RENDERER_EXT,
            LEGACYZINK_RENDERER,
            MOBILEGLUES_RENDERER
    };

    /** Return a copy so callers cannot mutate the canonical renderer registry. */
    public static String[] allRendererIds() {
        return ALL_RENDERERS.clone();
    }

    private Renderers() {
        // Constants-only registry.
    }
}
