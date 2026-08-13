package com.sablednah.legendquest.client;

import com.sablednah.legendquest.LegendQuest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only entrypoint. Does not load on dedicated servers, so referencing
 * client classes here is safe (the MobHealth pattern — no @OnlyIn, no
 * DistExecutor anywhere).
 */
@Mod(value = LegendQuest.MODID, dist = Dist.CLIENT)
public class LegendQuestClient {

    public LegendQuestClient(ModContainer container, IEventBus modEventBus) {
        modEventBus.addListener(LQKeyMappings::register);
        NeoForge.EVENT_BUS.register(CharacterPanel.class);
        NeoForge.EVENT_BUS.register(ClientCharacterState.class);
        NeoForge.EVENT_BUS.register(LQHud.class);
        NeoForge.EVENT_BUS.register(CombatIndicators.class);
        NeoForge.EVENT_BUS.register(ClientNotices.class);
        NeoForge.EVENT_BUS.register(ClientConduitSpeed.class);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> LQKeyMappings.onClientTick());
    }
}
