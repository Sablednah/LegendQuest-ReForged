package com.sablednah.legendquest.core;

/**
 * The XP curve. The 1.9.x plugin mirrored vanilla 1.8 XP with hardcoded curve
 * constants; ReForged owns its XP pool instead and uses a simple triangular
 * curve: reaching level n costs {@code base * n} more than level n-1, so
 * total XP for level n is {@code base * n * (n + 1) / 2}.
 */
public final class Leveling {

    /** Total XP required to be at {@code level}. Level 0 is free. */
    public static long totalXpForLevel(int level, long base) {
        if (level <= 0) return 0;
        return base * (long) level * (level + 1) / 2;
    }

    /** The level a total XP amount corresponds to, capped at {@code maxLevel}. */
    public static int levelForXp(long xp, long base, int maxLevel) {
        if (xp <= 0 || base <= 0) return 0;
        int level = 0;
        while (level < maxLevel && totalXpForLevel(level + 1, base) <= xp) {
            level++;
        }
        return level;
    }

    private Leveling() {}
}
