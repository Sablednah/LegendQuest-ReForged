package com.sablednah.legendquest.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.sablednah.legendquest.data.ItemRules;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Item allow/deny checks, preserving the documented 1.9.x semantics:
 * disallow always wins; if any source (race, main, sub) states an allowlist
 * for a slot family, the union of allowlists is authoritative; with no
 * statement anywhere, everything is allowed.
 */
public final class RestrictionEngine {

    /**
     * How many worn armour pieces this character may not wear. Disallowed
     * armour is not confiscated (looting enemy plate is half the fun on an
     * apocalypse server) — instead the wearer is hampered every second they
     * keep it on; see the penalty tick in LQServerEvents.
     */
    public static int disallowedArmourCount(ServerPlayer player) {
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack worn = player.getItemBySlot(slot);
            if (!worn.isEmpty() && !isAllowed(player, ItemRules.Slot.ARMOUR, worn)) count++;
        }
        return count;
    }

    public static boolean isAllowed(ServerPlayer player, ItemRules.Slot slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        Holder<Item> item = stack.getItemHolder();

        List<ItemRules> sources = new ArrayList<>(3);
        CharacterService.race(player).ifPresent(r -> sources.add(r.itemRules()));
        CharacterService.mainClass(player).ifPresent(c -> sources.add(c.itemRules()));
        CharacterService.subClass(player).ifPresent(c -> sources.add(c.itemRules()));

        boolean anyAllowStatement = false;
        boolean allowed = false;
        for (ItemRules rules : sources) {
            if (ItemRules.contains(rules.disallowed(slot), item)) {
                return false; // deny always wins
            }
            Optional<HolderSet<Item>> allow = rules.allowed(slot);
            if (allow.isPresent()) {
                anyAllowStatement = true;
                if (allow.get().contains(item)) allowed = true;
            }
        }
        return !anyAllowStatement || allowed;
    }

    private RestrictionEngine() {}
}
