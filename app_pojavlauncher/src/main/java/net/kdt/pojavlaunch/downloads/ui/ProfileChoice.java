package net.kdt.pojavlaunch.downloads.ui;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.downloads.ProfileTarget;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** One of the player's installations, with the Minecraft version and loader worked out. */
final class ProfileChoice {
    final Instance instance;
    final ProfileTarget target;

    private ProfileChoice(Instance instance, ProfileTarget target) {
        this.instance = instance;
        this.target = target;
    }

    String title() {
        return instance.name == null || instance.name.trim().isEmpty() ? "Installation" : instance.name.trim();
    }

    String subtitle() {
        String text = target.describe();
        return text.equals("Minecraft") && instance.versionId != null ? instance.versionId : text;
    }

    File gameDirectory() {
        return instance.getGameDirectory();
    }

    /** Blocking: reads the installations from disk. Call off the main thread. */
    static List<ProfileChoice> load() throws IOException {
        List<ProfileChoice> choices = new ArrayList<>();
        for (Instance instance : Instances.loadAllInstances()) {
            choices.add(new ProfileChoice(instance, targetOf(instance)));
        }
        return choices;
    }

    /** Index of the installation the player has selected, or 0. */
    static int selectedIndex(List<ProfileChoice> choices) {
        Instance selected = Instances.loadSelectedInstance();
        if (selected == null || selected.getId() == null) return 0;
        for (int i = 0; i < choices.size(); i++) {
            if (selected.getId().equals(choices.get(i).instance.getId())) return i;
        }
        return 0;
    }

    private static ProfileTarget targetOf(Instance instance) {
        String id = instance.versionId == null ? "" : instance.versionId;
        String inherits = null;
        if (!id.isEmpty() && Tools.DIR_HOME_VERSION != null) {
            File json = new File(Tools.DIR_HOME_VERSION, id + "/" + id + ".json");
            if (json.isFile()) {
                try {
                    inherits = ProfileTarget.inheritsFromJson(Tools.read(json.getAbsolutePath()));
                } catch (IOException | RuntimeException ignored) {
                    // Read the id alone then.
                }
            }
        }
        return ProfileTarget.parse(id, inherits);
    }
}
