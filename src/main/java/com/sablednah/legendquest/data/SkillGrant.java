package com.sablednah.legendquest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How a race, class or feat grants a skill — the easy end of the skill
 * system:
 *
 * <pre>
 * skills:
 *   legendquest:holy_light:
 *     level: 12       # character level required
 *     cost: 0         # skill points to buy; 0 = free once the level is reached
 *     karma_min: 50   # soul requirements: only the good may hold the light...
 *   legendquest:darkness:
 *     level: 12
 *     karma_max: -50  # ...and only the wicked may quench it.
 * </pre>
 *
 * <p>Karma bands make paired good/evil skill choices possible — grant both
 * to a class and each character can only ever hold one side. A skill whose
 * band you leave stops being owned (suspended, not forgotten): redemption
 * and corruption both do exactly what they say.</p>
 *
 * <p>Customising a skill's behaviour is no longer done here (the old broken
 * {@code vars:}/{@code skillname:} overrides): skills are data files now, so
 * a variant is simply another skill file referenced by its own id.</p>
 */
public record SkillGrant(int level, int cost, long karmaMin, long karmaMax) {

    public static final SkillGrant FREE = new SkillGrant(0, 0, Long.MIN_VALUE, Long.MAX_VALUE);

    public static final Codec<SkillGrant> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("level", 0).forGetter(SkillGrant::level),
            Codec.INT.optionalFieldOf("cost", 0).forGetter(SkillGrant::cost),
            Codec.LONG.optionalFieldOf("karma_min", Long.MIN_VALUE).forGetter(SkillGrant::karmaMin),
            Codec.LONG.optionalFieldOf("karma_max", Long.MAX_VALUE).forGetter(SkillGrant::karmaMax))
            .apply(i, SkillGrant::new));

    public boolean karmaAllows(long karma) {
        return karma >= karmaMin && karma <= karmaMax;
    }

    public boolean hasKarmaBand() {
        return karmaMin != Long.MIN_VALUE || karmaMax != Long.MAX_VALUE;
    }
}
