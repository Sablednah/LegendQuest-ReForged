package com.sablednah.legendquest.character;

import java.util.function.Supplier;

import com.sablednah.legendquest.LegendQuest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Attachment registration. One attachment: the character. */
public final class LQAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LegendQuest.MODID);

    public static final Supplier<AttachmentType<PlayerCharacter>> CHARACTER =
            ATTACHMENTS.register("character", () -> AttachmentType
                    .builder(PlayerCharacter::new)
                    .serialize(PlayerCharacter.MAP_CODEC)
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    private LQAttachments() {}
}
