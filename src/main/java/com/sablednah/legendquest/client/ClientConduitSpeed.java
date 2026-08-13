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

    private static boolean conduitDig(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.level.block.state.BlockState state) {
        CharacterSummaryPayload s = ClientCharacterState.summary();
        if (s == null || s.goldToolMana() <= 0 || s.mana() < s.goldToolMana()) return false;
        ItemStack held = player.getMainHandItem();
        return !held.isEmpty() && held.is(ARCANE_CONDUITS)
                && !held.isCorrectToolForDrops(state); // gold manages alone otherwise
    }

    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!event.getEntity().level().isClientSide()) return; // server handler owns that side
        if (conduitDig(event.getEntity(), event.getState())) {
            event.setNewSpeed(Math.max(event.getNewSpeed(), 9.0F)); // netherite's pace
        }
    }

    /**
     * The other half the client needs: destroy progress divides by 100
     * instead of 30 while the client believes it can't harvest the block —
     * the server-side grant alone still left obsidian at ~28s. Both sides
     * must agree the harvest is legitimate.
     */
    @SubscribeEvent
    static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.canHarvest() || !event.getEntity().level().isClientSide()) return;
        if (conduitDig(event.getEntity(), event.getTargetBlock())) {
            event.setCanHarvest(true);
        }
    }

    private ClientConduitSpeed() {}
}
