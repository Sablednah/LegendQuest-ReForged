package com.sablednah.legendquest.data;

import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Level-banded titles for a class — what the character is <em>called</em> at a
 * given rank, as opposed to what they can do:
 *
 * <pre>
 * titles:
 *   1: Squire
 *   10: Knight
 *   50: Lord
 * </pre>
 *
 * <p>A key is the level at which the title starts, and it holds until the next
 * one up, so the example gives Squire from 1, Knight from 10 and Lord from 50.
 * Deliberately the same shape as {@link LevelBonuses}, which pack authors have
 * already met, and deliberately per-class rather than global: a Wasteland Role
 * and a Cold Frontier Profession should be able to name their own ranks without
 * touching a config file.</p>
 *
 * <p>A blank title is treated as no title, which is how a class opts out of the
 * bottom band — "Squire" is worth having, "Level 1 Citizen" is not.</p>
 */
public record LevelTitles(Map<Integer, String> byLevel) {

    public static final LevelTitles NONE = new LevelTitles(Map.of());

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

    public static final Codec<LevelTitles> CODEC =
            Codec.unboundedMap(LEVEL_KEY, Codec.STRING).xmap(LevelTitles::new, LevelTitles::byLevel);

    /** The highest-threshold title at or below {@code level}, if any. */
    public Optional<String> titleFor(int level) {
        return byLevel.entrySet().stream()
                .filter(e -> e.getKey() <= level)
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .filter(title -> !title.isBlank());
    }
}
