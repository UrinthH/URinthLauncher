package dev.urinth.find;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Turns the plain {@link FindReply} text into coloured chat text. */
public final class FindMessages {
    private FindMessages() {}

    public static Component of(FindReply reply) {
        MutableComponent out = Component.empty();
        for (FindReply.Part part : reply.parts()) {
            out.append(Component.literal(part.text()).withStyle(format(part.tint())));
        }
        return out;
    }

    public static Component usage() {
        return Component.literal("Usage: /find <player>").withStyle(ChatFormatting.YELLOW);
    }

    private static ChatFormatting format(FindReply.Tint tint) {
        switch (tint) {
            case GRAY: return ChatFormatting.GRAY;
            case GOLD: return ChatFormatting.GOLD;
            case YELLOW: return ChatFormatting.YELLOW;
            case GREEN: return ChatFormatting.GREEN;
            case RED: return ChatFormatting.RED;
            case AQUA: return ChatFormatting.AQUA;
            case PURPLE: return ChatFormatting.LIGHT_PURPLE;
            default: return ChatFormatting.WHITE;
        }
    }
}
