package com.sablednah.legendquest.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A skill's four timing windows, in milliseconds — the unit every recovered
 * LegendQuest doc page uses ("All times are in milliseconds - 1000 ms = 1s").
 *
 * <p>The phase sequence is READY → BUILDING → DELAYED → ACTIVE → COOLDOWN,
 * derived purely from the last-use timestamp; see {@link SkillPhase#at}.</p>
 */
public record Timing(long buildupMs, long delayMs, long durationMs, long cooldownMs) {

    public static final Timing INSTANT = new Timing(0, 0, 0, 0);

    public static final MapCodec<Timing> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.optionalFieldOf("buildup", 0L).forGetter(Timing::buildupMs),
            Codec.LONG.optionalFieldOf("delay", 0L).forGetter(Timing::delayMs),
            Codec.LONG.optionalFieldOf("duration", 0L).forGetter(Timing::durationMs),
            Codec.LONG.optionalFieldOf("cooldown", 0L).forGetter(Timing::cooldownMs))
            .apply(i, Timing::new));

    /** Total time from activation until the skill is READY again. */
    public long totalMs() {
        return buildupMs + delayMs + durationMs + cooldownMs;
    }
}
