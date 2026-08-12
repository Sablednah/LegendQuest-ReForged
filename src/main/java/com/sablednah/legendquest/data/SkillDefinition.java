package com.sablednah.legendquest.data;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.legendquest.core.Timing;
import com.sablednah.legendquest.skills.SkillEffect;
import com.sablednah.legendquest.skills.SkillType;
import com.sablednah.legendquest.skills.TriggerSpec;

/**
 * One skill, from {@code data/<pack>/legendquest/skill/<name>.json} or
 * {@code config/legendquest/skills/<name>.yml}. Purely data: behaviour comes
 * from the composed {@link SkillEffect}s. A "variant" of a skill (the old
 * broken {@code skillname:} aliasing) is simply another file.
 *
 * <pre>
 * name: Blink
 * type: active
 * cooldown: 6000
 * mana_cost: 5
 * effects:
 *   - { type: "legendquest:teleport", max_range: 24 }
 * </pre>
 */
public record SkillDefinition(
        String name,
        Optional<String> description,
        String icon,
        SkillType type,
        Timing timing,
        SkillCosts costs,
        Optional<TriggerSpec> trigger,
        long passiveIntervalMs,
        List<SkillEffect> effects) {

    /** GUI icon shown in the skills panel/HUD when nothing is configured. */
    public static final String DEFAULT_ICON = "minecraft:enchanted_book";

    public static final Codec<SkillDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(SkillDefinition::name),
            Codec.STRING.optionalFieldOf("description").forGetter(SkillDefinition::description),
            // A plain string, not an Item codec: a typo'd icon should never
            // fail a world load — the client just falls back to the book.
            Codec.STRING.optionalFieldOf("icon", DEFAULT_ICON).forGetter(SkillDefinition::icon),
            SkillType.CODEC.optionalFieldOf("type", SkillType.ACTIVE).forGetter(SkillDefinition::type),
            Timing.MAP_CODEC.forGetter(SkillDefinition::timing),
            SkillCosts.MAP_CODEC.forGetter(SkillDefinition::costs),
            TriggerSpec.CODEC.optionalFieldOf("trigger").forGetter(SkillDefinition::trigger),
            Codec.LONG.optionalFieldOf("passive_interval", 3000L).forGetter(SkillDefinition::passiveIntervalMs),
            SkillEffect.CODEC.listOf().optionalFieldOf("effects", List.of())
                    .forGetter(SkillDefinition::effects))
            .apply(i, SkillDefinition::new));
}
