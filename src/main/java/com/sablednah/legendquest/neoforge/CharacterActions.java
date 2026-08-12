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
            Feedback.chat(player, "&cUnknown race: " + raceId);
            return false;
        }
        PlayerCharacter pc = CharacterService.data(player);
        boolean onDefault = CharacterService.race(player).map(Race::isDefault).orElse(true);
        if (pc.raceChanged() || !onDefault) {
            Feedback.chat(player, "&cYour race is chosen for life. An admin can change it.");
            return false;
        }
        if (!LQPermissions.canSelectRace(player, raceId)) {
            Feedback.chat(player, "&cThat race is not open to you.");
            return false;
        }
        Race target = holder.get().value();
        pc.setRace(raceId, !target.isDefault());
        CharacterService.refresh(player);
        Feedback.chat(player, "&6You are now " + article(target.name()) + " &l" + target.name() + "&r&6.");
        return true;
    }

    public static boolean chooseClass(ServerPlayer player, Identifier classId, boolean asSub) {
        var lookup = player.level().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS);
        var holder = lookup.get(ResourceKey.create(LQRegistries.CHAR_CLASS, classId));
        if (holder.isEmpty()) {
            Feedback.chat(player, "&cUnknown class: " + classId);
            return false;
        }
        CharClass target = holder.get().value();
        PlayerCharacter pc = CharacterService.data(player);

        if (!LQPermissions.canSelectClass(player, classId)) {
            Feedback.chat(player, "&cThat class is not open to you.");
            return false;
        }
        if (asSub && target.eligibility().mainOnly()) {
            Feedback.chat(player, "&c" + target.name() + " can only be a main class.");
            return false;
        }
        if (!asSub && target.eligibility().subOnly()) {
            Feedback.chat(player, "&c" + target.name() + " can only be a sub class.");
            return false;
        }
        if (!raceEligible(player, pc, target)) {
            Feedback.chat(player, "&cA " + CharacterService.race(player).map(Race::name).orElse("nobody")
                    + " cannot become " + article(target.name()) + " " + target.name() + ".");
            return false;
        }
        if (!requirementsMastered(pc, target)) {
            var requires = target.eligibility().requires();
            var requiresOne = target.eligibility().requiresOne();
            Feedback.chat(player, "&c" + target.name() + " requires mastering: "
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
        Feedback.chat(player, "&6You are now " + article(target.name()) + " &l" + target.name()
                + "&r&6" + (asSub ? " (sub class)." : "."));
        return true;
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
            Feedback.chat(player, "&cYou don't know that skill.");
            return false;
        }
        var def = SkillEngine.definition(player, skillId);
        if (def.isEmpty() || def.get().type() != com.sablednah.legendquest.skills.SkillType.ACTIVE) {
            Feedback.chat(player, "&cOnly active skills belong in a loadout.");
            return false;
        }
        PlayerCharacter pc = CharacterService.data(player);
        if (!pc.addToLoadout(skillId)) {
            Feedback.chat(player, "&7Already in the loadout.");
            return false;
        }
        Feedback.chat(player, "&6Added &l" + def.get().name() + "&r&6 to the loadout.");
        if (pc.loadoutItem().isEmpty()) {
            Feedback.chat(player, "&7Now hold your spellbook item and run /loadout bind.");
        }
        return true;
    }

    public static boolean loadoutRemove(ServerPlayer player, Identifier skillId) {
        if (CharacterService.data(player).removeFromLoadout(skillId)) {
            Feedback.chat(player, "&6Removed from the loadout.");
            return true;
        }
        Feedback.chat(player, "&7That skill isn't in the loadout.");
        return false;
    }

    private static String article(String noun) {
        return noun.isEmpty() || "AEIOU".indexOf(Character.toUpperCase(noun.charAt(0))) < 0 ? "a" : "an";
    }

    private CharacterActions() {}
}
