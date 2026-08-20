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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;

/**
 * The player-facing command surface. One root ({@code /lq}) plus the classic
 * shorthands ({@code /race}, {@code /class}, {@code /stats}, {@code /skill},
 * {@code /karma}, {@code /roll}) registered as thin aliases.
 */
public final class LQCommands {

    private static final DynamicCommandExceptionType ERROR_UNKNOWN_RACE =
            new DynamicCommandExceptionType(id -> Component.literal(Lang.fmt("msg.cmd.unknown_race", "id", id)));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_CLASS =
            new DynamicCommandExceptionType(id -> Component.literal(Lang.fmt("msg.cmd.unknown_class", "id", id)));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_SKILL =
            new DynamicCommandExceptionType(id -> Component.literal(Lang.fmt("msg.cmd.unknown_skill", "id", id)));
    private static final DynamicCommandExceptionType ERROR_AMBIGUOUS =
            new DynamicCommandExceptionType(ids -> Component.literal(
                    Lang.fmt("msg.cmd.ambiguous", "ids", ids)));

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
                .then(Commands.literal("leave").executes(LQCommands::partyLeave))
                .then(Commands.literal("tp").executes(LQCommands::partyTp))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> PartyActions.rename(
                                        ctx.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name"))
                                        ? 1 : 0)));

        // Spend skill points on permanent +1 stats; escalating cost.
        LiteralArgumentBuilder<CommandSourceStack> buystat = Commands.literal("buystat");
        for (com.sablednah.legendquest.core.Stat stat : com.sablednah.legendquest.core.Stat.values()) {
            buystat.then(Commands.literal(stat.key()).executes(ctx ->
                    CharacterActions.buyStat(ctx.getSource().getPlayerOrException(), stat) ? 1 : 0));
        }

        LiteralArgumentBuilder<CommandSourceStack> feat = Commands.literal("feat")
                .then(Commands.literal("list").executes(LQCommands::featList))
                .then(Commands.literal("buy")
                        .then(Commands.argument("feat", ResourceKeyArgument.key(LQRegistries.FEAT))
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(friendlyIds(
                                        ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.FEAT)
                                                .listElements().map(ref -> ref.key().identifier())), builder))
                                .executes(LQCommands::featBuy)));

        LiteralArgumentBuilder<CommandSourceStack> respec = Commands.literal("respec")
                .executes(ctx -> CharacterActions.respec(ctx.getSource().getPlayerOrException()) ? 1 : 0);

        // A floating stat block over your head is the sort of thing players
        // either like or want gone immediately, so it is theirs to switch off.
        LiteralArgumentBuilder<CommandSourceStack> nameplate = Commands.literal("nameplate")
                .then(Commands.literal("on").executes(ctx -> nameplate(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> nameplate(ctx, false)));

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
                                        .executes(ctx -> adminSetRace(ctx, false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> adminSetRace(ctx, true))))))
                .then(Commands.literal("setclass")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("class", ResourceKeyArgument.key(LQRegistries.CHAR_CLASS))
                                        .suggests(LQCommands::suggestClasses)
                                        .executes(ctx -> adminSetClass(ctx, false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> adminSetClass(ctx, true))))))
                .then(Commands.literal("addxp")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(LQCommands::adminAddXp))))
                .then(Commands.literal("setkarma")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg())
                                        .executes(LQCommands::adminSetKarma))))
                // Levels for people who would rather not do the XP arithmetic:
                // /lq admin level set|add|remove @a <n>, plus query to read one
                // back (command blocks get the level as the command result).
                .then(Commands.literal("level")
                        .then(levelVerb("set", LevelOp.SET))
                        .then(levelVerb("add", LevelOp.ADD))
                        .then(levelVerb("remove", LevelOp.REMOVE))
                        .then(Commands.literal("query")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(LQCommands::adminLevelQuery))));

        dispatcher.register(Commands.literal("lq")
                .executes(LQCommands::stats)
                .then(race).then(charClass).then(skill)
                .then(bind).then(unbind).then(loadout).then(party)
                .then(buystat).then(respec).then(feat)
                .then(stats).then(karma).then(roll).then(nameplate).then(admin));

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
            Feedback.chat(player, Lang.get("msg.bind.hold_item"));
            return 0;
        }
        if (!SkillEngine.grants(player).containsKey(skillId)) {
            Feedback.chat(player, Lang.get("msg.skill.not_known"));
            return 0;
        }
        var def = SkillEngine.definition(player, skillId);
        if (def.isEmpty() || def.get().type() != com.sablednah.legendquest.skills.SkillType.ACTIVE) {
            Feedback.chat(player, Lang.get("msg.bind.active_only"));
            return 0;
        }
        Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        PlayerCharacter pc = CharacterService.data(player);
        if (pc.loadoutItem().map(itemId::equals).orElse(false)) {
            Feedback.chat(player, Lang.get("msg.bind.is_spellbook"));
            return 0;
        }
        pc.bind(itemId, skillId);
        Feedback.chat(player, Lang.fmt("msg.bind.done",
                "skill", def.get().name(), "item", held.getHoverName().getString()));
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
                Feedback.chat(player, Lang.fmt("msg.party.info_invited", "name", invite.get().name()));
            } else {
                Feedback.chat(player, Lang.get("msg.party.info_none"));
            }
            return 0;
        }
        var p = party.get();
        StringBuilder sb = new StringBuilder(lc("msg.party.info_header", "name", p.name()));
        for (var memberId : p.members()) {
            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(memberId);
            String name = online != null ? online.getName().getString()
                    : memberId.toString().substring(0, 8) + "… " + lc("msg.list.offline");
            sb.append("\n §7-§r ").append(online != null ? "§a" : "§8").append(name);
            if (memberId.equals(p.owner())) sb.append(" §6").append(lc("msg.list.leader"));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int partyCreate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
        return PartyActions.create(player, name) ? 1 : 0;
    }

    private static int partyInvite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer invitee = EntityArgument.getPlayer(ctx, "player");
        return PartyActions.invite(player, invitee.getName().getString()) ? 1 : 0;
    }

    private static int partyAccept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return PartyActions.accept(ctx.getSource().getPlayerOrException()) ? 1 : 0;
    }

    private static int partyDecline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return PartyActions.decline(ctx.getSource().getPlayerOrException()) ? 1 : 0;
    }

    private static int partyTp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return PartyActions.teleport(ctx.getSource().getPlayerOrException()) ? 1 : 0;
    }

    private static int partyLeave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return PartyActions.leave(ctx.getSource().getPlayerOrException()) ? 1 : 0;
    }

    // --- loadout ---

    private static int loadoutShow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerCharacter pc = CharacterService.data(player);
        if (pc.loadout().isEmpty()) {
            Feedback.chat(player, Lang.get("msg.loadout.show_empty"));
            return 0;
        }
        String item = pc.loadoutItem().map(Identifier::toString).orElse(Lang.get("msg.loadout.no_item"));
        Feedback.chat(player, Lang.fmt("msg.loadout.show_header", "item", item));
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
        Feedback.chat(player, Lang.get("msg.loadout.cleared"));
        return 1;
    }

    private static int loadoutBind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var held = player.getMainHandItem();
        if (held.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.loadout.hold_spellbook"));
            return 0;
        }
        Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        PlayerCharacter pc = CharacterService.data(player);
        if (pc.bindingFor(itemId).isPresent()) {
            Feedback.chat(player, Lang.get("msg.loadout.bind_clash"));
            return 0;
        }
        pc.setLoadoutItem(java.util.Optional.of(itemId));
        Feedback.chat(player, Lang.fmt("msg.loadout.spellbook_set", "item", held.getHoverName().getString()));
        return 1;
    }

    private static int loadoutUnbind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CharacterService.data(player).setLoadoutItem(java.util.Optional.empty());
        Feedback.chat(player, Lang.get("msg.loadout.unbound"));
        return 1;
    }

    private static int unbind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var held = player.getMainHandItem();
        if (held.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.unbind.hold_item"));
            return 0;
        }
        Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        var removed = CharacterService.data(player).unbind(itemId);
        if (removed.isPresent()) {
            Feedback.chat(player, Lang.fmt("msg.unbind.done",
                    "skill", removed.get(), "item", held.getHoverName().getString()));
            return 1;
        }
        Feedback.chat(player, Lang.fmt("msg.unbind.nothing", "item", held.getHoverName().getString()));
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
        StringBuilder sb = new StringBuilder(lc("msg.list.races_header"));
        lookup.listElements().sorted(Comparator.comparing(r -> r.key().identifier()))
                .forEach(ref -> {
                    boolean locked = viewer != null
                            && !LQPermissions.canSelectRace(viewer, ref.key().identifier());
                    sb.append("\n §7-§r ").append(locked ? "§8" : "").append(ref.value().name())
                            .append(" §8(").append(ref.key().identifier()).append(")")
                            .append(ref.value().isDefault() ? " " + lc("msg.list.default_tag") : "")
                            .append(locked ? " " + lc("msg.list.locked_tag") : "");
                });
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int classList(CommandContext<CommandSourceStack> ctx) {
        var lookup = ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS);
        StringBuilder sb = new StringBuilder(lc("msg.list.classes_header"));
        lookup.listElements().sorted(Comparator.comparing(r -> r.key().identifier()))
                .forEach(ref -> sb.append("\n §7-§r ").append(ref.value().name())
                        .append(" §8(").append(ref.key().identifier()).append(")")
                        .append(ref.value().isDefault() ? " " + lc("msg.list.default_tag") : ""));
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
        String race = CharacterService.race(player).map(Race::name).orElse(Lang.get("msg.stats.undecided"));
        String main = CharacterService.mainClass(player).map(CharClass::name).orElse(Lang.get("msg.stats.citizen"));
        String sub = CharacterService.subClass(player).map(CharClass::name).map(n -> " / " + n).orElse("");
        StringBuilder sb = new StringBuilder();
        sb.append(lc("msg.stats.header", "player", player.getName().getString(),
                "race", race, "main", main, "sub", sub));
        sb.append("\n").append(lc("msg.stats.line1", "level", CharacterService.level(player),
                "karma_name", CharacterService.karmaName(pc.karma()), "karma", pc.karma()));
        for (Stat stat : Stat.values()) {
            int value = stats.get(stat);
            int mod = Stat.modifier(value);
            sb.append("\n§7").append(stat.name()).append(": §f").append(value)
                    .append(" §8(").append(mod >= 0 ? "+" : "").append(mod).append(")");
        }
        sb.append("\n").append(lc("msg.stats.hp_mana",
                "hp", String.format("%.0f", (double) player.getHealth()),
                "maxhp", String.format("%.0f", CharacterService.maxHealth(player)),
                "mana", String.format("%.0f", pc.mana()),
                "maxmana", String.format("%.0f", CharacterService.maxMana(player))));
        sb.append("\n").append(lc("msg.stats.points", "spent", pc.skillPointsSpent(),
                "total", CharacterService.skillPointsTotal(player)));
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int skillList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var grants = SkillEngine.grants(player);
        if (grants.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.list.no_skills"));
            return 0;
        }
        long now = System.currentTimeMillis();
        PlayerCharacter pc = CharacterService.data(player);
        StringBuilder sb = new StringBuilder(lc("msg.list.skills_header"));
        grants.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            Identifier id = entry.getKey();
            SkillGrant grant = entry.getValue();
            var def = SkillEngine.definition(player, id);
            if (def.isEmpty()) {
                sb.append("\n §c- ").append(id).append(" ").append(lc("msg.list.missing_def"));
                return;
            }
            boolean owned = SkillEngine.owns(player, id, grant);
            SkillPhase phase = SkillPhase.at(now, pc.lastUse(id), def.get().timing());
            sb.append("\n §7-§r ").append(owned ? "§a" : "§8").append(def.get().name())
                    .append(" §8(").append(id).append(") §7")
                    .append(def.get().type().name().toLowerCase());
            if (!owned) {
                sb.append(" §8[").append(lc("hb.grant_level", "level", grant.level()));
                if (grant.cost() > 0) sb.append(", ").append(lc("hb.grant_sp", "cost", grant.cost()));
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

    private static final DynamicCommandExceptionType ERROR_UNKNOWN_FEAT =
            new DynamicCommandExceptionType(id -> Component.literal(Lang.fmt("msg.cmd.unknown_feat", "id", id)));

    private static int featList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerCharacter pc = CharacterService.data(player);
        var lookup = ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.FEAT);
        StringBuilder sb = new StringBuilder(lc("msg.list.feats_header"));
        lookup.listElements().sorted(Comparator.comparing(r -> r.value().name())).forEach(ref -> {
            var feat = ref.value();
            boolean owned = pc.hasFeat(ref.key().identifier());
            sb.append("\n §7-§r ").append(owned ? "§a✔ " : "§f").append(feat.name())
                    .append(" §8(").append(lc("hb.grant_sp", "cost", feat.cost()));
            if (feat.level() > 0) sb.append(", ").append(lc("hb.grant_level", "level", feat.level()));
            sb.append(")");
        });
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int featBuy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Identifier featId = resolve(ctx.getSource(), LQRegistries.FEAT,
                ResourceKeyArgument.getRegistryKey(ctx, "feat", LQRegistries.FEAT, ERROR_UNKNOWN_FEAT),
                ERROR_UNKNOWN_FEAT);
        return CharacterActions.buyFeat(player, featId) ? 1 : 0;
    }

    private static int karma(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerCharacter pc = CharacterService.data(player);
        Feedback.chat(player, Lang.fmt("msg.karma.show",
                "name", CharacterService.karmaName(pc.karma()), "value", pc.karma()));
        return 1;
    }

    private static int nameplate(CommandContext<CommandSourceStack> ctx, boolean show)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CharacterService.data(player).setNameplateHidden(!show);
        // Apply it now rather than waiting up to a second for the sync tick:
        // a toggle that appears not to have worked gets typed again.
        Nameplate.refresh(player);
        Feedback.chat(player, Lang.get(show ? "msg.nameplate.on" : "msg.nameplate.off"));
        return 1;
    }

    private static int roll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int roll = Mechanics.d20(player.getRandom()::nextInt);
        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                Component.literal(lc("msg.roll.broadcast", "player", player.getName().getString(),
                        "roll", roll,
                        "flair", roll == 20 ? lc("msg.roll.nat20") : roll == 1 ? lc("msg.roll.nat1") : "")),
                false);
        return roll;
    }

    // --- admin ---

    private static int adminSetRace(CommandContext<CommandSourceStack> ctx, boolean force)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Identifier raceId = resolve(ctx.getSource(), LQRegistries.RACE,
                ResourceKeyArgument.getRegistryKey(ctx, "race", LQRegistries.RACE, ERROR_UNKNOWN_RACE),
                ERROR_UNKNOWN_RACE);
        Race race = ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.RACE)
                .get(ResourceKey.create(LQRegistries.RACE, raceId)).map(r -> r.value()).orElseThrow();

        // Legality: the target's current classes must be open to the new
        // race, or the admin must say 'force' (dwarf mages are a choice,
        // not an accident).
        if (!force) {
            for (var classId : java.util.List.of(
                    CharacterService.data(target).mainClassId(),
                    CharacterService.data(target).subClassId())) {
                var illegal = classId
                        .flatMap(id -> CharacterService.charClass(target, classId))
                        .filter(cls -> !CharacterActions.classOpenTo(raceId, race, cls));
                if (illegal.isPresent()) {
                    ctx.getSource().sendFailure(Component.literal(Lang.fmt("msg.admin.race_illegal",
                            "race", race.name(), "class", illegal.get().name())));
                    return 0;
                }
            }
        }
        CharacterService.data(target).setRace(raceId, false);
        CharacterActions.pruneUnknownSkills(target);
        CharacterService.refresh(target);
        ctx.getSource().sendSuccess(() -> Component.literal(Lang.fmt("msg.admin.set_race",
                "player", target.getName().getString(), "id", raceId,
                "forced", force ? " (forced)" : "")), true);
        return 1;
    }

    private static int adminSetClass(CommandContext<CommandSourceStack> ctx, boolean force)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Identifier classId = resolve(ctx.getSource(), LQRegistries.CHAR_CLASS,
                ResourceKeyArgument.getRegistryKey(ctx, "class", LQRegistries.CHAR_CLASS, ERROR_UNKNOWN_CLASS),
                ERROR_UNKNOWN_CLASS);
        CharClass cls = ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS)
                .get(ResourceKey.create(LQRegistries.CHAR_CLASS, classId)).map(r -> r.value()).orElseThrow();

        if (!force) {
            var raceBlock = CharacterService.data(target).raceId()
                    .flatMap(rid -> ctx.getSource().registryAccess().lookupOrThrow(LQRegistries.RACE)
                            .get(ResourceKey.create(LQRegistries.RACE, rid))
                            .map(ref -> !CharacterActions.classOpenTo(rid, ref.value(), cls)))
                    .orElse(false);
            if (raceBlock || cls.eligibility().subOnly()) {
                ctx.getSource().sendFailure(Component.literal(Lang.fmt(
                        raceBlock ? "msg.admin.class_blocked" : "msg.admin.class_subonly",
                        "class", cls.name())));
                return 0;
            }
        }
        CharacterService.data(target).setMainClass(classId);
        CharacterActions.pruneUnknownSkills(target);
        CharacterService.refresh(target);
        ctx.getSource().sendSuccess(() -> Component.literal(Lang.fmt("msg.admin.set_class",
                "player", target.getName().getString(), "id", classId,
                "forced", force ? " (forced)" : "")), true);
        return 1;
    }

    private static int adminAddXp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        PlayerCharacter pc = CharacterService.data(target);
        int before = CharacterService.level(target);
        pc.mainClassId().ifPresent(cls -> pc.addXp(cls, amount));
        CharacterService.afterXpChange(target, before);
        ctx.getSource().sendSuccess(() -> Component.literal(Lang.fmt("msg.admin.gave_xp",
                "player", target.getName().getString(), "amount", amount,
                "level", CharacterService.level(target))), true);
        return 1;
    }

    private enum LevelOp { SET, ADD, REMOVE }

    /** {@code <verb> <players> <level>} — the three level verbs are identical but for the arithmetic. */
    private static LiteralArgumentBuilder<CommandSourceStack> levelVerb(String name, LevelOp op) {
        return Commands.literal(name)
                .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                .executes(ctx -> adminLevel(ctx, op))));
    }

    /**
     * Levels are not stored — they are read back off the main class's XP bank —
     * so setting one means writing the XP that curve demands. {@code set} snaps
     * to the exact threshold for the level; {@code add} and {@code remove}
     * shift by whole levels and carry any progress banked toward the next one,
     * so {@code add 1} on a half-levelled character leaves them half-levelled.
     *
     * <p>Returns the number of players changed, vanilla-style. To read a level
     * back as a command result, use {@code /lq admin level query}.</p>
     */
    private static int adminLevel(CommandContext<CommandSourceStack> ctx, LevelOp op)
            throws CommandSyntaxException {
        var targets = EntityArgument.getPlayers(ctx, "players");
        int amount = IntegerArgumentType.getInteger(ctx, "level");
        long base = LQConfig.XP_LEVEL_BASE.get();
        int cap = LQConfig.MAX_LEVEL.get();
        int changed = 0;
        String lastName = "";
        int lastLevel = 0;
        long lastXp = 0;

        for (ServerPlayer target : targets) {
            PlayerCharacter pc = CharacterService.data(target);
            Optional<Identifier> classId = pc.mainClassId();
            if (classId.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal(Lang.fmt(
                        "msg.admin.level_none", "player", target.getName().getString())));
                continue;
            }
            int before = CharacterService.level(target);
            int after = Math.clamp(switch (op) {
                case SET -> amount;
                case ADD -> before + amount;
                case REMOVE -> before - amount;
            }, 0, cap);
            long xp = op == LevelOp.SET
                    ? Leveling.totalXpForLevel(after, base)
                    : Math.max(0, pc.xpFor(classId.get())
                            + Leveling.totalXpForLevel(after, base)
                            - Leveling.totalXpForLevel(before, base));

            pc.setXp(classId.get(), xp);
            CharacterService.afterXpChange(target, before);
            changed++;
            lastName = target.getName().getString();
            lastLevel = CharacterService.level(target);
            lastXp = xp;
        }

        if (changed == 1) {
            final String name = lastName;
            final int level = lastLevel;
            final long xp = lastXp;
            ctx.getSource().sendSuccess(() -> Component.literal(Lang.fmt("msg.admin.set_level",
                    "player", name, "level", level, "xp", xp)), true);
        } else if (changed > 1) {
            final int count = changed;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    Lang.fmt("msg.admin.level_many", "count", count)), true);
        }
        return changed;
    }

    private static int adminLevelQuery(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        PlayerCharacter pc = CharacterService.data(target);
        Optional<Identifier> classId = pc.mainClassId();
        if (classId.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(Lang.fmt(
                    "msg.admin.level_none", "player", target.getName().getString())));
            return 0;
        }
        int level = CharacterService.level(target);
        ctx.getSource().sendSuccess(() -> Component.literal(Lang.fmt("msg.admin.level_query",
                "player", target.getName().getString(), "level", level,
                "xp", pc.xpFor(classId.get()), "class", classId.get())), false);
        return level;
    }

    private static int adminSetKarma(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        PlayerCharacter pc = CharacterService.data(target);
        pc.addKarma(amount - pc.karma());
        ctx.getSource().sendSuccess(() -> Component.literal(Lang.fmt("msg.admin.set_karma",
                "player", target.getName().getString(), "amount", amount)), true);
        return 1;
    }

    private static String article(String noun) {
        return noun.isEmpty() || "AEIOU".indexOf(Character.toUpperCase(noun.charAt(0))) < 0 ? "a" : "an";
    }

    /** Screens built with '§' literals need Lang text converted the same way. */
    private static String lc(String key, Object... kv) {
        return Lang.fmt(key, kv).replace('&', '\u00a7');
    }

    private LQCommands() {}
}
