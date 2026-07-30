package com.sablednah.legendquest.data;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

/**
 * What a race or class may hold and wear. Lists accept item ids and item tags
 * ({@code "#minecraft:swords"}, {@code ["minecraft:bow", "#legendquest:staves"]}),
 * replacing the old data.yml material groups.
 *
 * <p>Semantics preserved from the original docs: an <em>absent</em> allowed
 * list is "no statement"; if any source (race, main class, sub class) makes a
 * statement for a slot, the union of statements is the allowlist and
 * everything else is denied. Disallowed lists always win, even against
 * another source's allow.</p>
 */
public record ItemRules(
        Optional<HolderSet<Item>> allowedWeapons,
        Optional<HolderSet<Item>> disallowedWeapons,
        Optional<HolderSet<Item>> allowedTools,
        Optional<HolderSet<Item>> disallowedTools,
        Optional<HolderSet<Item>> allowedArmour,
        Optional<HolderSet<Item>> disallowedArmour) {

    public static final ItemRules NONE = new ItemRules(Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public static final MapCodec<ItemRules> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("allowed_weapons")
                    .forGetter(ItemRules::allowedWeapons),
            RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("disallowed_weapons")
                    .forGetter(ItemRules::disallowedWeapons),
            RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("allowed_tools")
                    .forGetter(ItemRules::allowedTools),
            RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("disallowed_tools")
                    .forGetter(ItemRules::disallowedTools),
            RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("allowed_armour")
                    .forGetter(ItemRules::allowedArmour),
            RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("disallowed_armour")
                    .forGetter(ItemRules::disallowedArmour))
            .apply(i, ItemRules::new));

    /** Which slot family a rule check concerns. */
    public enum Slot { WEAPON, TOOL, ARMOUR }

    public Optional<HolderSet<Item>> allowed(Slot slot) {
        return switch (slot) {
            case WEAPON -> allowedWeapons;
            case TOOL -> allowedTools;
            case ARMOUR -> allowedArmour;
        };
    }

    public Optional<HolderSet<Item>> disallowed(Slot slot) {
        return switch (slot) {
            case WEAPON -> disallowedWeapons;
            case TOOL -> disallowedTools;
            case ARMOUR -> disallowedArmour;
        };
    }

    public static boolean contains(Optional<HolderSet<Item>> set, Holder<Item> item) {
        return set.isPresent() && set.get().contains(item);
    }
}
