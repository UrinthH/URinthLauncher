package dev.urinth.find;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class WireTest {
    private static FindReply found() {
        return new FindReply(FindReply.OK, "Notkiahh", 128.3, 63.0, -1073.0, "minecraft:overworld", "");
    }

    @Test
    void replyTravelsUnchanged() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        FindReplyPayload.CODEC.encode(buf, new FindReplyPayload(found()));
        FindReplyPayload received = FindReplyPayload.CODEC.decode(buf);
        assertEquals(found(), received.reply());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void listOfMatchesTravelsUnchanged() {
        FindReply sent = FindReply.ambiguous("no", java.util.List.of("Nora", "Notkiahh", "Noah"));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        FindReplyPayload.CODEC.encode(buf, new FindReplyPayload(sent));
        assertEquals(sent, FindReplyPayload.CODEC.decode(buf).reply());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void requestTravelsUnchanged() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        FindRequestPayload.CODEC.encode(buf, new FindRequestPayload("Notkiahh"));
        assertEquals("Notkiahh", FindRequestPayload.CODEC.decode(buf).name());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void aVeryLongNameIsCutNotRejected() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        FindRequestPayload.CODEC.encode(buf, new FindRequestPayload("x".repeat(500)));
        assertEquals(64, FindRequestPayload.CODEC.decode(buf).name().length());
    }

    @Test
    void theChatTextIsAsRequested() {
        assertEquals("XYZ: x128.30 y63 z-1073\nOverworld < Notkiahh", FindMessages.of(found()).getString());
    }

    @Test
    void theMessageChannelsAreNamedAfterTheMod() {
        assertEquals("urinth_find:request", FindRequestPayload.TYPE.id().toString());
        assertEquals("urinth_find:reply", FindReplyPayload.TYPE.id().toString());
    }

    @Test
    void theServerCommandExistsInBothSpellings() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        FindServer.registerCommands(dispatcher, 2);
        for (String word : new String[] {"find", "Find"}) {
            CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(word);
            assertNotNull(node);
            assertNotNull(node.getCommand());
            CommandNode<CommandSourceStack> player = node.getChild("player");
            assertNotNull(player);
            assertNotNull(player.getCommand());
        }
    }
}
