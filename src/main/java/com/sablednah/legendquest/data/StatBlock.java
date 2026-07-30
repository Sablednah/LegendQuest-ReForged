package com.sablednah.legendquest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.legendquest.core.Stat;

/**
 * Six stat values in one record. Used both for race/class <em>modifiers</em>
 * (defaults 0) and for a player's rolled base statline.
 *
 * <p>YAML shape matches the old configs:
 * <pre>
 * statmods:
 *   str: 1
 *   con: 2
 * </pre>
 */
public record StatBlock(int str, int dex, int con, int intel, int wis, int chr) {

    public static final StatBlock ZERO = new StatBlock(0, 0, 0, 0, 0, 0);

    public static final Codec<StatBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("str", 0).forGetter(StatBlock::str),
            Codec.INT.optionalFieldOf("dex", 0).forGetter(StatBlock::dex),
            Codec.INT.optionalFieldOf("con", 0).forGetter(StatBlock::con),
            Codec.INT.optionalFieldOf("int", 0).forGetter(StatBlock::intel),
            Codec.INT.optionalFieldOf("wis", 0).forGetter(StatBlock::wis),
            Codec.INT.optionalFieldOf("chr", 0).forGetter(StatBlock::chr))
            .apply(i, StatBlock::new));

    public int get(Stat stat) {
        return switch (stat) {
            case STR -> str;
            case DEX -> dex;
            case CON -> con;
            case INT -> intel;
            case WIS -> wis;
            case CHR -> chr;
        };
    }

    public StatBlock plus(StatBlock other) {
        return new StatBlock(str + other.str, dex + other.dex, con + other.con,
                intel + other.intel, wis + other.wis, chr + other.chr);
    }

    /**
     * The old plugin's quirky main+sub class combination rule, kept for
     * balance continuity: when both modifiers have the same sign take the
     * larger magnitude effect ({@code max}), when they differ in sign they sum.
     */
    public static int combineClassMods(int main, int sub) {
        if (main >= 0 && sub >= 0) return Math.max(main, sub);
        if (main <= 0 && sub <= 0) return Math.max(main, sub); // max of two negatives = the milder one
        return main + sub;
    }

    public static StatBlock combineClasses(StatBlock main, StatBlock sub) {
        return new StatBlock(
                combineClassMods(main.str, sub.str),
                combineClassMods(main.dex, sub.dex),
                combineClassMods(main.con, sub.con),
                combineClassMods(main.intel, sub.intel),
                combineClassMods(main.wis, sub.wis),
                combineClassMods(main.chr, sub.chr));
    }
}
