package com.sablednah.legendquest.data;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Innate perks a race or class grants just by being what it is — the
 * balancing weights behind flavourful restrictions ("dwarves can't work
 * magic, but stone remembers them"). Flat in the YAML:
 *
 * <pre>
 * attributes:
 *   minecraft:armor_toughness: { base: 1.0, per_level: 0.05 }
 *   minecraft:luck: { base: 0.5 }
 * enchant_rebate: 1      # XP levels handed back after enchanting
 * smith_refund: 0.2      # chance to recover a material when crafting gear
 * </pre>
 *
 * <p>Attribute ids are plain strings, not codec-validated registry entries:
 * a typo should cost the boon, not the world load. Race and class boons
 * stack additively.</p>
 */
public record Boons(
        Map<String, Bonus> attributes,
        int enchantRebate,
        double smithRefund) {

    public static final Boons NONE = new Boons(Map.of(), 0, 0.0D);

    /** A flat bonus plus a per-level ramp, evaluated at the player's level. */
    public record Bonus(double base, double perLevel) {

        public static final Codec<Bonus> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("base", 0.0D).forGetter(Bonus::base),
                Codec.DOUBLE.optionalFieldOf("per_level", 0.0D).forGetter(Bonus::perLevel))
                .apply(i, Bonus::new));

        public double at(int level) {
            return base + perLevel * level;
        }
    }

    public static final MapCodec<Boons> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Bonus.CODEC).optionalFieldOf("attributes", Map.of())
                    .forGetter(Boons::attributes),
            Codec.INT.optionalFieldOf("enchant_rebate", 0).forGetter(Boons::enchantRebate),
            Codec.DOUBLE.optionalFieldOf("smith_refund", 0.0D).forGetter(Boons::smithRefund))
            .apply(i, Boons::new));
}
