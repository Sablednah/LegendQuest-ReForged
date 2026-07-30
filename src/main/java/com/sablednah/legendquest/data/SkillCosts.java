package com.sablednah.legendquest.data;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * What using a skill costs (and pays). Flat in the skill YAML:
 *
 * <pre>
 * mana_cost: 10
 * karma_cost: 0
 * karma_required: 0     # negative = "must be at least this evil"
 * karma_reward: 5
 * consumes: minecraft:ender_pearl
 * consumes_qty: 1
 * xp: 2                 # class XP awarded on successful use
 * </pre>
 */
public record SkillCosts(
        int manaCost,
        long karmaCost,
        long karmaRequired,
        long karmaReward,
        Optional<Item> consumes,
        int consumesQty,
        int xpAward) {

    public static final SkillCosts FREE = new SkillCosts(0, 0, 0, 0, Optional.empty(), 1, 0);

    public static final MapCodec<SkillCosts> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("mana_cost", 0).forGetter(SkillCosts::manaCost),
            Codec.LONG.optionalFieldOf("karma_cost", 0L).forGetter(SkillCosts::karmaCost),
            Codec.LONG.optionalFieldOf("karma_required", 0L).forGetter(SkillCosts::karmaRequired),
            Codec.LONG.optionalFieldOf("karma_reward", 0L).forGetter(SkillCosts::karmaReward),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("consumes").forGetter(SkillCosts::consumes),
            Codec.INT.optionalFieldOf("consumes_qty", 1).forGetter(SkillCosts::consumesQty),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(SkillCosts::xpAward))
            .apply(i, SkillCosts::new));
}
