package dev.urinth.recordable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The F9 screen: buttons that cycle through the choices. Changes apply to the next recording. */
public final class SettingsScreen extends Screen {
    private static final String[] CORNERS = {"top left", "top right", "bottom left", "bottom right"};

    private final Screen parent;
    private int footerY;

    private record Entry(Supplier<String> label, Runnable change) {
    }

    public SettingsScreen(Screen parent) {
        super(Component.literal("Record-able Lite"));
        this.parent = parent;
    }

    private void cycleButton(int x, int y, int width, int buttonHeight, Entry e) {
        addRenderableWidget(Button.builder(Component.literal(e.label().get()), button -> {
            e.change().run();
            RecordableLite.saveSettings();
            button.setMessage(Component.literal(e.label().get()));
        }).bounds(x, y, width, buttonHeight).build());
    }

    private static String size(int mb) {
        return mb == 0 ? "no limit" : mb >= 1000 ? (mb / 1000.0) + " GB" : mb + " MB";
    }

    @Override
    protected void init() {
        Settings s = RecordableLite.settings();
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(() -> "Quality: " + s.preset.id + " (" + s.preset.height + "p)", () -> s.preset = s.preset.next()));
        entries.add(new Entry(() -> "Frame rate: " + (s.fpsCap > 0 ? "up to " + s.fps() + " fps" : "from quality (" + s.fps() + ")"),
                s::cycleFps));
        entries.add(new Entry(() -> "Encoder: " + (s.hardwareEncoder ? "phone's video chip" : "backup (.avi)"),
                () -> s.hardwareEncoder = !s.hardwareEncoder));
        entries.add(new Entry(() -> "Save in: " + (s.saveToGallery ? "gallery (Movies)" : "game folder"),
                () -> s.saveToGallery = !s.saveToGallery));
        entries.add(new Entry(() -> "Max file size: " + size(s.maxFileMb), s::cycleMaxFile));
        entries.add(new Entry(() -> "Red dot: " + (s.showIndicator ? "on" : "off"), () -> s.showIndicator = !s.showIndicator));
        entries.add(new Entry(() -> "Dot corner: " + CORNERS[Math.floorMod(s.overlayCorner, CORNERS.length)], s::cycleCorner));
        entries.add(new Entry(() -> "Dot size: " + s.overlayScale + "%", s::cycleScale));
        entries.add(new Entry(() -> "Swap blue/orange: " + (s.swapChroma ? "on" : "off"), () -> s.swapChroma = !s.swapChroma));

        int gap = 6;
        int columnWidth = Math.max(100, Math.min(170, (width - 3 * gap) / 2));
        int rows = (entries.size() + 1) / 2;
        int top = 30;
        int step = Math.max(21, Math.min(24, (height - top - 70) / rows));
        int buttonHeight = Math.min(20, step - 2);
        int left = width / 2 - (2 * columnWidth + gap) / 2;
        for (int i = 0; i < entries.size(); i++) {
            int column = i / rows;                              // the left column is filled first, top to bottom
            int row = i % rows;
            cycleButton(left + column * (columnWidth + gap), top + row * step, columnWidth, buttonHeight, entries.get(i));
        }
        int doneY = top + rows * step + 6;
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds(width / 2 - 60, doneY, 120, 20).build());
        footerY = doneY + 28;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        center(graphics, title, 12, 0xFFFFFFFF);
        center(graphics, Component.literal("Keys: - start/stop    = pause    F9 this screen"), footerY, 0xFFC0C0C0);
        center(graphics, Component.literal("Changes apply to the next recording. /recordable report shows what the recorder found out."),
                footerY + 12, 0xFF909090);
        center(graphics, Component.literal("Videos go to Movies/Record-able (the gallery and CapCut see them) when the phone allows it."),
                footerY + 24, 0xFF909090);
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
