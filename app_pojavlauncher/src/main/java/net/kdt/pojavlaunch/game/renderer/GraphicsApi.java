package net.kdt.pojavlaunch.game.renderer;

/** The graphics API a renderer draws with. The Settings switcher is a choice between these two. */
public enum GraphicsApi {
    /** OpenGL / OpenGL ES renderers: GL4ES, LTW (Performance Mode), MobileGlues, ANGLE and the like. */
    OPENGL,
    /** Renderers that run on the phone's Vulkan driver: Zink and Legacy Zink. */
    VULKAN
}
