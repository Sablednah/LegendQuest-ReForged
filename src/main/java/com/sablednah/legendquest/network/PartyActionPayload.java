package com.sablednah.legendquest.network;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: a party-tab button. Same rules and feedback as the
 * /party commands — the GUI only ever asks.
 */
public record PartyActionPayload(int action, String name) implements CustomPacketPayload {

    public static final int CREATE = 0;   // name ignored: auto-named
    public static final int INVITE = 1;   // name = player to invite
    public static final int ACCEPT = 2;
    public static final int DECLINE = 3;
    public static final int LEAVE = 4;
    public static final int TP = 5;

    public static final Type<PartyActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "party_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartyActionPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeByte(p.action);
                        buf.writeUtf(p.name);
                    },
                    buf -> new PartyActionPayload(buf.readByte(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
