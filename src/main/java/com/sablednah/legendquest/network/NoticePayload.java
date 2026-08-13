package com.sablednah.legendquest.network;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: a short notice worth showing OVER whatever GUI is open —
 * chat is blurred into uselessness behind the inventory, and that's exactly
 * where buy-rejection messages were going. Vanilla clients (no channel)
 * get plain chat instead; see Feedback.notify.
 */
public record NoticePayload(String message) implements CustomPacketPayload {

    public static final Type<NoticePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "notice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoticePayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.message),
                    buf -> new NoticePayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
