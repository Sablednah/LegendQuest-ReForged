package com.sablednah.legendquest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How a race or class grants a skill — the easy end of the skill system:
 *
 * <pre>
 * skills:
 *   legendquest:dodge:
 *     level: 5        # character level required
 *     cost: 0         # skill points to buy; 0 = free once the level is reached
 * </pre>
 *
 * <p>Customising a skill's behaviour is no longer done here (the old broken
 * {@code vars:}/{@code skillname:} overrides): skills are data files now, so
 * a variant is simply another skill file referenced by its own id.</p>
 */
public record SkillGrant(int level, int cost) {

    public static final SkillGrant FREE = new SkillGrant(0, 0);

    public static final Codec<SkillGrant> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("level", 0).forGetter(SkillGrant::level),
            Codec.INT.optionalFieldOf("cost", 0).forGetter(SkillGrant::cost))
            .apply(i, SkillGrant::new));
}
