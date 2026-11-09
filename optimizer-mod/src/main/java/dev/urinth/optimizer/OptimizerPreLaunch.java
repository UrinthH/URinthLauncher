package dev.urinth.optimizer;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs before the game's own classes load. The game reads the system property max.bg.threads once, when it starts, so this is the
 * only moment it can be set from a mod. Nothing here may fail: any problem is logged and the game starts with its own choice.
 */
public final class OptimizerPreLaunch implements PreLaunchEntrypoint {
    private static final Logger LOG = LoggerFactory.getLogger("urinth_optimizer");

    @Override
    public void onPreLaunch() {
        try {
            boolean dedicated = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
            OptimizerConfig config = ConfigStore.load(FabricLoader.getInstance().getConfigDir().resolve(OptimizerConfig.FILE_NAME), dedicated);
            if (System.getProperty(ThreadPlan.PROPERTY) != null) {
                LOG.info("[Optimizer] {} is already set to {}, leaving it", ThreadPlan.PROPERTY, System.getProperty(ThreadPlan.PROPERTY));
                return;
            }
            int cores = Runtime.getRuntime().availableProcessors();
            int workers = ThreadPlan.workers(cores, config.workerThreads());
            if (workers > 0) {
                System.setProperty(ThreadPlan.PROPERTY, Integer.toString(workers));
                LOG.info("[Optimizer] {} cores: at most {} background threads (the game would use {})", cores, workers, Math.max(1, cores - 1));
            } else {
                LOG.info("[Optimizer] background threads: the game's own choice ({} cores)", cores);
            }
        } catch (Throwable t) {
            LOG.warn("[Optimizer] could not set the thread count, the game uses its own", t);
        }
    }
}
