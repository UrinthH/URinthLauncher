package dev.urinth.recordable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** The F9 screen: a handful of buttons that cycle through the choices. Changes apply to the next recording. */
public final class SettingsScreen extends Screen {
    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Record-able Lite"));
        this.parent = parent;
    }

    private void cycleButton(int x, int y, int width, Supplier<String> label, Runnable change) {
        addRenderableWidget(Button.builder(Component.literal(label.get()), button -> {
            change.run();
            RecordableLite.saveSettings();
            button.setMessage(Component.literal(label.get()));
        }).bounds(x, y, width, 20).build());
    }

    @Override
    protected void init() {
        Settings s = RecordableLite.settings();
        int w = 220;
        int x = width / 2 - w / 2;
        int y = Math.max(34, height / 2 - 76);
        int step = 24;
        cycleButton(x, y, w, () -> "Quality: " + s.preset.id + " (" + s.preset.height + "p, " + s.preset.kbps / 1000.0 + " Mbit/s)",
                () -> s.preset = s.preset.next());
        cycleButton(x, y + step, w, () -> "Frame rate: " + (s.fpsCap > 0 ? "up to " + s.fps() + " fps" : "from quality (" + s.fps() + ")"),
                s::cycleFps);
        cycleButton(x, y + 2 * step, w, () -> "Video encoder: " + (s.hardwareEncoder ? "phone's video chip" : "backup (slower)"),
                () -> s.hardwareEncoder = !s.hardwareEncoder);
        cycleButton(x, y + 3 * step, w, () -> "Red dot while recording: " + (s.showIndicator ? "on" : "off"),
                () -> s.showIndicator = !s.showIndicator);
        cycleButton(x, y + 4 * step, w, () -> "Swap blue/orange colours: " + (s.swapChroma ? "on" : "off"),
                () -> s.swapChroma = !s.swapChroma);
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds(x, y + 5 * step + 8, w, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        center(graphics, title, 16, 0xFFFFFFFF);
        int bottom = Math.max(34, height / 2 - 76) + 5 * 24 + 40;
        center(graphics, Component.literal("Keys: - start/stop    = pause    F9 this screen"), bottom, 0xFFC0C0C0);
        center(graphics, Component.literal("Changes apply to the next recording. Videos are saved in the recordings folder."), bottom + 12,
                0xFF909090);
        center(graphics, Component.literal("Turn on \"swap colours\" only if blue and orange look exchanged in the video."), bottom + 24,
                0xFF909090);
    }

    private void center(GuiGraphics graphics, Component text, int y, int color) {
        graphics.drawString(font, text, width / 2 - font.width(text) / 2, y, color);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (RecordableLite.isSettingsKey(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        RecordableLite.saveSettings();
        minecraft.setScreen(parent);
    }
}
