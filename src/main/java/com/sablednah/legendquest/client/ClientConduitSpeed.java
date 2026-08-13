package com.sablednah.legendquest.client;

import com.sablednah.legendquest.LegendQuest;
import com.sablednah.legendquest.network.CharacterSummaryPayload;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The client half of the arcane-conduit boost. Mining progress is driven by
 * the CLIENT's destroy speed — the server-side BreakSpeed handler alone
 * left the visible (and effective) dig speed at gold-vs-obsidian 1.0, which
 * Jade cheerfully exposed as a red bar moving at glacier pace. This mirrors
 * the boost using the summary's boon value; the server remains the
 * authority on drops and the mana bill.
 */
public final class ClientConduitSpeed {

    private static final TagKey<Item> ARCANE_CONDUITS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(LegendQuest.MODID, "arcane_conduit_tools"));

    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!event.getEntity().level().isClientSide()) return; // server handler owns that side
        CharacterSummaryPayload s = ClientCharacterState.summary();
        if (s == null || s.goldToolMana() <= 0 || s.mana() < s.goldToolMana()) return;
        ItemStack held = event.getEntity().getMainHandItem();
        if (held.isEmpty() || !held.is(ARCANE_CONDUITS)) return;
        if (held.isCorrectToolForDrops(event.getState())) return; // gold manages alone
        event.setNewSpeed(Math.max(event.getNewSpeed(), 9.0F)); // netherite's pace
    }

    private ClientConduitSpeed() {}
}
