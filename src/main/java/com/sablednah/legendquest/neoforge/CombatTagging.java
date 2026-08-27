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

    private static Sink sink = (player, kind, source, seconds) -> { };

    static void setSink(Sink installed) {
        sink = installed;
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
