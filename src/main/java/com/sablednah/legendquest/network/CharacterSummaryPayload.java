package com.sablednah.legendquest.network;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Everything the client HUD/panels need to draw the character sheet.
 * Server → client, pushed on login, on character changes, and once a
 * second (mana and cooldowns move constantly anyway). Plain data only —
 * deliberately free of Minecraft world types so both sides can hold it.
 *
 * <p>The picker catalogs ({@code raceChoices}/{@code classChoices}) are only
 * populated while that choice is still open — once you're an Elf for life,
 * the race list stops travelling.</p>
 */
public record CharacterSummaryPayload(
        String raceName,
        String mainClassName,
        String subClassName,   // empty = none
        int level,
        float xpProgress,      // 0..1 into the current level
        String karmaName,
        float mana,
        float maxMana,
        int[] stats,           // STR DEX CON INT WIS CHR effective scores
        int spSpent,
        int spTotal,
        int statBoostCost,     // price of the next +1 stat, in skill points
        float goldToolMana,    // arcane conduit boon: mana per boosted block (0 = no boon)
        List<SkillEntry> skills,
        List<String> loadout,  // skill ids, in order
        int loadoutIndex,
        String loadoutItem,    // item id, empty = no spellbook bound
        List<PickEntry> raceChoices,   // empty = race locked in
        List<PickEntry> classChoices,  // empty = class chosen
        List<String> ownedFeats,       // feat ids already bought
        String partyName,              // "" = not in a party
        List<PartyMember> partyMembers,
        String partyInvite,            // party name inviting us, "" = none
        List<String> partyInvitable)   // online un-partied players (leader only)
        implements CustomPacketPayload {

    /** One row of the party member list. */
    public record PartyMember(String name, boolean online, boolean leader, boolean self) {}

    /** One row of the skill list, ready to render. */
    public record SkillEntry(String id, String name, String type, String description,
            String icon, int manaCost, int cooldownSec, int levelReq, int cost,
            boolean owned, int readyInSec,
            int durationSec,     // timing duration (0 = instant skill)
            int activeForSec,    // seconds of ACTIVE remaining right now
            String karmaNote,    // "" or "needs karma ≥ 50" — the REAL lock reason
            boolean enabled,     // false = the player switched it off themselves
            boolean toggleable) {} // false = nothing to switch (active, or a fixed drawback)

    /** One clickable option in the race/class picker. */
    public record PickEntry(String id, String name, String description, boolean available) {}

    public static final Type<CharacterSummaryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "character_summary"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterSummaryPayload> CODEC =
            StreamCodec.of(CharacterSummaryPayload::encode, CharacterSummaryPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, CharacterSummaryPayload p) {
        buf.writeUtf(p.raceName);
        buf.writeUtf(p.mainClassName);
        buf.writeUtf(p.subClassName);
        buf.writeVarInt(p.level);
        buf.writeFloat(p.xpProgress);
        buf.writeUtf(p.karmaName);
        buf.writeFloat(p.mana);
        buf.writeFloat(p.maxMana);
        for (int n = 0; n < 6; n++) buf.writeVarInt(p.stats[n]);
        buf.writeVarInt(p.spSpent);
        buf.writeVarInt(p.spTotal);
        buf.writeVarInt(p.statBoostCost);
        buf.writeFloat(p.goldToolMana);
        buf.writeVarInt(p.skills.size());
        for (SkillEntry s : p.skills) {
            buf.writeUtf(s.id());
            buf.writeUtf(s.name());
            buf.writeUtf(s.type());
            buf.writeUtf(s.description());
            buf.writeUtf(s.icon());
            buf.writeVarInt(s.manaCost());
            buf.writeVarInt(s.cooldownSec());
            buf.writeVarInt(s.levelReq());
            buf.writeVarInt(s.cost());
            buf.writeBoolean(s.owned());
            buf.writeVarInt(s.readyInSec());
            buf.writeVarInt(s.durationSec());
            buf.writeVarInt(s.activeForSec());
            buf.writeUtf(s.karmaNote());
            buf.writeBoolean(s.enabled());
            buf.writeBoolean(s.toggleable());
        }
        buf.writeVarInt(p.loadout.size());
        for (String id : p.loadout) buf.writeUtf(id);
        buf.writeVarInt(p.loadoutIndex);
        buf.writeUtf(p.loadoutItem);
        writePicks(buf, p.raceChoices);
        writePicks(buf, p.classChoices);
        buf.writeVarInt(p.ownedFeats.size());
        for (String feat : p.ownedFeats) buf.writeUtf(feat);
        buf.writeUtf(p.partyName);
        buf.writeVarInt(p.partyMembers.size());
        for (PartyMember m : p.partyMembers) {
            buf.writeUtf(m.name());
            buf.writeBoolean(m.online());
            buf.writeBoolean(m.leader());
            buf.writeBoolean(m.self());
        }
        buf.writeUtf(p.partyInvite);
        buf.writeVarInt(p.partyInvitable.size());
        for (String name : p.partyInvitable) buf.writeUtf(name);
    }

    private static CharacterSummaryPayload decode(RegistryFriendlyByteBuf buf) {
        String race = buf.readUtf();
        String mainClass = buf.readUtf();
        String subClass = buf.readUtf();
        int level = buf.readVarInt();
        float xpProgress = buf.readFloat();
        String karma = buf.readUtf();
        float mana = buf.readFloat();
        float maxMana = buf.readFloat();
        int[] stats = new int[6];
        for (int n = 0; n < 6; n++) stats[n] = buf.readVarInt();
        int spSpent = buf.readVarInt();
        int spTotal = buf.readVarInt();
        int statBoostCost = buf.readVarInt();
        float goldToolMana = buf.readFloat();
        int skillCount = buf.readVarInt();
        List<SkillEntry> skills = new ArrayList<>(skillCount);
        for (int n = 0; n < skillCount; n++) {
            skills.add(new SkillEntry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
                    buf.readBoolean(), buf.readBoolean()));
        }
        int loadoutCount = buf.readVarInt();
        List<String> loadout = new ArrayList<>(loadoutCount);
        for (int n = 0; n < loadoutCount; n++) loadout.add(buf.readUtf());
        int loadoutIndex = buf.readVarInt();
        String loadoutItem = buf.readUtf();
        List<PickEntry> raceChoices = readPicks(buf);
        List<PickEntry> classChoices = readPicks(buf);
        int featCount = buf.readVarInt();
        List<String> ownedFeats = new ArrayList<>(featCount);
        for (int n = 0; n < featCount; n++) ownedFeats.add(buf.readUtf());
        String partyName = buf.readUtf();
        int memberCount = buf.readVarInt();
        List<PartyMember> partyMembers = new ArrayList<>(memberCount);
        for (int n = 0; n < memberCount; n++) {
            partyMembers.add(new PartyMember(buf.readUtf(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean()));
        }
        String partyInvite = buf.readUtf();
        int invitableCount = buf.readVarInt();
        List<String> partyInvitable = new ArrayList<>(invitableCount);
        for (int n = 0; n < invitableCount; n++) partyInvitable.add(buf.readUtf());
        return new CharacterSummaryPayload(race, mainClass, subClass, level, xpProgress, karma, mana, maxMana,
                stats, spSpent, spTotal, statBoostCost, goldToolMana, skills, loadout, loadoutIndex, loadoutItem,
                raceChoices, classChoices, ownedFeats,
                partyName, partyMembers, partyInvite, partyInvitable);
    }

    private static void writePicks(RegistryFriendlyByteBuf buf, List<PickEntry> picks) {
        buf.writeVarInt(picks.size());
        for (PickEntry pick : picks) {
            buf.writeUtf(pick.id());
            buf.writeUtf(pick.name());
            buf.writeUtf(pick.description());
            buf.writeBoolean(pick.available());
        }
    }

    private static List<PickEntry> readPicks(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<PickEntry> picks = new ArrayList<>(count);
        for (int n = 0; n < count; n++) {
            picks.add(new PickEntry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }
        return picks;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
