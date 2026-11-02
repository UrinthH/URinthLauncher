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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sun lens flare for Fabric. No shader loader is needed. The mode is kept in config/urinth-sunflare.properties (options.txt is
 * never touched) and can be changed in four ways, so it is always reachable:
 * <ul>
 *   <li>"Sun Flare: ..." and "Sun Beams: ..." buttons on Sodium's video settings screen,</li>
 *   <li>the same button on the vanilla Video Settings screen,</li>
 *   <li>the chat commands /sunflare [off|subtle|full|strong] /sunflare beams [off|low|medium|high] and /sunflare style [real|pack],</li>
 *   <li>a key binding in Controls (unbound by default).</li>
 * </ul>
 */
public final class SunFlareMod implements ClientModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("urinth_sunflare");

    private FlareConfig config;
    private final Map<Screen, Button[]> buttons = new WeakHashMap<>();

    @Override
    public void onInitializeClient() {
        config = new FlareConfig(FabricLoader.getInstance().getConfigDir().resolve("urinth-sunflare.properties"));
        config.load();
        FlareRenderer renderer = new FlareRenderer(config);
        HudRenderCallback.EVENT.register(renderer::render);
        BeamRenderer beams = new BeamRenderer(config);
        WorldRenderEvents.LAST.register(beams::render);
        ClientTickEvents.END_CLIENT_TICK.register(beams::tick);

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
        return new Button[] {flare, beams, style};
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
        int value = slot == SettingsScreens.FLARE ? config.mode() : slot == SettingsScreens.BEAMS ? config.beams() : (config.realStyle() ? 0 : 1);
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
            root.then(beamsNode);
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

    private Component beamStatus() {
        return Component.literal("Sun Beams: " + FlareConfig.BEAM_NAMES[config.beams()]);
    }

    private Component status() {
        return Component.literal("Sun Flare: " + FlareConfig.NAMES[config.mode()]);
    }
}
