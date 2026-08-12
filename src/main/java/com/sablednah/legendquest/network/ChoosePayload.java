package com.sablednah.legendquest.network;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the player clicked a race or class in the picker.
 * Runs through {@code CharacterActions} — identical checks and messages
 * to /race choose and /class choose.
 */
public record ChoosePayload(int kind, String id) implements CustomPacketPayload {

    public static final int RACE = 0;
    public static final int MAIN_CLASS = 1;

    public static final Type<ChoosePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "choose"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChoosePayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeByte(p.kind);
                        buf.writeUtf(p.id);
                    },
                    buf -> new ChoosePayload(buf.readByte(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
