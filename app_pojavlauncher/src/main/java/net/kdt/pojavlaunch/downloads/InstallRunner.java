package net.kdt.pojavlaunch.downloads;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs one release into a folder together with what it needs. Blocking: run it off the main
 * thread. No Android classes, so it is tested against a local stand-in for Modrinth.
 */
public final class InstallRunner {
    private InstallRunner() {}

    public interface Listener {
        /** A short line for the status text, such as "Downloading sodium.jar (2 of 3)". */
        void onStatus(String text);

        /** Bytes of file number {@code index} (0 based) out of {@code count}. */
        void onFileProgress(int index, int count, long done, long total);

        boolean isCancelled();
    }

    public static final class Outcome {
        /** Files that were saved now, required ones first. */
        public final List<File> saved = new ArrayList<>();
        /** Files that were already in the folder, byte for byte. */
        public final List<File> alreadyThere = new ArrayList<>();
        /** Older releases of the same mod that were removed because the new one replaced them. */
        public final List<File> replaced = new ArrayList<>();
        /** The file of the release that was asked for. */
        public File main;
        /** Number of required mods that were added next to it. */
        public int dependenciesAdded;
    }

    public static Outcome run(ModrinthClient client, ProjectVersion chosen, DownloadCategory category,
                              ProfileTarget target, File folder, boolean withDependencies, Listener listener) throws IOException {
        boolean isMod = category == DownloadCategory.MODS;
        InstalledMods installed = InstalledMods.empty();
        if (isMod && folder.isDirectory()) {
            listener.onStatus("Checking the mods already installed…");
            installed = InstalledMods.scan(client, folder);
        }
        check(listener);
        List<ProjectVersion> plan;
        if (isMod && withDependencies) {
            listener.onStatus("Looking for the mods it needs…");
            plan = InstallPlan.build(client, chosen, target, true, installed.projects());
        } else {
            plan = InstallPlan.build(client, chosen, target, false);
        }
        check(listener);

        Outcome outcome = new Outcome();
        for (int i = 0; i < plan.size(); i++) {
            ProjectVersion version = plan.get(i);
            final int index = i;
            final int count = plan.size();
            listener.onStatus("Downloading " + (version.filename.isEmpty() ? version.name : version.filename)
                    + (count > 1 ? " (" + (i + 1) + " of " + count + ")" : ""));
            FileInstaller.Result result = FileInstaller.install(version, folder, new FileInstaller.Progress() {
                @Override
                public void onBytes(long done, long total) {
                    listener.onFileProgress(index, count, done, total);
                }

                @Override
                public boolean isCancelled() {
                    return listener.isCancelled();
                }
            });
            (result.alreadyInstalled ? outcome.alreadyThere : outcome.saved).add(result.file);
            boolean isChosen = i == plan.size() - 1;
            if (isChosen) {
                outcome.main = result.file;
                if (isMod && !chosen.projectId.isEmpty()) {
                    // The new release is in place and checked; now take out the older one, or the
                    // game would start with two copies of the same mod.
                    for (File old : installed.filesOf(chosen.projectId)) {
                        if (!old.equals(result.file) && old.delete()) outcome.replaced.add(old);
                    }
                }
            } else if (!result.alreadyInstalled) {
                outcome.dependenciesAdded++;
            }
        }
        return outcome;
    }

    private static void check(Listener listener) throws IOException {
        if (listener.isCancelled()) throw new IOException("Cancelled");
    }
}
