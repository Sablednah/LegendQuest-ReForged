package com.sablednah.legendquest.network;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the dice tray threw a die. Carries the same notation a
 * player would type after {@code /roll} ("d20 str adv"), so the server parses
 * it with the same code and the tray can ask for nothing a typed roll cannot.
 */
public record RollPayload(String notation) implements CustomPacketPayload {

    public static final Type<RollPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "roll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RollPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.notation, 64),
                    buf -> new RollPayload(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
