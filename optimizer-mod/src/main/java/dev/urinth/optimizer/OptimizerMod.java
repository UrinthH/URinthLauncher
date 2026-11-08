package dev.urinth.optimizer;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OptimizerMod implements ModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("urinth_optimizer");

    @Override
    public void onInitialize() {
        LOG.info("[Optimizer] loaded");
    }
}
