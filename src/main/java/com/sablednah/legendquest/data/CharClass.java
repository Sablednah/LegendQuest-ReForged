package com.sablednah.legendquest.data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One character class, from {@code data/<pack>/legendquest/class/<name>.json}
 * or {@code config/legendquest/classes/<name>.yml}.
 *
 * <p>Named {@code CharClass} because {@code Class} is taken. Dual-classing:
 * a player has a main class and optionally a sub class; {@code mainOnly} /
 * {@code subOnly} restrict where a class may sit, and {@code requires} /
 * {@code requiresOne} build class-dependency chains ("Warlord requires
 * fighter or ranger"), checked against <em>mastered</em> classes.</p>
 */
public record CharClass(
        Race.Identity identity,
        Growth growth,
        StatBlock statmods,
        Eligibility eligibility,
        int frequency,
        boolean isDefault,
        Optional<String> perm,
        CraftRules craftRules,
        ItemRules itemRules,
        Race.Progression progression,
        Boons boons,
        LevelTitles titles) {

    /** How the class scales the race's base numbers. */
    public record Growth(double healthMod, double healthPerLevel, double manaBonus,
            double manaPerLevel, double manaPerSecond, double speedMod) {

        public static final MapCodec<Growth> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("health_mod", 0.0D).forGetter(Growth::healthMod),
                Codec.DOUBLE.optionalFieldOf("health_per_level", 0.0D).forGetter(Growth::healthPerLevel),
                Codec.DOUBLE.optionalFieldOf("mana_bonus", 0.0D).forGetter(Growth::manaBonus),
                Codec.DOUBLE.optionalFieldOf("mana_per_level", 0.0D).forGetter(Growth::manaPerLevel),
                Codec.DOUBLE.optionalFieldOf("mana_per_second", 0.0D).forGetter(Growth::manaPerSecond),
                Codec.DOUBLE.optionalFieldOf("speed_mod", 0.0D).forGetter(Growth::speedMod))
                .apply(i, Growth::new));
    }

    /** Who may take this class, and where it may sit. */
    public record Eligibility(
            List<Identifier> allowedRaces,
            List<String> allowedGroups,
            List<Identifier> requires,
            List<Identifier> requiresOne,
            boolean mainOnly,
            boolean subOnly) {

        public static final Eligibility ANY =
                new Eligibility(List.of(), List.of(), List.of(), List.of(), false, false);

        public static final MapCodec<Eligibility> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.listOf().optionalFieldOf("allowed_races", List.of())
                        .forGetter(Eligibility::allowedRaces),
                Codec.STRING.listOf().optionalFieldOf("allowed_groups", List.of())
                        .forGetter(Eligibility::allowedGroups),
                Identifier.CODEC.listOf().optionalFieldOf("requires", List.of())
                        .forGetter(Eligibility::requires),
                Identifier.CODEC.listOf().optionalFieldOf("requires_one", List.of())
                        .forGetter(Eligibility::requiresOne),
                Codec.BOOL.optionalFieldOf("main_only", false).forGetter(Eligibility::mainOnly),
                Codec.BOOL.optionalFieldOf("sub_only", false).forGetter(Eligibility::subOnly))
                .apply(i, Eligibility::new));
    }

    public static final Codec<CharClass> CODEC = RecordCodecBuilder.create(i -> i.group(
            Race.Identity.MAP_CODEC.forGetter(CharClass::identity),
            Growth.MAP_CODEC.forGetter(CharClass::growth),
            StatBlock.CODEC.optionalFieldOf("statmods", StatBlock.ZERO).forGetter(CharClass::statmods),
            Eligibility.MAP_CODEC.forGetter(CharClass::eligibility),
            Codec.INT.optionalFieldOf("frequency", 100).forGetter(CharClass::frequency),
            Codec.BOOL.optionalFieldOf("default", false).forGetter(CharClass::isDefault),
            Codec.STRING.optionalFieldOf("perm").forGetter(CharClass::perm),
            CraftRules.MAP_CODEC.forGetter(CharClass::craftRules),
            ItemRules.MAP_CODEC.forGetter(CharClass::itemRules),
            Race.Progression.MAP_CODEC.forGetter(CharClass::progression),
            Boons.MAP_CODEC.forGetter(CharClass::boons),
            LevelTitles.CODEC.optionalFieldOf("titles", LevelTitles.NONE).forGetter(CharClass::titles))
            .apply(i, CharClass::new));

    public String name() { return identity.name(); }
    public Map<Identifier, SkillGrant> skills() { return progression.skills(); }
    public LevelBonuses levels() { return progression.levels(); }

    /** What a character of this class is called at {@code level}, if anything. */
    public Optional<String> titleAt(int level) { return titles.titleFor(level); }
}
