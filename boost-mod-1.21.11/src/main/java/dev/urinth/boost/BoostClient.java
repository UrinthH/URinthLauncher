package dev.urinth.boost;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * URinth Boost: takes away the things that cost the most frames in a crowd and are the least missed, but only while
 * the frame rate is low (see {@link Governor}). The decisions are in {@link BoostRuntime}, the game side in
 * {@link BoostGame}, the typing in {@link BoostCommand}, the four small changes to the game's drawing in the
 * {@code mixin} package.
 *
 * Everything is only about what is drawn on this screen. No packet is sent or changed, no game option is touched.
 */
public final class BoostClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        try {
            BoostGame.init();
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> BoostCommand.register(dispatcher));
            ClientTickEvents.START_CLIENT_TICK.register(BoostGame::startTick);
            ClientTickEvents.END_CLIENT_TICK.register(BoostGame::endTick);
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> BoostGame.LOGGER.info("[URinth Boost] ready"));
        } catch (Throwable error) {
            // The mod must never be the reason the game does not start.
            BoostGame.LOGGER.error("[URinth Boost] could not start, so it is off", error);
        }
    }
}
