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
        modEventBus.addListener(com.sablednah.legendquest.network.LQNetwork::register);

        // Game bus: character lifecycle, combat, skills, commands, permissions.
        NeoForge.EVENT_BUS.register(LQServerEvents.class);
        NeoForge.EVENT_BUS.register(com.sablednah.legendquest.neoforge.LQPermissions.class);
        // Party-chat capture for servers with no Standards installed. Kept out
        // of LQServerEvents because it is the one listener that stands itself
        // down: when Standards is present its router takes the job below, and
        // this becomes a no-op. Both are armed here in the constructor, well
        // before any player exists to type, so there is no window in which the
        // two could both deliver. See PartyChat#onChat.
        NeoForge.EVENT_BUS.register(com.sablednah.legendquest.neoforge.PartyChat.class);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                LQCommands.register(event.getDispatcher()));

        // Standards is a SOFT dependency: it supplies the chat name-decorator,
        // chat-router and combat APIs. Without it LegendQuest decorates
        // nothing, routes party chat through its own listener above, and
        // reports no combat. The isLoaded check has to sit here, outside those
        // classes, because naming one is what triggers loading it -- and they
        // are the only two that import com.sablednah.standards. Calling them
        // unguarded would be a NoClassDefFoundError on every server without
        // Standards installed.
        if (net.neoforged.fml.ModList.get().isLoaded("standards")) {
            optionalIntegration("chat", com.sablednah.legendquest.neoforge.ChatSupport::register);
            optionalIntegration("combat", com.sablednah.legendquest.neoforge.CombatSupport::register);
        }
    }

    /**
     * Wire up one optional Standards feature, surviving a Standards that is
     * present but older than the API this was built against.
     *
     * <p><b>{@code isLoaded} is not enough, and finding that out cost a dead
     * server.</b> It answers "is Standards here", not "is Standards new enough
     * to have the classes I call". A 1.0.1 install has the chat API and no
     * combat package at all, so touching {@code Combat} threw
     * {@code NoClassDefFoundError} during mod construction and took the whole
     * game down with it — from a mod that is supposed to work fine with no
     * Standards whatsoever.</p>
     *
     * <p>{@code LinkageError} rather than {@code Exception}: a missing class is
     * an {@code Error}, so catching exceptions would not have caught this. Each
     * feature is wired separately so an older Standards still gets the parts it
     * does have — chat kept working in exactly the case that killed combat.</p>
     */
    private static void optionalIntegration(String feature, Runnable register) {
        try {
            register.run();
        } catch (LinkageError e) {
            LOGGER.warn("Standards is installed but has no {} API this build can use"
                    + " -- that feature is off. Update Standards to re-enable it. ({})",
                    feature, e.toString());
        }
    }

    private void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            event.addRepositorySource(consumer -> consumer.accept(YamlConfigPack.makePack()));
        }
    }
}
