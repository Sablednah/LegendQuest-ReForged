package com.sablednah.legendquest.core;

import java.util.Locale;

/**
 * The classic six-stat line. Scores default to 12 (modifier +1); the modifier
 * formula {@code (score / 2) - 5} is the 1.9.x plugin's rule, kept verbatim so
 * old balance carries over.
 */
public enum Stat {
    STR,
    DEX,
    CON,
    INT,
    WIS,
    CHR;

    /** D&D-style modifier: 10-11 → 0, 12-13 → +1, 8-9 → -1 ... */
    public static int modifier(int score) {
        return (score / 2) - 5;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
