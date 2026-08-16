package com.sablednah.legendquest.skills;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;

/**
 * One thing a skill does when it fires. Implementations are immutable records
 * whose fields mirror their YAML/JSON keys; per-player state lives in the
 * player's attachment, never on the effect.
 *
 * <p>Third-party skill-pack mods add new effect types with
 * {@link SkillEffectTypes#register} — the modern replacement for dropping
 * skill jars in a folder.</p>
 */
public interface SkillEffect {

    Codec<SkillEffect> CODEC =
            Identifier.CODEC.dispatch("type", SkillEffect::type, SkillEffectTypes::codecOf);

    /** The id this effect was registered under (its {@code type} in data). */
    Identifier type();

    /** Apply the effect. Runs on the server thread. */
    void apply(SkillContext ctx);

    /**
     * One plain sentence describing what this does, for the handbook's "what it
     * does" block — the numbers a player cannot otherwise see, written from the
     * data rather than from a skill author's prose, so it can never drift out of
     * date. Built-ins phrase themselves through {@code Lang}, so a server's
     * vocabulary and translations carry through.
     *
     * <p>Empty is the default and is honest: the handbook falls back to naming
     * the effect type, so an undescribed effect from a third-party pack reads as
     * something rather than as a gap. Overriding it is the polite thing to do —
     * your pack is the only place that knows what your effect means.</p>
     */
    default String describe() {
        return "";
    }
}
