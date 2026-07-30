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

    private Mechanics() {}
}
