package dev.urinth.boost;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** /uboost in the chat of the game. It is a client command: the server never sees it. */
final class BoostCommand {
    private BoostCommand() {}

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("uboost")
                .executes(context -> {
                    status(context.getSource());
                    return 1;
                });
        for (BoostMode mode : BoostMode.values()) {
            root.then(ClientCommandManager.literal(mode.word()).executes(context -> {
                setMode(context.getSource(), mode);
                return 1;
            }));
        }
        root.then(ClientCommandManager.literal("target").then(
                ClientCommandManager.argument("fps", IntegerArgumentType.integer(BoostConfig.MIN_TARGET, BoostConfig.MAX_TARGET))
                        .executes(context -> {
                            setTarget(context.getSource(), IntegerArgumentType.getInteger(context, "fps"));
                            return 1;
                        })));
        root.then(ClientCommandManager.literal("players")
                .executes(context -> {
                    sayPlayers(context.getSource());
                    return 1;
                })
                .then(ClientCommandManager.literal("auto").executes(context -> {
                    setPlayers(context.getSource(), BoostConfig.PLAYERS_AUTO);
                    return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(context -> {
                    setPlayers(context.getSource(), BoostConfig.PLAYERS_OFF);
                    return 1;
                }))
                .then(ClientCommandManager.argument("count",
                                IntegerArgumentType.integer(BoostConfig.MIN_PLAYERS, BoostConfig.MAX_PLAYERS))
                        .executes(context -> {
                            setPlayers(context.getSource(), IntegerArgumentType.getInteger(context, "count"));
                            return 1;
                        })));
        root.then(ClientCommandManager.literal("bench").executes(context -> {
            bench(context.getSource());
            return 1;
        }));
        dispatcher.register(root);
    }

    private static void say(FabricClientCommandSource source, String line) {
        source.sendFeedback(Component.literal(line));
    }

    private static void status(FabricClientCommandSource source) {
        BoostRuntime runtime = BoostGame.runtime();
        if (runtime == null) {
            say(source, "URinth Boost did not start. See the game log.");
            return;
        }
        for (String line : BoostText.status(runtime)) say(source, line);
    }

    private static void setMode(FabricClientCommandSource source, BoostMode mode) {
        BoostRuntime runtime = BoostGame.runtime();
        if (runtime == null) return;
        runtime.setMode(mode);
        BoostGame.save();
        if (mode.isAuto()) {
            say(source, "Boost is automatic: it takes things away only while your frame rate is below "
                    + runtime.target() + ". Change that with /uboost target <fps>.");
        } else if (mode == BoostMode.OFF) {
            say(source, "Boost is off: nothing is taken away.");
        } else {
            say(source, "Boost is held at " + mode.word() + ". /uboost auto lets it decide by itself.");
            say(source, BoostText.describe(runtime.limits()));
            say(source, BoostText.playersLine(runtime));
        }
    }

    private static void sayPlayers(FabricClientCommandSource source) {
        BoostRuntime runtime = BoostGame.runtime();
        if (runtime == null) return;
        say(source, BoostText.playersLine(runtime));
        say(source, "Change it: /uboost players auto | off | <" + BoostConfig.MIN_PLAYERS + " to " + BoostConfig.MAX_PLAYERS
                + ">. A number draws only that many of the nearest other players, always.");
    }

    private static void setPlayers(FabricClientCommandSource source, int setting) {
        BoostRuntime runtime = BoostGame.runtime();
        if (runtime == null) return;
        runtime.setPlayersSetting(setting);
        BoostGame.save();
        say(source, BoostText.playersChanged(runtime));
    }

    private static void setTarget(FabricClientCommandSource source, int fps) {
        BoostRuntime runtime = BoostGame.runtime();
        if (runtime == null) return;
        runtime.setTarget(fps);
        BoostGame.save();
        say(source, "In automatic mode Boost aims for " + runtime.target() + " fps.");
    }

    private static void bench(FabricClientCommandSource source) {
        BoostRuntime runtime = BoostGame.runtime();
        Minecraft minecraft = Minecraft.getInstance();
        if (runtime == null) return;
        if (minecraft.level == null || minecraft.player == null) {
            say(source, "Join a world or a server first, stand still where it is busy, then run /uboost bench.");
            return;
        }
        if (!runtime.startBench()) {
            say(source, "Boost switched itself off, so there is nothing to test. See /uboost.");
            return;
        }
        say(source, "Boost test started, " + Bench.totalSeconds() + " seconds. Stand still and do not open menus. It switches "
                + "Boost off and on a few times and then tells you what it measured.");
    }
}
