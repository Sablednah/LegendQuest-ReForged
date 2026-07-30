package com.sablednah.legendquest.data;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-level rewards, cumulative up to the player's current level:
 *
 * <pre>
 * levels:
 *   10: { hp: 5 }
 *   50: { dex: 1, hp: 5 }
 *   100: { hp: 5, mana: 5, sp: 5, manaregen: 2.5 }
 * </pre>
 *
 * (The old per-level allow/disallow item lists are deferred; see PORTING.md.)
 */
public record LevelBonuses(Map<Integer, Bonus> byLevel) {

    public static final LevelBonuses NONE = new LevelBonuses(Map.of());

    /** JSON object keys are strings, so the level key needs a parse step. */
    private static final Codec<Integer> LEVEL_KEY = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    return DataResult.success(Integer.parseInt(s.trim()));
                } catch (NumberFormatException e) {
                    return DataResult.error(() -> "Level key '" + s + "' is not a number");
                }
            },
            String::valueOf);

    public static final Codec<LevelBonuses> CODEC =
            Codec.unboundedMap(LEVEL_KEY, Bonus.CODEC).xmap(LevelBonuses::new, LevelBonuses::byLevel);

    public record Bonus(int hp, int mana, int sp, double manaRegen, StatBlock stats) {

        public static final Codec<Bonus> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("hp", 0).forGetter(Bonus::hp),
                Codec.INT.optionalFieldOf("mana", 0).forGetter(Bonus::mana),
                Codec.INT.optionalFieldOf("sp", 0).forGetter(Bonus::sp),
                Codec.DOUBLE.optionalFieldOf("manaregen", 0.0D).forGetter(Bonus::manaRegen),
                StatBlock.CODEC.optionalFieldOf("stats", StatBlock.ZERO).forGetter(Bonus::stats))
                .apply(i, Bonus::new));
    }

    /** Sum of a numeric bonus over all thresholds at or below {@code level}. */
    public int totalInt(int level, java.util.function.ToIntFunction<Bonus> getter) {
        int total = 0;
        for (var e : byLevel.entrySet()) {
            if (e.getKey() <= level) total += getter.applyAsInt(e.getValue());
        }
        return total;
    }

    public double totalDouble(int level, java.util.function.ToDoubleFunction<Bonus> getter) {
        double total = 0;
        for (var e : byLevel.entrySet()) {
            if (e.getKey() <= level) total += getter.applyAsDouble(e.getValue());
        }
        return total;
    }

    public StatBlock totalStats(int level) {
        StatBlock total = StatBlock.ZERO;
        for (var e : byLevel.entrySet()) {
            if (e.getKey() <= level) total = total.plus(e.getValue().stats());
        }
        return total;
    }
}
