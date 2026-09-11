package com.sablednah.legendquest.character;

import java.util.function.Supplier;

import com.sablednah.legendquest.LegendQuest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Attachment registration: the character, and the tracking mark. */
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

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    private LQAttachments() {}
}
