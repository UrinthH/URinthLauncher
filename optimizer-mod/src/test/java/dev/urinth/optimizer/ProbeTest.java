package dev.urinth.optimizer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Prints what Minecraft 1.21.4 looks like in the names this mod is compiled against, into build/probe. A mixin target that is
 * not in the game can only fail while the game runs, so the names are read from the game itself instead of from memory.
 * It never fails the build.
 */
class ProbeTest {
    private static final String[] DUMP = {
        "net/minecraft/client/renderer/GameRenderer",
        "net/minecraft/client/renderer/LevelRenderer",
        "net/minecraft/client/renderer/SectionOcclusionGraph",
        "net/minecraft/client/renderer/ViewArea",
        "net/minecraft/client/renderer/chunk/SectionRenderDispatcher",
        "net/minecraft/client/renderer/FogRenderer",
        "net/minecraft/client/renderer/culling/Frustum",
        "net/minecraft/client/renderer/entity/EntityRenderDispatcher",
        "net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher",
        "net/minecraft/client/particle/ParticleEngine",
        "net/minecraft/client/Options",
        "net/minecraft/client/Minecraft",
        "com/mojang/blaze3d/platform/Window",
        "com/mojang/blaze3d/systems/RenderSystem",
        "net/minecraft/client/gui/screens/options/VideoSettingsScreen",
        "net/minecraft/client/multiplayer/ClientChunkCache",
        "net/minecraft/server/players/PlayerList",
        "net/minecraft/server/level/ServerChunkCache",
        "net/minecraft/server/level/ChunkMap",
        "net/minecraft/server/level/DistanceManager",
        "net/minecraft/client/server/IntegratedServer",
        "net/minecraft/server/dedicated/DedicatedServer",
        "net/minecraft/server/MinecraftServer",
        "net/minecraft/Util",
        "net/minecraft/world/entity/Mob",
        "net/minecraft/world/level/NaturalSpawner",
    };

    /** {owner, method}: which game code calls it. */
    private static final String[][] CALLERS = {
        {"net/minecraft/client/renderer/GameRenderer", "getRenderDistance"},
        {"net/minecraft/client/renderer/GameRenderer", "getDepthFar"},
        {"net/minecraft/client/renderer/FogRenderer", "setupFog"},
        {"net/minecraft/server/players/PlayerList", "setSimulationDistance"},
        {"net/minecraft/server/players/PlayerList", "setViewDistance"},
        {"net/minecraft/client/Options", "getEffectiveRenderDistance"},
        {"net/minecraft/client/renderer/ViewArea", "isInViewDistance"},
        {"net/minecraft/client/renderer/SectionOcclusionGraph", "getRelativeFrom"},
        {"net/minecraft/Util", "backgroundExecutor"},
    };

    private static final String[] SEARCH = {
        ".*Fog.*", ".*ViewArea.*", ".*OcclusionGraph.*", ".*SectionRender.*", ".*Frustum.*", ".*ParticleLimit.*",
        ".*client/renderer/chunk/.*",
    };

    /** Bytecode of whole small classes or chosen methods: {class, method-name regex}. */
    private static final String[][] BYTECODE = {
        {"net/minecraft/client/renderer/ViewArea", ".*"},
        {"net/minecraft/client/renderer/SectionOcclusionGraph", "(?!lambda).*"},
        {"net/minecraft/client/renderer/GameRenderer", "getRenderDistance|getDepthFar|getProjectionMatrix|getBasicProjectionMatrix"},
        {"net/minecraft/server/players/PlayerList", "setSimulationDistance|setViewDistance|getSimulationDistance|getViewDistance"},
        {"net/minecraft/client/server/IntegratedServer", "tickServer"},
        {"net/minecraft/client/renderer/entity/EntityRenderDispatcher", "shouldRender"},
    };

    @Test
    void probe() {
        Path out = Path.of("build", "probe");
        StringBuilder log = new StringBuilder();
        try {
            Files.createDirectories(out);
            List<Path> jars = ClassFiles.gameJars();
            log.append("game jars: ").append(jars).append('\n');
            safe(log, "classes", () -> dump(out.resolve("classes.txt")));
            safe(log, "callers", () -> callers(out.resolve("callers.txt"), jars));
            safe(log, "search", () -> search(out.resolve("search.txt"), jars));
            safe(log, "bytecode", () -> bytecode(out.resolve("bytecode.txt")));
        } catch (Throwable t) {
            log.append("probe failed: ").append(trace(t));
        }
        try {
            Files.writeString(out.resolve("log.txt"), log.toString());
        } catch (Throwable ignored) {
            // nothing to do: the probe only reports
        }
    }

    private interface Step {
        void run() throws Exception;
    }

    private static void safe(StringBuilder log, String name, Step step) {
        try {
            step.run();
            log.append(name).append(": ok\n");
        } catch (Throwable t) {
            log.append(name).append(": FAILED ").append(trace(t)).append('\n');
        }
    }

    private static String trace(Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        return w.toString();
    }

    // ---- what the classes look like ----------------------------------------------------------------------------------------

    private static void dump(Path file) throws Exception {
        StringBuilder text = new StringBuilder();
        for (String name : DUMP) {
            ClassNode node = ClassFiles.read(name);
            if (node == null) {
                text.append("MISSING ").append(name).append("\n\n");
                continue;
            }
            describe(text, node);
            for (InnerClassNode inner : node.innerClasses) {
                if (!node.name.equals(inner.outerName)) continue;
                ClassNode innerNode = ClassFiles.read(inner.name);
                if (innerNode != null) describe(text, innerNode);
            }
        }
        Files.writeString(file, text.toString());
    }

    private static void describe(StringBuilder text, ClassNode node) {
        text.append("CLASS ").append(node.name).append(" extends ").append(node.superName)
            .append(" implements ").append(node.interfaces).append("\n");
        for (FieldNode f : node.fields) {
            if ((f.access & 0x1000) != 0) continue;
            text.append("  F ").append(access(f.access)).append(Type.getType(f.desc).getClassName()).append(' ').append(f.name).append("\n");
        }
        for (MethodNode m : node.methods) {
            if ((m.access & 0x1000) != 0 || m.name.startsWith("lambda$") || m.name.startsWith("access$")) continue;
            text.append("  M ").append(access(m.access)).append(m.name).append(m.desc).append("   ")
                .append(readable(m.desc)).append("\n");
        }
        text.append("\n");
    }

    private static String readable(String desc) {
        StringBuilder out = new StringBuilder("(");
        Type[] args = Type.getArgumentTypes(desc);
        for (int i = 0; i < args.length; i++) {
            if (i > 0) out.append(", ");
            out.append(simple(args[i].getClassName()));
        }
        return out.append(") -> ").append(simple(Type.getReturnType(desc).getClassName())).toString();
    }

    private static String simple(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private static String access(int a) {
        StringBuilder s = new StringBuilder();
        if ((a & 0x0001) != 0) s.append("public ");
        if ((a & 0x0004) != 0) s.append("protected ");
        if ((a & 0x0002) != 0) s.append("private ");
        if ((a & 0x0008) != 0) s.append("static ");
        if ((a & 0x0010) != 0) s.append("final ");
        if ((a & 0x0400) != 0) s.append("abstract ");
        return s.toString();
    }

    // ---- who calls what ----------------------------------------------------------------------------------------------------

    private static void callers(Path file, List<Path> jars) throws Exception {
        TreeSet<String> lines = new TreeSet<>();
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) continue;
                    ClassNode node = new ClassNode();
                    try (var in = zip.getInputStream(entry)) {
                        new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    }
                    for (MethodNode m : node.methods) {
                        if (m.instructions == null) continue;
                        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                            if (!(insn instanceof MethodInsnNode call)) continue;
                            for (String[] target : CALLERS) {
                                if (call.owner.equals(target[0]) && call.name.equals(target[1])) {
                                    lines.add(target[0].substring(target[0].lastIndexOf('/') + 1) + "." + target[1] + "  <-  "
                                        + node.name + "#" + m.name + m.desc + "   [calls " + call.name + call.desc + "]");
                                }
                            }
                        }
                    }
                }
            }
        }
        Files.writeString(file, String.join("\n", lines) + "\n");
    }

    // ---- class names --------------------------------------------------------------------------------------------------------

    private static void search(Path file, List<Path> jars) throws Exception {
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : SEARCH) patterns.add(Pattern.compile(regex));
        TreeSet<String> names = new TreeSet<>();
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (!name.endsWith(".class") || !name.startsWith("net/minecraft/")) continue;
                    for (Pattern p : patterns) {
                        if (p.matcher(name).matches()) {
                            names.add(name.substring(0, name.length() - 6));
                            break;
                        }
                    }
                }
            }
        }
        Files.writeString(file, String.join("\n", names) + "\n");
    }

    // ---- bytecode of a few methods ------------------------------------------------------------------------------------------

    private static void bytecode(Path file) throws Exception {
        StringBuilder text = new StringBuilder();
        for (String[] entry : BYTECODE) {
            ClassNode node = ClassFiles.read(entry[0]);
            if (node == null) {
                text.append("MISSING ").append(entry[0]).append("\n");
                continue;
            }
            Predicate<String> wanted = Pattern.compile(entry[1]).asMatchPredicate();
            for (MethodNode m : node.methods) {
                if (!wanted.test(m.name)) continue;
                text.append("=== ").append(node.name).append('#').append(m.name).append(m.desc).append('\n');
                Textifier printer = new Textifier();
                TraceMethodVisitor visitor = new TraceMethodVisitor(printer);
                m.accept(visitor);
                StringWriter w = new StringWriter();
                printer.print(new PrintWriter(w));
                text.append(w).append('\n');
            }
        }
        Files.writeString(file, text.toString());
    }
}
