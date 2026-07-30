package com.sablednah.legendquest.neoforge;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.sablednah.legendquest.LQConfig;
import com.sablednah.legendquest.LQRegistries;
import com.sablednah.legendquest.character.PlayerCharacter;
import com.sablednah.legendquest.core.Leveling;
import com.sablednah.legendquest.core.Mechanics;
import com.sablednah.legendquest.core.SkillPhase;
import com.sablednah.legendquest.core.Stat;
import com.sablednah.legendquest.data.CharClass;
import com.sablednah.legendquest.data.Race;
import com.sablednah.legendquest.data.SkillGrant;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.arguments.LongArgumentType;

/**
 * The player-facing command surface. One root ({@code /lq}) plus the classic
 * shorthands ({@code /race}, {@code /class}, {@code /stats}, {@code /skill},
 * {@code /karma}, {@code /roll}) registered as thin aliases.
 */
public final class LQCommands {

    private static final DynamicCommandExceptionType ERROR_UNKNOWN_RACE =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown race: " + id));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_CLASS =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown class: " + id));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_SKILL =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown skill: " + id));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> race = Commands.literal("race")
                .then(Commands.literal("list").executes(LQCommands::raceList))
                .then(Commands.literal("choose")
                        .then(Commands.argument("race", ResourceKeyArgument.key(LQRegistries.RACE))
                                .suggests(LQCommands::suggestRaces)
                                .executes(LQCommands::raceChoose)));

        LiteralArgumentBuilder<CommandSourceStack> charClass = Commands.literal("class")
                .then(Commands.literal("list").executes(LQCommands::classList))
                .then(Commands.literal("choose")
                        .then(Commands.argument("class", ResourceKeyArgument.key(LQRegistries.CHAR_CLASS))
                                .suggests(LQCommands::suggestClasses)
                                .executes(ctx -> classChoose(ctx, false))))
                .then(Commands.literal("sub")
                        .then(Commands.argument("class", ResourceKeyArgument.key(LQRegistries.CHAR_CLASS))
                                .suggests(LQCommands::suggestClasses)
                                .executes(ctx -> classChoose(ctx, true))));

        LiteralArgumentBuilder<CommandSourceStack> skill = Commands.literal("skill")
                .then(Commands.literal("list").executes(LQCommands::skillList))
                .then(Commands.literal("use")
                        .then(Commands.argument("skill", ResourceKeyArgument.key(LQRegistries.SKILL))
                                .suggests(LQCommands::suggestOwnedSkills)
                                .executes(LQCommands::skillUse)))
                .then(Commands.literal("buy")
                        .then(Commands.argument("skill", ResourceKeyArgument.key(LQRegistries.SKILL))
                                .suggests(LQCommands::suggestOwnedSkills)
                                .executes(LQCommands::skillBuy)));

        // The old /link|/bind: bind the held item type to a skill; right-click fires it.
        LiteralArgumentBuilder<CommandSourceStack> bind = Commands.literal("bind")
                .then(Commands.argument("skill", ResourceKeyArgument.key(LQRegistries.SKILL))
                        .suggests(LQCommands::suggestOwnedSkills)
                        .executes(LQCommands::bind));
        LiteralArgumentBuilder<CommandSourceStack> unbind =
                Commands.literal("unbind").executes(LQCommands::unbind);

        // The loadout: an ordered skill list cycled on one "spellbook" item.
        // Right-click casts the selected skill; sneak+right-click cycles.
        LiteralArgumentBuilder<CommandSourceStack> loadout = Commands.literal("loadout")
                .executes(LQCommands::loadoutShow)
                .then(Commands.literal("add")
                        .then(Commands.argument("skill", ResourceKeyArgument.key(LQRegistries.SKILL))
                                .suggests(LQCommands::suggestOwnedSkills)
                                .executes(LQCommands::loadoutAdd)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("skill", ResourceKeyArgument.key(LQRegistries.SKILL))
                                .suggests(LQCommands::suggestOwnedSkills)
                                .executes(LQCommands::loadoutRemove)))
                .then(Commands.literal("clear").executes(LQCommands::loadoutClear))
                .then(Commands.literal("bind").executes(LQCommands::loadoutBind))
                .then(Commands.literal("unbind").executes(LQCommands::loadoutUnbind));

        LiteralArgumentBuilder<CommandSourceStack> stats =
                Commands.literal("stats").executes(LQCommands::stats);
        LiteralArgumentBuilder<CommandSourceStack> karma =
                Commands.literal("karma").executes(LQCommands::karma);
        LiteralArgumentBuilder<CommandSourceStack> roll =
                Commands.literal("roll").executes(LQCommands::roll);

        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("setrace")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("race", ResourceKeyArgument.key(LQRegistries.RACE))
                                        .suggests(LQCommands::suggestRaces)
                                        .executes(LQCommands::adminSetRace))))
                .then(Commands.literal("setclass")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("class", ResourceKeyArgument.key(LQRegistries.CHAR_CLASS))
                                        .suggests(LQCommands::suggestClasses)
                                        .executes(LQCommands::adminSetClass))))
                .then(Commands.literal("addxp")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(LQCommands::adminAddXp))))
                .then(Commands.literal("setkarma")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg())
                                        .executes(LQCommands::adminSetKarma))));

        dispatcher.register(Commands.literal("lq")
                .executes(LQCommands::stats)
                .then(race).then(charClass).then(skill)
                .then(bind).then(unbind).then(loadout)
                .then(stats).then(karma).then(roll).then(admin));

        // Classic shorthands.
        dispatcher.register(race);
        dispatcher.register(charClass);
        dispatcher.register(skill);
        dispatcher.register(bind);
        dispatcher.register(Commands.literal("link") // the other old name
                .then(Commands.argument("skill", ResourceKeyArgument.key(LQRegistries.SKILL))
                        .suggests(LQCommands::suggestOwnedSkills)
                        .executes(LQCommands::bind)));
        dispatcher.register(unbind);
        dispatcher.register(Commands.literal("unlink").executes(LQCommands::unbind));
        dispatcher.register(loadout);
        dispatcher.register(stats);
        dispatcher.register(karma);
        dispatcher.register(roll);
    }

    private static int bind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceKey<?> key = ResourceKeyArgument.getRegistryKey(
                ctx, "skill", LQRegistries.SKILL, ERROR_UNKNOWN_SKILL);
        Identifier skillId = key.identifier();
        var held = player.getMainHandItem();
        if (held.isEmpty()) {
            Feedback.chat(player, "&cHold the item you want to bind first.");
            return 0;
        }
        if (!SkillEngine.grants(player).containsKey(skillId)) {
            Feedback.chat(player, "&cYou don't know that skill.");
            return 0;
        }
        var def = SkillEngine.definition(player, skillId);
        if (def.isEmpty() || def.get().type() != com.sablednah.legendquest.skills.SkillType.ACTIVE) {
            Feedback.chat(player, "&cOnly active skills can be bound to items.");
            return 0;
        }
        Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        PlayerCharacter pc = CharacterService.data(player);
        if (pc.loadoutItem().map(itemId::equals).orElse(false)) {
            Feedback.chat(player, "&cThat item type is your loadout spellbook — /loadout unbind it first.");
            return 0;
        }
        pc.bind(itemId, skillId);
        Feedback.chat(player, "&6Bound &l" + def.get().name() + "&r&6 to "
                + held.getHoverName().getString() + " &7(right-click to use, /unbind to clear)");
        return 1;
    }

    // --- loadout ---

    private static int loadoutShow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerCharacter pc = CharacterService.data(player);
        if (pc.loadout().isEmpty()) {
            Feedback.chat(player, "&7Loadout empty. /loadout add <skill>, then hold an item and /loadout bind.");
            return 0;
        }
        String item = pc.loadoutItem().map(Identifier::toString).orElse("&cno item bound — /loadout bind");
        Feedback.chat(player, "&6Loadout &7(" + item + "&7):");
        Feedback.chat(player, LQServerEvents.loadoutBar(player, pc));
        return 1;
    }

    private static int loadoutAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceKey<?> key = ResourceKeyArgument.getRegistryKey(
                ctx, "skill", LQRegistries.SKILL, ERROR_UNKNOWN_SKILL);
        Identifier skillId = key.identifier();
        if (!SkillEngine.grants(player).containsKey(skillId)) {
            Feedback.chat(player, "&cYou don't know that skill.");
            return 0;
        }
        var def = SkillEngine.definition(player, skillId);
        if (def.isEmpty() || def.get().type() != com.sablednah.legendquest.skills.SkillType.ACTIVE) {
            Feedback.chat(player, "&cOnly active skills belong in a loadout.");
            return 0;
        }
        PlayerCharacter pc = CharacterService.data(player);
        if (!pc.addToLoadout(skillId)) {
            Feedback.chat(player, "&7Already in the loadout.");
            return 0;
        }
        Feedback.chat(player, "&6Added &l" + def.get().name() + "&r&6 to the loadout.");
        if (pc.loadoutItem().isEmpty()) {
            Feedback.chat(player, "&7Now hold your spellbook item and run /loadout bind.");
        }
        return 1;
    }

    private static int loadoutRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceKey<?> key = ResourceKeyArgument.getRegistryKey(
                ctx, "skill", LQRegistries.SKILL, ERROR_UNKNOWN_SKILL);
        PlayerCharacter pc = CharacterService.data(player);
        if (pc.removeFromLoadout(key.identifier())) {
            Feedback.chat(player, "&6Removed from the loadout.");
            return 1;
        }
        Feedback.chat(player, "&7That skill isn't in the loadout.");
        return 0;
    }

    private static int loadoutClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CharacterService.data(player).clearLoadout();
        Feedback.chat(player, "&6Loadout cleared.");
        return 1;
    }

    private static int loadoutBind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var held = player.getMainHandItem();
        if (held.isEmpty()) {
            Feedback.chat(player, "&cHold the item you want as your spellbook.");
            return 0;
        }
        Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        PlayerCharacter pc = CharacterService.data(player);
        if (pc.bindingFor(itemId).isPresent()) {
            Feedback.chat(player, "&cThat item type already has a single-skill /bind — /unbind it first.");
            return 0;
        }
        pc.setLoadoutItem(java.util.Optional.of(itemId));
        Feedback.chat(player, "&6" + held.getHoverName().getString()
                + " is now your spellbook: right-click casts, sneak+right-click cycles.");
        return 1;
    }

    private static int loadoutUnbind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CharacterService.data(player).setLoadoutItem(java.util.Optional.empty());
        Feedback.chat(player, "&6Loadout item unbound (the skill list is kept).");
        return 1;
    }

    private static int unbind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var held = player.getMainHandItem();
        if (held.isEmpty()) {
            Feedback.chat(player, "&cHold the item you want to unbind.");
            return 0;
        }
        Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        var removed = CharacterService.data(player).unbind(itemId);
        if (removed.isPresent()) {
            Feedback.chat(player, "&6Unbound &l" + removed.get() + "&r&6 from "
                    + held.getHoverName().getString() + ".");
            return 1;
        }
        Feedback.chat(player, "&7Nothing is bound to " + held.getHoverName().getString() + ".");
        return 0;
    }

    // --- suggestions ---

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRaces(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        var ids = ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.RACE)
                .listElements().map(ref -> ref.key().identifier().toString()).toList();
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestClasses(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        var ids = ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS)
                .listElements().map(ref -> ref.key().identifier().toString()).toList();
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOwnedSkills(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            var ids = SkillEngine.grants(player).keySet().stream().map(Identifier::toString).sorted().toList();
            return SharedSuggestionProvider.suggest(ids, builder);
        }
        return builder.buildFuture();
    }

    // --- players ---

    private static int raceList(CommandContext<CommandSourceStack> ctx) {
        var lookup = ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.RACE);
        StringBuilder sb = new StringBuilder("§6Races:§r");
        lookup.listElements().sorted(Comparator.comparing(r -> r.key().identifier()))
                .forEach(ref -> sb.append("\n §7-§r ").append(ref.value().name())
                        .append(" §8(").append(ref.key().identifier()).append(")")
                        .append(ref.value().isDefault() ? " §7[default]" : ""));
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int classList(CommandContext<CommandSourceStack> ctx) {
        var lookup = ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS);
        StringBuilder sb = new StringBuilder("§6Classes:§r");
        lookup.listElements().sorted(Comparator.comparing(r -> r.key().identifier()))
                .forEach(ref -> sb.append("\n §7-§r ").append(ref.value().name())
                        .append(" §8(").append(ref.key().identifier()).append(")")
                        .append(ref.value().isDefault() ? " §7[default]" : ""));
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int raceChoose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceKey<Race> key = ResourceKeyArgument.getRegistryKey(
                ctx, "race", LQRegistries.RACE, ERROR_UNKNOWN_RACE);
        PlayerCharacter pc = CharacterService.data(player);

        boolean onDefault = CharacterService.race(player).map(Race::isDefault).orElse(true);
        if (pc.raceChanged() || !onDefault) {
            Feedback.chat(player, "&cYour race is chosen for life. An admin can change it.");
            return 0;
        }
        Race target = ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.RACE)
                .get(key).map(r -> r.value()).orElseThrow();
        pc.setRace(key.identifier(), !target.isDefault());
        CharacterService.refresh(player);
        Feedback.chat(player, "&6You are now " + article(target.name()) + " &l" + target.name() + "&r&6.");
        return 1;
    }

    private static int classChoose(CommandContext<CommandSourceStack> ctx, boolean asSub)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceKey<CharClass> key = ResourceKeyArgument.getRegistryKey(
                ctx, "class", LQRegistries.CHAR_CLASS, ERROR_UNKNOWN_CLASS);
        CharClass target = ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS)
                .get(key).map(r -> r.value()).orElseThrow();
        PlayerCharacter pc = CharacterService.data(player);

        if (asSub && target.eligibility().mainOnly()) {
            Feedback.chat(player, "&c" + target.name() + " can only be a main class.");
            return 0;
        }
        if (!asSub && target.eligibility().subOnly()) {
            Feedback.chat(player, "&c" + target.name() + " can only be a sub class.");
            return 0;
        }
        // Race eligibility.
        Optional<Identifier> raceId = pc.raceId();
        List<Identifier> allowedRaces = target.eligibility().allowedRaces();
        List<String> allowedGroups = target.eligibility().allowedGroups();
        if (!allowedRaces.isEmpty() || !allowedGroups.isEmpty()) {
            boolean raceOk = raceId.isPresent() && allowedRaces.contains(raceId.get());
            boolean groupOk = CharacterService.race(player)
                    .map(r -> r.groups().stream().anyMatch(allowedGroups::contains)).orElse(false);
            if (!raceOk && !groupOk) {
                Feedback.chat(player, "&cA " + CharacterService.race(player).map(Race::name).orElse("nobody")
                        + " cannot become " + article(target.name()) + " " + target.name() + ".");
                return 0;
            }
        }
        // Dependency chains, checked against mastered classes.
        long masterXp = Leveling.totalXpForLevel(LQConfig.MAX_LEVEL.get(), LQConfig.XP_LEVEL_BASE.get());
        var requires = target.eligibility().requires();
        var requiresOne = target.eligibility().requiresOne();
        boolean requiresOk = requires.stream().allMatch(id -> pc.xpFor(id) >= masterXp)
                && (requiresOne.isEmpty() || requiresOne.stream().anyMatch(id -> pc.xpFor(id) >= masterXp));
        if (!requiresOk) {
            Feedback.chat(player, "&c" + target.name() + " requires mastering: "
                    + (requires.isEmpty() ? "" : requires)
                    + (requiresOne.isEmpty() ? "" : " one of " + requiresOne));
            return 0;
        }

        if (asSub) {
            pc.setSubClass(Optional.of(key.identifier()));
        } else {
            pc.setMainClass(key.identifier());
        }
        CharacterService.refresh(player);
        Feedback.chat(player, "&6You are now " + article(target.name()) + " &l" + target.name()
                + "&r&6" + (asSub ? " (sub class)." : "."));
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerCharacter pc = CharacterService.data(player);
        var stats = CharacterService.effectiveStats(player);
        String race = CharacterService.race(player).map(Race::name).orElse("Undecided");
        String main = CharacterService.mainClass(player).map(CharClass::name).orElse("Citizen");
        String sub = CharacterService.subClass(player).map(CharClass::name).map(n -> " / " + n).orElse("");
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== ").append(player.getName().getString()).append(" — ")
                .append(race).append(" ").append(main).append(sub).append(" ===§r");
        sb.append("\n§7Level §f").append(CharacterService.level(player))
                .append(" §7Karma §f").append(CharacterService.karmaName(pc.karma()))
                .append(" §8(").append(pc.karma()).append(")");
        for (Stat stat : Stat.values()) {
            int value = stats.get(stat);
            int mod = Stat.modifier(value);
            sb.append("\n§7").append(stat.name()).append(": §f").append(value)
                    .append(" §8(").append(mod >= 0 ? "+" : "").append(mod).append(")");
        }
        sb.append("\n§7HP §f").append(String.format("%.0f", (double) player.getHealth()))
                .append("§7/§f").append(String.format("%.0f", CharacterService.maxHealth(player)))
                .append(" §7Mana §b").append(String.format("%.0f", pc.mana()))
                .append("§7/§b").append(String.format("%.0f", CharacterService.maxMana(player)));
        sb.append("\n§7Skill points spent §f").append(pc.skillPointsSpent())
                .append("§7/§f").append(CharacterService.skillPointsTotal(player));
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int skillList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var grants = SkillEngine.grants(player);
        if (grants.isEmpty()) {
            Feedback.chat(player, "&7Your race and class grant no skills.");
            return 0;
        }
        long now = System.currentTimeMillis();
        PlayerCharacter pc = CharacterService.data(player);
        StringBuilder sb = new StringBuilder("§6Skills:§r");
        grants.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            Identifier id = entry.getKey();
            SkillGrant grant = entry.getValue();
            var def = SkillEngine.definition(player, id);
            if (def.isEmpty()) {
                sb.append("\n §c- ").append(id).append(" (missing definition!)");
                return;
            }
            boolean owned = SkillEngine.owns(player, id, grant);
            SkillPhase phase = SkillPhase.at(now, pc.lastUse(id), def.get().timing());
            sb.append("\n §7-§r ").append(owned ? "§a" : "§8").append(def.get().name())
                    .append(" §8(").append(id).append(") §7")
                    .append(def.get().type().name().toLowerCase());
            if (!owned) {
                sb.append(" §8[level ").append(grant.level());
                if (grant.cost() > 0) sb.append(", ").append(grant.cost()).append(" sp");
                sb.append("]");
            } else if (phase != SkillPhase.READY) {
                sb.append(" §c").append(phase.name().toLowerCase());
            }
        });
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int skillUse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceKey<?> key = ResourceKeyArgument.getRegistryKey(
                ctx, "skill", LQRegistries.SKILL, ERROR_UNKNOWN_SKILL);
        return SkillEngine.use(player, key.identifier()) ? 1 : 0;
    }

    private static int skillBuy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceKey<?> key = ResourceKeyArgument.getRegistryKey(
                ctx, "skill", LQRegistries.SKILL, ERROR_UNKNOWN_SKILL);
        Identifier id = key.identifier();
        PlayerCharacter pc = CharacterService.data(player);
        SkillGrant grant = SkillEngine.grants(player).get(id);
        if (grant == null) {
            Feedback.chat(player, "&cYour race/class does not offer that skill.");
            return 0;
        }
        if (grant.cost() <= 0) {
            Feedback.chat(player, "&7No purchase needed — it unlocks at level " + grant.level() + ".");
            return 0;
        }
        if (pc.hasPurchased(id)) {
            Feedback.chat(player, "&7Already bought.");
            return 0;
        }
        int available = CharacterService.skillPointsTotal(player) - pc.skillPointsSpent();
        if (available < grant.cost()) {
            Feedback.chat(player, "&cNeeds " + grant.cost() + " skill points; you have " + available + ".");
            return 0;
        }
        pc.purchase(id, grant.cost());
        Feedback.chat(player, "&6Learned &l"
                + SkillEngine.definition(player, id).map(d -> d.name()).orElse(id.toString())
                + "&r&6 for " + grant.cost() + " points.");
        return 1;
    }

    private static int karma(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerCharacter pc = CharacterService.data(player);
        Feedback.chat(player, "&6Karma: &f" + CharacterService.karmaName(pc.karma())
                + " &8(" + pc.karma() + ")");
        return 1;
    }

    private static int roll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int roll = Mechanics.d20(player.getRandom()::nextInt);
        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§7" + player.getName().getString() + " rolls a d20: §f" + roll
                        + (roll == 20 ? " §6— natural 20!" : roll == 1 ? " §c— oof." : "")),
                false);
        return roll;
    }

    // --- admin ---

    private static int adminSetRace(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ResourceKey<Race> key = ResourceKeyArgument.getRegistryKey(
                ctx, "race", LQRegistries.RACE, ERROR_UNKNOWN_RACE);
        CharacterService.data(target).setRace(key.identifier(), false);
        CharacterService.refresh(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set " + target.getName().getString() + "'s race to " + key.identifier()), true);
        return 1;
    }

    private static int adminSetClass(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ResourceKey<CharClass> key = ResourceKeyArgument.getRegistryKey(
                ctx, "class", LQRegistries.CHAR_CLASS, ERROR_UNKNOWN_CLASS);
        CharacterService.data(target).setMainClass(key.identifier());
        CharacterService.refresh(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set " + target.getName().getString() + "'s class to " + key.identifier()), true);
        return 1;
    }

    private static int adminAddXp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        PlayerCharacter pc = CharacterService.data(target);
        pc.mainClassId().ifPresent(cls -> pc.addXp(cls, amount));
        CharacterService.refresh(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Gave " + target.getName().getString() + " " + amount + " class XP (level "
                        + CharacterService.level(target) + ")"), true);
        return 1;
    }

    private static int adminSetKarma(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        PlayerCharacter pc = CharacterService.data(target);
        pc.addKarma(amount - pc.karma());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set " + target.getName().getString() + "'s karma to " + amount), true);
        return 1;
    }

    private static String article(String noun) {
        return noun.isEmpty() || "AEIOU".indexOf(Character.toUpperCase(noun.charAt(0))) < 0 ? "a" : "an";
    }

    private LQCommands() {}
}
