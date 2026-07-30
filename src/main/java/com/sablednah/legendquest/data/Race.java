package com.sablednah.legendquest.data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One playable race, defined by a data file at
 * {@code data/<pack>/legendquest/race/<name>.json} or (via the YAML front
 * door) {@code config/legendquest/races/<name>.yml}.
 *
 * <p>Sub-records ({@link Identity}, {@link Vitals}, {@link Progression})
 * exist only because {@code RecordCodecBuilder.group} stops at 16 fields; as
 * {@code MapCodec}s they read sibling keys, so every field stays flat in the
 * YAML/JSON. Delegate accessors keep callers unaware of the grouping.</p>
 */
public record Race(
        Identity identity,
        Vitals vitals,
        StatBlock statmods,
        List<String> groups,
        int frequency,
        boolean isDefault,
        Optional<String> perm,
        CraftRules craftRules,
        ItemRules itemRules,
        Progression progression) {

    /** Naming and flavour. */
    public record Identity(String name, Optional<String> plural, Optional<String> chatTag,
            Optional<String> description, Optional<String> longDescription, double size) {

        public static final MapCodec<Identity> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Identity::name),
                Codec.STRING.optionalFieldOf("plural").forGetter(Identity::plural),
                Codec.STRING.optionalFieldOf("chat_tag").forGetter(Identity::chatTag),
                Codec.STRING.optionalFieldOf("description").forGetter(Identity::description),
                Codec.STRING.optionalFieldOf("long_description").forGetter(Identity::longDescription),
                Codec.DOUBLE.optionalFieldOf("size", 1.8D).forGetter(Identity::size))
                .apply(i, Identity::new));
    }

    /**
     * Base survival numbers. {@code base_speed} keeps the old scale where 0.2
     * is normal walking speed ("0.4 would be twice as fast").
     */
    public record Vitals(double baseHealth, double baseSpeed, double baseMana, double manaPerSecond) {

        public static final MapCodec<Vitals> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("base_health", 20.0D).forGetter(Vitals::baseHealth),
                Codec.DOUBLE.optionalFieldOf("base_speed", 0.2D).forGetter(Vitals::baseSpeed),
                Codec.DOUBLE.optionalFieldOf("base_mana", 10.0D).forGetter(Vitals::baseMana),
                Codec.DOUBLE.optionalFieldOf("mana_per_second", 1.0D).forGetter(Vitals::manaPerSecond))
                .apply(i, Vitals::new));
    }

    /** Skills, skill points, level rewards and XP tuning. */
    public record Progression(
            Map<Identifier, SkillGrant> skills,
            double skillPoints,
            double skillPointsPerLevel,
            LevelBonuses levels,
            double xpAdjustKill,
            double xpAdjustMine,
            double xpAdjustSmelt) {

        public static final Progression NONE =
                new Progression(Map.of(), 0, 0, LevelBonuses.NONE, 0, 0, 0);

        public static final MapCodec<Progression> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.unboundedMap(Identifier.CODEC, SkillGrant.CODEC)
                        .optionalFieldOf("skills", Map.of()).forGetter(Progression::skills),
                Codec.DOUBLE.optionalFieldOf("skill_points", 0.0D).forGetter(Progression::skillPoints),
                Codec.DOUBLE.optionalFieldOf("skill_points_per_level", 0.0D)
                        .forGetter(Progression::skillPointsPerLevel),
                LevelBonuses.CODEC.optionalFieldOf("levels", LevelBonuses.NONE)
                        .forGetter(Progression::levels),
                Codec.DOUBLE.optionalFieldOf("xp_adjust_kill", 0.0D).forGetter(Progression::xpAdjustKill),
                Codec.DOUBLE.optionalFieldOf("xp_adjust_mine", 0.0D).forGetter(Progression::xpAdjustMine),
                Codec.DOUBLE.optionalFieldOf("xp_adjust_smelt", 0.0D).forGetter(Progression::xpAdjustSmelt))
                .apply(i, Progression::new));
    }

    public static final Codec<Race> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identity.MAP_CODEC.forGetter(Race::identity),
            Vitals.MAP_CODEC.forGetter(Race::vitals),
            StatBlock.CODEC.optionalFieldOf("statmods", StatBlock.ZERO).forGetter(Race::statmods),
            Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(Race::groups),
            Codec.INT.optionalFieldOf("frequency", 100).forGetter(Race::frequency),
            Codec.BOOL.optionalFieldOf("default", false).forGetter(Race::isDefault),
            Codec.STRING.optionalFieldOf("perm").forGetter(Race::perm),
            CraftRules.MAP_CODEC.forGetter(Race::craftRules),
            ItemRules.MAP_CODEC.forGetter(Race::itemRules),
            Progression.MAP_CODEC.forGetter(Race::progression))
            .apply(i, Race::new));

    // Convenience delegates so callers don't care about the grouping.
    public String name() { return identity.name(); }
    public double size() { return identity.size(); }
    public double baseHealth() { return vitals.baseHealth(); }
    public double baseSpeed() { return vitals.baseSpeed(); }
    public double baseMana() { return vitals.baseMana(); }
    public double manaPerSecond() { return vitals.manaPerSecond(); }
    public Map<Identifier, SkillGrant> skills() { return progression.skills(); }
    public LevelBonuses levels() { return progression.levels(); }
}
