package dev.urinth.sunflare;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import java.util.Locale;
import java.util.WeakHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sun lens flare for Fabric. No shader loader is needed. The mode is kept in config/urinth-sunflare.properties (options.txt is
 * never touched) and can be changed in four ways, so it is always reachable:
 * <ul>
 *   <li>"Sun Flare: ..." and "Sun Beams: ..." buttons (and a "Fancy Leaves" one) on Sodium's video settings screen,</li>
 *   <li>the same button on the vanilla Video Settings screen,</li>
 *   <li>the chat commands /sunflare perf (stutter and frame-rate report), /sunflare fps [n] (the Perf Guard), /sunflare [off|subtle|full|strong] /sunflare beams [off|low|medium|high|extreme], /sunflare leaves [off|light|balanced|safe] and /sunflare style [real|pack],</li>
 *   <li>a key binding in Controls (unbound by default).</li>
 * </ul>
 */
public final class SunFlareMod implements ClientModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("urinth_sunflare");

    private FlareConfig config;
    private PerfMonitor perf;
    private final Map<Screen, Button[]> buttons = new WeakHashMap<>();

    @Override
    public void onInitializeClient() {
        config = new FlareConfig(FabricLoader.getInstance().getConfigDir().resolve("urinth-sunflare.properties"));
        config.load();
        applyLeaves(false);
        registerLightingPack();
        FlareRenderer renderer = new FlareRenderer(config);
        HudRenderCallback.EVENT.register(renderer::render);
        GodRays godRays = new GodRays(config);
        // finds the cause of stutter and freezes: counts them and writes logs/urinth-perf.log (nothing per frame, see PerfMonitor)
        perf = new PerfMonitor(FabricLoader.getInstance().getGameDir().resolve("logs").resolve("urinth-perf.log"),
            config::perfLog, godRays::describe);
        godRays.setPerf(perf);
        WorldRenderEvents.LAST.register(godRays::render);
        BlobShadow blobShadow = new BlobShadow();
        WorldRenderEvents.AFTER_ENTITIES.register(blobShadow::render);

        registerScreenButton();
        registerCommand();
        registerKey();
        LOG.info("[SunFlare] ready, mode={}", FlareConfig.NAMES[config.mode()]);
    }

    // ---- the buttons on the video settings screen ------------------------------------------------------------------------

    private void registerScreenButton() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (SettingsScreens.classify(screen.getClass().getName()) == SettingsScreens.Kind.NONE) return;
            try {
                // A new screen layout (first open, or a resize): drop the old buttons and make new ones for this size.
                Button[] old = buttons.remove(screen);
                if (old != null) for (Button b : old) Screens.getButtons(screen).remove(b);
                buttons.put(screen, makeButtons(screen, width));
                ensureButtons(screen);
                // Sodium wipes and rebuilds every widget each time a tab is picked. This runs right before each draw of
                // this screen, so the buttons are put back before the frame that would have been missing them.
                ScreenEvents.beforeRender(screen).register((s, graphics, mouseX, mouseY, delta) -> ensureButtons(s));
            } catch (Throwable t) {
                LOG.warn("[SunFlare] could not add the settings buttons to {}", screen.getClass().getName(), t);
            }
        });
        // Backup for the frames the per-screen hook does not cover.
        ClientTickEvents.END_CLIENT_TICK.register(client -> ensureButtons(client.screen));
    }

    /**
     * "URinth Lighting": a small resource pack inside the mod (on by default; switch it off in Resource Packs). It replaces the game's
     * 16 x 16 light colour picture, which is made once per tick, so warm sunlight and darker, cooler shadows cost nothing per frame.
     */
    private void registerLightingPack() {
        try {
            FabricLoader.getInstance().getModContainer("urinth_sunflare").ifPresent(mod ->
                ResourceManagerHelper.registerBuiltinResourcePack(ResourceLocation.fromNamespaceAndPath("urinth_sunflare", "lighting"),
                    mod, Component.literal("URinth Lighting"), ResourcePackActivationType.DEFAULT_ENABLED));
        } catch (Throwable t) {
            LOG.warn("[SunFlare] could not register the lighting pack", t);
        }
    }

    private Button[] makeButtons(Screen screen, int width) {
        SettingsScreens.Kind kind = SettingsScreens.classify(screen.getClass().getName());
        int[] f = SettingsScreens.bounds(kind, width, SettingsScreens.FLARE);
        int[] b = SettingsScreens.bounds(kind, width, SettingsScreens.BEAMS);
        Button flare = Button.builder(label(SettingsScreens.FLARE, f[2]), x -> {
            config.cycle();
            config.save();
            x.setMessage(label(SettingsScreens.FLARE, x.getWidth()));
        }).bounds(f[0], f[1], f[2], f[3]).build();
        Button beams = Button.builder(label(SettingsScreens.BEAMS, b[2]), x -> {
            config.cycleBeams();
            config.save();
            x.setMessage(label(SettingsScreens.BEAMS, x.getWidth()));
        }).bounds(b[0], b[1], b[2], b[3]).build();
        int[] st = SettingsScreens.bounds(kind, width, SettingsScreens.STYLE);
        Button style = Button.builder(label(SettingsScreens.STYLE, st[2]), x -> {
            config.toggleStyle();
            config.save();
            x.setMessage(label(SettingsScreens.STYLE, x.getWidth()));
        }).bounds(st[0], st[1], st[2], st[3]).build();
        int[] lv = SettingsScreens.bounds(kind, width, SettingsScreens.LEAVES);
        Button leaves = Button.builder(label(SettingsScreens.LEAVES, lv[2]), x -> {
            config.cycleLeaves();
            config.save();
            applyLeaves(true);
            x.setMessage(label(SettingsScreens.LEAVES, x.getWidth()));
        }).bounds(lv[0], lv[1], lv[2], lv[3]).build();
        if (kind == SettingsScreens.Kind.SODIUM) {
            // the Perf Guard: the lowest frame rate the effects may push the game to. Sodium's screen only; the vanilla screen has
            // no free room for a fifth button, there /sunflare fps does the same.
            int[] pf = SettingsScreens.bounds(kind, width, SettingsScreens.PERF);
            Button guard = Button.builder(label(SettingsScreens.PERF, pf[2]), x -> {
                config.cycleTargetFps();
                config.save();
                x.setMessage(label(SettingsScreens.PERF, x.getWidth()));
            }).bounds(pf[0], pf[1], pf[2], pf[3]).build();
            return new Button[] {flare, beams, style, leaves, guard};
        }
        return new Button[] {flare, beams, style, leaves};
    }

    /** Puts the buttons back on the screen if a rebuild removed them. Safe to call as often as wanted. */
    private void ensureButtons(Screen screen) {
        if (screen == null) return;
        Button[] pair = buttons.get(screen);
        if (pair == null) return;
        for (int slot = 0; slot < pair.length; slot++) {
            Button button = pair[slot];
            if (screen.children().contains(button)) continue;
            try {
                var list = Screens.getButtons(screen);
                list.remove(button);
                list.add(button);
                button.setMessage(label(slot, button.getWidth()));
            } catch (Throwable t) {
                buttons.remove(screen);
                LOG.warn("[SunFlare] could not restore the settings buttons", t);
                return;
            }
        }
    }

    private Component label(int slot, int width) {
        int value = slot == SettingsScreens.FLARE ? config.mode() : slot == SettingsScreens.BEAMS ? config.beams()
            : slot == SettingsScreens.LEAVES ? config.leaves() : slot == SettingsScreens.PERF ? config.targetFps()
            : (config.realStyle() ? 0 : 1);
        return Component.literal(SettingsScreens.label(slot, value, width));
    }

    // ---- /sunflare ---------------------------------------------------------------------------------------------------

    private void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("sunflare").executes(ctx -> {
                config.cycle();
                config.save();
                ctx.getSource().sendFeedback(status());
                return 1;
            });
            for (int i = 0; i < FlareConfig.NAMES.length; i++) {
                final int mode = i;
                root.then(ClientCommandManager.literal(FlareConfig.NAMES[i].toLowerCase(Locale.ROOT)).executes(ctx -> {
                    config.setMode(mode);
                    config.save();
                    ctx.getSource().sendFeedback(status());
                    return 1;
                }));
            }
            var beamsNode = ClientCommandManager.literal("beams").executes(ctx -> {
                config.cycleBeams();
                config.save();
                ctx.getSource().sendFeedback(beamStatus());
                return 1;
            });
            for (int i = 0; i < FlareConfig.BEAM_NAMES.length; i++) {
                final int level = i;
                beamsNode.then(ClientCommandManager.literal(FlareConfig.BEAM_NAMES[i].toLowerCase(Locale.ROOT)).executes(ctx -> {
                    config.setBeams(level);
                    config.save();
                    ctx.getSource().sendFeedback(beamStatus());
                    return 1;
                }));
            }
            root.then(ClientCommandManager.literal("fps").then(
                ClientCommandManager.argument("target", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 240))
                    .executes(ctx -> {
                        int fps = config.setTargetFps(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "target"));
                        config.save();
                        ctx.getSource().sendFeedback(Component.literal(fps == 0 ? "Frame guard: off" : "Frame guard: keep about " + fps + " FPS"));
                        return 1;
                    })));
            root.then(beamsNode);
            var perfNode = ClientCommandManager.literal("perf").executes(ctx -> {
                for (String line : perf.summary()) ctx.getSource().sendFeedback(Component.literal(line));
                ctx.getSource().sendFeedback(Component.literal("Stutter log: " + (config.perfLog() ? perf.logFile() : "off")
                    + " | Perf Guard: " + FlareConfig.fpsName(config.targetFps())));
                return 1;
            });
            perfNode.then(ClientCommandManager.literal("reset").executes(ctx -> {
                perf.stats().reset();
                ctx.getSource().sendFeedback(Component.literal("Stutter counters reset"));
                return 1;
            }));
            perfNode.then(ClientCommandManager.literal("log")
                .then(ClientCommandManager.literal("on").executes(ctx -> {
                    config.setPerfLog(true);
                    config.save();
                    ctx.getSource().sendFeedback(Component.literal("Stutter log: on (" + perf.logFile() + ")"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(ctx -> {
                    config.setPerfLog(false);
                    config.save();
                    ctx.getSource().sendFeedback(Component.literal("Stutter log: off"));
                    return 1;
                })));
            root.then(perfNode);
            var leavesNode = ClientCommandManager.literal("leaves").executes(ctx -> {
                config.cycleLeaves();
                config.save();
                applyLeaves(true);
                ctx.getSource().sendFeedback(leavesStatus());
                return 1;
            });
            for (int i = 0; i < FlareConfig.LEAVES_NAMES.length; i++) {
                final int level = i;
                leavesNode.then(ClientCommandManager.literal(FlareConfig.LEAVES_NAMES[i].toLowerCase(Locale.ROOT)).executes(ctx -> {
                    config.setLeaves(level);
                    config.save();
                    applyLeaves(true);
                    ctx.getSource().sendFeedback(leavesStatus());
                    return 1;
                }));
            }
            root.then(leavesNode);
            var styleNode = ClientCommandManager.literal("style").executes(ctx -> {
                config.toggleStyle();
                config.save();
                ctx.getSource().sendFeedback(styleStatus());
                return 1;
            });
            for (String name : new String[] {"real", "pack"}) {
                styleNode.then(ClientCommandManager.literal(name).executes(ctx -> {
                    config.setRealStyle(name.equals("real"));
                    config.save();
                    ctx.getSource().sendFeedback(styleStatus());
                    return 1;
                }));
            }
            root.then(styleNode);
            dispatcher.register(root);
        });
    }

    // ---- key binding -------------------------------------------------------------------------------------------------

    private void registerKey() {
        KeyMapping key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.urinth_sunflare.cycle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.urinth_sunflare"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                config.cycle();
                config.save();
                if (client.player != null) client.player.displayClientMessage(status(), true);
            }
        });
    }

    private Component styleStatus() {
        return Component.literal("Flare Style: " + (config.realStyle() ? "Real" : "Pack"));
    }

    private Component leavesStatus() {
        return Component.literal("Fancy Leaves: " + FlareConfig.LEAVES_NAMES[config.leaves()]
            + (config.leaves() == 0 ? "" : " (hidden inner leaf faces are skipped)"));
    }

    /** Hands the setting to the chunk builder; the chunks are rebuilt (once) when the level changes. */
    private void applyLeaves(boolean rebuild) {
        LeafCull.setDepth(config.leaves());
        if (!rebuild) return;
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.levelRenderer != null && mc.level != null) mc.levelRenderer.allChanged();
        } catch (Throwable t) {
            LOG.warn("[SunFlare] could not rebuild the chunks after changing the leaves", t);
        }
    }

    private Component beamStatus() {
        return Component.literal("Sun Beams: " + FlareConfig.BEAM_NAMES[config.beams()]);
    }

    private Component status() {
        return Component.literal("Sun Flare: " + FlareConfig.NAMES[config.mode()]);
    }
}
