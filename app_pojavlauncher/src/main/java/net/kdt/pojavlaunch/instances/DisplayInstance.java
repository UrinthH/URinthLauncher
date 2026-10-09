package net.kdt.pojavlaunch.instances;

import java.io.File;

public class DisplayInstance {
    protected transient File mInstanceRoot;
    public String name;
    public String versionId;
    public String icon;

    protected void sanitize() {
        sanitizeIcon();
    }

    protected DisplayInstance() {
    }

    protected File getInstanceIconLocation() {
        return new File(mInstanceRoot, "icon.webp");
    }

    private void sanitizeIcon() {
        // Older profiles often have the generic default icon. Infer a loader icon
        // from their saved profile name so loader instances are not all identical.
        if (icon == null || InstanceIconProvider.FALLBACK_ICON_NAME.equalsIgnoreCase(icon)
                || !InstanceIconProvider.hasStaticIcon(icon)) {
            String label = ((name == null ? "" : name) + " " + (versionId == null ? "" : versionId))
                    .toLowerCase(java.util.Locale.ROOT);
            if (label.contains("optifine")) icon = "optifine";
            else if (label.contains("neoforge")) icon = "neoforge";
            else if (label.contains("fabric")) icon = "fabric";
            else if (label.contains("quilt")) icon = "quilt";
            else if (label.contains("forge")) icon = "forge";
            else icon = InstanceIconProvider.FALLBACK_ICON_NAME;
        }
    }
}
