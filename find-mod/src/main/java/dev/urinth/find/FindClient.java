package dev.urinth.find;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/**
 * Where the typing happens: /find and /Find in the chat of the game.
 *
 * <ul>
 * <li>In a world you host (single player or opened to LAN) the game itself knows where everybody is.</li>
 * <li>On a server, the game only knows the players near it. Those are answered at once. A player further away
 * (any distance, any dimension) is asked about at the server, if the server has this mod. If it does not, an
 * operator is answered by the server's own /data command (see {@link FindOperator}). Anybody else is shown where
 * the player was when the game last saw them, and how long ago, or is told the game cannot know.</li>
 * </ul>
 */
public final class FindClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // Command words are case sensitive, so /find and /Find are two words for the same command.
            for (String word : new String[] {"find", "Find"}) {
                dispatcher.register(ClientCommandManager.literal(word)
                        .executes(context -> {
                            context.getSource().sendFeedback(FindMessages.usage());
                            return 1;
                        })
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
                                .executes(context -> {
                                    find(context.getSource(), StringArgumentType.getString(context, "player"));
                                    return 1;
                                })));
            }
        });

        // The server's answer to a request sent by find().
        ClientPlayNetworking.registerGlobalReceiver(FindReplyPayload.TYPE,
                (payload, context) -> context.player().displayClientMessage(FindMessages.of(payload.reply()), false));

        // Operator mode: keep the two /data answers out of the chat and show the /find result instead.
        ClientReceiveMessageEvents.ALLOW_GAME.register(FindOperator::allow);
        ClientTickEvents.END_CLIENT_TICK.register(FindOperator::tick);

        // Where each player was when the game last saw them, for the ones who have walked out of view.
        ClientTickEvents.END_CLIENT_TICK.register(FindSightings::tick);
    }

    private static void find(FabricClientCommandSource source, String typed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        MinecraftServer own = minecraft.getSingleplayerServer();
        if (own != null) {
            // The list of players belongs to the server thread, so ask it there and show the answer here.
            own.execute(() -> {
                FindReply reply = FindLookup.lookup(typed, FindServer.online(own));
                minecraft.execute(() -> source.sendFeedback(FindMessages.of(reply)));
            });
            return;
        }

        String dimension = minecraft.level.dimension().location().toString();
        List<Located> seen = new ArrayList<>();
        for (Player player : minecraft.level.players()) {
            seen.add(new Located(player.getGameProfile().getName(), player.getX(), player.getY(), player.getZ(), dimension));
        }

        // An operator (level 2) may use /data on the server, and that works at any distance.
        boolean operator = minecraft.player != null && minecraft.player.hasPermissions(2);
        FindPlan plan = FindPlan.decide(typed, seen, source.getOnlinePlayerNames(),
                ClientPlayNetworking.canSend(FindRequestPayload.TYPE), operator, FindSightings.remembered());
        if (plan.show() != null) {
            source.sendFeedback(FindMessages.of(plan.show()));
        } else if (plan.ask() != null) {
            ClientPlayNetworking.send(new FindRequestPayload(plan.ask()));
        } else {
            FindOperator.start(minecraft, plan.command());
        }
    }
}
