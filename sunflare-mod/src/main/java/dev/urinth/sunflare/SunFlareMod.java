package dev.urinth.sunflare;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sun lens flare for Fabric. No shader loader is needed. The mode is switched with a button in Options > Video Settings
 * and kept in config/urinth-sunflare.properties (options.txt is never touched).
 */
public final class SunFlareMod implements ClientModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("urinth_sunflare");
    private static final String[] NAMES = {"Off", "Subtle", "Full"};

    private FlareConfig config;

    @Override
    public void onInitializeClient() {
        config = new FlareConfig(FabricLoader.getInstance().getConfigDir().resolve("urinth-sunflare.properties"));
        config.load();
        FlareRenderer renderer = new FlareRenderer(config);
        HudRenderCallback.EVENT.register(renderer::render);

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof VideoSettingsScreen)) return;
            boolean wide = width >= 320;
            int w = wide ? 118 : 90;
            Button button = Button.builder(label(), b -> {
                config.cycle();
                config.save();
                b.setMessage(label());
            }).bounds(width - w - 4, 6, w, 20).build();
            Screens.getButtons(screen).add(button);
        });
        LOG.info("[SunFlare] ready, mode={}", NAMES[config.mode()]);
    }

    private Component label() {
        return Component.literal("Sun Flare: " + NAMES[config.mode()]);
    }
}
