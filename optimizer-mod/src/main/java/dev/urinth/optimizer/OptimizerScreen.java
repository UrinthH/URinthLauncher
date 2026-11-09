package dev.urinth.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The settings screen: one button per setting, each cycling through its values and saving at once. Opened from the Video Settings
 * screen (vanilla or Sodium), with /urinthopt settings, or with the key in Controls.
 */
public final class OptimizerScreen extends Screen {
    private final Screen parent;
    private final List<Supplier<String>> labels = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();

    public OptimizerScreen(Screen parent) {
        super(Component.literal("URinth Optimizer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labels.clear();
        actions.clear();
        buttons.clear();
        OptimizerConfig c = OptimizerConfig.current();
        add(() -> "Adaptive distance: " + onOff(c.adaptive()), () -> c.setAdaptive(!c.adaptive()));
        add(() -> "Target: " + c.targetFps() + " FPS", c::cycleTargetFps);
        add(() -> "Shortest: " + c.minDistance() + " chunks", c::cycleMinDistance);
        add(() -> "Far chunk culling: " + onOff(c.cull()), () -> c.setCull(!c.cull()));
        add(() -> "Particles: " + capital(OptimizerConfig.particlesName(c.particles())), c::cycleParticles);
        add(() -> "Upload smoothing: " + onOff(c.uploads()), () -> c.setUploads(!c.uploads()));
        add(() -> "Sim. limit: " + (c.simulationCap() == 0 ? "Off" : c.simulationCap() + " chunks"), c::cycleSimulationCap);
        add(() -> "Server auto-adjust: " + onOff(c.serverGovernor()), () -> c.setServerGovernor(!c.serverGovernor()));
        add(() -> "Threads: " + capital(OptimizerConfig.threadsName(c.workerThreads())), c::cycleThreads);

        int[][] cells = OptimizerScreens.grid(this.width, this.height, labels.size());
        for (int i = 0; i < labels.size(); i++) {
            int[] cell = cells[i];
            Supplier<String> label = labels.get(i);
            Runnable action = actions.get(i);
            Button button = Button.builder(Component.literal(label.get()), b -> {
                try {
                    action.run();
                } catch (Throwable t) {
                    OptimizerMod.LOG.warn("[Optimizer] a setting could not be changed", t);
                }
                OptimizerMod.save();
                OptimizerClient.settingsChanged();
                refreshLabels();
            }).bounds(cell[0], cell[1], cell[2], cell[3]).build();
            buttons.add(button);
            this.addRenderableWidget(button);
        }
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
            .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void add(Supplier<String> label, Runnable action) {
        labels.add(label);
        actions.add(action);
    }

    private void refreshLabels() {
        for (int i = 0; i < buttons.size(); i++) buttons.get(i).setMessage(Component.literal(labels.get(i).get()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        List<String> lines = OptimizerClient.statusLines();
        int y = 24;
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            graphics.drawCenteredString(this.font, Component.literal(lines.get(i)), this.width / 2, y, 0xB8B8B8);
            y += 10;
        }
        graphics.drawCenteredString(this.font, Component.literal("Your render distance in Video Settings is never changed."),
            this.width / 2, this.height - 42, 0x909090);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    private static String onOff(boolean on) {
        return on ? "ON" : "OFF";
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
