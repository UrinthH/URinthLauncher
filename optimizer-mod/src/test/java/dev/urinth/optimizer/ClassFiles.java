package dev.urinth.optimizer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/** Reads class files of the game from the class path without loading or initialising them. */
final class ClassFiles {
    private ClassFiles() {}

    /** The class as the game ships it (names as this mod is compiled against: the official Mojang names), or null. */
    static ClassNode read(String internalName) throws IOException {
        ClassLoader loader = ClassFiles.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(internalName + ".class")) {
            if (in == null) return null;
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    /** The jar (or jars, if the game is split in client and common) that hold the game's classes. */
    static List<Path> gameJars() {
        List<Path> jars = new ArrayList<>();
        for (String marker : new String[] {"net/minecraft/client/Minecraft.class", "net/minecraft/server/MinecraftServer.class"}) {
            URL url = ClassFiles.class.getClassLoader().getResource(marker);
            if (url == null) continue;
            String text = url.toString();
            if (!text.startsWith("jar:file:") || !text.contains("!/")) continue;
            Path jar = Path.of(URLDecoder.decode(text.substring("jar:file:".length(), text.indexOf("!/")), StandardCharsets.UTF_8));
            if (!jars.contains(jar)) jars.add(jar);
        }
        return jars;
    }
}
