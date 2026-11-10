package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The game stopped at start ("Mixin transformation of net.minecraft.class_8679 failed") because of how a mixin class was
 * written into the jar, not because of what it does. Mixin's {@code @Redirect} takes ONE {@code at}. A build against a newer
 * Mixin than the game runs with writes it as a list of one, and the MixinExtras that ships with Fabric Loader 0.17.3
 * (0.5.0) casts it to a single annotation and throws ClassCastException while it prepares the class. A newer MixinExtras
 * reads both, so a build that only ran against a newer loader never saw it.
 *
 * These tests read the class files themselves, as they come out of the compiler and as they are in the finished jar (after the
 * names are remapped), and fail when the shape is one the game's MixinExtras cannot read. When MixinExtras is on the class path
 * they also run its own transformer over every mixin class, the code that threw.
 */
class MixinShapeTest {
    private static final String PKG = "dev/urinth/optimizer/mixin/";

    /** Injectors whose "at" is one annotation in the Mixin the game runs with (the others, like @Inject, always take a list). */
    private static final Set<String> SINGLE_AT = Set.of(
        "Lorg/spongepowered/asm/mixin/injection/Redirect;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;");

    // ---- the check -----------------------------------------------------------------------------------------------------------

    /** Everything in the class that a MixinExtras 0.5.0 cannot read, one line each. Empty when the class is fine. */
    static List<String> problems(ClassNode node) {
        List<String> found = new ArrayList<>();
        for (MethodNode method : node.methods) {
            check(node, method, method.invisibleAnnotations, found);
            check(node, method, method.visibleAnnotations, found);
        }
        return found;
    }

    private static void check(ClassNode node, MethodNode method, List<AnnotationNode> annotations, List<String> found) {
        if (annotations == null) return;
        for (AnnotationNode annotation : annotations) {
            if (!SINGLE_AT.contains(annotation.desc) || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if ("at".equals(annotation.values.get(i)) && annotation.values.get(i + 1) instanceof List) {
                    found.add(node.name + "." + method.name + ": " + annotation.desc.substring(annotation.desc.lastIndexOf('/') + 1).replace(";", "")
                        + " has \"at\" as a list; it must be a single annotation (the mod was built against a newer Mixin than the game runs with)");
                }
            }
        }
    }

    private static ClassNode read(InputStream in) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    // ---- the classes ---------------------------------------------------------------------------------------------------------

    private static List<String> mixinClassNames() throws IOException {
        String json;
        try (InputStream in = MixinShapeTest.class.getClassLoader().getResourceAsStream("urinth_optimizer.mixins.json")) {
            assertTrue(in != null, "urinth_optimizer.mixins.json is not in the mod");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Set<String> names = new TreeSet<>();
        java.util.regex.Matcher item = java.util.regex.Pattern.compile("\"([A-Za-z0-9_]+Mixin)\"").matcher(json);
        while (item.find()) names.add(item.group(1));
        return new ArrayList<>(names);
    }

    private static List<ClassNode> compiledMixins() throws IOException {
        List<ClassNode> nodes = new ArrayList<>();
        for (String name : mixinClassNames()) {
            try (InputStream in = MixinShapeTest.class.getClassLoader().getResourceAsStream(PKG + name + ".class")) {
                assertTrue(in != null, name + " is listed in the mixin configuration but has no class file");
                nodes.add(read(in));
            }
        }
        return nodes;
    }

    private static List<ClassNode> jarMixins(File jar) throws IOException {
        List<ClassNode> nodes = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(PKG) || !name.endsWith(".class")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    nodes.add(read(in));
                }
            }
        }
        return nodes;
    }

    @Test
    void theCompiledMixinsAreSingleValuedWhereTheGameExpectsThat() throws IOException {
        List<ClassNode> nodes = compiledMixins();
        assertTrue(nodes.size() >= 5, "found only " + nodes.size() + " mixin classes");
        List<String> all = new ArrayList<>();
        for (ClassNode node : nodes) all.addAll(problems(node));
        assertEquals(List.of(), all, "the compiled mixins would stop the game with the MixinExtras of Fabric Loader 0.17.3");
    }

    @Test
    void theFinishedJarIsSingleValuedToo() throws IOException {
        String path = System.getProperty("urinth.jar");
        assumeTrue(path != null && new File(path).isFile(), "no finished jar given (run through Gradle's test task)");
        List<ClassNode> nodes = jarMixins(new File(path));
        assertTrue(nodes.size() >= 5, "found only " + nodes.size() + " mixin classes in " + path);
        List<String> all = new ArrayList<>();
        for (ClassNode node : nodes) all.addAll(problems(node));
        assertEquals(List.of(), all, "the jar would stop the game with the MixinExtras of Fabric Loader 0.17.3");
    }

    // ---- the check itself, on made-up classes --------------------------------------------------------------------------------

    private static ClassNode made(String injector, boolean listed) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V21;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        node.name = PKG + "Made";
        node.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, "handler", "(Ljava/lang/Object;)I", null, null);
        AnnotationNode at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        at.values = new ArrayList<>(List.of("value", "INVOKE", "target", "Lnet/minecraft/Some;thing()I"));
        AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/" + injector + ";");
        Object atValue = listed ? new ArrayList<Object>(List.of(at)) : at;
        annotation.values = new ArrayList<>(List.of("method", new ArrayList<>(List.of("someMethod")), "at", atValue));
        // the injector annotations have runtime retention, so the compiler puts them in RuntimeVisibleAnnotations, which is also
        // where MixinExtras looks (Annotations.getVisible)
        method.visibleAnnotations = new ArrayList<>(List.of(annotation));
        node.methods.add(method);
        return node;
    }

    @Test
    void theCheckFlagsAListAndAcceptsASingleAnnotation() {
        assertFalse(problems(made("Redirect", true)).isEmpty());
        assertFalse(problems(made("ModifyVariable", true)).isEmpty());
        assertEquals(List.of(), problems(made("Redirect", false)));
        assertEquals(List.of(), problems(made("ModifyVariable", false)));
        // @Inject always takes a list, so a list is the normal form there
        assertEquals(List.of(), problems(made("Inject", true)));
    }

    // ---- the real MixinExtras ------------------------------------------------------------------------------------------------

    private static final String FACTORY_REDIRECT = "com.llamalad7.mixinextras.wrapper.factory.FactoryRedirectWrapperMixinTransformer";

    /** Runs MixinExtras' transformer (the code that threw) over the class. Returns the failure, or null when it ran through. */
    private static Throwable runTransformer(Object transformer, Method transform, ClassNode node) {
        Object info = Proxy.newProxyInstance(MixinShapeTest.class.getClassLoader(), new Class<?>[] {transform.getParameterTypes()[0]},
            (proxy, method, args) -> {
                Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == java.util.List.class) return Collections.emptyList();
                if (type == java.util.Set.class) return Collections.emptySet();
                if (type == String.class) return "dev.urinth.optimizer.mixin.Made";
                return null;
            });
        try {
            transform.invoke(transformer, info, node);
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause() != null ? e.getCause() : e;
        } catch (ReflectiveOperationException e) {
            return e;
        }
    }

    @Test
    void theMixinExtrasOfTheGameCanPrepareEveryMixinClass() throws Exception {
        Class<?> type;
        try {
            type = Class.forName(FACTORY_REDIRECT);
        } catch (ClassNotFoundException | LinkageError e) {
            assumeTrue(false, "MixinExtras is not on the test class path");
            return;
        }
        Object transformer = type.getDeclaredConstructor().newInstance();
        Method transform = null;
        for (Method candidate : type.getMethods()) {
            if (candidate.getName().equals("transform") && candidate.getParameterCount() == 2
                && candidate.getParameterTypes()[1] == ClassNode.class) transform = candidate;
        }
        assumeTrue(transform != null, "the MixinExtras on the class path has no transform(info, class) to call");

        StringBuilder report = new StringBuilder("MixinExtras transformer: ").append(FACTORY_REDIRECT).append('\n');
        // the control: the form that stopped the game. A MixinExtras that reads both forms is fine, one that does not must fail here
        Throwable control = runTransformer(transformer, transform, made("Redirect", true));
        report.append("control (at as a list): ").append(control == null ? "read without trouble" : control.toString()).append('\n');

        List<ClassNode> nodes = compiledMixins();
        File jar = System.getProperty("urinth.jar") == null ? null : new File(System.getProperty("urinth.jar"));
        if (jar != null && jar.isFile()) nodes = jarMixins(jar);
        List<String> broken = new ArrayList<>();
        for (ClassNode node : nodes) {
            Throwable failure = runTransformer(transformer, transform, node);
            report.append(node.name).append(": ").append(failure == null ? "ok" : failure.toString()).append('\n');
            if (failure instanceof ClassCastException) broken.add(node.name + ": " + failure);
        }
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build", "mixinextras-check.txt"), report.toString());
        assertEquals(List.of(), broken, "the game's MixinExtras throws on these classes:\n" + report);
    }
}
