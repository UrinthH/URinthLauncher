package dev.urinth.find;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * URinth Find: type /find &lt;player&gt; in chat and get the player's coordinates and dimension.
 *
 * This is the part that runs on both sides (and on a dedicated server): the network messages, the answer a
 * server gives to a client that asks where a player is, and /find on the server. The client part, where the
 * typing happens, is {@link FindClient}.
 */
public final class FindMod implements ModInitializer {
    @Override
    public void onInitialize() {
        final int permissionLevel = FindServer.loadPermissionLevel();

        PayloadTypeRegistry.playC2S().register(FindRequestPayload.TYPE, FindRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FindReplyPayload.TYPE, FindReplyPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(FindRequestPayload.TYPE,
                (payload, context) -> FindServer.answer(context.player(), payload.name(), permissionLevel));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                FindServer.registerCommands(dispatcher, permissionLevel));
    }
}
