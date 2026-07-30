package com.sablednah.legendquest.skills;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Everything a skill effect gets to work with when it fires.
 *
 * @param caster        the player using the skill
 * @param level         the caster's level (world)
 * @param skillLevel    the caster's character level when firing (for scaling)
 * @param triggerTarget for TRIGGERED skills, the other party of the combat
 *                      event (victim on hit/kill, attacker on hurt); null
 *                      otherwise. Target resolution prefers this.
 */
public record SkillContext(
        ServerPlayer caster,
        ServerLevel level,
        int skillLevel,
        @Nullable LivingEntity triggerTarget) {

    public static SkillContext of(ServerPlayer caster, int skillLevel) {
        return new SkillContext(caster, caster.level(), skillLevel, null);
    }
}
