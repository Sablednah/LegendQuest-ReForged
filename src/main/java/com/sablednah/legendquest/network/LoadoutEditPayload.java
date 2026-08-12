package com.sablednah.legendquest.network;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the skills panel edited the loadout (drag & drop or
 * click). Validated server-side by the same rules as /loadout — the GUI is
 * a request, never an authority.
 */
public record LoadoutEditPayload(int action, String skillId, int from, int to)
        implements CustomPacketPayload {

    public static final int ADD = 0;      // skillId (+to = insert position, -1 = end)
    public static final int REMOVE = 1;   // skillId
    public static final int MOVE = 2;     // from -> to
    public static final int SELECT = 3;   // to = slot

    public static final Type<LoadoutEditPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "loadout_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoadoutEditPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeByte(p.action);
                        buf.writeUtf(p.skillId);
                        buf.writeVarInt(p.from);
                        buf.writeVarInt(p.to);
                    },
                    buf -> new LoadoutEditPayload(buf.readByte(), buf.readUtf(),
                            buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
