package com.sablednah.legendquest.character;

import java.util.function.Supplier;

import com.sablednah.legendquest.LegendQuest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Attachment registration: the character, the tracking mark, and last health. */
public final class LQAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LegendQuest.MODID);

    public static final Supplier<AttachmentType<PlayerCharacter>> CHARACTER =
            ATTACHMENTS.register("character", () -> AttachmentType
                    .builder(PlayerCharacter::new)
                    .serialize(PlayerCharacter.MAP_CODEC)
                    .copyOnDeath()
                    .build());

    /**
     * When a tracked creature stops being quarry, as an absolute game time.
     *
     * <p>Serialized, so a marked creature is still marked after a restart —
     * the mark is a fact about the world rather than about the session. Not
     * {@code copyOnDeath}: dying ends the hunt by definition.</p>
     *
     * <p>An attachment rather than a key in {@code getPersistentData()}
     * precisely because {@link com.sablednah.legendquest.skills.effects.Tracking}
     * asks every mob on every despawn check whether it is marked, and
     * {@code hasData} answers no without allocating anything.</p>
     */
    public static final Supplier<AttachmentType<Long>> TRACKED =
            ATTACHMENTS.register("tracked", () -> AttachmentType
                    .builder(() -> 0L)
                    .serialize(com.mojang.serialization.Codec.LONG.fieldOf("until"))
                    .build());

    /**
     * The health the player actually had when they left.
     *
     * <p><b>Why this has to exist.</b> Our max-health bonus is a
     * <i>transient</i> attribute modifier, so it is not in the save file. When
     * a player loads, vanilla reads {@code Health} from their NBT and hands it
     * to {@code setHealth}, which clamps to the max health <i>at that moment</i>
     * — and at that moment the modifier has not been applied yet, so the max is
     * vanilla's 20. A character with 33 max health who logs out at full comes
     * back at 20, permanently, every single time. The login handler then
     * restores the max to 33, which makes it look like the bonus is working and
     * hides the loss.</p>
     *
     * <p>So the real figure is kept here, written when they leave and put back
     * after the modifiers are applied on the way in. Same family as the respawn
     * trap in CLAUDE.md: the repair has to happen where the value still exists.</p>
     */
    public static final Supplier<AttachmentType<Float>> LAST_HEALTH =
            ATTACHMENTS.register("last_health", () -> AttachmentType
                    .builder(() -> 0.0F)
                    .serialize(com.mojang.serialization.Codec.FLOAT.fieldOf("hp"))
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    private LQAttachments() {}
}
