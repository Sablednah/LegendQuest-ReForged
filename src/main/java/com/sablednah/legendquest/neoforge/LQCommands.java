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
    private static final DynamicCommandExceptionType ERROR_AMBIGUOUS =
            new DynamicCommandExceptionType(ids -> Component.literal(
                    "That short name matches more than one entry — use the full id: " + ids));

    /**
     * Resolve what the player typed, accepting the bare name: {@code dwarf}
     * instead of {@code legendquest:dwarf}. An identifier with no namespace
     * parses as {@code minecraft:<path>} — the signal the namespace was
     * omitted — so we match on path across every namespace. An exact hit
     * always wins, and two packs sharing a short name is reported, not
     * guessed at. (Pattern from ZombieMod's command resolver.)
     */
    private static <T> Identifier resolve(CommandSourceStack source,
            ResourceKey<Registry<T>> registry, ResourceKey<T> typedKey,
            DynamicCommandExceptionType unknown) throws CommandSyntaxException {
        var lookup = source.registryAccess().lookupOrThrow(registry);
        if (lookup.get(typedKey).isPresent()) return typedKey.identifier();

        Identifier typed = typedKey.identifier();
        if (!typed.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            throw unknown.create(typed);
        }
        var byPath = lookup.listElements()
                .filter(h -> h.key().identifier().getPath().equals(typed.getPath()))
                .toList();
        return switch (byPath.size()) {
            case 1 -> byPath.getFirst().key().identifier();
            case 0 -> throw unknown.create(typed.getPath());
            default -> throw ERROR_AMBIGUOUS.create(byPath.stream()
                    .map(h -> h.key().identifier().toString())
                    .collect(java.util.stream.Collectors.joining(", ")));
        };
    }

    /** Suggest bare names where unambiguous, plus full ids for everything. */
    private static java.util.List<String> friendlyIds(java.util.stream.Stream<Identifier> ids) {
        var all = ids.toList();
        var pathCounts = new java.util.HashMap<String, Integer>();
        all.forEach(id -> pathCounts.merge(id.getPath(), 1, Integer::sum));
        var out = new java.util.ArrayList<String>();
        for (Identifier id : all) {
            if (pathCounts.get(id.getPath()) == 1) out.add(id.getPath());
            else out.add(id.toString());
        }
        java.util.Collections.sort(out);
        return out;
    }

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

        LiteralArgumentBuilder<CommandSourceStack> party = Commands.literal("party")
                .executes(LQCommands::partyInfo)
                .then(Commands.literal("create")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(LQCommands::partyCreate)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(LQCommands::partyInvite)))
                .then(Commands.literal("accept").executes(LQCommands::partyAccept))
                .then(Commands.literal("decline").executes(LQCommands::partyDecline))
                .then(Commands.literal("leave").executes(LQCommands::partyLeave));

        // Spend skill points on permanent +1 stats; escalating cost.
        LiteralArgumentBuilder<CommandSourceStack> buystat = Commands.literal("buystat");
        for (com.sablednah.legendquest.core.Stat stat : com.sablednah.legendquest.core.Stat.values()) {
            buystat.then(Commands.literal(stat.key()).executes(ctx ->
                    CharacterActions.buyStat(ctx.getSource().getPlayerOrException(), stat) ? 1 : 0));
        }

        LiteralArgumentBuilder<CommandSourceStack> respec = Commands.literal("respec")
                .executes(ctx -> CharacterActions.respec(ctx.getSource().getPlayerOrException()) ? 1 : 0);

        LiteralArgumentBuilder<CommandSourceStack> stats =
                Commands.literal("stats").executes(LQCommands::stats);
        LiteralArgumentBuilder<CommandSourceStack> karma =
                Commands.literal("karma").executes(LQCommands::karma);
        LiteralArgumentBuilder<CommandSourceStack> roll =
                Commands.literal("roll").executes(LQCommands::roll);

        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin")
                // Vanilla op level OR the legendquest.admin node (LuckPerms etc).
                .requires(src -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(src)
                        || (src.getEntity() instanceof ServerPlayer sp && LQPermissions.isAdmin(sp)))
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
                .then(bind).then(unbind).then(loadout).then(party)
                .then(buystat).then(respec)
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
        dispatcher.register(party);
        dispatcher.register(stats);
        dispatcher.register(karma);
        dispatcher.register(roll);
    }

    private static int bind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Identifier skillId = resolve(ctx.getSource(), LQRegistries.SKILL,
                ResourceKeyArgument.getRegistryKey(ctx, "skill", LQRegistries.SKILL, ERROR_UNKNOWN_SKILL),
                ERROR_UNKNOWN_SKILL);
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

    // --- party ---

    private static int partyInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var parties = Parties.get(ctx.getSource().getServer());
        var party = parties.partyOf(player.getUUID());
        if (party.isEmpty()) {
            var invite = parties.pendingInvite(player.getUUID());
            if (invite.isPresent()) {
                Feedback.chat(player, "&6You are invited to &l" + invite.get().name()
                        + "&r&6 — /party accept or /party decline.");
            } else {
                Feedback.chat(player, "&7Not in a party. /party create <name> to start one.");
            }
            return 0;
        }
        var p = party.get();
        StringBuilder sb = new StringBuilder("§6Party §l" + p.name() + "§r§6 — members:");
        for (var memberId : p.members()) {
            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(memberId);
            String name = online != null ? online.getName().getString()
                    : memberId.toString().substring(0, 8) + "… (offline)";
            sb.append("\n §7-§r ").append(online != null ? "§a" : "§8").append(name);
            if (memberId.equals(p.owner())) sb.append(" §6(leader)");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int partyCreate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
        var error = Parties.get(ctx.getSource().getServer()).create(name, player);
        if (error.isPresent()) {
            Feedback.chat(player, "&c" + error.get());
            return 0;
        }
        Feedback.chat(player, "&6Party &l" + name + "&r&6 created. /party invite <player> to grow it.");
        return 1;
    }

    private static int partyInvite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer invitee = EntityArgument.getPlayer(ctx, "player");
        var parties = Parties.get(ctx.getSource().getServer());
        var party = parties.partyOf(player.getUUID());
        if (party.isEmpty()) {
            Feedback.chat(player, "&cYou are not in a party. /party create <name> first.");
            return 0;
        }
        if (invitee == player || party.get().isMember(invitee.getUUID())) {
            Feedback.chat(player, "&7They are already in the party.");
            return 0;
        }
        if (parties.partyOf(invitee.getUUID()).isPresent()) {
            Feedback.chat(player, "&c" + invitee.getName().getString() + " is already in a party.");
            return 0;
        }
        parties.invite(party.get(), invitee.getUUID());
        Feedback.chat(player, "&6Invited " + invitee.getName().getString() + ".");
        Feedback.chat(invitee, "&6" + player.getName().getString() + " invites you to party &l"
                + party.get().name() + "&r&6 — /party accept or /party decline.");
        return 1;
    }

    private static int partyAccept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var parties = Parties.get(ctx.getSource().getServer());
        var joined = parties.accept(player.getUUID());
        if (joined.isEmpty()) {
            Feedback.chat(player, "&cNo open invitation (it may have expired with a restart).");
            return 0;
        }
        Feedback.chat(player, "&6You joined &l" + joined.get().name() + "&r&6.");
        for (var memberId : joined.get().members()) {
            if (memberId.equals(player.getUUID())) continue;
            ServerPlayer member = ctx.getSource().getServer().getPlayerList().getPlayer(memberId);
            if (member != null) {
                Feedback.chat(member, "&6" + player.getName().getString() + " joined the party.");
            }
        }
        return 1;
    }

    private static int partyDecline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Parties.get(ctx.getSource().getServer()).decline(player.getUUID());
        Feedback.chat(player, "&7Invitation declined.");
        return 1;
    }

    private static int partyLeave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var left = Parties.get(ctx.getSource().getServer()).remove(player.getUUID());
        if (left.isEmpty()) {
            Feedback.chat(player, "&7You are not in a party.");
            return 0;
        }
        Feedback.chat(player, "&6You left &l" + left.get().name() + "&r&6.");
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
        Feedback.chat(player, LQServerEvents.loadoutBar(player, pc, "&e"));
        return 1;
    }

    private static int loadoutAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Identifier skillId = resolve(ctx.getSource(), LQRegistries.SKILL,
                ResourceKeyArgument.getRegistryKey(ctx, "skill", LQRegistries.SKILL, ERROR_UNKNOWN_SKILL),
                ERROR_UNKNOWN_SKILL);
        return CharacterActions.loadoutAdd(player, skillId) ? 1 : 0;
    }

    private static int loadoutRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Identifier skillId = resolve(ctx.getSource(), LQRegistries.SKILL,
                ResourceKeyArgument.getRegistryKey(ctx, "skill", LQRegistries.SKILL, ERROR_UNKNOWN_SKILL),
                ERROR_UNKNOWN_SKILL);
        return CharacterActions.loadoutRemove(player, skillId) ? 1 : 0;
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
        return SharedSuggestionProvider.suggest(friendlyIds(
                ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.RACE)
                        .listElements().map(ref -> ref.key().identifier())), builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestClasses(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(friendlyIds(
                ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS)
                        .listElements().map(ref -> ref.key().identifier())), builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOwnedSkills(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            return SharedSuggestionProvider.suggest(
                    friendlyIds(SkillEngine.grants(player).keySet().stream()), builder);
        }
        return builder.buildFuture();
    }

    // --- players ---

    private static int raceList(CommandContext<CommandSourceStack> ctx) {
        var lookup = ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.RACE);
        ServerPlayer viewer = ctx.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        StringBuilder sb = new StringBuilder("§6Races:§r");
        lookup.listElements().sorted(Comparator.comparing(r -> r.key().identifier()))
                .forEach(ref -> {
                    boolean locked = viewer != null
                            && !LQPermissions.canSelectRace(viewer, ref.key().identifier());
                    sb.append("\n §7-§r ").append(locked ? "§8" : "").append(ref.value().name())
                            .append(" §8(").append(ref.key().identifier()).append(")")
                            .append(ref.value().isDefault() ? " §7[default]" : "")
                            .append(locked ? " §c[locked]" : "");
                });
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
        Identifier raceId = resolve(ctx.getSource(), LQRegistries.RACE,
                ResourceKeyArgument.getRegistryKey(ctx, "race", LQRegistries.RACE, ERROR_UNKNOWN_RACE),
                ERROR_UNKNOWN_RACE);
        return CharacterActions.chooseRace(player, raceId) ? 1 : 0;
    }

    private static int classChoose(CommandContext<CommandSourceStack> ctx, boolean asSub)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Identifier classId = resolve(ctx.getSource(), LQRegistries.CHAR_CLASS,
                ResourceKeyArgument.getRegistryKey(ctx, "class", LQRegistries.CHAR_CLASS, ERROR_UNKNOWN_CLASS),
                ERROR_UNKNOWN_CLASS);
        return CharacterActions.chooseClass(player, classId, asSub) ? 1 : 0;
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
        Identifier skillId = resolve(ctx.getSource(), LQRegistries.SKILL,
                ResourceKeyArgument.getRegistryKey(ctx, "skill", LQRegistries.SKILL, ERROR_UNKNOWN_SKILL),
                ERROR_UNKNOWN_SKILL);
        return SkillEngine.use(player, skillId).fired() ? 1 : 0;
    }

    private static int skillBuy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Identifier id = resolve(ctx.getSource(), LQRegistries.SKILL,
                ResourceKeyArgument.getRegistryKey(ctx, "skill", LQRegistries.SKILL, ERROR_UNKNOWN_SKILL),
                ERROR_UNKNOWN_SKILL);
        return CharacterActions.buySkill(player, id) ? 1 : 0;
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
        Identifier raceId = resolve(ctx.getSource(), LQRegistries.RACE,
                ResourceKeyArgument.getRegistryKey(ctx, "race", LQRegistries.RACE, ERROR_UNKNOWN_RACE),
                ERROR_UNKNOWN_RACE);
        CharacterService.data(target).setRace(raceId, false);
        CharacterService.refresh(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set " + target.getName().getString() + "'s race to " + raceId), true);
        return 1;
    }

    private static int adminSetClass(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Identifier classId = resolve(ctx.getSource(), LQRegistries.CHAR_CLASS,
                ResourceKeyArgument.getRegistryKey(ctx, "class", LQRegistries.CHAR_CLASS, ERROR_UNKNOWN_CLASS),
                ERROR_UNKNOWN_CLASS);
        CharacterService.data(target).setMainClass(classId);
        CharacterService.refresh(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set " + target.getName().getString() + "'s class to " + classId), true);
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
