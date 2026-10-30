package net.kdt.pojavlaunch.content;

/** What a mod, pack or shader says about itself, read from inside its file. */
public final class ContentInfo {
    public String name = "", version = "", description = "";
    /** The file's own icon (PNG/JPEG bytes), or null when it has none. */
    public byte[] icon;
    /** True when the file could not be opened (damaged, or not a zip). */
    public boolean unreadable;

    public boolean hasIcon() {
        return icon != null && icon.length > 0;
    }
}
