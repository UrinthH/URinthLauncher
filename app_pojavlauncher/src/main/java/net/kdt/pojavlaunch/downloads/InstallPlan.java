package net.kdt.pojavlaunch.downloads;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out which files an install needs: the chosen release plus every release it requires, with
 * the required ones first. Stops with a clear message instead of installing half of a set.
 * Required mods that are already in the profile (any release of them) are left alone, so the
 * profile never ends up with two copies of the same mod, which the loaders refuse to start with.
 */
public final class InstallPlan {
    private static final int MAX_DEPTH = 12;

    private final ModrinthClient client;
    private final ProfileTarget target;
    private final Set<String> installedProjects;
    private final List<ProjectVersion> ordered = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();

    private InstallPlan(ModrinthClient client, ProfileTarget target, Set<String> installedProjects) {
        this.client = client;
        this.target = target;
        this.installedProjects = installedProjects;
    }

    public static List<ProjectVersion> build(ModrinthClient client, ProjectVersion chosen, ProfileTarget target,
                                             boolean withDependencies) throws IOException {
        return build(client, chosen, target, withDependencies, Collections.<String>emptySet());
    }

    /**
     * The files to install, required releases first and the chosen one last.
     *
     * @param installedProjects ids of projects that already have a release in the profile
     */
    public static List<ProjectVersion> build(ModrinthClient client, ProjectVersion chosen, ProfileTarget target,
                                             boolean withDependencies, Set<String> installedProjects) throws IOException {
        InstallPlan plan = new InstallPlan(client, target, installedProjects == null ? Collections.<String>emptySet() : installedProjects);
        plan.seen.add(key(chosen));
        if (withDependencies) plan.visit(chosen, 0);
        plan.ordered.add(chosen); // always last: everything it needs goes in before it
        return plan.ordered;
    }

    private void visit(ProjectVersion version, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("A mod needs too many other mods. The install was stopped before anything was added.");
        for (ProjectVersion.Dependency dependency : version.dependencies) {
            if (!dependency.isRequired()) continue;
            if (!dependency.projectId.isEmpty() && installedProjects.contains(dependency.projectId)) continue;
            ProjectVersion required = find(dependency);
            if (required == null) {
                String what = !dependency.projectId.isEmpty() ? dependency.projectId : dependency.versionId;
                throw new IOException("A required mod (" + what + ") has no release for " + target.describe()
                        + ". The install was stopped before anything was added.");
            }
            if (!required.projectId.isEmpty() && installedProjects.contains(required.projectId)) continue;
            // Marked before going deeper, so two mods that need each other cannot loop.
            if (!seen.add(key(required))) continue;
            visit(required, depth + 1);
            ordered.add(required);
        }
    }

    private static String key(ProjectVersion version) {
        return version.id.isEmpty() ? version.url : version.id;
    }

    private ProjectVersion find(ProjectVersion.Dependency dependency) throws IOException {
        if (!dependency.versionId.isEmpty()) {
            ProjectVersion version = client.version(dependency.versionId);
            return version.hasFile() ? version : null;
        }
        if (dependency.projectId.isEmpty()) return null;
        String[] game = target.gameVersion.isEmpty() ? null : new String[]{target.gameVersion};
        for (ProjectVersion candidate : client.projectVersions(dependency.projectId, target.modLoaders(), game)) {
            if (candidate.isCompatibleWith(target, DownloadCategory.MODS)) return candidate;
        }
        return null;
    }

    /** The newest release to offer first: a full release if there is one, else the newest of any kind. */
    public static ProjectVersion preferred(List<ProjectVersion> newestFirst) {
        for (ProjectVersion version : newestFirst) {
            if ("release".equals(version.type)) return version;
        }
        return newestFirst.isEmpty() ? null : newestFirst.get(0);
    }
}
