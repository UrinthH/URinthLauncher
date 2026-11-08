package dev.urinth.find;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Operator mode of /find, the client side of it: on a server that does not have this mod, an operator may ask
 * the server where a player is with the vanilla command
 * <pre>
 * /data get entity &lt;player&gt; Pos
 * /data get entity &lt;player&gt; Dimension
 * </pre>
 * The server answers however far away the player is and in whichever dimension, so there is no distance limit.
 * The two answers arrive as ordinary chat lines; this class keeps them out of the chat, collects them in a
 * {@link DataQuery} and shows the usual two-line /find result instead. Only the game thread touches it.
 */
final class FindOperator {
    /** The key of the line a server prints for /data get entity: "%s has the following entity data: %s". */
    static final String QUERY_KEY = "commands.data.entity.query";

    private static DataQuery pending;

    private FindOperator() {}

    /** Sends the two commands and starts waiting for their answers. */
    static void start(Minecraft minecraft, String name) {
        if (minecraft.player == null) return;
        begin(name);
        minecraft.player.connection.sendCommand("data get entity " + name + " Pos");
        minecraft.player.connection.sendCommand("data get entity " + name + " Dimension");
    }

    /** Starts waiting for the answers about this player. A question still waiting is dropped. */
    static void begin(String name) {
        pending = new DataQuery(name, nowMs());
    }

    /** Stops waiting. */
    static void cancel() {
        pending = null;
    }

    /**
     * Called for every message from the server. False hides it: it is one of the two answers this class asked
     * for. Everything else is shown as usual.
     */
    static boolean allow(Component message, boolean overlay) {
        DataQuery query = pending;
        if (query == null || overlay) return true;
        DataReply.Seen seen = read(message);
        if (seen == null || !DataReply.isPlayer(seen.who(), query.name())) return true;
        return !query.accept(seen.value(), nowMs());
    }

    /** Called every game tick: shows the result once everything has arrived or the wait is over. */
    static void tick(Minecraft minecraft) {
        if (pending == null) return;
        if (minecraft.player == null || minecraft.level == null) {
            cancel();
            return;
        }
        FindReply reply = result(nowMs());
        if (reply != null) minecraft.player.displayClientMessage(FindMessages.of(reply), false);
    }

    /** What to show now, or null while the wait goes on. Showing it ends the wait. */
    static FindReply result(long nowMs) {
        DataQuery query = pending;
        if (query == null || !query.isDue(nowMs)) return null;
        pending = null;
        return query.reply();
    }

    /**
     * Who and what a /data get entity answer is about, or null for any other message. Only the real thing
     * counts: a message with the key above and exactly two values. The same words typed by another player are
     * plain text and are not taken for an answer.
     */
    static DataReply.Seen read(Component message) {
        if (message == null) return null;
        if (!(message.getContents() instanceof TranslatableContents translatable)) return null;
        if (!QUERY_KEY.equals(translatable.getKey())) return null;
        Object[] args = translatable.getArgs();
        if (args == null || args.length != 2) return null;
        return new DataReply.Seen(text(args[0]), text(args[1]));
    }

    private static String text(Object argument) {
        return argument instanceof Component component ? component.getString() : String.valueOf(argument);
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }
}
