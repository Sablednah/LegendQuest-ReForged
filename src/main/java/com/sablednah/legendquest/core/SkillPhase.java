package com.sablednah.legendquest.core;

/**
 * Where a used skill is in its lifecycle. A pure function of "when was it
 * last used" — the old {@code SkillDataStore.checkPhase()} rewritten without
 * the mutable shared state.
 */
public enum SkillPhase {
    READY,
    BUILDING,
    DELAYED,
    ACTIVE,
    COOLDOWN;

    /**
     * @param nowMs     current wall-clock ms
     * @param lastUseMs when the skill was last activated, or 0 for never
     */
    public static SkillPhase at(long nowMs, long lastUseMs, Timing t) {
        if (lastUseMs <= 0) return READY;
        long elapsed = nowMs - lastUseMs;
        if (elapsed < 0) return READY; // clock went backwards; fail open
        if (elapsed < t.buildupMs()) return BUILDING;
        elapsed -= t.buildupMs();
        if (elapsed < t.delayMs()) return DELAYED;
        elapsed -= t.delayMs();
        if (elapsed < t.durationMs()) return ACTIVE;
        elapsed -= t.durationMs();
        if (elapsed < t.cooldownMs()) return COOLDOWN;
        return READY;
    }

    /** ms until the skill is READY again (0 when it already is). */
    public static long remainingMs(long nowMs, long lastUseMs, Timing t) {
        if (lastUseMs <= 0) return 0;
        long end = lastUseMs + t.totalMs();
        return Math.max(0, end - nowMs);
    }
}
