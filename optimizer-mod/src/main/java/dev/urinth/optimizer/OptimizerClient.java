package dev.urinth.optimizer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client side of URinth Optimizer. Once a second it looks at the frame rate and tells the controller, which answers how many
 * chunks to draw; the answer is handed to SoftDistance, which the mixins read (fog, chunk culling). The settings are in the screen
 * (Video Settings > URinth Optimizer), with /urinthopt, or with a key. options.txt is never touched.
 */
public final class OptimizerClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("urinth_optimizer");

    /** Ticks to wait after joining a world or changing dimension before the frame rate means anything (10 seconds). */
    static final int SETTLE_TICKS = 200;

    private static volatile OptimizerClient instance;

    private final DistanceGovernor governor = new DistanceGovernor();
    private final ViewValve valve = new ViewValve();
    // The button must not hold on to its screen: a WeakHashMap lets go of a key only if nothing reachable from its value points back
    // at it, and a click handler that names the screen does exactly that (the screen, and everything it holds, would stay for good).
    private final Map<Screen, Button> screenButtons = new WeakHashMap<>();
    private final Set<Screen> hooked = Collections.newSetFromMap(new WeakHashMap<>());
    private int ticks;
    private int settle;
    private int lastCull = Integer.MAX_VALUE;
    private ClientLevel lastLevel;
    private boolean openSettingsNext;
    private boolean wasActive;

    @Override
    public void onInitializeClient() {
        instance = this;
        // each part is registered on its own: one that cannot be (another mod in the way, a changed API) must not stop the game
        try {
            ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        } catch (Throwable t) {
            LOG.warn("[Optimizer] could not start the controller", t);
        }
        try {
            registerScreenButton();
        } catch (Throwable t) {
            LOG.warn("[Optimizer] could not add the settings button", t);
        }
        try {
            registerCommand();
        } catch (Throwable t) {
            LOG.warn("[Optimizer] could not add /urinthopt", t);
        }
        try {
            registerKey();
        } catch (Throwable t) {
            LOG.warn("[Optimizer] could not add the key", t);
        }
        Monitor.start();
        LOG.info("[Optimizer] client ready");
    }

    // ---- once a tick -------------------------------------------------------------------------------------------------------

    private void onTick(Minecraft mc) {
        if (!Hooks.usable("controller")) return;
        try {
            if (openSettingsNext && mc.screen == null) {
                openSettingsNext = false;
                mc.setScreen(new OptimizerScreen(null));
            }
            ticks++;
            ClientLevel level = mc.level;
            if (level == null) {
                if (lastLevel != null || wasActive) leaveWorld();
                return;
            }
            if (level != lastLevel) {
                lastLevel = level;
                settle = SETTLE_TICKS;
            }
            if (settle > 0) settle--;

            OptimizerConfig config = OptimizerConfig.current();
            int real = mc.options.getEffectiveRenderDistance();
            Monitor.setWatching(settle <= 0 && mc.isWindowActive() && !mc.getWindow().isMinimized());
            if (ticks % 20 == 10) {
                // offset from the controller's second so the two never land in the same tick
                IntegratedServer server = mc.getSingleplayerServer();
                Monitor.setFacts(mc.getFps(), server == null ? -1f : server.getCurrentSmoothedTickTime());
                memoryStep(mc, config, server != null);
            }
            if (!config.adaptive()) {
                if (wasActive) {
                    // switched off: let the fog move back out smoothly, then the game's own distance is all that is left
                    wasActive = false;
                    governor.reset();
                    SoftDistance.apply(real, real, config.cull());
                    ParticleGate.setLow(false);
                }
            } else if (ticks % 20 == 0) {
                wasActive = true;
                int target = TargetFps.effective(config.targetFps(), mc.options.framerateLimit().get(),
                    mc.options.enableVsync().get(), mc.getWindow().getRefreshRate());
                boolean running = settle <= 0 && !mc.isPaused() && mc.isWindowActive() && !mc.getWindow().isMinimized();
                int soft = governor.second(mc.getFps(), target, real, config.minDistance(), running);
                SoftDistance.apply(soft, real, config.cull());
                ParticleGate.setLow(governor.struggling());
                UploadGate.setTargetFps(target);
            }

            // the culling limit moves with the fog: when it changes the game's chunk graph has to be walked again (only that;
            // nothing is reloaded or rebuilt)
            int cull = SoftDistance.cullLimit();
            if (cull != lastCull) {
                lastCull = cull;
                if (mc.levelRenderer != null) mc.levelRenderer.needsUpdate();
            }
        } catch (Throwable t) {
            Hooks.failed("controller", t);
            if (!Hooks.usable("controller")) {
                // it keeps failing: stop, and put the game's own distance and particles back
                SoftDistance.reset();
                ParticleGate.setLow(false);
                ParticleGate.setShortOfMemory(false);
                valve.reset();
                ViewCap.set(0);
            }
        }
    }

    /**
     * Once a second: what the game does about a shortage of memory (the monitor measures it, see {@link Monitor}). Particles are
     * thinned and the server's simulation distance is shortened while it is tight; as the last resort, while it is critical, the
     * world around the player is shortened by two chunks (see {@link ViewValve}). None of it touches options.txt or the option
     * values, and all of it is given back when the memory is fine again.
     */
    private void memoryStep(Minecraft mc, OptimizerConfig config, boolean ownServer) {
        if (!Hooks.usable("memory guard")) return;
        try {
            int mode = config.memoryGuard();
            MemoryGuard.Level level = Monitor.level();
            ParticleGate.setShortOfMemory(mode != OptimizerConfig.MEMORY_OFF && level != MemoryGuard.Level.OK);
            // a server somewhere else decides its own view distance: there is nothing to shorten from here
            boolean full = mode == OptimizerConfig.MEMORY_FULL && ownServer;
            int option = mc.options.renderDistance().get();
            int before = valve.cap();
            int cap = valve.update(level, option, full, System.nanoTime() / 1_000_000_000L);
            ViewCap.set(cap);
            if (cap != before) announceView(mc, before, cap, option);
        } catch (Throwable t) {
            Hooks.failed("memory guard", t);
            if (!Hooks.usable("memory guard")) {
                ParticleGate.setShortOfMemory(false);
                valve.reset();
                ViewCap.set(0);
            }
        }
    }

    private static void announceView(Minecraft mc, int before, int cap, int option) {
        String text;
        if (cap == 0) {
            text = "[URinth Optimizer] Memory is fine again: the world is back to your " + option + " chunks.";
        } else if (before == 0 || cap < before) {
            text = "[URinth Optimizer] The game is almost out of memory, so the world is shortened to " + cap
                + " chunks for now to avoid a crash (your setting stays " + option + ").";
        } else {
            text = "[URinth Optimizer] More room in memory: the world is " + cap + " chunks now (your setting: " + option + ").";
        }
        LOG.info(text);
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), false);
    }

    private void leaveWorld() {
        lastLevel = null;
        wasActive = false;
        lastCull = Integer.MAX_VALUE;
        governor.reset();
        SoftDistance.reset();
        ParticleGate.setLow(false);
        ParticleGate.setShortOfMemory(false);
        valve.reset();
        ViewCap.set(0);
        Monitor.setWatching(false);
    }

    /** The settings changed (the screen, the command): apply them without waiting for the next second. */
    static void settingsChanged() {
        OptimizerClient c = instance;
        if (c == null) return;
        OptimizerConfig config = OptimizerConfig.current();
        UploadGate.setTargetFps(config.targetFps());
        if (!config.adaptive()) ParticleGate.setLow(false);
    }

    // ---- the status --------------------------------------------------------------------------------------------------------

    /** A few lines on what the mod is doing, for the screen and for /urinthopt. */
    static List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        OptimizerConfig c = OptimizerConfig.current();
        OptimizerClient self = instance;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || self == null) {
            lines.add("Adaptive distance: " + (c.adaptive() ? "ON" : "OFF") + ". Join a world to see it work.");
        } else {
            int real = mc.options.getEffectiveRenderDistance();
            int soft = self.governor.soft() == 0 ? real : Math.min(self.governor.soft(), real);
            lines.add("Adaptive distance: " + (c.adaptive() ? "ON" : "OFF") + " | you chose " + real + " chunks | drawing "
                + (c.adaptive() ? soft : real) + " now");
            lines.add("FPS " + mc.getFps() + " (target " + (self.governor.lastTarget() == 0 ? c.targetFps() : self.governor.lastTarget())
                + ") | " + self.governor.reason() + (self.governor.giveUp() ? " | paused: it did not help" : ""));
        }
        String memory = Monitor.describe() + " | guard " + OptimizerConfig.memoryGuardName(c.memoryGuard());
        if (self != null && self.valve.cap() > 0) {
            memory += " | world shortened to " + self.valve.cap() + " chunks";
        }
        lines.add(memory);
        lines.add("Hooks: fog " + Hooks.state(Hooks.fog) + ", culling " + Hooks.state(Hooks.cull) + ", particles "
            + Hooks.state(Hooks.particles) + ", uploads " + Hooks.state(Hooks.uploads) + ", view distance guard " + Hooks.state(Hooks.view));
        lines.add(OptimizerMod.serverStatus() + " | background threads: " + System.getProperty(ThreadPlan.PROPERTY, "the game's choice"));
        if (!Monitor.lastHitch().isEmpty()) lines.add("Last freeze in the log: " + Monitor.lastHitch());
        if (!Hooks.failure().isEmpty()) lines.add("A hook failed and was skipped: " + Hooks.failure());
        return lines;
    }

    // ---- the button on the video settings screens --------------------------------------------------------------------------

    private void registerScreenButton() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            // The game's own class is checked as a class, not by name: outside the dev environment the game's classes carry
            // other names than the ones in the source. Sodium's screens are not the game's, so their names are what they look like.
            OptimizerScreens.Kind kind = screen instanceof VideoSettingsScreen
                ? OptimizerScreens.Kind.VANILLA : OptimizerScreens.classify(screen.getClass().getName());
            if (kind == OptimizerScreens.Kind.NONE) return;
            try {
                Button old = screenButtons.remove(screen);
                if (old != null) Screens.getButtons(screen).remove(old);
                int[] b = OptimizerScreens.bounds(kind, width, height);
                if (b == null) return;
                // the screen the button is on is the one that is open when it is pressed: no reference to it is kept here
                Button button = Button.builder(Component.literal(OptimizerScreens.buttonLabel(b[2])),
                    x -> client.setScreen(new OptimizerScreen(client.screen))).bounds(b[0], b[1], b[2], b[3]).build();
                screenButtons.put(screen, button);
                ensureButton(screen);
                // Sodium wipes and rebuilds its widgets each time a tab is picked: put the button back before every draw
                if (hooked.add(screen)) {
                    ScreenEvents.beforeRender(screen).register((s, graphics, mouseX, mouseY, delta) -> ensureButton(s));
                }
            } catch (Throwable t) {
                LOG.warn("[Optimizer] could not add the settings button to {}", screen.getClass().getName(), t);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> ensureButton(client.screen));
    }

    private void ensureButton(Screen screen) {
        if (screen == null) return;
        Button button = screenButtons.get(screen);
        if (button == null || screen.children().contains(button)) return;
        try {
            var list = Screens.getButtons(screen);
            list.remove(button);
            list.add(button);
        } catch (Throwable t) {
            screenButtons.remove(screen);
            LOG.warn("[Optimizer] could not restore the settings button", t);
        }
    }

    // ---- /urinthopt --------------------------------------------------------------------------------------------------------

    private void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("urinthopt").executes(ctx -> {
                for (String line : statusLines()) ctx.getSource().sendFeedback(Component.literal(line));
                return 1;
            });
            root.then(ClientCommandManager.literal("on").executes(ctx -> {
                OptimizerConfig.current().setAdaptive(true);
                OptimizerMod.save();
                ctx.getSource().sendFeedback(Component.literal("Adaptive distance: ON"));
                return 1;
            }));
            root.then(ClientCommandManager.literal("off").executes(ctx -> {
                OptimizerConfig.current().setAdaptive(false);
                OptimizerMod.save();
                ctx.getSource().sendFeedback(Component.literal("Adaptive distance: OFF (the fog moves back to your render distance)"));
                return 1;
            }));
            root.then(ClientCommandManager.literal("target").then(
                ClientCommandManager.argument("fps", IntegerArgumentType.integer(20, 240)).executes(ctx -> {
                    int fps = OptimizerConfig.current().setTargetFps(IntegerArgumentType.getInteger(ctx, "fps"));
                    OptimizerMod.save();
                    settingsChanged();
                    ctx.getSource().sendFeedback(Component.literal("Target: keep about " + fps + " FPS"));
                    return 1;
                })));
            root.then(ClientCommandManager.literal("min").then(
                ClientCommandManager.argument("chunks", IntegerArgumentType.integer(2, 16)).executes(ctx -> {
                    int chunks = OptimizerConfig.current().setMinDistance(IntegerArgumentType.getInteger(ctx, "chunks"));
                    OptimizerMod.save();
                    ctx.getSource().sendFeedback(Component.literal("Shortest distance: " + chunks + " chunks"));
                    return 1;
                })));
            var memory = ClientCommandManager.literal("memory");
            for (int mode = OptimizerConfig.MEMORY_OFF; mode <= OptimizerConfig.MEMORY_FULL; mode++) {
                final int chosen = mode;
                memory.then(ClientCommandManager.literal(OptimizerConfig.memoryGuardName(mode)).executes(ctx -> {
                    OptimizerConfig.current().setMemoryGuard(chosen);
                    OptimizerMod.save();
                    ctx.getSource().sendFeedback(Component.literal("Memory guard: " + OptimizerConfig.memoryGuardName(chosen)));
                    return 1;
                }));
            }
            root.then(memory);
            root.then(ClientCommandManager.literal("reset").executes(ctx -> {
                governor.reset();
                ctx.getSource().sendFeedback(Component.literal("Forgot what it learned: starting again from your render distance"));
                return 1;
            }));
            root.then(ClientCommandManager.literal("settings").executes(ctx -> {
                openSettingsNext = true;
                return 1;
            }));
            dispatcher.register(root);
        });
    }

    // ---- key binding -------------------------------------------------------------------------------------------------------

    private void registerKey() {
        KeyMapping key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.urinth_optimizer.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.urinth_optimizer"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) client.setScreen(new OptimizerScreen(client.screen));
        });
    }
}
