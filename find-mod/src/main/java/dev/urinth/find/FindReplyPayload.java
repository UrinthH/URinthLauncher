package dev.urinth.find;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the answer to a {@link FindRequestPayload}. */
public record FindReplyPayload(FindReply reply) implements CustomPacketPayload {
    private static final int MAX_NAME = 64;
    private static final int MAX_DIMENSION = 256;
    private static final int MAX_OTHERS = 512;

    public static final CustomPacketPayload.Type<FindReplyPayload> TYPE =
            new CustomPacketPayload.Type<FindReplyPayload>(ResourceLocation.fromNamespaceAndPath("urinth_find", "reply"));

    public static final StreamCodec<FriendlyByteBuf, FindReplyPayload> CODEC =
            new StreamCodec<FriendlyByteBuf, FindReplyPayload>() {
                @Override
                public FindReplyPayload decode(FriendlyByteBuf buf) {
                    int status = buf.readVarInt();
                    String name = buf.readUtf(MAX_NAME);
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    String dimension = buf.readUtf(MAX_DIMENSION);
                    String others = buf.readUtf(MAX_OTHERS);
                    return new FindReplyPayload(new FindReply(status, name, x, y, z, dimension, others));
                }

                @Override
                public void encode(FriendlyByteBuf buf, FindReplyPayload payload) {
                    FindReply reply = payload.reply();
                    buf.writeVarInt(reply.status());
                    buf.writeUtf(FindFormat.cut(reply.name(), MAX_NAME), MAX_NAME);
                    buf.writeDouble(reply.x());
                    buf.writeDouble(reply.y());
                    buf.writeDouble(reply.z());
                    buf.writeUtf(FindFormat.cut(reply.dimension(), MAX_DIMENSION), MAX_DIMENSION);
                    buf.writeUtf(FindFormat.cut(reply.others(), MAX_OTHERS), MAX_OTHERS);
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
