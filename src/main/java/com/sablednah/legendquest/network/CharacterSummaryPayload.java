package com.sablednah.legendquest.network;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Everything the client HUD/panel needs to draw the character sheet.
 * Server → client, pushed on login, on character changes, and once a
 * second (mana and cooldowns move constantly anyway). Plain data only —
 * deliberately free of Minecraft world types so both sides can hold it.
 */
public record CharacterSummaryPayload(
        String raceName,
        String mainClassName,
        String subClassName,   // empty = none
        int level,
        String karmaName,
        float mana,
        float maxMana,
        int[] stats,           // STR DEX CON INT WIS CHR effective scores
        int spSpent,
        int spTotal,
        List<SkillEntry> skills) implements CustomPacketPayload {

    /** One row of the skill list, ready to render. */
    public record SkillEntry(String name, String type, int levelReq, int cost,
            boolean owned, int readyInSec) {}

    public static final Type<CharacterSummaryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "character_summary"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterSummaryPayload> CODEC =
            StreamCodec.of(CharacterSummaryPayload::encode, CharacterSummaryPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, CharacterSummaryPayload p) {
        buf.writeUtf(p.raceName);
        buf.writeUtf(p.mainClassName);
        buf.writeUtf(p.subClassName);
        buf.writeVarInt(p.level);
        buf.writeUtf(p.karmaName);
        buf.writeFloat(p.mana);
        buf.writeFloat(p.maxMana);
        for (int n = 0; n < 6; n++) buf.writeVarInt(p.stats[n]);
        buf.writeVarInt(p.spSpent);
        buf.writeVarInt(p.spTotal);
        buf.writeVarInt(p.skills.size());
        for (SkillEntry s : p.skills) {
            buf.writeUtf(s.name());
            buf.writeUtf(s.type());
            buf.writeVarInt(s.levelReq());
            buf.writeVarInt(s.cost());
            buf.writeBoolean(s.owned());
            buf.writeVarInt(s.readyInSec());
        }
    }

    private static CharacterSummaryPayload decode(RegistryFriendlyByteBuf buf) {
        String race = buf.readUtf();
        String main = buf.readUtf();
        String sub = buf.readUtf();
        int level = buf.readVarInt();
        String karma = buf.readUtf();
        float mana = buf.readFloat();
        float maxMana = buf.readFloat();
        int[] stats = new int[6];
        for (int n = 0; n < 6; n++) stats[n] = buf.readVarInt();
        int spSpent = buf.readVarInt();
        int spTotal = buf.readVarInt();
        int count = buf.readVarInt();
        List<SkillEntry> skills = new ArrayList<>(count);
        for (int n = 0; n < count; n++) {
            skills.add(new SkillEntry(buf.readUtf(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));
        }
        return new CharacterSummaryPayload(race, main, sub, level, karma,
                mana, maxMana, stats, spSpent, spTotal, skills);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
