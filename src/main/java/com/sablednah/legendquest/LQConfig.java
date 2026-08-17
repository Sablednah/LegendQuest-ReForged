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
    public static final ModConfigSpec.BooleanValue LEVEL_UP_FANFARE;
    public static final ModConfigSpec.IntValue MINE_XP_ORE;
    public static final ModConfigSpec.IntValue MINE_XP_BLOCK;
    public static final ModConfigSpec.IntValue SMELT_XP_ITEM;
    public static final ModConfigSpec.IntValue PASSIVE_TICK_MS;
    public static final ModConfigSpec.IntValue STAT_BOOST_BASE_COST;
    public static final ModConfigSpec.IntValue RESPEC_LEVEL_COST;
    public static final ModConfigSpec.LongValue KARMA_KILL_PLAYER;
    public static final ModConfigSpec.LongValue KARMA_KILL_VILLAGER;
    public static final ModConfigSpec.LongValue KARMA_KILL_ANIMAL;
    public static final ModConfigSpec.LongValue KARMA_KILL_MONSTER;
    public static final ModConfigSpec.ConfigValue<String> KARMA_POSITIVE_NAMES;
    public static final ModConfigSpec.ConfigValue<String> KARMA_NEGATIVE_NAMES;
    public static final ModConfigSpec.IntValue PARTY_RANGE;
    public static final ModConfigSpec.IntValue PARTY_XP_SHARE;
    public static final ModConfigSpec.BooleanValue BLOCK_PARTY_PVP;
    public static final ModConfigSpec.IntValue PARTY_TP_COOLDOWN;

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
        LEVEL_UP_FANFARE = BUILDER
                .comment("Announce a level with a title card and the toast chime, not just a",
                        "chat line. Off leaves the chat line alone.")
                .define("levelUpFanfare", true);
        MINE_XP_ORE = BUILDER
                .comment("Class XP for breaking an ore (anything tagged #c:ores, so modded",
                        "ores count). A race or class's xp_adjust_mine applies on top. 0 disables.")
                .defineInRange("mineXpOre", 4, 0, 10_000);
        MINE_XP_BLOCK = BUILDER
                .comment("Class XP for breaking any other block that needs a tool and is not",
                        "instant-break — the mundane graft of digging out a mine. 0 disables.")
                .defineInRange("mineXpBlock", 1, 0, 10_000);
        SMELT_XP_ITEM = BUILDER
                .comment("Class XP per item taken out of a furnace. A race or class's",
                        "xp_adjust_smelt applies on top. 0 disables.")
                .defineInRange("smeltXpItem", 1, 0, 10_000);
        BUILDER.pop();

        BUILDER.comment("Skills").push("skills");
        PASSIVE_TICK_MS = BUILDER
                .comment("How often PASSIVE skill effects re-apply, in milliseconds.")
                .defineInRange("passiveTickMs", 3000, 250, 60_000);
        STAT_BOOST_BASE_COST = BUILDER
                .comment("Skill points for the first +1 stat boost; every boost already",
                        "bought raises the next one's price by one (they're meant to sting).")
                .defineInRange("statBoostBaseCost", 5, 1, 1_000);
        RESPEC_LEVEL_COST = BUILDER
                .comment("Character levels burned by /lq respec (which refunds every skill point).")
                .defineInRange("respecLevelCost", 1, 0, 100);
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

        BUILDER.comment("Parties").push("party");
        PARTY_RANGE = BUILDER
                .comment("Members within this many blocks of a kill share its XP.")
                .defineInRange("range", 32, 4, 256);
        PARTY_XP_SHARE = BUILDER
                .comment("Percent of kill XP each nearby party member also receives.")
                .defineInRange("xpSharePercent", 50, 0, 100);
        BLOCK_PARTY_PVP = BUILDER
                .comment("Party members cannot damage each other.")
                .define("blockPartyPvP", true);
        PARTY_TP_COOLDOWN = BUILDER
                .comment("Seconds between /party tp uses (0 disables the command).")
                .defineInRange("tpCooldownSeconds", 60, 0, 86_400);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private LQConfig() {}
}
