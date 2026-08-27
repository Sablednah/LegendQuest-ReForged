package com.sablednah.legendquest.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Says "that was an act of combat", to whoever is listening.
 *
 * <p>Standards owns the question <em>is this player in a fight</em>, because it
 * owns the escape hatches a fight should close — {@code /home}, {@code /tpa},
 * logging out. LegendQuest owns several of the answers, because a curse, a
 * summon or a swing that misses are all acts of combat that Standards has no
 * way of seeing.</p>
 *
 * <p><b>This class never mentions Standards.</b> It holds a sink that does
 * nothing until {@link CombatSupport} installs a real one, so every call here
 * is safe on a server that has never heard of it — the same shape as
 * {@link PartyChat}'s name styler, and for the same reason: naming the class
 * that imports Standards is what loads it.</p>
 */
public final class CombatTagging {

    /** Mirrors Standards' CombatKind without naming it. */
    public enum Kind { PVP, PVE, SKILL }

    /** Installed by {@link CombatSupport} when Standards is present. */
    @FunctionalInterface
    public interface Sink {
        /** @param seconds 0 to use the server's configured duration for the kind */
        void tag(ServerPlayer player, Kind kind, String source, int seconds);
    }

    /**
     * Asks whether one player may harm another at all.
     *
     * <p>Returns the refusal, already worded for the player, or empty when it
     * is allowed. {@code Component} is a Minecraft type, so this stays free of
     * Standards even though Standards is what answers it.</p>
     */
    @FunctionalInterface
    public interface Guard {
        java.util.Optional<net.minecraft.network.chat.Component> forbidden(
                ServerPlayer caster, ServerPlayer target);
    }

    /**
     * Asks whether players may fight <em>here</em>, wherever "here" is.
     *
     * <p>The other half of the question {@link Guard} asks. Two seams because
     * they are two different facts: a peaceful faction is about the pair, a
     * safe zone is about the ground they are standing on, and neither implies
     * the other.</p>
     */
    @FunctionalInterface
    public interface PlaceGuard {
        boolean pvpAllowed(net.minecraft.server.level.ServerLevel level,
                net.minecraft.core.BlockPos pos);
    }

    private static Sink sink = (player, kind, source, seconds) -> { };
    private static Guard guard = (caster, target) -> java.util.Optional.empty();
    private static PlaceGuard placeGuard = (level, pos) -> true;

    static void setSink(Sink installed) {
        sink = installed;
    }

    static void setGuard(Guard installed) {
        guard = installed;
    }

    static void setPlaceGuard(PlaceGuard installed) {
        placeGuard = installed;
    }

    /**
     * May players fight where this effect is landing?
     *
     * <p>Checked <em>at the target</em> for something aimed at somebody, and at
     * the centre of the area for something that goes off in a place. A snare
     * cast from outside a safe zone into it is still a snare inside the safe
     * zone, which is the case that makes the caster's own position the wrong
     * thing to ask about.</p>
     */
    public static boolean pvpAllowedAt(net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos pos) {
        return placeGuard.pvpAllowed(level, pos);
    }

    /** Convenience for the common case: is fighting allowed where this entity stands? */
    public static boolean pvpAllowedAt(LivingEntity target) {
        return target.level() instanceof net.minecraft.server.level.ServerLevel level
                && pvpAllowedAt(level, target.blockPosition());
    }

    /**
     * May this player do something hostile to that target?
     *
     * <p>Only ever asked for a hostile effect that is <em>not</em> damage.
     * Player-on-player damage is gated by Standards on the damage event
     * itself, so asking again would be duplicating a check somebody else
     * already owns. A curse, a snare or a blind is a different matter: no
     * damage event fires, so nothing else can refuse it, and a faction that
     * declared itself peaceful would be peaceful against swords and defenceless
     * against spells.</p>
     *
     * <p>Empty for a non-player target: the question is player-on-player, and
     * a wolf has no opinion about faction relations.</p>
     */
    public static java.util.Optional<net.minecraft.network.chat.Component> forbidden(
            ServerPlayer caster, LivingEntity target) {
        if (!(target instanceof ServerPlayer victim) || victim == caster) {
            return java.util.Optional.empty();
        }
        return guard.forbidden(caster, victim);
    }

    /**
     * Both halves at once: may this caster harm this target, here?
     *
     * <p>Effects should call <b>this</b> rather than the two separately —
     * remembering to ask one question is easy and remembering to ask two is
     * exactly the kind of thing that gets forgotten in the fifth effect
     * somebody adds. The place is checked at the target, and only for another
     * player: a safe zone stops a duel, not a wolf.</p>
     *
     * @return the refusal to show the caster, or empty when it may proceed
     */
    public static java.util.Optional<net.minecraft.network.chat.Component> refuses(
            ServerPlayer caster, LivingEntity target) {
        if (!(target instanceof ServerPlayer victim) || victim == caster) {
            return java.util.Optional.empty();
        }
        java.util.Optional<net.minecraft.network.chat.Component> pair = guard.forbidden(caster, victim);
        if (pair.isPresent()) return pair;
        if (!pvpAllowedAt(victim)) {
            return java.util.Optional.of(net.minecraft.network.chat.Component.literal(
                    Lang.get("msg.combat.pvp_off_here").replace('&', '§')));
        }
        return java.util.Optional.empty();
    }

    /** True when anything is listening — lets callers skip work they need not do. */
    public static boolean active() {
        return sink != NONE;
    }

    private static final Sink NONE = (player, kind, source, seconds) -> { };

    static {
        sink = NONE;
    }

    /**
     * Both sides of a melee exchange that LegendQuest resolved as a miss.
     *
     * <p>A miss cancels the damage event, so nothing else on the server ever
     * learns the swing happened — and "I attacked you and missed" is exactly
     * the moment somebody would like to leave. Whether the dodge was clever or
     * lucky, a fight is happening.</p>
     */
    public static void melee(LivingEntity attacker, LivingEntity victim, String source) {
        boolean pvp = attacker instanceof ServerPlayer && victim instanceof ServerPlayer;
        Kind kind = pvp ? Kind.PVP : Kind.PVE;
        if (attacker instanceof ServerPlayer p) sink.tag(p, kind, source, 0);
        if (victim instanceof ServerPlayer p) sink.tag(p, kind, source, 0);
    }

    /**
     * A player did something hostile with a skill.
     *
     * <p>Tagged even when the skill deals no damage, which is the whole point:
     * blinding somebody and strolling to a teleport is the exact trick a combat
     * tag exists to stop.</p>
     */
    public static void skill(ServerPlayer caster, net.minecraft.resources.Identifier skillId) {
        sink.tag(caster, Kind.SKILL, skillId.toString(), 0);
    }

    /** Someone on the receiving end of a hostile skill. Mobs are ignored. */
    public static void skillVictim(LivingEntity victim, net.minecraft.resources.Identifier skillId) {
        if (victim instanceof ServerPlayer p) sink.tag(p, Kind.SKILL, skillId.toString(), 0);
    }

    private CombatTagging() {}
}
