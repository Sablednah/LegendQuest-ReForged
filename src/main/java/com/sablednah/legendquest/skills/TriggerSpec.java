package com.sablednah.legendquest.skills;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * When a TRIGGERED skill fires.
 *
 * <pre>
 * trigger: { on: melee_hit, chance: 25.0 }
 * </pre>
 */
public record TriggerSpec(Kind on, double chance) {

    public enum Kind {
        /** The player lands a melee hit; trigger target = victim. */
        MELEE_HIT,
        /** The player takes damage; trigger target = attacker (if living). */
        HURT,
        /** The player kills something; trigger target = victim. */
        KILL,
        /** The player lands from a fall (before damage applies). */
        FALL;

        public static final Codec<Kind> CODEC = Codec.STRING.xmap(
                v -> valueOf(v.toUpperCase(Locale.ROOT)),
                v -> v.name().toLowerCase(Locale.ROOT));
    }

    public static final Codec<TriggerSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("on").forGetter(TriggerSpec::on),
            Codec.DOUBLE.optionalFieldOf("chance", 100.0D).forGetter(TriggerSpec::chance))
            .apply(i, TriggerSpec::new));
}
