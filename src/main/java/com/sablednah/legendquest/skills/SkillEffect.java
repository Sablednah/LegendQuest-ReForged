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
     * Undo what {@link #apply} left behind, for a passive the player has just
     * switched off.
     *
     * <p>A passive re-applies itself every few seconds, so merely stopping the
     * tick is not the same as stopping the skill: the last application runs its
     * full duration first, and the player watches the thing they just turned
     * off fade out on its own schedule. A visible correction is itself a
     * defect, so the switch takes effect at the moment it is thrown.</p>
     *
     * <p><b>Defaults to a no-op</b>, which is right for everything momentary —
     * a heal that has already landed is not owed back. Only effects that leave
     * lasting state on the caster need override it.</p>
     */
    default void revoke(SkillContext ctx) {
        // nothing lingering to take back
    }

    /**
     * True when this is an act of aggression against somebody else.
     *
     * <p>Used to mark the caster as being in combat, so a server running
     * Standards can stop them teleporting or logging straight out of a fight
     * they started. It matters most for effects that deal no damage: blinding
     * somebody and strolling to a {@code /home} is the exact trick a combat tag
     * exists to prevent, and no damage event would ever reveal it.</p>
     *
     * <p><b>Defaults to false</b>, so a skill pack that has never heard of any
     * of this stays quiet rather than tagging players for buffing each other. A
     * pack whose effect really is hostile overrides it — one line, and the
     * whole thing degrades to nothing on servers without Standards.</p>
     */
    default boolean hostile() {
        return false;
    }

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
