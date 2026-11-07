package dev.urinth.find;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: where is this player? Only sent to a server that has this mod. */
public record FindRequestPayload(String name) implements CustomPacketPayload {
    static final int MAX_NAME = 64;

    public static final CustomPacketPayload.Type<FindRequestPayload> TYPE =
            new CustomPacketPayload.Type<FindRequestPayload>(ResourceLocation.fromNamespaceAndPath("urinth_find", "request"));

    public static final StreamCodec<FriendlyByteBuf, FindRequestPayload> CODEC =
            new StreamCodec<FriendlyByteBuf, FindRequestPayload>() {
                @Override
                public FindRequestPayload decode(FriendlyByteBuf buf) {
                    return new FindRequestPayload(buf.readUtf(MAX_NAME));
                }

                @Override
                public void encode(FriendlyByteBuf buf, FindRequestPayload payload) {
                    buf.writeUtf(FindFormat.cut(payload.name(), MAX_NAME), MAX_NAME);
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
