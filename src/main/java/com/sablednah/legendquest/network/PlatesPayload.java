package com.sablednah.legendquest.network;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: the entity ids of every nameplate, all of them, whenever
 * the set changes and at login. The client needs them to leave the plates out
 * of a shader pack's shadow pass (see client.PlateRenderer); entity tags never
 * reach a client, and no synced field of a text display says "this one".
 */
public record PlatesPayload(int[] ids) implements CustomPacketPayload {

    public static final Type<PlatesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "plates"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlatesPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarIntArray(p.ids),
                    buf -> new PlatesPayload(buf.readVarIntArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
