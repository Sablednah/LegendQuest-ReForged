package com.sablednah.legendquest.skills;

import java.util.Locale;

import com.mojang.serialization.Codec;

/** How a skill is invoked. */
public enum SkillType {
    /** Fired by the player: {@code /skill use <id>} (item binding later). */
    ACTIVE,
    /** Always on; effects re-applied on the passive tick. */
    PASSIVE,
    /** Fires from a combat trigger (see TriggerSpec), never manually. */
    TRIGGERED;

    public static final Codec<SkillType> CODEC = Codec.STRING.xmap(
            v -> valueOf(v.toUpperCase(Locale.ROOT)),
            v -> v.name().toLowerCase(Locale.ROOT));
}
