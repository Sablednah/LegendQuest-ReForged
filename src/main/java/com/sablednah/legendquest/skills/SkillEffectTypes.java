package com.sablednah.legendquest.skills;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.sablednah.legendquest.LegendQuest;
import com.sablednah.legendquest.skills.effects.LQEffects;

import net.minecraft.resources.Identifier;

/**
 * The lookup {@link SkillEffect#CODEC} dispatches through.
 *
 * <p>{@link #register} is deliberately public: skill-pack mods call it from
 * their constructor to contribute effect types without LegendQuest depending
 * on them. This is the ReForged answer to the old "drop a jar in
 * {@code plugins/LegendQuest/skills}" mechanism.</p>
 */
public final class SkillEffectTypes {

    private static final Map<Identifier, MapCodec<? extends SkillEffect>> TYPES = new LinkedHashMap<>();

    static {
        LQEffects.registerBuiltin();
    }

    /**
     * Contribute an effect type. Call during mod construction (before
     * datapacks load). Re-registering an id is refused loudly rather than
     * silently replacing another mod's type.
     */
    public static synchronized void register(Identifier id, MapCodec<? extends SkillEffect> codec) {
        MapCodec<? extends SkillEffect> old = TYPES.putIfAbsent(id, codec);
        if (old != null) {
            LegendQuest.LOGGER.error("Skill effect type '{}' is already registered; ignoring duplicate", id);
        }
    }

    /**
     * @return the codec for an effect type. Never null: an unknown id yields
     *         a codec that fails with a message naming the id and listing
     *         what is available — what a pack author needs to see in the log.
     */
    public static MapCodec<? extends SkillEffect> codecOf(Identifier id) {
        MapCodec<? extends SkillEffect> codec = TYPES.get(id);
        return codec != null ? codec : new UnknownType(id);
    }

    public static Set<Identifier> known() {
        return TYPES.keySet();
    }

    /**
     * Fails both ways with a message naming the offending type and listing
     * the valid ones. Spelled out (rather than a unit codec decoding to null)
     * so a typo in a pack is a log line, not a later NullPointerException.
     */
    private static final class UnknownType extends MapCodec<SkillEffect> {

        private final Identifier id;

        UnknownType(Identifier id) {
            this.id = id;
        }

        private DataResult<SkillEffect> fail() {
            return DataResult.error(
                    () -> "Unknown skill effect type '" + id + "'; known types are " + TYPES.keySet());
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }

        @Override
        public <T> DataResult<SkillEffect> decode(DynamicOps<T> ops, MapLike<T> input) {
            return fail();
        }

        @Override
        public <T> RecordBuilder<T> encode(SkillEffect input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return prefix.withErrorsFrom(fail());
        }
    }

    private SkillEffectTypes() {}
}
