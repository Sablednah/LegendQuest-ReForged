package com.sablednah.legendquest.neoforge;

import java.util.Optional;
import java.util.Random;

import com.sablednah.legendquest.LQConfig;
import com.sablednah.legendquest.LQRegistries;
import com.sablednah.legendquest.LegendQuest;
import com.sablednah.legendquest.character.LQAttachments;
import com.sablednah.legendquest.character.PlayerCharacter;
import com.sablednah.legendquest.core.Leveling;
import com.sablednah.legendquest.core.Stat;
import com.sablednah.legendquest.data.CharClass;
import com.sablednah.legendquest.data.Race;
import com.sablednah.legendquest.data.StatBlock;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Derived-character logic: resolving race/class registry entries, computing
 * stats and vitals, and pushing them onto the player as attribute modifiers.
 *
 * <p>Modifiers are <em>transient</em> (never saved) with fixed per-source ids
 * and re-applied on login and on any character change — so they always
 * reflect current data files, and removing the mod leaves clean players.</p>
 */
public final class CharacterService {

    private static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(LegendQuest.MODID, "health");
    private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(LegendQuest.MODID, "speed");
    private static final double VANILLA_BASE_HEALTH = 20.0D;
    private static final double NORMAL_SPEED = 0.2D; // the old configs' "normal walking speed"

    public static PlayerCharacter data(ServerPlayer player) {
        return player.getData(LQAttachments.CHARACTER);
    }

    // --- registry access ---

    public static Optional<Race> race(ServerPlayer player) {
        PlayerCharacter pc = data(player);
        HolderLookup.RegistryLookup<Race> lookup =
                player.level().registryAccess().lookupOrThrow(LQRegistries.RACE);
        return pc.raceId()
                .flatMap(id -> lookup.get(ResourceKey.create(LQRegistries.RACE, id)))
                .map(Holder.Reference::value);
    }

    public static Optional<CharClass> charClass(ServerPlayer player, Optional<Identifier> id) {
        HolderLookup.RegistryLookup<CharClass> lookup =
                player.level().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS);
        return id.flatMap(i -> lookup.get(ResourceKey.create(LQRegistries.CHAR_CLASS, i)))
                .map(Holder.Reference::value);
    }

    public static Optional<CharClass> mainClass(ServerPlayer player) {
        return charClass(player, data(player).mainClassId());
    }

    public static Optional<CharClass> subClass(ServerPlayer player) {
        return charClass(player, data(player).subClassId());
    }

    /** Find the entry flagged {@code default: true} (the old Undecided/Citizen). */
    public static Optional<Identifier> defaultRace(ServerPlayer player) {
        var lookup = player.level().registryAccess().lookupOrThrow(LQRegistries.RACE);
        return lookup.listElements()
                .filter(ref -> ref.value().isDefault())
                .map(ref -> ref.key().identifier())
                .findFirst();
    }

    public static Optional<Identifier> defaultClass(ServerPlayer player) {
        var lookup = player.level().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS);
        return lookup.listElements()
                .filter(ref -> ref.value().isDefault())
                .map(ref -> ref.key().identifier())
                .findFirst();
    }

    // --- initialisation ---

    /** First-login setup: roll the statline, assign defaults, fill mana. */
    public static void ensureInitialised(ServerPlayer player) {
        PlayerCharacter pc = data(player);
        if (pc.baseStats().isEmpty()) {
            pc.setBaseStats(rollStats(player));
        }
        if (pc.raceId().isEmpty()) {
            defaultRace(player).ifPresent(id -> pc.setRace(id, false));
        }
        if (pc.mainClassId().isEmpty()) {
            defaultClass(player).ifPresent(pc::setMainClass);
        }
        if (pc.mana() <= 0) {
            pc.setMana(maxMana(player));
        }
    }

    /**
     * The old "random statline based on their uuid": deterministic, so a
     * player re-rolling by deleting data gets the same character back.
     */
    private static StatBlock rollStats(ServerPlayer player) {
        if (LQConfig.STATLINE_MODE.get() == LQConfig.StatlineMode.FLAT_12) {
            return new StatBlock(12, 12, 12, 12, 12, 12);
        }
        Random random = new Random(player.getUUID().getMostSignificantBits()
                ^ player.getUUID().getLeastSignificantBits());
        int[] rolls = new int[6];
        for (int n = 0; n < 6; n++) {
            // 4d6 drop lowest, the tabletop classic.
            int a = random.nextInt(6) + 1, b = random.nextInt(6) + 1,
                    c = random.nextInt(6) + 1, d = random.nextInt(6) + 1;
            rolls[n] = a + b + c + d - Math.min(Math.min(a, b), Math.min(c, d));
        }
        return new StatBlock(rolls[0], rolls[1], rolls[2], rolls[3], rolls[4], rolls[5]);
    }

    // --- derived numbers ---

    /** Character level = level of the main class's XP bank. */
    public static int level(ServerPlayer player) {
        PlayerCharacter pc = data(player);
        long xp = pc.mainClassId().map(pc::xpFor).orElse(0L);
        return Leveling.levelForXp(xp, LQConfig.XP_LEVEL_BASE.get(), LQConfig.MAX_LEVEL.get());
    }

    /** Effective stat line: rolled base + race mods + combined class mods + level bonuses. */
    public static StatBlock effectiveStats(ServerPlayer player) {
        PlayerCharacter pc = data(player);
        StatBlock stats = pc.baseStats().orElse(new StatBlock(12, 12, 12, 12, 12, 12));
        stats = stats.plus(race(player).map(Race::statmods).orElse(StatBlock.ZERO));
        StatBlock main = mainClass(player).map(CharClass::statmods).orElse(StatBlock.ZERO);
        StatBlock sub = subClass(player).map(CharClass::statmods).orElse(StatBlock.ZERO);
        stats = stats.plus(subClass(player).isPresent() ? StatBlock.combineClasses(main, sub) : main);
        int lvl = level(player);
        StatBlock fromLevels = race(player).map(r -> r.levels().totalStats(lvl)).orElse(StatBlock.ZERO)
                .plus(mainClass(player).map(c -> c.levels().totalStats(lvl)).orElse(StatBlock.ZERO));
        StatBlock bought = new StatBlock(
                pc.statBoost(Stat.STR.key()), pc.statBoost(Stat.DEX.key()),
                pc.statBoost(Stat.CON.key()), pc.statBoost(Stat.INT.key()),
                pc.statBoost(Stat.WIS.key()), pc.statBoost(Stat.CHR.key()));
        return stats.plus(fromLevels).plus(bought);
    }

    /** Escalating price of the NEXT +1 stat boost, wherever it lands. */
    public static int nextStatBoostCost(ServerPlayer player) {
        return LQConfig.STAT_BOOST_BASE_COST.get() + data(player).totalStatBoosts();
    }

    public static int statModifier(ServerPlayer player, Stat stat) {
        return Stat.modifier(effectiveStats(player).get(stat));
    }

    /**
     * Max health, following the old formula's shape: race base + CON modifier
     * + best class mod, then per-level growth scaled ±50% by CON.
     */
    public static double maxHealth(ServerPlayer player) {
        int con = statModifier(player, Stat.CON);
        double base = race(player).map(Race::baseHealth).orElse(VANILLA_BASE_HEALTH) + con;
        double classMod = best(player, CharClass.Growth::healthMod);
        double perLevel = best(player, CharClass.Growth::healthPerLevel);
        double conScale = ((con * 10.0D) + 100.0D) / 100.0D;
        int lvl = level(player);
        double fromLevels = race(player).map(r -> (double) r.levels().totalInt(lvl, LevelBonusGetters.HP))
                .orElse(0.0D)
                + mainClass(player).map(c -> (double) c.levels().totalInt(lvl, LevelBonusGetters.HP))
                        .orElse(0.0D);
        return Math.max(1.0D, base + classMod + lvl * perLevel * conScale + fromLevels * conScale);
    }

    public static double maxMana(ServerPlayer player) {
        int wis = statModifier(player, Stat.WIS);
        double base = race(player).map(Race::baseMana).orElse(10.0D) + wis;
        double classBonus = best(player, CharClass.Growth::manaBonus);
        double perLevel = best(player, CharClass.Growth::manaPerLevel);
        int lvl = level(player);
        double fromLevels = race(player).map(r -> (double) r.levels().totalInt(lvl, LevelBonusGetters.MANA))
                .orElse(0.0D)
                + mainClass(player).map(c -> (double) c.levels().totalInt(lvl, LevelBonusGetters.MANA))
                        .orElse(0.0D);
        return Math.max(0.0D, base + classBonus + lvl * perLevel + fromLevels);
    }

    public static double manaPerSecond(ServerPlayer player) {
        double race = race(player).map(Race::manaPerSecond).orElse(1.0D);
        double cls = best(player, CharClass.Growth::manaPerSecond);
        int lvl = level(player);
        double fromLevels = mainClass(player)
                .map(c -> c.levels().totalDouble(lvl, LevelBonuses -> LevelBonuses.manaRegen())).orElse(0.0D);
        return race + cls + fromLevels;
    }

    /** Skill points available to spend (race + class pools, INT-flavoured later). */
    public static int skillPointsTotal(ServerPlayer player) {
        int lvl = level(player);
        double race = race(player)
                .map(r -> r.progression().skillPoints() + lvl * r.progression().skillPointsPerLevel())
                .orElse(0.0D);
        double cls = mainClass(player)
                .map(c -> c.progression().skillPoints() + lvl * c.progression().skillPointsPerLevel())
                .orElse(0.0D);
        int spBonus = mainClass(player).map(c -> c.levels().totalInt(lvl, LevelBonusGetters.SP)).orElse(0)
                + race(player).map(r -> r.levels().totalInt(lvl, LevelBonusGetters.SP)).orElse(0);
        return (int) Math.floor(race + cls) + spBonus;
    }

    /** Best-of-main-and-sub for a class growth number (the old max rule). */
    private static double best(ServerPlayer player, java.util.function.ToDoubleFunction<CharClass.Growth> getter) {
        double main = mainClass(player).map(c -> getter.applyAsDouble(c.growth())).orElse(0.0D);
        double sub = subClass(player).map(c -> getter.applyAsDouble(c.growth())).orElse(0.0D);
        return Math.max(main, sub);
    }

    // --- application ---

    /**
     * The one call after anything that changes who the character is.
     * (Worn-armour penalties need no hook here: the per-second penalty tick
     * in LQServerEvents re-evaluates gear against the *current* character,
     * so a class change takes effect within a second.)
     */
    public static void refresh(ServerPlayer player) {
        applyModifiers(player);
        CharacterSync.send(player);
    }

    /** Push derived numbers onto the entity. Call via {@link #refresh}. */
    public static void applyModifiers(ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double target = maxHealth(player);
            health.addOrUpdateTransientModifier(new AttributeModifier(
                    HEALTH_ID, target - VANILLA_BASE_HEALTH, AttributeModifier.Operation.ADD_VALUE));
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double raceSpeed = race(player).map(Race::baseSpeed).orElse(NORMAL_SPEED);
            double multiplier = (raceSpeed / NORMAL_SPEED) - 1.0D
                    + best(player, CharClass.Growth::speedMod);
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    SPEED_ID, multiplier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        applyBoonAttributes(player);
    }

    /**
     * Innate attribute boons (dwarven toughness, hobbit luck...), race +
     * classes stacking, ramping with level. We sweep the union of attribute
     * ids mentioned by ANY race/class so a stale bonus is removed when the
     * player changes into something less gifted.
     */
    private static void applyBoonAttributes(ServerPlayer player) {
        int lvl = level(player);
        java.util.Map<String, Double> combined = new java.util.HashMap<>();
        java.util.Set<String> mentioned = new java.util.HashSet<>();

        race(player).ifPresent(r -> accumulateBoons(combined, r.boons(), lvl));
        mainClass(player).ifPresent(c -> accumulateBoons(combined, c.boons(), lvl));
        subClass(player).ifPresent(c -> accumulateBoons(combined, c.boons(), lvl));
        player.level().registryAccess().lookupOrThrow(LQRegistries.RACE).listElements()
                .forEach(ref -> mentioned.addAll(ref.value().boons().attributes().keySet()));
        player.level().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS).listElements()
                .forEach(ref -> mentioned.addAll(ref.value().boons().attributes().keySet()));

        for (String idString : mentioned) {
            Identifier attrId = Identifier.tryParse(idString);
            if (attrId == null) continue;
            var holder = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE
                    .get(ResourceKey.create(net.minecraft.core.registries.Registries.ATTRIBUTE, attrId));
            if (holder.isEmpty()) continue; // typo'd id: boon lost, world fine
            AttributeInstance instance = player.getAttribute(holder.get());
            if (instance == null) continue;
            Identifier modId = Identifier.fromNamespaceAndPath(LegendQuest.MODID,
                    "boon." + attrId.getNamespace() + "." + attrId.getPath());
            Double value = combined.get(idString);
            if (value != null && value != 0.0D) {
                instance.addOrUpdateTransientModifier(new AttributeModifier(
                        modId, value, AttributeModifier.Operation.ADD_VALUE));
            } else {
                instance.removeModifier(modId);
            }
        }
    }

    private static void accumulateBoons(java.util.Map<String, Double> into,
            com.sablednah.legendquest.data.Boons boons, int level) {
        boons.attributes().forEach((id, bonus) -> into.merge(id, bonus.at(level), Double::sum));
    }

    /** Race + main + sub, for the scalar boons (rebates, refunds). */
    static double totalBoon(ServerPlayer player,
            java.util.function.ToDoubleFunction<com.sablednah.legendquest.data.Boons> getter) {
        return race(player).map(r -> getter.applyAsDouble(r.boons())).orElse(0.0D)
                + mainClass(player).map(c -> getter.applyAsDouble(c.boons())).orElse(0.0D)
                + subClass(player).map(c -> getter.applyAsDouble(c.boons())).orElse(0.0D);
    }

    // --- karma ---

    /** Log-scale karma title from the config name lists. */
    public static String karmaName(long karma) {
        String csv = karma >= 0 ? LQConfig.KARMA_POSITIVE_NAMES.get() : LQConfig.KARMA_NEGATIVE_NAMES.get();
        String[] names = csv.split(",");
        long magnitude = Math.abs(karma);
        int index = magnitude == 0 ? 0 : (int) Math.floor(Math.log10(magnitude));
        index = Math.min(index, names.length - 1);
        return names[Math.max(0, index)].trim();
    }

    /** ToIntFunction getters for LevelBonuses sums (avoids lambda clutter above). */
    private static final class LevelBonusGetters {
        static final java.util.function.ToIntFunction<com.sablednah.legendquest.data.LevelBonuses.Bonus> HP =
                com.sablednah.legendquest.data.LevelBonuses.Bonus::hp;
        static final java.util.function.ToIntFunction<com.sablednah.legendquest.data.LevelBonuses.Bonus> MANA =
                com.sablednah.legendquest.data.LevelBonuses.Bonus::mana;
        static final java.util.function.ToIntFunction<com.sablednah.legendquest.data.LevelBonuses.Bonus> SP =
                com.sablednah.legendquest.data.LevelBonuses.Bonus::sp;
    }

    private CharacterService() {}
}
