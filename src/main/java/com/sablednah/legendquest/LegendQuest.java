package com.sablednah.legendquest;

import com.mojang.logging.LogUtils;
import com.sablednah.legendquest.character.LQAttachments;
import com.sablednah.legendquest.neoforge.LQCommands;
import com.sablednah.legendquest.neoforge.LQServerEvents;
import com.sablednah.legendquest.yaml.YamlConfigPack;

import net.minecraft.server.packs.PackType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * LegendQuest ReForged — races, classes, skills and character progression.
 *
 * <p>Server-authoritative: vanilla clients can join and play. Loader-agnostic
 * logic lives under {@code core}; NeoForge glue under {@code neoforge}; wire
 * formats under {@code network}; the optional HUD under {@code client}.</p>
 */
@Mod(LegendQuest.MODID)
public class LegendQuest {
    // Must match mod_id in gradle.properties and modId in neoforge.mods.toml.
    public static final String MODID = "legendquest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LegendQuest(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("LegendQuest ReForged initialising");

        modContainer.registerConfig(ModConfig.Type.COMMON, LQConfig.SPEC);

        // Mod bus: registries, attachments, and the YAML front door.
        modEventBus.addListener(LQRegistries::register);
        LQAttachments.register(modEventBus);
        modEventBus.addListener(this::onAddPackFinders);

        // Game bus: character lifecycle, combat, skills, commands, permissions.
        NeoForge.EVENT_BUS.register(LQServerEvents.class);
        NeoForge.EVENT_BUS.register(com.sablednah.legendquest.neoforge.LQPermissions.class);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                LQCommands.register(event.getDispatcher()));
    }

    private void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            event.addRepositorySource(consumer -> consumer.accept(YamlConfigPack.makePack()));
        }
    }
}
