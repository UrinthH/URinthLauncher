package dev.urinth.recordable;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Record-able Lite: records gameplay without costing frames per second. This class only connects the game to the recorder: keys,
 * commands, the pause-menu buttons, the red dot and the hooks that run once per rendered frame. The work itself is in
 * {@link RecorderSession}.
 */
public final class RecordableLite implements ClientModInitializer {
    public static final String ID = "recordable_lite";
    public static final Logger LOG = LoggerFactory.getLogger("Record-able Lite");

    private static final ConcurrentLinkedQueue<String> MESSAGES = new ConcurrentLinkedQueue<>();
    /** Screens that already got their render hook (weak, so closed screens can be collected). */
    private static final Map<Screen, Boolean> HOOKED_SCREENS = Collections.synchronizedMap(new WeakHashMap<>());

    private static Settings settings = new Settings();
    private static Path settingsFile;
    private static Path gameDir;
    /** The running recording (render thread only). */
    private static RecorderSession session;
    /** A recording that was stopped and is still closing its file in the background. */
    private static RecorderSession finishing;
    private static KeyMapping toggleKey;
    private static KeyMapping pauseKey;
    private static KeyMapping settingsKey;

    public static Settings settings() {
        return settings;
    }

    public static void saveSettings() {
        try {
            settings.save(settingsFile);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not save the settings", e);
        }
    }

    /** True if the pressed key is the one bound to the settings screen. */
    public static boolean isSettingsKey(int keyCode, int scanCode) {
        return settingsKey != null && settingsKey.matches(keyCode, scanCode);
    }

    public static boolean isRecording() {
        RecorderSession s = session;
        return s != null && s.isActive();
    }

    @Override
    public void onInitializeClient() {
        gameDir = FabricLoader.getInstance().getGameDir();
        settingsFile = FabricLoader.getInstance().getConfigDir().resolve("recordable_lite.json");
        settings = Settings.load(settingsFile);
        checkCrashMarker();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.recordable_lite.toggle", InputConstants.Type.KEYSYM,
                InputConstants.KEY_MINUS, "key.categories.recordable_lite"));
        pauseKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.recordable_lite.pause", InputConstants.Type.KEYSYM,
                InputConstants.KEY_EQUALS, "key.categories.recordable_lite"));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.recordable_lite.settings", InputConstants.Type.KEYSYM,
                InputConstants.KEY_F9, "key.categories.recordable_lite"));

        HudRenderCallback.EVENT.register(RecordableLite::onHud);
        WorldRenderEvents.END.register(context -> onWorldEnd());
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof PauseScreen) {
                addPauseButtons(screen);
            }
            // A screen is set up again each time the window changes size; its render hook must be added only once
            if (HOOKED_SCREENS.put(screen, Boolean.TRUE) == null) {
                ScreenEvents.afterRender(screen).register(RecordableLite::onScreenRender);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(RecordableLite::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(RecordableLite::onStopping);
        ClientCommandRegistrationCallback.EVENT.register(RecordableLite::registerCommands);
        LOG.info("Record-able Lite is loaded (keys: - start/stop, = pause, F9 settings; recordings go to {})", gameDir.resolve("recordings"));
    }

    /**
     * If the game died while the video encoder was starting last time, the native encoder is the likely cause: switch it off, so the
     * player gets the backup format instead of another crash, and say so.
     */
    private static void checkCrashMarker() {
        Path marker = Settings.configDir(gameDir).resolve("encoder-starting.marker");
        if (Files.exists(marker)) {
            settings.hardwareEncoder = false;
            saveSettings();
            try {
                Files.deleteIfExists(marker);
            } catch (IOException ignored) {
                // it would only trigger this once more
            }
            MESSAGES.add("The game closed while the phone's video encoder was starting last time, so the hardware encoder is switched off "
                    + "now and the backup format is used. You can turn it on again with F9.");
        }
    }

    // ---- recording control (render thread) ------------------------------------------------------------------------------------

    public static void toggle(Minecraft mc) {
        if (isRecording()) {
            stopRecording("");
        } else {
            startRecording(mc);
        }
    }

    private static void startRecording(Minecraft mc) {
        if (isRecording()) {
            return;
        }
        if (finishing != null && !finishing.isDone()) {
            MESSAGES.add("The last recording is still being saved. Try again in a moment.");
            return;
        }
        finishing = null;
        RenderTarget target = mc.getMainRenderTarget();
        try {
            session = RecorderSession.start(settings, gameDir, target.viewWidth, target.viewHeight, MESSAGES::add);
        } catch (Throwable t) {
            LOG.error("Could not start recording", t);
            MESSAGES.add("Could not start recording: " + t);
            session = null;
        }
    }

    private static void stopRecording(String reason) {
        RecorderSession s = session;
        if (s != null) {
            s.stop(reason);
            finishing = s;
            session = null;
        }
    }

    private static void pauseOrResume() {
        RecorderSession s = session;
        if (s == null || !s.isActive()) {
            MESSAGES.add("Nothing is being recorded.");
        } else if (s.state() == RecorderSession.State.PAUSED) {
            s.resume();
        } else {
            s.pause();
        }
    }

    // ---- hooks ----------------------------------------------------------------------------------------------------------------

    /** The in-game HUD is drawn: the finished picture of the game is in the main render target. */
    private static void onHud(GuiGraphics graphics, DeltaTracker delta) {
        if (session == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            frame(mc, graphics);
        }
    }

    /**
     * The world was drawn. With the HUD hidden (F1) the game skips the HUD completely, so {@link #onHud} never runs; this is then the last
     * point of the frame that is still ours. It does nothing in every other case, so no frame is captured twice.
     */
    private static void onWorldEnd() {
        if (session == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.options.hideGui) {
            frame(mc, null);
        }
    }

    /** A screen was drawn on top of (or instead of) the game. */
    private static void onScreenRender(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
        if (session == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == screen) {
            frame(mc, graphics);
        }
    }

    private static void frame(Minecraft mc, GuiGraphics graphics) {
        RecorderSession s = session;
        if (s == null) {
            return;
        }
        try {
            if (s.isActive()) {
                if (graphics != null) {
                    graphics.flush();                      // draw what the HUD has batched so far, so it is in the picture
                }
                RenderTarget target = mc.getMainRenderTarget();
                s.frame(target.frameBufferId, target.viewWidth, target.viewHeight);
            }
            if (!s.isActive()) {
                session = null;                            // the recorder stopped by itself
                finishing = s;
                return;
            }
            if (settings.showIndicator && graphics != null) {
                drawIndicator(mc, graphics, s);            // after the capture, so the dot is not in the video
            }
        } catch (Throwable t) {
            LOG.error("Recording hook failed", t);
            session = null;
            finishing = s;
            try {
                s.stop("internal error");
            } catch (Throwable ignored) {
                // nothing more can be done
            }
        }
    }

    private static void drawIndicator(Minecraft mc, GuiGraphics graphics, RecorderSession s) {
        boolean paused = s.state() == RecorderSession.State.PAUSED;
        boolean on = (System.currentTimeMillis() / 600) % 2 == 0;
        int dot = paused ? 0xFFFFB000 : on ? 0xFFE02020 : 0xFF8A1515;
        String text = (paused ? "PAUSED " : "REC ") + RecorderSession.clock(s.elapsedMs());
        if (!paused && s.currentFps() < settings.fps()) {
            text += " (" + s.currentFps() + " fps)";
        }
        int width = mc.font.width(text) + 12;
        int x = graphics.guiWidth() / 2 - width / 2;
        int y = 3;
        graphics.fill(x, y + 1, x + 6, y + 7, dot);
        graphics.drawString(mc.font, text, x + 11, y, 0xFFFFFFFF, true);
    }

    private static void onTick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            toggle(mc);
        }
        while (pauseKey.consumeClick()) {
            pauseOrResume();
        }
        while (settingsKey.consumeClick()) {
            if (!(mc.screen instanceof SettingsScreen)) {
                mc.setScreen(new SettingsScreen(mc.screen));
            }
        }
        RecorderSession s = session;
        if (s != null && !s.isActive()) {
            session = null;
            finishing = s;
        }
        String message;
        while ((message = MESSAGES.poll()) != null) {
            mc.gui.getChat().addMessage(Component.literal("[Recorder] " + message));
        }
    }

    private static void onStopping(Minecraft mc) {
        RecorderSession s = session;
        if (s != null) {
            session = null;
            s.stopAndWait("the game was closed", 4000);
        }
        RecorderSession f = finishing;
        if (f != null) {
            f.awaitDone(4000);
        }
    }

    // ---- pause menu -----------------------------------------------------------------------------------------------------------

    private static Component recordLabel() {
        return Component.literal(isRecording() ? "Stop recording" : "Start recording");
    }

    /** Touch screens have no - key at hand, so the pause menu gets the controls too. */
    private static void addPauseButtons(Screen screen) {
        Button record = Button.builder(recordLabel(), button -> {
            Minecraft mc = Minecraft.getInstance();
            boolean wasRecording = isRecording();
            toggle(mc);
            button.setMessage(recordLabel());
            if (!wasRecording && isRecording()) {
                mc.setScreen(null);                        // back to the game, the recording is running
            }
        }).bounds(6, 6, 124, 20).build();
        Button options = Button.builder(Component.literal("Recorder settings"),
                button -> Minecraft.getInstance().setScreen(new SettingsScreen(screen))).bounds(6, 30, 124, 20).build();
        Screens.getButtons(screen).add(record);
        Screens.getButtons(screen).add(options);
    }

    // ---- commands -------------------------------------------------------------------------------------------------------------

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(ClientCommandManager.literal("recordable")
                .then(ClientCommandManager.literal("start").executes(c -> {
                    startRecording(c.getSource().getClient());
                    return 1;
                }))
                .then(ClientCommandManager.literal("stop").executes(c -> {
                    if (isRecording()) {
                        stopRecording("");
                    } else {
                        c.getSource().sendFeedback(Component.literal("Nothing is being recorded."));
                    }
                    return 1;
                }))
                .then(ClientCommandManager.literal("pause").executes(c -> {
                    pauseOrResume();
                    return 1;
                }))
                .then(ClientCommandManager.literal("settings").executes(c -> {
                    Minecraft mc = c.getSource().getClient();
                    mc.execute(() -> mc.setScreen(new SettingsScreen(null)));
                    return 1;
                }))
                .then(ClientCommandManager.literal("status").executes(c -> {
                    RecorderSession s = session;
                    String text = s != null && s.isActive()
                            ? "Recording " + RecorderSession.clock(s.elapsedMs()) + " at " + s.currentFps() + " fps: " + s.description()
                            : "Not recording. Preset " + settings.preset.id + " (" + settings.height() + "p, up to " + settings.fps()
                            + " fps), hardware encoder " + (settings.hardwareEncoder ? "on" : "off") + ".";
                    c.getSource().sendFeedback(Component.literal(text));
                    return 1;
                }))
                .then(ClientCommandManager.literal("preset")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Settings.Preset p : Settings.Preset.values()) {
                                        builder.suggest(p.id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(c -> {
                                    settings.preset = Settings.Preset.byId(StringArgumentType.getString(c, "name"));
                                    saveSettings();
                                    c.getSource().sendFeedback(Component.literal("Preset: " + settings.preset.id + " ("
                                            + settings.height() + "p, up to " + settings.fps() + " fps). It applies to the next recording."));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("fps")
                        .then(ClientCommandManager.argument("fps", IntegerArgumentType.integer(0, 30))
                                .executes(c -> {
                                    settings.fpsCap = IntegerArgumentType.getInteger(c, "fps");
                                    saveSettings();
                                    c.getSource().sendFeedback(Component.literal("Frame rate limit: up to " + settings.fps()
                                            + " fps (0 = what the preset says). It applies to the next recording."));
                                    return 1;
                                }))));
    }
}
