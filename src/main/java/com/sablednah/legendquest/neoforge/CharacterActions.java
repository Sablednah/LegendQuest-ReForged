package com.sablednah.legendquest.neoforge;

import java.util.List;
import java.util.Optional;

import com.sablednah.legendquest.LQConfig;
import com.sablednah.legendquest.LQRegistries;
import com.sablednah.legendquest.character.PlayerCharacter;
import com.sablednah.legendquest.core.Leveling;
import com.sablednah.legendquest.data.CharClass;
import com.sablednah.legendquest.data.Race;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * The single rule set for character-changing actions, shared by the commands
 * and the GUI payloads — clicking a race in the panel and typing
 * {@code /race choose} run exactly the same checks and say the same things.
 * Every method validates, gives feedback, and returns whether it happened.
 */
public final class CharacterActions {

    // --- race / class selection ---

    public static boolean chooseRace(ServerPlayer player, Identifier raceId) {
        var lookup = player.level().registryAccess().lookupOrThrow(LQRegistries.RACE);
        var holder = lookup.get(ResourceKey.create(LQRegistries.RACE, raceId));
        if (holder.isEmpty()) {
            Feedback.notify(player, "&cUnknown race: " + raceId);
            return false;
        }
        PlayerCharacter pc = CharacterService.data(player);
        boolean onDefault = CharacterService.race(player).map(Race::isDefault).orElse(true);
        if (pc.raceChanged() || !onDefault) {
            Feedback.notify(player, "&cYour race is chosen for life. An admin can change it.");
            return false;
        }
        if (!LQPermissions.canSelectRace(player, raceId)) {
            Feedback.notify(player, "&cThat race is not open to you.");
            return false;
        }
        Race target = holder.get().value();
        pc.setRace(raceId, !target.isDefault());
        CharacterService.refresh(player);
        Feedback.notify(player, "&6You are now " + article(target.name()) + " &l" + target.name() + "&r&6.");
        return true;
    }

    public static boolean chooseClass(ServerPlayer player, Identifier classId, boolean asSub) {
        var lookup = player.level().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS);
        var holder = lookup.get(ResourceKey.create(LQRegistries.CHAR_CLASS, classId));
        if (holder.isEmpty()) {
            Feedback.notify(player, "&cUnknown class: " + classId);
            return false;
        }
        CharClass target = holder.get().value();
        PlayerCharacter pc = CharacterService.data(player);

        if (!LQPermissions.canSelectClass(player, classId)) {
            Feedback.notify(player, "&cThat class is not open to you.");
            return false;
        }
        if (asSub && target.eligibility().mainOnly()) {
            Feedback.notify(player, "&c" + target.name() + " can only be a main class.");
            return false;
        }
        if (!asSub && target.eligibility().subOnly()) {
            Feedback.notify(player, "&c" + target.name() + " can only be a sub class.");
            return false;
        }
        if (!raceEligible(player, pc, target)) {
            Feedback.notify(player, "&cA " + CharacterService.race(player).map(Race::name).orElse("nobody")
                    + " cannot become " + article(target.name()) + " " + target.name() + ".");
            return false;
        }
        if (!requirementsMastered(pc, target)) {
            var requires = target.eligibility().requires();
            var requiresOne = target.eligibility().requiresOne();
            Feedback.notify(player, "&c" + target.name() + " requires mastering: "
                    + (requires.isEmpty() ? "" : requires)
                    + (requiresOne.isEmpty() ? "" : " one of " + requiresOne));
            return false;
        }

        if (asSub) {
            pc.setSubClass(Optional.of(classId));
        } else {
            pc.setMainClass(classId);
        }
        CharacterService.refresh(player);
        Feedback.notify(player, "&6You are now " + article(target.name()) + " &l" + target.name()
                + "&r&6" + (asSub ? " (sub class)." : "."));
        return true;
    }

    /** Is this class open to that race? (Shared with admin legality checks.) */
    public static boolean classOpenTo(Identifier raceId, Race race, CharClass cls) {
        var el = cls.eligibility();
        return (el.allowedRaces().isEmpty() && el.allowedGroups().isEmpty())
                || el.allowedRaces().contains(raceId)
                || race.groups().stream().anyMatch(el.allowedGroups()::contains);
    }

    static boolean raceEligible(ServerPlayer player, PlayerCharacter pc, CharClass target) {
        List<Identifier> allowedRaces = target.eligibility().allowedRaces();
        List<String> allowedGroups = target.eligibility().allowedGroups();
        if (allowedRaces.isEmpty() && allowedGroups.isEmpty()) return true;
        boolean raceOk = pc.raceId().isPresent() && allowedRaces.contains(pc.raceId().get());
        boolean groupOk = CharacterService.race(player)
                .map(r -> r.groups().stream().anyMatch(allowedGroups::contains)).orElse(false);
        return raceOk || groupOk;
    }

    static boolean requirementsMastered(PlayerCharacter pc, CharClass target) {
        long masterXp = Leveling.totalXpForLevel(LQConfig.MAX_LEVEL.get(), LQConfig.XP_LEVEL_BASE.get());
        var requires = target.eligibility().requires();
        var requiresOne = target.eligibility().requiresOne();
        return requires.stream().allMatch(id -> pc.xpFor(id) >= masterXp)
                && (requiresOne.isEmpty() || requiresOne.stream().anyMatch(id -> pc.xpFor(id) >= masterXp));
    }

    /** GUI greying: could this player take the class right now (as main)? */
    static boolean classAvailable(ServerPlayer player, PlayerCharacter pc, Identifier id, CharClass target) {
        return LQPermissions.canSelectClass(player, id)
                && !target.eligibility().subOnly()
                && raceEligible(player, pc, target)
                && requirementsMastered(pc, target);
    }

    // --- loadout editing (commands + drag & drop) ---

    public static boolean loadoutAdd(ServerPlayer player, Identifier skillId) {
        if (!SkillEngine.grants(player).containsKey(skillId)) {
            Feedback.notify(player, "&cYou don't know that skill.");
            return false;
        }
        var def = SkillEngine.definition(player, skillId);
        if (def.isEmpty() || def.get().type() != com.sablednah.legendquest.skills.SkillType.ACTIVE) {
            Feedback.notify(player, "&cOnly active skills belong in a loadout.");
            return false;
        }
        PlayerCharacter pc = CharacterService.data(player);
        if (!pc.addToLoadout(skillId)) {
            Feedback.notify(player, "&7Already in the loadout.");
            return false;
        }
        Feedback.notify(player, "&6Added &l" + def.get().name() + "&r&6 to the loadout.");
        if (pc.loadoutItem().isEmpty()) {
            Feedback.notify(player, "&7Now hold your spellbook item and run /loadout bind.");
        }
        return true;
    }

    public static boolean loadoutRemove(ServerPlayer player, Identifier skillId) {
        if (CharacterService.data(player).removeFromLoadout(skillId)) {
            Feedback.notify(player, "&6Removed from the loadout.");
            return true;
        }
        Feedback.notify(player, "&7That skill isn't in the loadout.");
        return false;
    }

    // --- spending skill points ---

    public static boolean buySkill(ServerPlayer player, Identifier skillId) {
        PlayerCharacter pc = CharacterService.data(player);
        var grant = SkillEngine.grants(player).get(skillId);
        if (grant == null) {
            Feedback.notify(player, "&cYour race/class does not offer that skill.");
            return false;
        }
        if (grant.cost() <= 0) {
            Feedback.notify(player, "&7No purchase needed — it unlocks at level " + grant.level() + ".");
            return false;
        }
        if (pc.hasPurchased(skillId)) {
            Feedback.notify(player, "&7Already bought.");
            return false;
        }
        if (CharacterService.level(player) < grant.level()) {
            Feedback.notify(player, "&cThat needs level " + grant.level() + ".");
            return false;
        }
        if (!grant.karmaAllows(pc.karma())) {
            Feedback.notify(player, pc.karma() < grant.karmaMin()
                    ? "&cYour soul is not bright enough for that."
                    : "&cYour soul is not dark enough for that.");
            return false;
        }
        int available = CharacterService.skillPointsTotal(player) - pc.skillPointsSpent();
        if (available < grant.cost()) {
            Feedback.notify(player, "&cNeeds " + grant.cost() + " skill points; you have " + available + ".");
            return false;
        }
        pc.purchase(skillId, grant.cost());
        Feedback.notify(player, "&6Learned &l"
                + SkillEngine.definition(player, skillId).map(d -> d.name()).orElse(skillId.toString())
                + "&r&6 for " + grant.cost() + " points.");
        CharacterSync.send(player);
        return true;
    }

    public static boolean buyStat(ServerPlayer player, com.sablednah.legendquest.core.Stat stat) {
        PlayerCharacter pc = CharacterService.data(player);
        int cost = CharacterService.nextStatBoostCost(player);
        int available = CharacterService.skillPointsTotal(player) - pc.skillPointsSpent();
        if (available < cost) {
            Feedback.notify(player, "&cA +1 " + stat.name() + " costs " + cost
                    + " skill points; you have " + available + ".");
            return false;
        }
        pc.buyStatBoost(stat.key(), cost);
        CharacterService.refresh(player);
        Feedback.notify(player, "&6+1 " + stat.name() + " bought for " + cost
                + " points &7(next boost costs " + CharacterService.nextStatBoostCost(player) + ").");
        return true;
    }

    public static boolean buyFeat(ServerPlayer player, Identifier featId) {
        var lookup = player.level().registryAccess().lookupOrThrow(LQRegistries.FEAT);
        var holder = lookup.get(ResourceKey.create(LQRegistries.FEAT, featId));
        if (holder.isEmpty()) {
            Feedback.notify(player, "&cUnknown feat: " + featId);
            return false;
        }
        var feat = holder.get().value();
        PlayerCharacter pc = CharacterService.data(player);
        if (pc.hasFeat(featId)) {
            Feedback.notify(player, "&7You already have &l" + feat.name() + "&7.");
            return false;
        }
        if (CharacterService.level(player) < feat.level()) {
            Feedback.notify(player, "&c" + feat.name() + " needs level " + feat.level() + ".");
            return false;
        }
        if (!feat.karmaAllows(pc.karma())) {
            Feedback.notify(player, pc.karma() < feat.karmaMin()
                    ? "&cYour soul is not bright enough for " + feat.name() + "."
                    : "&cYour soul is not dark enough for " + feat.name() + ".");
            return false;
        }
        for (Identifier required : feat.requires()) {
            if (!pc.hasFeat(required)) {
                String name = lookup.get(ResourceKey.create(LQRegistries.FEAT, required))
                        .map(r -> r.value().name()).orElse(required.toString());
                Feedback.notify(player, "&c" + feat.name() + " requires the &l" + name + "&r&c feat first.");
                return false;
            }
        }
        boolean raceOk = feat.allowedRaces().isEmpty() && feat.allowedGroups().isEmpty()
                || (pc.raceId().isPresent() && feat.allowedRaces().contains(pc.raceId().get()))
                || CharacterService.race(player)
                        .map(r -> r.groups().stream().anyMatch(feat.allowedGroups()::contains))
                        .orElse(false);
        if (!raceOk) {
            Feedback.notify(player, "&c" + feat.name() + " is not in your blood.");
            return false;
        }
        if (!feat.allowedClasses().isEmpty()
                && !(pc.mainClassId().map(feat.allowedClasses()::contains).orElse(false)
                        || pc.subClassId().map(feat.allowedClasses()::contains).orElse(false))) {
            Feedback.notify(player, "&c" + feat.name() + " is not for your calling.");
            return false;
        }
        int available = CharacterService.skillPointsTotal(player) - pc.skillPointsSpent();
        if (available < feat.cost()) {
            Feedback.notify(player, "&c" + feat.name() + " costs " + feat.cost()
                    + " skill points; you have " + available + ".");
            return false;
        }
        pc.buyFeat(featId, feat.cost());
        CharacterService.refresh(player); // boons/proficiencies apply now
        Feedback.notify(player, "&6Feat gained: &l" + feat.name() + "&r&6 ("
                + feat.cost() + " points).");
        return true;
    }

    // --- respec: burn levels, get every point back ---

    private static final java.util.Map<java.util.UUID, Long> RESPEC_OFFERS =
            new java.util.HashMap<>();

    /** Two-step: first call quotes the price, second within 30s pays it. */
    public static boolean respec(ServerPlayer player) {
        PlayerCharacter pc = CharacterService.data(player);
        int levelCost = LQConfig.RESPEC_LEVEL_COST.get();
        if (pc.skillPointsSpent() <= 0) {
            Feedback.notify(player, "&7Nothing to respec — no skill points are spent.");
            return false;
        }
        Long offered = RESPEC_OFFERS.get(player.getUUID());
        long now = System.currentTimeMillis();
        if (offered == null || now - offered > 30_000) {
            RESPEC_OFFERS.put(player.getUUID(), now);
            Feedback.notify(player, "&6Respec refunds &l" + pc.skillPointsSpent()
                    + "&r&6 skill points (forgetting bought skills and stat boosts)"
                    + (levelCost > 0 ? " and burns &c" + levelCost + " character level"
                            + (levelCost == 1 ? "" : "s") + "&6." : "."));
            Feedback.notify(player, "&7Run &f/lq respec&7 again within 30 seconds to seal it.");
            return false;
        }
        RESPEC_OFFERS.remove(player.getUUID());
        int refunded = pc.skillPointsSpent();
        pc.refundPurchases();
        if (levelCost > 0) {
            pc.mainClassId().ifPresent(cls -> {
                int level = CharacterService.level(player);
                int target = Math.max(0, level - levelCost);
                pc.setXp(cls, Leveling.totalXpForLevel(target, LQConfig.XP_LEVEL_BASE.get()));
            });
        }
        CharacterService.refresh(player);
        Feedback.notify(player, "&6The past unravels: &f" + refunded
                + "&6 skill points refunded, level " + CharacterService.level(player)
                + " — spend wiser this time.");
        return true;
    }

    private static String article(String noun) {
        return noun.isEmpty() || "AEIOU".indexOf(Character.toUpperCase(noun.charAt(0))) < 0 ? "a" : "an";
    }

    private CharacterActions() {}
}
