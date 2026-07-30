package com.sablednah.legendquest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The old "core ability" booleans: may this race/class craft, smelt, brew,
 * enchant, repair, tame? Parsed now for schema completeness; enforcement is
 * wired up per-station as the events are surveyed (see PORTING.md deferred
 * list). All default to allowed.
 */
public record CraftRules(boolean crafting, boolean smelting, boolean brewing,
        boolean enchanting, boolean repairing, boolean taming) {

    public static final CraftRules ALL = new CraftRules(true, true, true, true, true, true);

    public static final MapCodec<CraftRules> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("allow_crafting", true).forGetter(CraftRules::crafting),
            Codec.BOOL.optionalFieldOf("allow_smelting", true).forGetter(CraftRules::smelting),
            Codec.BOOL.optionalFieldOf("allow_brewing", true).forGetter(CraftRules::brewing),
            Codec.BOOL.optionalFieldOf("allow_enchanting", true).forGetter(CraftRules::enchanting),
            Codec.BOOL.optionalFieldOf("allow_repairing", true).forGetter(CraftRules::repairing),
            Codec.BOOL.optionalFieldOf("allow_taming", true).forGetter(CraftRules::taming))
            .apply(i, CraftRules::new));
}
