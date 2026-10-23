package com.sablednah.legendquest.core;

import java.util.function.IntUnaryOperator;

/**
 * d20 mechanics, straight from the tabletop roots of the 1.9.x plugin.
 * Deliberately free of Minecraft imports: callers pass a bounded-random
 * function (e.g. {@code entity.getRandom()::nextInt}).
 */
public final class Mechanics {

    /** 1..20 */
    public static int d20(IntUnaryOperator nextInt) {
        return nextInt.applyAsInt(20) + 1;
    }

    /**
     * A skill test: d20 + modifier vs difficulty. Rolls of 1 always fail and
     * 20 always succeed, as in the original.
     */
    public static boolean skillTest(IntUnaryOperator nextInt, int modifier, int difficulty) {
        int roll = d20(nextInt);
        if (roll == 1) return false;
        if (roll == 20) return true;
        return roll + modifier >= difficulty;
    }

    /**
     * An opposed test (attacker vs defender), used for hit/dodge. Ties go to
     * the attacker, matching the old combat code.
     */
    public static boolean opposedTest(IntUnaryOperator nextInt, int attackerMod, int defenderMod) {
        return d20(nextInt) + attackerMod >= d20(nextInt) + defenderMod;
    }

    /** {@link #opposedTest} with the drama kept: a natural 20 is a CRIT. */
    public enum AttackOutcome { MISS, HIT, CRIT }

    public static AttackOutcome opposedAttack(IntUnaryOperator nextInt, int attackerMod, int defenderMod) {
        int attackRoll = d20(nextInt);
        if (attackRoll == 20) return AttackOutcome.CRIT;
        return attackRoll + attackerMod >= d20(nextInt) + defenderMod
                ? AttackOutcome.HIT : AttackOutcome.MISS;
    }

    /**
     * The attacker's modifier from relative size: a bigger target is easier to
     * hit, a smaller one harder. Sizes are heights in blocks.
     *
     * <p>One point per {@code blocksPerPoint} of difference, rounded to the
     * nearest whole point (symmetrically, so a small attacker and a big one
     * are mirror images), then held to +/-{@code max}.</p>
     */
    public static int sizeModifier(double attackerSize, double targetSize, double blocksPerPoint, int max) {
        if (blocksPerPoint <= 0 || max <= 0) return 0;
        double points = (targetSize - attackerSize) / blocksPerPoint;
        int rounded = (int) (Math.signum(points) * Math.round(Math.abs(points)));
        return Math.max(-max, Math.min(max, rounded));
    }

    /**
     * D&D's proficiency bonus (+2 at levels 1-4, +3 at 5-8 ... +6 at 17-20),
     * spread over this server's level range: level 1 is D&D's 1, the cap is
     * D&D's 20, and everything between is scaled. Level 0 counts as 1.
     */
    public static int proficiency(int level, int maxLevel) {
        if (maxLevel <= 1) {
            return 2;
        }
        int l = Math.max(1, Math.min(level, maxLevel));
        int dnd = 1 + (int) Math.floor((l - 1) * 19.0 / (maxLevel - 1));
        return 2 + (dnd - 1) / 4;
    }

    private Mechanics() {}
}
