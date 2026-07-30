package com.sablednah.legendquest;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-owner tuning. Content (races/classes/skills) is data, not config;
 * this covers only the global dials. Values are read live via {@code .get()}
 * so edits apply without a restart.
 */
public final class LQConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public enum StatlineMode {
        /** Deterministic 3d6-style roll seeded from the player's UUID. */
        UUID_RANDOM,
        /** Everyone starts with straight 12s. */
        FLAT_12
    }

    public static final ModConfigSpec.EnumValue<StatlineMode> STATLINE_MODE;
    public static final ModConfigSpec.BooleanValue USE_D20_COMBAT;
    public static final ModConfigSpec.BooleanValue USE_SIZE_IN_COMBAT;
    public static final ModConfigSpec.IntValue XP_LEVEL_BASE;
    public static final ModConfigSpec.IntValue MAX_LEVEL;
    public static final ModConfigSpec.IntValue PASSIVE_TICK_MS;
    public static final ModConfigSpec.LongValue KARMA_KILL_PLAYER;
    public static final ModConfigSpec.LongValue KARMA_KILL_VILLAGER;
    public static final ModConfigSpec.LongValue KARMA_KILL_ANIMAL;
    public static final ModConfigSpec.LongValue KARMA_KILL_MONSTER;
    public static final ModConfigSpec.ConfigValue<String> KARMA_POSITIVE_NAMES;
    public static final ModConfigSpec.ConfigValue<String> KARMA_NEGATIVE_NAMES;

    static {
        BUILDER.comment("Character statline").push("stats");
        STATLINE_MODE = BUILDER
                .comment("How a new character's base stats are decided.",
                        "uuid_random: deterministic roll from the player's UUID (the old default).",
                        "flat_12: everyone starts with straight 12s.")
                .defineEnum("statlineMode", StatlineMode.UUID_RANDOM);
        BUILDER.pop();

        BUILDER.comment("Combat").push("combat");
        USE_D20_COMBAT = BUILDER
                .comment("Opposed d20 DEX tests decide hit/dodge between players and mobs.")
                .define("useD20Combat", true);
        USE_SIZE_IN_COMBAT = BUILDER
                .comment("Race size adjusts hit difficulty (bigger = easier to hit).")
                .define("useSizeInCombat", true);
        BUILDER.pop();

        BUILDER.comment("Experience and levels").push("xp");
        XP_LEVEL_BASE = BUILDER
                .comment("Triangular level curve base: total XP for level n = base * n * (n+1) / 2.")
                .defineInRange("xpLevelBase", 100, 1, 1_000_000);
        MAX_LEVEL = BUILDER
                .comment("Level cap. Reaching it masters the class (unlocks dependant classes).")
                .defineInRange("maxLevel", 150, 1, 10_000);
        BUILDER.pop();

        BUILDER.comment("Skills").push("skills");
        PASSIVE_TICK_MS = BUILDER
                .comment("How often PASSIVE skill effects re-apply, in milliseconds.")
                .defineInRange("passiveTickMs", 3000, 250, 60_000);
        BUILDER.pop();

        BUILDER.comment("Karma (alignment). Positive deeds raise it, dark ones lower it.").push("karma");
        KARMA_KILL_PLAYER = BUILDER.defineInRange("killPlayer", -10_000L, Long.MIN_VALUE, Long.MAX_VALUE);
        KARMA_KILL_VILLAGER = BUILDER.defineInRange("killVillager", -1_000L, Long.MIN_VALUE, Long.MAX_VALUE);
        KARMA_KILL_ANIMAL = BUILDER.defineInRange("killAnimal", -10L, Long.MIN_VALUE, Long.MAX_VALUE);
        KARMA_KILL_MONSTER = BUILDER.defineInRange("killMonster", 20L, Long.MIN_VALUE, Long.MAX_VALUE);
        KARMA_POSITIVE_NAMES = BUILDER
                .comment("Comma-separated titles on the good side of the log scale.")
                .define("positiveNames", "Neutral,Kind,Good,Samaritan,Saintly");
        KARMA_NEGATIVE_NAMES = BUILDER
                .comment("Comma-separated titles on the dark side of the log scale.")
                .define("negativeNames", "Neutral,Rascal,Rogue,Villainous,Diabolic");
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private LQConfig() {}
}
