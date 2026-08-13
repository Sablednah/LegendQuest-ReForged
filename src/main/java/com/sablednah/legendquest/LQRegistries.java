package com.sablednah.legendquest;

import com.sablednah.legendquest.data.CharClass;
import com.sablednah.legendquest.data.Race;
import com.sablednah.legendquest.data.SkillDefinition;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Registry keys owned by LegendQuest.
 *
 * <p>All three are <em>datapack</em> registries — the delivery mechanism for
 * content: drop JSON in a datapack (or YAML in {@code config/legendquest/},
 * which the YAML front door converts) and get new races/classes/skills with
 * no code and no restart. Network codecs are supplied so definitions sync to
 * clients — required for the HUD to name things without hardcoding.</p>
 */
public final class LQRegistries {

    public static final ResourceKey<Registry<Race>> RACE =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "race"));

    public static final ResourceKey<Registry<CharClass>> CHAR_CLASS =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "class"));

    public static final ResourceKey<Registry<SkillDefinition>> SKILL =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "skill"));

    public static final ResourceKey<Registry<com.sablednah.legendquest.data.Feat>> FEAT =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "feat"));

    /** Registered on the mod event bus. */
    static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(RACE, Race.CODEC, Race.CODEC);
        event.dataPackRegistry(CHAR_CLASS, CharClass.CODEC, CharClass.CODEC);
        event.dataPackRegistry(SKILL, SkillDefinition.CODEC, SkillDefinition.CODEC);
        event.dataPackRegistry(FEAT, com.sablednah.legendquest.data.Feat.CODEC,
                com.sablednah.legendquest.data.Feat.CODEC);
    }

    private LQRegistries() {}
}
