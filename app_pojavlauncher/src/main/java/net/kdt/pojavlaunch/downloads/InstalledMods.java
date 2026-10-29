package net.kdt.pojavlaunch.downloads;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which Modrinth projects already have a file in a folder. Each .jar's SHA-1 is looked up at
 * Modrinth, which names the release (and project) the file belongs to. Files Modrinth does not
 * know are left out, and are never touched.
 */
public final class InstalledMods {
    private final Map<String, List<File>> filesByProject = new HashMap<>();

    public static InstalledMods empty() {
        return new InstalledMods();
    }

    public static InstalledMods scan(ModrinthClient client, File folder) throws IOException {
        InstalledMods result = new InstalledMods();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
        if (files == null || files.length == 0) return result;
        Map<String, File> byHash = new HashMap<>();
        for (File file : files) {
            if (file.isFile()) byHash.put(FileInstaller.sha1(file), file);
        }
        if (byHash.isEmpty()) return result;
        Map<String, ProjectVersion> known = client.versionsByHashes(new ArrayList<>(byHash.keySet()));
        for (Map.Entry<String, ProjectVersion> entry : known.entrySet()) {
            File file = byHash.get(entry.getKey().toLowerCase(java.util.Locale.ROOT));
            String project = entry.getValue().projectId;
            if (file == null || project.isEmpty()) continue;
            List<File> list = result.filesByProject.get(project);
            if (list == null) {
                list = new ArrayList<>();
                result.filesByProject.put(project, list);
            }
            list.add(file);
        }
        return result;
    }

    public Set<String> projects() {
        return Collections.unmodifiableSet(filesByProject.keySet());
    }

    public boolean has(String projectId) {
        return filesByProject.containsKey(projectId);
    }

    public List<File> filesOf(String projectId) {
        List<File> files = filesByProject.get(projectId);
        return files == null ? Collections.<File>emptyList() : Collections.unmodifiableList(files);
    }
}
