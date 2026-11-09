package dev.urinth.optimizer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * URinth Optimizer, the part that runs everywhere (the game, and a dedicated server): loads the settings and looks after the
 * simulation distance of the world the server runs. The client side (render distance, settings screen) is OptimizerClient.
 */
public final class OptimizerMod implements ModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("urinth_optimizer");

    /** The least the server governor may lower the simulation distance to. */
    static final int MIN_SIMULATION = 4;
    /** Every how many server ticks the governor looks (5 seconds). */
    static final int SAMPLE_TICKS = 100;

    private static final ServerGovernor GOVERNOR = new ServerGovernor();
    private static java.nio.file.Path configFile;
    private static int serverTicks;

    @Override
    public void onInitialize() {
        boolean dedicated = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
        configFile = FabricLoader.getInstance().getConfigDir().resolve(OptimizerConfig.FILE_NAME);
        OptimizerConfig config = ConfigStore.load(configFile, dedicated);
        OptimizerConfig.setCurrent(config);
        startWorld(config);
        try {
            ServerTickEvents.END_SERVER_TICK.register(OptimizerMod::onServerTick);
            ServerLifecycleEvents.SERVER_STARTING.register(server -> startWorld(OptimizerConfig.current()));
        } catch (Throwable t) {
            LOG.warn("[Optimizer] could not register the server hooks", t);
        }
        LOG.info("[Optimizer] loaded: adaptive distance {}, target {} FPS, simulation limit {}", config.adaptive() ? "on" : "off",
            config.targetFps(), config.simulationCap() == 0 ? "off" : config.simulationCap() + " chunks");
    }

    /** Saves the settings (called by the screen, the command and the key). */
    static void save() {
        ConfigStore.save(configFile, OptimizerConfig.current());
    }

    private static void startWorld(OptimizerConfig config) {
        GOVERNOR.reset(config.simulationCap());
        ServerCap.reset();
        ServerCap.setCap(config.simulationCap());
        serverTicks = 0;
    }

    private static void onServerTick(MinecraftServer server) {
        if (++serverTicks % SAMPLE_TICKS != 0) return;
        try {
            OptimizerConfig config = OptimizerConfig.current();
            int configured = config.simulationCap();
            int cap = configured;
            if (configured > 0 && config.serverGovernor()) {
                cap = GOVERNOR.sample(server.getCurrentSmoothedTickTime(), configured, MIN_SIMULATION);
            } else if (configured > 0) {
                GOVERNOR.reset(configured);
            }
            if (cap != ServerCap.cap()) {
                ServerCap.setCap(cap);
                int asked = ServerCap.requested();
                // the game asked for this distance earlier; asking again makes it apply the new limit (this is the server thread)
                if (asked > 0) server.getPlayerList().setSimulationDistance(asked);
            }
        } catch (Throwable t) {
            Hooks.failed("server governor", t);
        }
    }

    /** The server governor's state, for the status. */
    static String serverStatus() {
        int cap = ServerCap.cap();
        int asked = ServerCap.requested();
        if (OptimizerConfig.current().simulationCap() == 0) return "simulation distance: not limited";
        return "simulation distance: limit " + cap + " chunks" + (asked > 0 ? " (the game asked for " + asked + ")" : " (no world yet)")
            + (OptimizerConfig.current().serverGovernor() ? ", adjusts to the server speed (changed " + GOVERNOR.changes() + " times)" : "");
    }
}
