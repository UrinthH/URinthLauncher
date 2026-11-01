package dev.urinth.sunflare;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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
 *   <li>a "Sun Flare: ..." button on Sodium's video settings screen,</li>
 *   <li>the same button on the vanilla Video Settings screen,</li>
 *   <li>the chat command /sunflare [off|subtle|full|strong],</li>
 *   <li>a key binding in Controls (unbound by default).</li>
 * </ul>
 */
public final class SunFlareMod implements ClientModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("urinth_sunflare");

    private FlareConfig config;
    private final Map<Screen, Button> buttons = new WeakHashMap<>();

    @Override
    public void onInitializeClient() {
        config = new FlareConfig(FabricLoader.getInstance().getConfigDir().resolve("urinth-sunflare.properties"));
        config.load();
        FlareRenderer renderer = new FlareRenderer(config);
        HudRenderCallback.EVENT.register(renderer::render);

        registerScreenButton();
        registerCommand();
        registerKey();
        LOG.info("[SunFlare] ready, mode={}", FlareConfig.NAMES[config.mode()]);
    }

    // ---- the button on the video settings screen -------------------------------------------------------------------------

    private void registerScreenButton() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (SettingsScreens.classify(screen.getClass().getName()) == SettingsScreens.Kind.NONE) return;
            try {
                // A new screen layout (first open, or a resize): drop the old button and make one for this size.
                Button old = buttons.remove(screen);
                if (old != null) Screens.getButtons(screen).remove(old);
                buttons.put(screen, makeButton(screen, width));
                ensureButton(screen);
                // Sodium wipes and rebuilds every widget each time a tab is picked. This runs right before each draw of
                // this screen, so the button is put back before the frame that would have been missing it.
                ScreenEvents.beforeRender(screen).register((s, graphics, mouseX, mouseY, delta) -> ensureButton(s));
            } catch (Throwable t) {
                LOG.warn("[SunFlare] could not add the settings button to {}", screen.getClass().getName(), t);
            }
        });
        // Backup for the frames the per-screen hook does not cover.
        ClientTickEvents.END_CLIENT_TICK.register(client -> ensureButton(client.screen));
    }

    private Button makeButton(Screen screen, int width) {
        SettingsScreens.Kind kind = SettingsScreens.classify(screen.getClass().getName());
        int[] r = SettingsScreens.bounds(kind, width);
        return Button.builder(label(r[2]), b -> {
            config.cycle();
            config.save();
            b.setMessage(label(b.getWidth()));
        }).bounds(r[0], r[1], r[2], r[3]).build();
    }

    /** Puts the button back on the screen if a rebuild removed it. Safe to call as often as wanted. */
    private void ensureButton(Screen screen) {
        if (screen == null) return;
        Button button = buttons.get(screen);
        if (button == null) return;
        if (screen.children().contains(button)) return;
        try {
            var list = Screens.getButtons(screen);
            list.remove(button);
            list.add(button);
            button.setMessage(label(button.getWidth()));
        } catch (Throwable t) {
            buttons.remove(screen);
            LOG.warn("[SunFlare] could not restore the settings button", t);
        }
    }

    private Component label(int width) {
        return Component.literal(SettingsScreens.label(config.mode(), width));
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
                root.then(ClientCommandManager.literal(FlareConfig.NAMES[i].toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                    config.setMode(mode);
                    config.save();
                    ctx.getSource().sendFeedback(status());
                    return 1;
                }));
            }
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

    private Component status() {
        return Component.literal("Sun Flare: " + FlareConfig.NAMES[config.mode()]);
    }
}
