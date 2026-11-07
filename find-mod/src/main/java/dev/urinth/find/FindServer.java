package dev.urinth.find;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The part of the mod that knows where everybody is: a server (a dedicated one, or the one inside a world you
 * host) has every player's position. Used for the /find command on the server, for requests from a client that
 * has the mod, and by the client itself in a world it hosts.
 */
public final class FindServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("urinth_find");

    private FindServer() {}

    /**
     * Who may use /find on a server. Read from config/urinth_find.properties, which a dedicated server creates
     * on its first start. Anything unreadable means operators only.
     */
    static int loadPermissionLevel() {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            Path file = loader.getConfigDir().resolve(FindConfig.FILE_NAME);
            if (Files.isRegularFile(file)) return FindConfig.permissionLevel(Files.readString(file));
            if (loader.getEnvironmentType() == EnvType.SERVER) Files.writeString(file, FindConfig.DEFAULT_TEXT);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read or write {}: {}", FindConfig.FILE_NAME, e.toString());
        }
        return FindConfig.DEFAULT_LEVEL;
    }

    static Located located(ServerPlayer player) {
        return new Located(player.getGameProfile().getName(), player.getX(), player.getY(), player.getZ(),
                player.level().dimension().location().toString());
    }

    /** Every player online, with position. Call on the server thread. */
    static List<Located> online(MinecraftServer server) {
        List<Located> all = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) all.add(located(player));
        return all;
    }

    /** A client with the mod asked where a player is: check it may ask, look the player up, answer. */
    static void answer(ServerPlayer requester, String typed, int permissionLevel) {
        FindReply reply;
        MinecraftServer server = requester.getServer();
        if (!requester.createCommandSourceStack().hasPermission(permissionLevel)) {
            reply = FindReply.noPermission();
        } else if (server == null) {
            reply = FindReply.notOnline(typed);
        } else {
            reply = FindLookup.lookup(typed, online(server));
        }
        ServerPlayNetworking.send(requester, new FindReplyPayload(reply));
    }

    /** /find and /Find on the server, for players whose game does not have the mod (and for the console). */
    static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, int permissionLevel) {
        for (String word : new String[] {"find", "Find"}) {
            dispatcher.register(Commands.literal(word)
                    .requires(source -> source.hasPermission(permissionLevel))
                    .executes(context -> {
                        context.getSource().sendSuccess(FindMessages::usage, false);
                        return 1;
                    })
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((context, builder) ->
                                    SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
                            .executes(context -> {
                                CommandSourceStack source = context.getSource();
                                String typed = StringArgumentType.getString(context, "player");
                                FindReply reply = FindLookup.lookup(typed, online(source.getServer()));
                                Component message = FindMessages.of(reply);
                                if (reply.isSuccess()) {
                                    source.sendSuccess(() -> message, false);
                                } else {
                                    source.sendFailure(message);
                                }
                                return reply.isSuccess() ? 1 : 0;
                            })));
        }
    }
}
