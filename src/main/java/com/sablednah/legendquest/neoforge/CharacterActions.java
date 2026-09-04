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
            Feedback.notify(player, Lang.fmt("msg.choose.unknown_race", "id", raceId));
            return false;
        }
        PlayerCharacter pc = CharacterService.data(player);
        boolean onDefault = CharacterService.race(player).map(Race::isDefault).orElse(true);
        if (pc.raceChanged() || !onDefault) {
            Feedback.notify(player, Lang.get("msg.choose.race_locked_in"));
            return false;
        }
        if (!LQPermissions.canSelectRace(player, raceId)) {
            Feedback.notify(player, Lang.get("msg.choose.race_not_open"));
            return false;
        }
        Race target = holder.get().value();
        pc.setRace(raceId, !target.isDefault());
        pruneUnknownSkills(player);
        CharacterService.refresh(player);
        Feedback.notify(player, Lang.fmt("msg.choose.race_done", "article", article(target.name()), "race", target.name()));
        return true;
    }

    public static boolean chooseClass(ServerPlayer player, Identifier classId, boolean asSub) {
        var lookup = player.level().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS);
        var holder = lookup.get(ResourceKey.create(LQRegistries.CHAR_CLASS, classId));
        if (holder.isEmpty()) {
            Feedback.notify(player, Lang.fmt("msg.choose.unknown_class", "id", classId));
            return false;
        }
        CharClass target = holder.get().value();
        PlayerCharacter pc = CharacterService.data(player);

        if (!LQPermissions.canSelectClass(player, classId)) {
            Feedback.notify(player, Lang.get("msg.choose.class_not_open"));
            return false;
        }
        if (asSub && target.eligibility().mainOnly()) {
            Feedback.notify(player, Lang.fmt("msg.choose.main_only", "class", target.name()));
            return false;
        }
        if (!asSub && target.eligibility().subOnly()) {
            Feedback.notify(player, Lang.fmt("msg.choose.sub_only", "class", target.name()));
            return false;
        }
        if (!raceEligible(player, pc, target)) {
            Feedback.notify(player, Lang.fmt("msg.choose.race_cannot",
                    "race", CharacterService.race(player).map(Race::name).orElse("nobody"),
                    "article", article(target.name()), "class", target.name()));
            return false;
        }
        if (!requirementsMastered(pc, target)) {
            var requires = target.eligibility().requires();
            var requiresOne = target.eligibility().requiresOne();
            Feedback.notify(player, Lang.fmt("msg.choose.requires_mastering", "class", target.name(),
                    "requires", (requires.isEmpty() ? "" : requires)
                            + (requiresOne.isEmpty() ? "" : " one of " + requiresOne)));
            return false;
        }

        // Captured before the switch: what they are walking away from, and how
        // far they had got. Only worth saying for a DIFFERENT main class that
        // has actually been played.
        Optional<Identifier> leaving = asSub ? Optional.empty()
                : pc.mainClassId().filter(id -> !id.equals(classId)).filter(id -> pc.xpFor(id) > 0L);
        int leavingLevel = leaving
                .map(id -> Leveling.levelForXp(pc.xpFor(id),
                        LQConfig.XP_LEVEL_BASE.get(), LQConfig.MAX_LEVEL.get()))
                .orElse(0);

        if (asSub) {
            pc.setSubClass(Optional.of(classId));
        } else {
            pc.setMainClass(classId);
        }
        pruneUnknownSkills(player);
        CharacterService.refresh(player);
        Feedback.notify(player, Lang.fmt("msg.choose.class_done", "article", article(target.name()),
                "class", target.name(), "suffix", asSub ? " (sub)." : "."));

        // Switching main class is alarming and harmless, which is the worst
        // combination to leave unexplained: level, max health, title and skills
        // all drop to the new class's level 0 in the same instant, and it reads
        // exactly like having lost the character. XP is banked PER CLASS and
        // nothing ever clears a bank, so the old life is intact and one command
        // away. Say so at the moment the bar drops, not in the documentation.
        leaving.ifPresent(id -> Feedback.notify(player, Lang.fmt("msg.choose.class_banked",
                "class", CharacterService.charClass(player, Optional.of(id)).map(CharClass::name)
                        .orElse(id.getPath()),
                "level", leavingLevel, "id", id.toString())));
        return true;
    }

    /**
     * After any identity change: drop loadout entries and item bindings for
     * skills the character no longer has ANY grant for — a class switch
     * must not leave five ghost books in the loadout strip. Karma-suspended
     * skills keep their slots (they still have a grant; they're sleeping).
     */
    /**
     * Switch a passive or triggered skill off, or back on.
     *
     * <p>Shared by {@code /skill toggle} and the click on its row in the skills
     * panel, so the two can never grow different rules or different wording.
     * The reply always names the way back — a player who switched night vision
     * off through a shader pack must never have to go looking for how to undo
     * it.</p>
     *
     * @return true if the skill actually changed state.
     */
    public static boolean toggleSkill(ServerPlayer player, Identifier skillId) {
        String name = SkillEngine.definition(player, skillId)
                .map(com.sablednah.legendquest.data.SkillDefinition::name).orElse(skillId.getPath());
        switch (SkillEngine.toggle(player, skillId)) {
            case SWITCHED_OFF -> {
                Feedback.notify(player, Lang.fmt("msg.skill.toggled_off",
                        "skill", name, "id", SkillEngine.friendlyId(player, skillId)));
                CharacterSync.send(player);
                return true;
            }
            case SWITCHED_ON -> {
                Feedback.notify(player, Lang.fmt("msg.skill.toggled_on", "skill", name));
                CharacterSync.send(player);
                return true;
            }
            case ACTIVE_TYPE -> Feedback.notify(player, Lang.fmt("msg.skill.toggle_active", "skill", name));
            case FIXED -> Feedback.notify(player, Lang.fmt("msg.skill.toggle_fixed", "skill", name));
            case NOT_LOADED -> Feedback.notify(player, Lang.fmt("msg.skill.not_loaded", "id", skillId));
            default -> Feedback.notify(player, Lang.get("msg.skill.not_known"));
        }
        return false;
    }

    public static void pruneUnknownSkills(ServerPlayer player) {
        var known = SkillEngine.grants(player).keySet();
        PlayerCharacter pc = CharacterService.data(player);
        for (Identifier skillId : pc.loadout()) {
            if (!known.contains(skillId)) pc.removeFromLoadout(skillId);
        }
        for (var binding : pc.bindings().entrySet()) {
            Identifier skillId = Identifier.tryParse(binding.getValue());
            if (skillId == null || !known.contains(skillId)) {
                Identifier itemId = Identifier.tryParse(binding.getKey());
                if (itemId != null) pc.unbind(itemId);
            }
        }
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
            Feedback.notify(player, Lang.get("msg.skill.not_known"));
            return false;
        }
        var def = SkillEngine.definition(player, skillId);
        if (def.isEmpty() || def.get().type() != com.sablednah.legendquest.skills.SkillType.ACTIVE) {
            Feedback.notify(player, Lang.get("msg.loadout.active_only"));
            return false;
        }
        PlayerCharacter pc = CharacterService.data(player);
        if (!pc.addToLoadout(skillId)) {
            Feedback.notify(player, Lang.get("msg.loadout.already_in"));
            return false;
        }
        Feedback.notify(player, Lang.fmt("msg.loadout.added", "skill", def.get().name()));
        if (pc.loadoutItem().isEmpty()) {
            Feedback.notify(player, Lang.get("msg.loadout.bind_hint"));
        }
        return true;
    }

    public static boolean loadoutRemove(ServerPlayer player, Identifier skillId) {
        if (CharacterService.data(player).removeFromLoadout(skillId)) {
            Feedback.notify(player, Lang.get("msg.loadout.removed"));
            return true;
        }
        Feedback.notify(player, Lang.get("msg.loadout.not_in"));
        return false;
    }

    // --- spending skill points ---

    public static boolean buySkill(ServerPlayer player, Identifier skillId) {
        PlayerCharacter pc = CharacterService.data(player);
        var grant = SkillEngine.grants(player).get(skillId);
        if (grant == null) {
            Feedback.notify(player, Lang.get("msg.buy.skill_not_offered"));
            return false;
        }
        if (grant.cost() <= 0) {
            Feedback.notify(player, Lang.fmt("msg.buy.no_purchase_needed", "level", grant.level()));
            return false;
        }
        if (pc.hasPurchased(skillId)) {
            Feedback.notify(player, Lang.get("msg.buy.already_bought"));
            return false;
        }
        if (CharacterService.level(player) < grant.level()) {
            Feedback.notify(player, Lang.fmt("msg.buy.needs_level", "level", grant.level()));
            return false;
        }
        if (!grant.karmaAllows(pc.karma())) {
            Feedback.notify(player, Lang.get(pc.karma() < grant.karmaMin()
                    ? "msg.buy.soul_not_bright" : "msg.buy.soul_not_dark"));
            return false;
        }
        int available = CharacterService.skillPointsTotal(player) - pc.skillPointsSpent();
        if (available < grant.cost()) {
            Feedback.notify(player, Lang.fmt("msg.buy.not_enough_points", "cost", grant.cost(), "have", available));
            return false;
        }
        pc.purchase(skillId, grant.cost());
        Feedback.notify(player, Lang.fmt("msg.buy.learned",
                "skill", SkillEngine.definition(player, skillId).map(d -> d.name()).orElse(skillId.toString()),
                "cost", grant.cost()));
        CharacterSync.send(player);
        return true;
    }

    public static boolean buyStat(ServerPlayer player, com.sablednah.legendquest.core.Stat stat) {
        PlayerCharacter pc = CharacterService.data(player);
        int cost = CharacterService.nextStatBoostCost(player);
        int available = CharacterService.skillPointsTotal(player) - pc.skillPointsSpent();
        if (available < cost) {
            Feedback.notify(player, Lang.fmt("msg.buy.stat_cost", "stat", stat.name(), "cost", cost, "have", available));
            return false;
        }
        pc.buyStatBoost(stat.key(), cost);
        CharacterService.refresh(player);
        Feedback.notify(player, Lang.fmt("msg.buy.stat_done", "stat", stat.name(), "cost", cost, "next", CharacterService.nextStatBoostCost(player)));
        return true;
    }

    public static boolean buyFeat(ServerPlayer player, Identifier featId) {
        var lookup = player.level().registryAccess().lookupOrThrow(LQRegistries.FEAT);
        var holder = lookup.get(ResourceKey.create(LQRegistries.FEAT, featId));
        if (holder.isEmpty()) {
            Feedback.notify(player, Lang.fmt("msg.feat.unknown", "id", featId));
            return false;
        }
        var feat = holder.get().value();
        PlayerCharacter pc = CharacterService.data(player);
        if (pc.hasFeat(featId)) {
            Feedback.notify(player, Lang.fmt("msg.feat.already", "feat", feat.name()));
            return false;
        }
        if (CharacterService.level(player) < feat.level()) {
            Feedback.notify(player, Lang.fmt("msg.feat.needs_level", "feat", feat.name(), "level", feat.level()));
            return false;
        }
        if (!feat.karmaAllows(pc.karma())) {
            Feedback.notify(player, Lang.fmt(pc.karma() < feat.karmaMin()
                    ? "msg.feat.soul_not_bright" : "msg.feat.soul_not_dark", "feat", feat.name()));
            return false;
        }
        for (Identifier required : feat.requires()) {
            if (!pc.hasFeat(required)) {
                String name = lookup.get(ResourceKey.create(LQRegistries.FEAT, required))
                        .map(r -> r.value().name()).orElse(required.toString());
                Feedback.notify(player, Lang.fmt("msg.feat.requires", "feat", feat.name(), "required", name));
                return false;
            }
        }
        boolean raceOk = feat.allowedRaces().isEmpty() && feat.allowedGroups().isEmpty()
                || (pc.raceId().isPresent() && feat.allowedRaces().contains(pc.raceId().get()))
                || CharacterService.race(player)
                        .map(r -> r.groups().stream().anyMatch(feat.allowedGroups()::contains))
                        .orElse(false);
        if (!raceOk) {
            Feedback.notify(player, Lang.fmt("msg.feat.not_your_blood", "feat", feat.name()));
            return false;
        }
        if (!feat.allowedClasses().isEmpty()
                && !(pc.mainClassId().map(feat.allowedClasses()::contains).orElse(false)
                        || pc.subClassId().map(feat.allowedClasses()::contains).orElse(false))) {
            Feedback.notify(player, Lang.fmt("msg.feat.not_your_calling", "feat", feat.name()));
            return false;
        }
        int available = CharacterService.skillPointsTotal(player) - pc.skillPointsSpent();
        if (available < feat.cost()) {
            Feedback.notify(player, Lang.fmt("msg.feat.cost", "feat", feat.name(), "cost", feat.cost(), "have", available));
            return false;
        }
        pc.buyFeat(featId, feat.cost());
        CharacterService.refresh(player); // boons/proficiencies apply now
        Feedback.notify(player, Lang.fmt("msg.feat.gained", "feat", feat.name(), "cost", feat.cost()));
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
            Feedback.notify(player, Lang.get("msg.respec.nothing"));
            return false;
        }
        Long offered = RESPEC_OFFERS.get(player.getUUID());
        long now = System.currentTimeMillis();
        if (offered == null || now - offered > 30_000) {
            RESPEC_OFFERS.put(player.getUUID(), now);
            Feedback.notify(player, Lang.fmt("msg.respec.offer", "points", pc.skillPointsSpent(),
                    "levelcost", levelCost > 0
                            ? Lang.fmt("msg.respec.offer_levels", "levels", levelCost) : "."));
            Feedback.notify(player, Lang.get("msg.respec.confirm"));
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
        pruneUnknownSkills(player);
        CharacterService.refresh(player);
        Feedback.notify(player, Lang.fmt("msg.respec.done", "points", refunded, "level", CharacterService.level(player)));
        return true;
    }

    private static String article(String noun) {
        return noun.isEmpty() || "AEIOU".indexOf(Character.toUpperCase(noun.charAt(0))) < 0 ? "a" : "an";
    }

    private CharacterActions() {}
}
