package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks every change this mod makes to the game's code against the game itself. A mixin that does not fit cannot be tried
 * without starting the game, so this reads the class files of Minecraft 1.21.4 (the same names the mod is compiled against)
 * and checks, for every injection: the target class exists, the target method exists with the signature the handler was written
 * for, the call being redirected is really made by the method it is redirected in, and a shadowed field exists with that type.
 * Anything that does not fit fails the build here instead of silently doing nothing in the game.
 */
class MixinTargetsTest {
    private static final String PKG = "dev/urinth/optimizer/mixin/";
    private static final String CI = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CIR = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";

    // ---- the mixin configuration ---------------------------------------------------------------------------------------------

    private static String resource(String name) throws IOException {
        try (var in = MixinTargetsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name + " is not in the mod");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> mixinNames() throws IOException {
        String json = resource("urinth_optimizer.mixins.json");
        List<String> names = new ArrayList<>();
        for (String key : new String[] {"mixins", "client", "server"}) {
            Matcher array = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
            if (!array.find()) continue;
            Matcher item = Pattern.compile("\"([A-Za-z0-9_.]+)\"").matcher(array.group(1));
            while (item.find()) names.add(item.group(1));
        }
        return names;
    }

    @Test
    void theConfigurationIsFailSoft() throws IOException {
        String json = resource("urinth_optimizer.mixins.json");
        assertTrue(json.contains("\"required\": false"), "a mixin that cannot apply must not stop the game");
        assertTrue(Pattern.compile("\"defaultRequire\"\\s*:\\s*0").matcher(json).find(), "an injection that cannot apply must be skipped");
        assertFalse(mixinNames().isEmpty());
    }

    @Test
    void everyMixinInTheConfigurationExistsAndEveryMixinClassIsListed() throws IOException {
        Set<String> listed = new TreeSet<>(mixinNames());
        Set<String> onDisk = new TreeSet<>();
        Path dir = Path.of("build", "classes", "java", "main", "dev", "urinth", "optimizer", "mixin");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".class") && !n.contains("$"))
                    .forEach(n -> onDisk.add(n.substring(0, n.length() - 6)));
            }
            assertEquals(listed, onDisk, "the classes in the mixin package and the names in the configuration must be the same");
        }
        for (String name : listed) assertNotNull(ClassFiles.read(PKG + name), "mixin class " + name);
    }

    @Test
    void theEntryPointsInTheModFileAreRealClasses() throws IOException {
        String json = resource("fabric.mod.json");
        Matcher m = Pattern.compile("\"(dev\\.urinth\\.optimizer\\.[A-Za-z0-9_.]+)\"").matcher(json);
        int found = 0;
        while (m.find()) {
            found++;
            assertNotNull(ClassFiles.read(m.group(1).replace('.', '/')), "entry point " + m.group(1));
        }
        assertEquals(3, found, "preLaunch, main and client");
    }

    @Test
    void nothingOutsideTheMixinPackageReferencesAMixin() throws IOException {
        Path dir = Path.of("build", "classes", "java", "main", "dev", "urinth", "optimizer");
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                assertFalse(bytes.contains("dev/urinth/optimizer/mixin"), file.getFileName() + " reaches into the mixin package, which the game forbids");
            }
        }
    }

    // ---- every injection against the game --------------------------------------------------------------------------------------

    @Test
    void everyInjectionFitsTheGamesCode() throws IOException {
        int checked = 0;
        for (String name : mixinNames()) checked += check(name);
        // fog (and the freeze monitor), particles, uploads (and its shadowed queue), culling, simulation distance and the integrated
        // server's view distance: six injections and one shadowed field
        assertTrue(checked >= 7, "injections checked: " + checked);
    }

    /**
     * The memory guard's last resort redirects the FIRST {@code Math.max} in {@code IntegratedServer.tickServer}. If a game update
     * ever reorders that method so that the first one is something else, the redirect would cap the wrong number, so this checks,
     * in the game's own code, that the first one reads the render distance option and the second the simulation distance option,
     * and that the method compares the result with the player list's view distance and sets it when they differ (which is why the
     * capped number can be handed back here without the game undoing it on the next tick).
     */
    @Test
    void theIntegratedServerReadsTheRenderDistanceFirstAndTheSimulationDistanceSecond() throws IOException {
        ClassNode server = ClassFiles.read("net/minecraft/client/server/IntegratedServer");
        assertNotNull(server);
        List<MethodNode> ticks = methods(server, "tickServer(Ljava/util/function/BooleanSupplier;)V");
        assertEquals(1, ticks.size(), "IntegratedServer.tickServer(BooleanSupplier)");
        List<String> optionBeforeEachMax = new ArrayList<>();
        String lastOption = null;
        boolean readsViewDistance = false;
        boolean setsViewDistance = false;
        for (AbstractInsnNode insn = ticks.get(0).instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode call)) continue;
            if (call.owner.equals("net/minecraft/client/Options") && (call.name.equals("renderDistance") || call.name.equals("simulationDistance"))) {
                lastOption = call.name;
            }
            if (call.owner.equals("java/lang/Math") && call.name.equals("max") && call.desc.equals("(II)I")) {
                optionBeforeEachMax.add(String.valueOf(lastOption));
                lastOption = null;
            }
            if (call.owner.equals("net/minecraft/server/players/PlayerList") && call.name.equals("getViewDistance") && call.desc.equals("()I")) {
                readsViewDistance = true;
            }
            if (call.owner.equals("net/minecraft/server/players/PlayerList") && call.name.equals("setViewDistance") && call.desc.equals("(I)V")) {
                setsViewDistance = true;
            }
        }
        assertEquals(List.of("renderDistance", "simulationDistance"), optionBeforeEachMax,
            "the Math.max calls of IntegratedServer.tickServer, each with the option read just before it");
        assertTrue(readsViewDistance, "tickServer no longer compares with PlayerList.getViewDistance()");
        assertTrue(setsViewDistance, "tickServer no longer sets PlayerList.setViewDistance(int)");
    }

    /** The mixin config lists the redirect where it belongs: it is about a client-only class, so it must be in the client list. */
    @Test
    void theIntegratedServerMixinIsAClientMixin() throws IOException {
        String json = resource("urinth_optimizer.mixins.json");
        Matcher client = Pattern.compile("\"client\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        assertTrue(client.find(), "no client list in the mixin configuration");
        assertTrue(client.group(1).contains("\"IntegratedServerMixin\""), "IntegratedServerMixin must be a client mixin");
        Matcher common = Pattern.compile("\"mixins\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        assertTrue(common.find());
        assertFalse(common.group(1).contains("IntegratedServerMixin"), "a dedicated server has no IntegratedServer class");
    }

    private static int check(String simpleName) throws IOException {
        ClassNode mixin = ClassFiles.read(PKG + simpleName);
        assertNotNull(mixin, simpleName);
        AnnotationNode head = annotation(mixin.invisibleAnnotations, mixin.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        assertNotNull(head, simpleName + " has no @Mixin");
        List<ClassNode> targets = new ArrayList<>();
        for (Object t : list(value(head, "value"))) {
            ClassNode node = ClassFiles.read(((Type) t).getInternalName());
            assertNotNull(node, simpleName + ": target class " + t + " is not in the game");
            targets.add(node);
        }
        assertEquals(1, targets.size(), simpleName + " should have exactly one target");
        ClassNode target = targets.get(0);
        int checked = 0;

        for (FieldNode field : mixin.fields) {
            if (annotation(field.invisibleAnnotations, field.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;") == null) continue;
            FieldNode real = target.fields.stream().filter(f -> f.name.equals(field.name)).findFirst().orElse(null);
            assertNotNull(real, simpleName + ": the game has no field " + field.name + " in " + target.name);
            assertEquals(real.desc, field.desc, simpleName + ": field " + field.name + " has another type in the game");
            checked++;
        }

        for (MethodNode handler : mixin.methods) {
            AnnotationNode inject = annotation(handler.invisibleAnnotations, handler.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
            AnnotationNode redirect = annotation(handler.invisibleAnnotations, handler.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
            AnnotationNode modify = annotation(handler.invisibleAnnotations, handler.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;");
            if (inject != null) {
                checkInject(simpleName, handler, inject, target);
                checked++;
            }
            if (redirect != null) {
                checkRedirect(simpleName, handler, redirect, target);
                checked++;
            }
            if (modify != null) {
                checkModifyVariable(simpleName, handler, modify, target);
                checked++;
            }
        }
        return checked;
    }

    private static void checkInject(String who, MethodNode handler, AnnotationNode inject, ClassNode target) {
        Type[] have = Type.getArgumentTypes(handler.desc);
        assertTrue(have.length >= 1, who + "." + handler.name + " needs the callback parameter");
        String callback = have[have.length - 1].getInternalName();
        for (Object m : list(value(inject, "method"))) {
            List<MethodNode> candidates = methods(target, (String) m);
            assertFalse(candidates.isEmpty(), who + "." + handler.name + ": " + target.name + " has no method " + m);
            boolean fits = false;
            StringBuilder tried = new StringBuilder();
            for (MethodNode candidate : candidates) {
                Type[] args = Type.getArgumentTypes(candidate.desc);
                boolean isVoid = Type.getReturnType(candidate.desc).getSort() == Type.VOID;
                String expectedCallback = isVoid ? CI : CIR;
                tried.append(" ").append(candidate.name).append(candidate.desc);
                if (have.length != args.length + 1 || !callback.equals(expectedCallback)) continue;
                boolean same = true;
                for (int i = 0; i < args.length; i++) same &= args[i].equals(have[i]);
                if (same) fits = true;
            }
            assertTrue(fits, who + "." + handler.name + " " + handler.desc + " does not fit any of:" + tried);
        }
        for (Object at : list(value(inject, "at"))) {
            String where = (String) value((AnnotationNode) at, "value");
            assertTrue(where.equals("HEAD") || where.equals("RETURN"), who + "." + handler.name + ": injection point " + where + " is not one checked here");
        }
    }

    private static void checkRedirect(String who, MethodNode handler, AnnotationNode redirect, ClassNode target) throws IOException {
        // @Redirect has one @At (an @Inject has an array of them)
        Object atValue = value(redirect, "at");
        AnnotationNode at = atValue instanceof AnnotationNode node ? node : (AnnotationNode) list(atValue).get(0);
        assertEquals("INVOKE", value(at, "value"), who + "." + handler.name);
        String ref = (String) value(at, "target");
        Matcher parts = Pattern.compile("L([^;]+);([^(]+)(\\(.*)").matcher(ref);
        assertTrue(parts.matches(), who + "." + handler.name + ": unreadable target " + ref);
        String owner = parts.group(1);
        String method = parts.group(2);
        String desc = parts.group(3);
        ClassNode ownerNode = ClassFiles.read(owner);
        assertNotNull(ownerNode, who + "." + handler.name + ": " + owner + " is not in the game");
        MethodNode real = ownerNode.methods.stream().filter(m -> m.name.equals(method) && m.desc.equals(desc)).findFirst().orElse(null);
        assertNotNull(real, who + "." + handler.name + ": " + owner + " has no " + method + desc);
        boolean isStatic = (real.access & 0x0008) != 0;
        // the handler takes the object the call is made on (unless static), then the call's own arguments, and answers the same type
        List<Type> expected = new ArrayList<>();
        if (!isStatic) expected.add(Type.getObjectType(owner));
        for (Type t : Type.getArgumentTypes(desc)) expected.add(t);
        assertEquals(expected.toString(), List.of(Type.getArgumentTypes(handler.desc)).toString(), who + "." + handler.name + " parameters");
        assertEquals(Type.getReturnType(desc), Type.getReturnType(handler.desc), who + "." + handler.name + " return type");
        // and every method it is redirected in really makes that call
        List<Object> methods = list(value(redirect, "method"));
        assertFalse(methods.isEmpty());
        for (Object m : methods) {
            List<MethodNode> candidates = methods(target, (String) m);
            assertFalse(candidates.isEmpty(), who + "." + handler.name + ": " + target.name + " has no method " + m);
            boolean calls = false;
            for (MethodNode candidate : candidates) {
                for (AbstractInsnNode insn = candidate.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(method) && call.desc.equals(desc)) calls = true;
                }
            }
            assertTrue(calls, who + "." + handler.name + ": " + target.name + "#" + m + " does not call " + owner + "." + method + desc);
        }
    }

    private static void checkModifyVariable(String who, MethodNode handler, AnnotationNode modify, ClassNode target) {
        Type[] have = Type.getArgumentTypes(handler.desc);
        assertEquals(1, have.length, who + "." + handler.name + " takes the value");
        assertEquals(have[0], Type.getReturnType(handler.desc), who + "." + handler.name + " returns the same type");
        assertEquals(Boolean.TRUE, value(modify, "argsOnly"), who + "." + handler.name + " must change an argument, not a local");
        for (Object m : list(value(modify, "method"))) {
            List<MethodNode> candidates = methods(target, (String) m);
            assertFalse(candidates.isEmpty(), who + "." + handler.name + ": " + target.name + " has no method " + m);
            for (MethodNode candidate : candidates) {
                long same = List.of(Type.getArgumentTypes(candidate.desc)).stream().filter(t -> t.equals(have[0])).count();
                assertEquals(1L, same, who + "." + handler.name + ": " + candidate.name + candidate.desc + " must have exactly one argument of type " + have[0]);
            }
        }
    }

    // ---- facts about the game the mod relies on ------------------------------------------------------------------------------

    @Test
    void theThreadPropertyIsTheOneTheGameReads() throws IOException {
        ClassNode util = ClassFiles.read("net/minecraft/Util");
        assertNotNull(util);
        FieldNode property = util.fields.stream().filter(f -> f.name.equals("MAX_THREADS_SYSTEM_PROPERTY")).findFirst().orElse(null);
        assertNotNull(property, "the game has no thread property any more");
        assertEquals(ThreadPlan.PROPERTY, property.value);
    }

    @Test
    void theParticleLevelsTheHookUsesExist() throws IOException {
        ClassNode status = ClassFiles.read("net/minecraft/server/level/ParticleStatus");
        assertNotNull(status);
        for (String name : new String[] {"ALL", "DECREASED", "MINIMAL"}) {
            assertTrue(status.fields.stream().anyMatch(f -> f.name.equals(name)), "ParticleStatus." + name);
        }
    }

    @Test
    void theGameMethodsTheClientCallsExist() throws IOException {
        String[][] calls = {
            {"net/minecraft/client/Minecraft", "getFps", "()I"},
            {"net/minecraft/client/Minecraft", "isPaused", "()Z"},
            {"net/minecraft/client/Minecraft", "isWindowActive", "()Z"},
            {"net/minecraft/client/Options", "getEffectiveRenderDistance", "()I"},
            {"net/minecraft/client/renderer/LevelRenderer", "needsUpdate", "()V"},
            {"com/mojang/blaze3d/platform/Window", "getRefreshRate", "()I"},
            {"com/mojang/blaze3d/platform/Window", "isMinimized", "()Z"},
            {"net/minecraft/server/MinecraftServer", "getCurrentSmoothedTickTime", "()F"},
            {"net/minecraft/server/players/PlayerList", "setSimulationDistance", "(I)V"},
            {"net/minecraft/server/players/PlayerList", "getViewDistance", "()I"},
            {"net/minecraft/server/players/PlayerList", "setViewDistance", "(I)V"},
            {"net/minecraft/client/Options", "renderDistance", "()Lnet/minecraft/client/OptionInstance;"},
            {"net/minecraft/client/Options", "simulationDistance", "()Lnet/minecraft/client/OptionInstance;"},
            {"net/minecraft/client/Minecraft", "getSingleplayerServer", "()Lnet/minecraft/client/server/IntegratedServer;"},
        };
        for (String[] call : calls) {
            ClassNode node = ClassFiles.read(call[0]);
            assertNotNull(node, call[0]);
            assertTrue(node.methods.stream().anyMatch(m -> m.name.equals(call[1]) && m.desc.equals(call[2])), call[0] + "." + call[1] + call[2]);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------------

    /** Methods of the class named {@code spec}: "name" or "name(desc)". */
    private static List<MethodNode> methods(ClassNode node, String spec) {
        String name = spec;
        String desc = null;
        int paren = spec.indexOf('(');
        if (paren >= 0) {
            name = spec.substring(0, paren);
            desc = spec.substring(paren);
        }
        List<MethodNode> found = new ArrayList<>();
        for (MethodNode m : node.methods) {
            if (m.name.equals(name) && (desc == null || m.desc.equals(desc))) found.add(m);
        }
        return found;
    }

    private static AnnotationNode annotation(List<AnnotationNode> a, List<AnnotationNode> b, String desc) {
        for (List<AnnotationNode> list : List.of(a == null ? List.<AnnotationNode>of() : a, b == null ? List.<AnnotationNode>of() : b)) {
            for (AnnotationNode node : list) if (node.desc.equals(desc)) return node;
        }
        return null;
    }

    private static Object value(AnnotationNode node, String key) {
        if (node.values == null) return null;
        for (int i = 0; i + 1 < node.values.size(); i += 2) {
            if (key.equals(node.values.get(i))) return node.values.get(i + 1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }
}
