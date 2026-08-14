package com.sablednah.legendquest.network;

import java.util.LinkedHashMap;
import java.util.Map;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: the server's vocabulary (term.* and ui.* strings from
 * messages.yml), sent on login and after /reload. The panel, handbook and
 * HUD label themselves from this — when the server says races are
 * "Archetypes" and mana is "Energy", every modded client agrees.
 */
public record VocabPayload(Map<String, String> entries) implements CustomPacketPayload {

    public static final Type<VocabPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "vocab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VocabPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.entries.size());
                        p.entries.forEach((k, v) -> {
                            buf.writeUtf(k);
                            buf.writeUtf(v);
                        });
                    },
                    buf -> {
                        int count = buf.readVarInt();
                        Map<String, String> entries = new LinkedHashMap<>();
                        for (int n = 0; n < count; n++) {
                            entries.put(buf.readUtf(), buf.readUtf());
                        }
                        return new VocabPayload(entries);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
