package com.sablednah.legendquest.network;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: something dramatic happened at a world position — pop a
 * floating word there. The client picks the actual word, colour and jitter
 * (cosmetics are client business); the server only says what kind of drama.
 */
public record CombatIndicatorPayload(double x, double y, double z, int kind)
        implements CustomPacketPayload {

    public static final int MISS = 0;    // the d20 said no
    public static final int HIT = 1;     // clean hit
    public static final int CRIT = 2;    // natural 20
    public static final int FUMBLE = 3;  // wrong weapon for your class

    public static final Type<CombatIndicatorPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "combat_indicator"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatIndicatorPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeDouble(p.x);
                        buf.writeDouble(p.y);
                        buf.writeDouble(p.z);
                        buf.writeByte(p.kind);
                    },
                    buf -> new CombatIndicatorPayload(buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
