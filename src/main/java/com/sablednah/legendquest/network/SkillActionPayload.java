package com.sablednah.legendquest.network;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: a hotkey or panel click asked for a skill action. Only
 * sent by modded clients that already received a summary (so the server
 * definitely speaks our channel). The server treats every action exactly
 * like the equivalent command — same checks, same feedback.
 */
public record SkillActionPayload(int action, int slot, String id) implements CustomPacketPayload {

    public static final int USE_SELECTED = 0;
    public static final int CYCLE = 1;
    public static final int USE_SLOT = 2;   // slot
    public static final int BUY_SKILL = 3;  // id = skill id
    public static final int BUY_STAT = 4;   // id = stat key ("str".."chr")

    public static final Type<SkillActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "skill_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillActionPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeByte(p.action);
                        buf.writeByte(p.slot);
                        buf.writeUtf(p.id);
                    },
                    buf -> new SkillActionPayload(buf.readByte(), buf.readByte(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
