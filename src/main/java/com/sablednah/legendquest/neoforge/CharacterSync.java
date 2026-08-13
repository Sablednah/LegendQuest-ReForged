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
        PacketDistributor.sendToPlayer(player, summarize(player));
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
                    long waitMs = SkillPhase.remainingMs(now, pc.lastUse(id), def.get().timing());
                    skills.add(new CharacterSummaryPayload.SkillEntry(
                            id.toString(),
                            def.get().name(),
                            def.get().type().name(),
                            def.get().description().orElse(""),
                            def.get().icon(),
                            def.get().costs().manaCost(),
                            (int) (def.get().timing().cooldownMs() / 1000),
                            grant.level(),
                            grant.cost(),
                            SkillEngine.owns(player, id, grant),
                            waitMs <= 0 ? 0 : (int) (waitMs / 1000 + 1)));
                });

        return new CharacterSummaryPayload(
                CharacterService.race(player).map(Race::name).orElse("Undecided"),
                CharacterService.mainClass(player).map(CharClass::name).orElse("Citizen"),
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
                skills,
                pc.loadout().stream().map(Identifier::toString).toList(),
                pc.loadoutIndex(),
                pc.loadoutItem().map(Identifier::toString).orElse(""),
                raceChoices(player, pc),
                classChoices(player, pc));
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
