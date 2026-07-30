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
}
