package com.sablednah.legendquest.network;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.legendquest.LegendQuest;
import com.sablednah.legendquest.core.Dice;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → the roller's client: what their roll came to, so the dice tray can
 * land on the number the server already chose. Sent for typed rolls too.
 *
 * @param statKey "" for no stat, else {@code Stat.key()}
 * @param edge    {@code Dice.Edge} ordinal
 * @param dice    every die kept, in order
 * @param dropped the die advantage or disadvantage threw away, 0 if none
 * @param flair   {@link #PLAIN}, {@link #NAT_20} or {@link #NAT_1}
 */
public record RollResultPayload(int sides, String statKey, int statMod, int edge,
        List<Integer> dice, int dropped, int total, int flair) implements CustomPacketPayload {

    public static final int PLAIN = 0;
    public static final int NAT_20 = 1;
    public static final int NAT_1 = 2;

    public static final Type<RollResultPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "roll_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RollResultPayload> CODEC =
            StreamCodec.of(RollResultPayload::encode, RollResultPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, RollResultPayload p) {
        buf.writeVarInt(p.sides);
        buf.writeUtf(p.statKey, 8);
        buf.writeVarInt(p.statMod);
        buf.writeByte(p.edge);
        buf.writeVarInt(p.dice.size());
        for (int d : p.dice) buf.writeVarInt(d);
        buf.writeVarInt(p.dropped);
        buf.writeVarInt(p.total);
        buf.writeByte(p.flair);
    }

    private static RollResultPayload decode(RegistryFriendlyByteBuf buf) {
        int sides = buf.readVarInt();
        String statKey = buf.readUtf(8);
        int statMod = buf.readVarInt();
        int edge = buf.readByte();
        int count = Math.min(buf.readVarInt(), Dice.MAX_COUNT);
        List<Integer> dice = new ArrayList<>(count);
        for (int n = 0; n < count; n++) dice.add(buf.readVarInt());
        return new RollResultPayload(sides, statKey, statMod, edge, List.copyOf(dice),
                buf.readVarInt(), buf.readVarInt(), buf.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
