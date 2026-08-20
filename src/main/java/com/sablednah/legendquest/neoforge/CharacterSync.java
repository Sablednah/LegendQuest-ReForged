package com.sablednah.legendquest.neoforge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.sablednah.legendquest.LQRegistries;
import com.sablednah.legendquest.character.PlayerCharacter;
import com.sablednah.legendquest.core.SkillPhase;
import com.sablednah.legendquest.core.Stat;
import com.sablednah.legendquest.data.CharClass;
import com.sablednah.legendquest.data.Race;
import com.sablednah.legendquest.data.SkillGrant;
import com.sablednah.legendquest.network.CharacterSummaryPayload;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Builds and pushes the client character summary. Vanilla clients simply
 * never registered the channel ({@code optional()}), so sends to them are
 * dropped silently — everything important also exists as chat/action bar.
 */
public final class CharacterSync {

    public static void send(ServerPlayer player) {
        Net.sendIfAble(player, summarize(player));
        // Every meaningful character change already funnels through here, so
        // this is where the plate stays honest. refresh() no-ops unless the
        // rendered text actually changed -- this also runs on the 1/s mana
        // tick, which must not become a team packet per player per second.
        Nameplate.refresh(player);
    }

    private static CharacterSummaryPayload summarize(ServerPlayer player) {
        PlayerCharacter pc = CharacterService.data(player);
        var stats = CharacterService.effectiveStats(player);
        int[] statArray = new int[6];
        for (Stat stat : Stat.values()) statArray[stat.ordinal()] = stats.get(stat);

        long now = System.currentTimeMillis();
        List<CharacterSummaryPayload.SkillEntry> skills = new ArrayList<>();
        SkillEngine.grants(player).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Identifier id = entry.getKey();
                    SkillGrant grant = entry.getValue();
                    var def = SkillEngine.definition(player, id);
                    if (def.isEmpty()) return;
                    var timing = def.get().timing();
                    long last = pc.lastUse(id);
                    long waitMs = SkillPhase.remainingMs(now, last, timing);
                    int activeFor = 0;
                    if (SkillPhase.at(now, last, timing) == SkillPhase.ACTIVE) {
                        long activeEnd = last + timing.buildupMs() + timing.delayMs() + timing.durationMs();
                        activeFor = (int) Math.max(1, (activeEnd - now) / 1000 + 1);
                    }
                    String karmaNote = "";
                    if (grant.hasKarmaBand() && !grant.karmaAllows(pc.karma())) {
                        karmaNote = pc.karma() < grant.karmaMin()
                                ? Lang.fmt("msg.karma.needs_min", "value", grant.karmaMin())
                                : Lang.fmt("msg.karma.needs_max", "value", grant.karmaMax());
                    }
                    skills.add(new CharacterSummaryPayload.SkillEntry(
                            id.toString(),
                            def.get().name(),
                            def.get().type().name(),
                            def.get().description().orElse(""),
                            def.get().icon(),
                            def.get().costs().manaCost(),
                            (int) (timing.cooldownMs() / 1000),
                            grant.level(),
                            grant.cost(),
                            SkillEngine.owns(player, id, grant),
                            waitMs <= 0 ? 0 : (int) (waitMs / 1000 + 1),
                            (int) (timing.durationMs() / 1000),
                            activeFor,
                            karmaNote));
                });

        return new CharacterSummaryPayload(
                CharacterService.race(player).map(Race::name).orElse(Lang.get("msg.stats.undecided")),
                CharacterService.mainClass(player).map(CharClass::name).orElse(Lang.get("msg.stats.citizen")),
                CharacterService.subClass(player).map(CharClass::name).orElse(""),
                CharacterService.level(player),
                xpProgress(player, pc),
                CharacterService.karmaName(pc.karma()),
                (float) pc.mana(),
                (float) CharacterService.maxMana(player),
                statArray,
                pc.skillPointsSpent(),
                CharacterService.skillPointsTotal(player),
                CharacterService.nextStatBoostCost(player),
                (float) CharacterService.totalBoon(player, b -> b.goldToolMana()),
                skills,
                pc.loadout().stream().map(Identifier::toString).toList(),
                pc.loadoutIndex(),
                pc.loadoutItem().map(Identifier::toString).orElse(""),
                raceChoices(player, pc),
                classChoices(player, pc),
                List.copyOf(pc.featIds()),
                partyName(player),
                partyMembers(player),
                partyInvite(player),
                partyInvitable(player));
    }

    // --- party section ---

    private static String partyName(ServerPlayer player) {
        return Parties.get(player.level().getServer())
                .partyOf(player.getUUID()).map(Parties.Party::name).orElse("");
    }

    private static List<CharacterSummaryPayload.PartyMember> partyMembers(ServerPlayer player) {
        var server = player.level().getServer();
        var party = Parties.get(server).partyOf(player.getUUID());
        if (party.isEmpty()) return List.of();
        List<CharacterSummaryPayload.PartyMember> out = new ArrayList<>();
        for (var memberId : party.get().members()) {
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            String name = online != null ? online.getName().getString()
                    : memberId.toString().substring(0, 8) + "…";
            out.add(new CharacterSummaryPayload.PartyMember(name, online != null,
                    memberId.equals(party.get().owner()), memberId.equals(player.getUUID())));
        }
        return out;
    }

    private static String partyInvite(ServerPlayer player) {
        return Parties.get(player.level().getServer())
                .pendingInvite(player.getUUID()).map(Parties.Party::name).orElse("");
    }

    /** Leaders see who they could invite: online, un-partied, not them. */
    private static List<String> partyInvitable(ServerPlayer player) {
        var server = player.level().getServer();
        var parties = Parties.get(server);
        var party = parties.partyOf(player.getUUID());
        if (party.isEmpty() || !party.get().owner().equals(player.getUUID())) return List.of();
        List<String> out = new ArrayList<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player || parties.partyOf(other.getUUID()).isPresent()) continue;
            out.add(other.getName().getString());
            if (out.size() >= 8) break;
        }
        return out;
    }

    /** How far into the current level, 0..1 (pegged at 1 at the level cap). */
    private static float xpProgress(ServerPlayer player, PlayerCharacter pc) {
        long base = com.sablednah.legendquest.LQConfig.XP_LEVEL_BASE.get();
        int maxLevel = com.sablednah.legendquest.LQConfig.MAX_LEVEL.get();
        int level = CharacterService.level(player);
        if (level >= maxLevel) return 1.0F;
        long xp = pc.mainClassId().map(pc::xpFor).orElse(0L);
        long floor = com.sablednah.legendquest.core.Leveling.totalXpForLevel(level, base);
        long ceiling = com.sablednah.legendquest.core.Leveling.totalXpForLevel(level + 1, base);
        if (ceiling <= floor) return 1.0F;
        return Math.max(0.0F, Math.min(1.0F, (xp - floor) / (float) (ceiling - floor)));
    }

    /** Non-empty only while the race choice is still open. Default races
     *  ("Undecided") are the placeholder, not an option. */
    private static List<CharacterSummaryPayload.PickEntry> raceChoices(
            ServerPlayer player, PlayerCharacter pc) {
        boolean onDefault = CharacterService.race(player).map(Race::isDefault).orElse(true);
        if (pc.raceChanged() || !onDefault) return List.of();
        List<CharacterSummaryPayload.PickEntry> out = new ArrayList<>();
        player.level().registryAccess().lookupOrThrow(LQRegistries.RACE).listElements()
                .filter(ref -> !ref.value().isDefault())
                .sorted(Comparator.comparing(ref -> ref.value().name()))
                .forEach(ref -> out.add(new CharacterSummaryPayload.PickEntry(
                        ref.key().identifier().toString(),
                        ref.value().name(),
                        describe(ref.value().identity().description(),
                                ref.value().identity().longDescription()),
                        LQPermissions.canSelectRace(player, ref.key().identifier()))));
        return out;
    }

    /** Non-empty while the main class is still the default one. */
    private static List<CharacterSummaryPayload.PickEntry> classChoices(
            ServerPlayer player, PlayerCharacter pc) {
        boolean onDefault = CharacterService.mainClass(player).map(CharClass::isDefault).orElse(true);
        if (!onDefault) return List.of();
        List<CharacterSummaryPayload.PickEntry> out = new ArrayList<>();
        player.level().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS).listElements()
                .filter(ref -> !ref.value().isDefault())
                .sorted(Comparator.comparing(ref -> ref.value().name()))
                .forEach(ref -> out.add(new CharacterSummaryPayload.PickEntry(
                        ref.key().identifier().toString(),
                        ref.value().name(),
                        describe(ref.value().identity().description(),
                                ref.value().identity().longDescription()),
                        CharacterActions.classAvailable(player, pc,
                                ref.key().identifier(), ref.value()))));
        return out;
    }

    private static String describe(java.util.Optional<String> description,
            java.util.Optional<String> longDescription) {
        return description.orElse(longDescription.orElse(""));
    }

    private CharacterSync() {}
}
