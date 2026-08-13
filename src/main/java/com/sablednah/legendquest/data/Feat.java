package com.sablednah.legendquest.data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A feat: a purchasable bundle of character, bought with skill points, from
 * {@code data/<pack>/legendquest/feat/<name>.json} or (via the YAML front
 * door) {@code config/legendquest/feats/<name>.yml}.
 *
 * <p>Feats reuse every engine the mod already has — {@link Boons} for
 * passives, {@link ItemRules} for proficiencies, skill grants for actives —
 * so a feat file can say "+1 toughness", "may wear heavy armour" or
 * "learns Blink" (or all three) with zero new code. Gating: minimum level,
 * feat chains ({@code requires}), and race/group/class eligibility.
 * This is what makes two level-10 elf fighters different people.</p>
 *
 * <pre>
 * name: Toughness
 * description: Hard to put down.
 * icon: minecraft:shield
 * cost: 8
 * level: 5
 * requires: []
 * attributes:
 *   minecraft:max_health: { base: 4 }
 * </pre>
 */
public record Feat(
        String name,
        Optional<String> description,
        String icon,
        int cost,
        int level,
        List<Identifier> requires,
        List<Identifier> allowedRaces,
        List<String> allowedGroups,
        List<Identifier> allowedClasses,
        Boons boons,
        Map<Identifier, SkillGrant> skills,
        ItemRules itemRules) {

    public static final Codec<Feat> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(Feat::name),
            Codec.STRING.optionalFieldOf("description").forGetter(Feat::description),
            Codec.STRING.optionalFieldOf("icon", "minecraft:nether_star").forGetter(Feat::icon),
            Codec.INT.optionalFieldOf("cost", 5).forGetter(Feat::cost),
            Codec.INT.optionalFieldOf("level", 0).forGetter(Feat::level),
            Identifier.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(Feat::requires),
            Identifier.CODEC.listOf().optionalFieldOf("allowed_races", List.of())
                    .forGetter(Feat::allowedRaces),
            Codec.STRING.listOf().optionalFieldOf("allowed_groups", List.of())
                    .forGetter(Feat::allowedGroups),
            Identifier.CODEC.listOf().optionalFieldOf("allowed_classes", List.of())
                    .forGetter(Feat::allowedClasses),
            Boons.MAP_CODEC.forGetter(Feat::boons),
            Codec.unboundedMap(Identifier.CODEC, SkillGrant.CODEC)
                    .optionalFieldOf("skills", Map.of()).forGetter(Feat::skills),
            ItemRules.MAP_CODEC.forGetter(Feat::itemRules))
            .apply(i, Feat::new));
}
