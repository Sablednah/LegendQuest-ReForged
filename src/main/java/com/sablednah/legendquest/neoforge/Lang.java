package com.sablednah.legendquest.neoforge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sablednah.legendquest.LegendQuest;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Every string the server ever shows a player, keyed and owner-editable.
 *
 * <p>Why not vanilla translatable components? Vanilla clients don't carry
 * our lang file — they'd see raw keys. So the server resolves text itself
 * from {@code config/legendquest/messages.yml}, which owners edit for
 * translation OR terminology: a zombie server sets {@code term.race} to
 * "Archetype" and every message, handbook page and GUI label follows
 * (labels via the vocab sync — see VocabPayload).</p>
 *
 * <p>Templates use <code>{placeholder}</code> for runtime values and may
 * reference terms as <code>{term.race}</code> etc. '&amp;' colour codes work
 * everywhere. The file is generated with every known key on first run;
 * missing keys fall back to the baked defaults, so upgrades never break a
 * customised file. Reloaded on /reload (text is not a frozen registry).</p>
 */
public final class Lang {

    /** Baked defaults: the complete catalogue. Registration order = file order. */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static Map<String, String> active = new LinkedHashMap<>();

    static String def(String key, String template) {
        DEFAULTS.put(key, template);
        return key;
    }

    // --- the vocabulary: the nouns a genre re-skins ---
    static {
        def("term.race", "Race");
        def("term.races", "Races");
        def("term.class", "Class");
        def("term.classes", "Classes");
        def("term.skill", "Skill");
        def("term.skills", "Skills");
        def("term.feat", "Feat");
        def("term.feats", "Feats");
        def("term.gear", "Gear");
        def("term.mana", "Mana");
        def("term.karma", "Karma");
        def("term.level", "Level");
        def("term.party", "Party");
        def("term.skill_points", "Skill points");
        def("term.loadout", "Loadout");
        def("term.spellbook", "Spellbook");
        def("term.handbook", "Players Handbook");
        def("term.boon", "Boon");
        def("term.soul", "Soul");
        def("term.stats", "Stats");
    }

    // --- ui.* : labels the modded client draws (synced with the terms) ---
    static {
        def("ui.no_data", "No LegendQuest data");
        def("ui.buy", "Buy");
        def("ui.points_to_spend", "Points to spend");
        def("ui.boost_a_stat", "Boost a stat");
        def("ui.skills_live_hint", "{term.skills} live on the ✦ tab");
        def("ui.no_spellbook", "No {term.spellbook} — drop an item on the ? slot");
        def("ui.spellbook_label", "{term.spellbook}");
        def("ui.choose_your_race", "Choose your {term.race}:");
        def("ui.choose_your_class", "Choose your {term.class}:");
        def("ui.click_to_choose", "Click to choose!");
        def("ui.not_open_to_you", "Not open to you.");
        def("ui.locked", "[locked]");
        def("ui.soul_locked", "[soul]");
        def("ui.handbook_tooltip", "{term.races}, {term.classes} and {term.skills} — everything a legend needs to know.");
        def("ui.no_party", "No {term.party}.");
        def("ui.create_party", "Create {term.party}");
        def("ui.name_it", "Name it…");
        def("ui.party_pitch_1", "Shared XP, no friendly fire,");
        def("ui.party_pitch_2", "and /party tp to regroup.");
        def("ui.invited_to", "Invited to");
        def("ui.accept", "Accept");
        def("ui.decline", "Decline");
        def("ui.teleport", "Teleport");
        def("ui.leave", "Leave");
        def("ui.invite", "Invite:");
        def("ui.you", "(you)");
        def("ui.offline", "(offline)");
        def("ui.known", "✔ Known");
        def("ui.rename_party", "Rename {term.party}");
        def("ui.nothing_here", "Nothing here yet.");
        def("ui.character_sheet", "Character sheet");
        def("ui.skills_and_loadout", "{term.skills} & {term.loadout}");
        def("ui.rename_party_tip", "Opens chat pre-filled with /party rename");
        def("ui.open_in_handbook", "Open in the {term.handbook}");
        def("ui.right_click_handbook", "Right-click: handbook");
        def("ui.stat_buy_tip", "Permanently raises the stat for {cost} {term.skill_points}.");
        def("ui.stat_buy_tip_2", "Each boost bought raises the next one's price.");
        def("ui.stat_buy_tip_3", "Regret it later? /lq respec");
        def("ui.loadout_slot_tip", "Click to select · drag off to remove");
        def("ui.spellbook_item", "{term.spellbook} item");
        def("ui.spellbook_bound_tip", "right-click casts the selected {term.skill}, sneak+right-click cycles.");
        def("ui.spellbook_none", "No item bound yet.");
        def("ui.spellbook_pick_hint", "Pick an item up from your inventory and click here to set it.");
        def("ui.spellbook_unbind_hint", "Click with an empty cursor to unbind.");
        def("ui.buy_chip_tip", "The green chip buys it.");
        def("ui.buy_cmd_tip", "Buy with /skill buy when you have the points");
        def("ui.passive_tip", "Always on.");
        def("ui.triggered_tip", "Fires on its trigger.");
        def("ui.already_in_loadout", "Already in the {term.loadout}");
        def("ui.drag_to_loadout", "Click or drag to the {term.loadout}");
        def("ui.cooldown", "cooldown");
        def("ui.soul_bound", "Soul-bound:");
        def("ui.requires_level", "Requires {term.level}");
        def("ui.lvl_short", "lvl");
        def("ui.off_label", "[off]");
        def("ui.toggle_off_tip", "Click to switch off");
        def("ui.toggle_on_tip", "Switched off — click to turn back on");
        def("ui.always_on_tip", "Cannot be switched off.");
        def("ui.sp_short", "sp");
    }

    // --- msg.* : everything the server says ---
    static {
        def("msg.login", "&6[LegendQuest]&r {race} {class} &7({term.level} {level}, {karma})");
        def("msg.login.content_gone", "&6This server no longer offers: &c{list}&6. Those choices were "
                + "reset (your XP is safe) — pick a new {term.race} or {term.class} from the character panel.");
        def("msg.login.skills_off", "&7You have switched off: {list}&7. "
                + "&f/skill toggle <id>&7 turns one back on.");
        def("msg.levelup", "&6[LegendQuest]&a {term.level} up! You are now {term.level} {level}.");
        def("msg.levelup.title", "&6&l{term.level} {level}");
        def("msg.levelup.subtitle", "&e{class}");
        def("msg.reload_notice", "&6[LegendQuest]&7 Tags reloaded. Content changes apply on server RESTART; messages.yml applied now.");

        def("msg.skill.not_known", "&cYou don't know that {term.skill}.");
        def("msg.skill.not_loaded", "&c{term.skill} '{id}' is not loaded — tell an admin.");
        def("msg.skill.not_active_type", "&e{skill} is {type} — it works on its own. &7/skill toggle {id} &eswitches it off.");
        def("msg.skill.not_active_fixed", "&e{skill} is {type} — it works on its own.");
        def("msg.skill.level_locked", "&c{skill} unlocks at {term.level} {level}.");
        def("msg.skill.not_purchased", "&c{skill} must be bought first: /skill buy {id} ({cost} points)");
        def("msg.skill.soul_dark", "&5{skill} has left you — your {term.soul} is too dark.");
        def("msg.skill.soul_bright", "&5{skill} has left you — your {term.soul} is too bright.");
        def("msg.skill.phase_wait", "&c{skill} is {phase} — ready in {sec}s");
        def("msg.skill.building", "&d{skill} building up...");
        def("msg.skill.no_mana", "&9Not enough {term.mana} for {skill} ({have}/{need}).");
        def("msg.skill.needs_item", "&c{skill} needs {qty}x {item}.");
        def("msg.skill.not_virtuous", "&cYou are not virtuous enough for {skill}.");
        def("msg.skill.not_wicked", "&cYou are not wicked enough for {skill}.");
        def("msg.skill.fades_soon", "&e⌛ {skill} fades in 5 seconds!");
        def("msg.skill.toggled_off", "&7{skill} is now &coff&7. &f/skill toggle {id}&7 turns it back on.");
        def("msg.skill.toggled_on", "&a{skill} is now on.");
        def("msg.skill.toggle_active", "&e{skill} is an {term.skill} you use — it never fires on its own, so there is nothing to switch off.");
        def("msg.skill.toggle_fixed", "&e{skill} cannot be switched off — it is part of what you are.");
        def("msg.skill.off_marker", "&c[off]");

        def("msg.buy.skill_not_offered", "&cYour {term.race}/{term.class} does not offer that {term.skill}.");
        def("msg.buy.no_purchase_needed", "&7No purchase needed — it unlocks at {term.level} {level}.");
        def("msg.buy.already_bought", "&7Already bought.");
        def("msg.buy.needs_level", "&cThat needs {term.level} {level}.");
        def("msg.buy.soul_not_bright", "&cYour {term.soul} is not bright enough for that.");
        def("msg.buy.soul_not_dark", "&cYour {term.soul} is not dark enough for that.");
        def("msg.buy.not_enough_points", "&cNeeds {cost} {term.skill_points}; you have {have}.");
        def("msg.buy.learned", "&6Learned &l{skill}&r&6 for {cost} points.");
        def("msg.buy.stat_cost", "&cA +1 {stat} costs {cost} {term.skill_points}; you have {have}.");
        def("msg.buy.stat_done", "&6+1 {stat} bought for {cost} points &7(next boost costs {next}).");

        def("msg.feat.unknown", "&cUnknown {term.feat}: {id}");
        def("msg.feat.already", "&7You already have &l{feat}&7.");
        def("msg.feat.needs_level", "&c{feat} needs {term.level} {level}.");
        def("msg.feat.soul_not_bright", "&cYour {term.soul} is not bright enough for {feat}.");
        def("msg.feat.soul_not_dark", "&cYour {term.soul} is not dark enough for {feat}.");
        def("msg.feat.requires", "&c{feat} requires the &l{required}&r&c {term.feat} first.");
        def("msg.feat.not_your_blood", "&c{feat} is not in your blood.");
        def("msg.feat.not_your_calling", "&c{feat} is not for your calling.");
        def("msg.feat.cost", "&c{feat} costs {cost} {term.skill_points}; you have {have}.");
        def("msg.feat.gained", "&6{term.feat} gained: &l{feat}&r&6 ({cost} points).");

        def("msg.choose.unknown_race", "&cUnknown {term.race}: {id}");
        def("msg.choose.race_locked_in", "&cYour {term.race} is chosen for life. An admin can change it.");
        def("msg.choose.race_not_open", "&cThat {term.race} is not open to you.");
        def("msg.choose.race_done", "&6You are now {article} &l{race}&r&6.");
        def("msg.choose.unknown_class", "&cUnknown {term.class}: {id}");
        def("msg.choose.class_not_open", "&cThat {term.class} is not open to you.");
        def("msg.choose.main_only", "&c{class} can only be a main {term.class}.");
        def("msg.choose.sub_only", "&c{class} can only be a sub {term.class}.");
        def("msg.choose.race_cannot", "&cA {race} cannot become {article} {class}.");
        def("msg.choose.requires_mastering", "&c{class} requires mastering: {requires}");
        def("msg.choose.class_done", "&6You are now {article} &l{class}&r&6{suffix}");
        // Shown only when leaving a main class that has been played. The point
        // is reassurance, not warning: the level, health and title all drop at
        // once and it looks like loss, when nothing has been lost at all.
        def("msg.choose.class_banked",
                "&7Your &f{class}&7 is kept at {term.level} &f{level}&7 — "
                        + "&f/lq class choose {id}&7 goes back to it.");

        def("msg.respec.nothing", "&7Nothing to respec — no {term.skill_points} are spent.");
        def("msg.respec.offer", "&6Respec refunds &l{points}&r&6 {term.skill_points} (forgetting bought {term.skills} and stat boosts){levelcost}");
        def("msg.respec.offer_levels", " and burns &c{levels} character {term.level}(s)&6.");
        def("msg.respec.confirm", "&7Run &f/lq respec&7 again within 30 seconds to seal it.");
        def("msg.respec.done", "&6The past unravels: &f{points}&6 {term.skill_points} refunded, {term.level} {level} — spend wiser this time.");

        def("msg.party.not_in_one", "&7You are not in a {term.party}.");
        // Party chat. The channel tag is {term.party}, so a server that renamed
        // parties to "Crew" shows [Crew] here without editing these lines.
        def("msg.party.chat.no_party", "&cYou are not in a {term.party} to talk to.");
        def("msg.party.chat.line", "&9[{term.party}] &f{player}&7: &f{message}");
        // A listener hears every party at once, so their copy names which one.
        def("msg.party.chat.spy_line", "&8[&7spy&8] &9[{party}] &f{player}&7: &7{message}");
        // Capture: plain chat routed to the party. The "on" line names the way
        // back out in the same breath -- the whole danger of this toggle is
        // forgetting it is on, so the reminder ships with the switch.
        def("msg.party.capture.on",
                "&9Your chat now goes to your {term.party} only. &7/pc again to stop.");
        def("msg.party.capture.off", "&7Your chat goes to the whole server again.");
        def("msg.party.capture.dropped",
                "&cYour {term.party} is gone — your chat is public again.");
        def("msg.party.spy.on", "&7You are now listening to {term.party} chat.");
        def("msg.party.spy.off", "&7You have stopped listening to {term.party} chat.");
        def("msg.party.spy.denied", "&cYou do not have permission to listen to {term.party} chat.");
        def("msg.party.created", "&6{term.party} &l{name}&r&6 created. Invite from the {term.party} tab or /party invite.");
        def("msg.party.not_online", "&c{name} is not online.");
        def("msg.party.create_first", "&cYou are not in a {term.party}. Create one first.");
        def("msg.party.already_member", "&7They are already in the {term.party}.");
        def("msg.party.already_partied", "&c{name} is already in a {term.party}.");
        def("msg.party.invited", "&6Invited {name}.");
        def("msg.party.invitation", "&6{name} invites you to {term.party} &l{party}&r&6 — {term.party} tab or /party accept.");
        def("msg.party.no_invite", "&cNo open invitation (it may have expired with a restart).");
        def("msg.party.joined", "&6You joined &l{name}&r&6.");
        def("msg.party.member_joined", "&6{name} joined the {term.party}.");
        def("msg.party.declined", "&7Invitation declined.");
        def("msg.party.left", "&6You left &l{name}&r&6.");
        def("msg.party.leader_only_rename", "&cOnly the {term.party} leader may rename it.");
        def("msg.party.renamed", "&6The {term.party} &l{old}&r&6 is now &l{new}&r&6.");
        def("msg.party.tp_disabled", "&c{term.party} teleport is disabled on this server.");
        def("msg.party.tp_cooldown", "&cThe {term.party} bond needs {sec}s to regather.");
        def("msg.party.tp_nobody", "&7No {term.party} members are online in this dimension.");
        def("msg.party.tp_unsafe", "&cNowhere safe to arrive — your {term.party} stands in strange places.");
        def("msg.party.tp_done", "&6The {term.party} bond draws you across the world.");
        def("msg.party.already_in_one", "&cYou are already in a {term.party}.");
        def("msg.party.name_taken", "&cA {term.party} with that name already exists.");
        def("msg.party.name_rules", "&c{term.party} names: 2-24 letters, numbers, _ or -.");
        def("msg.party.friendly_fire", "&7{name} is in your {term.party}.");

        def("msg.combat.dodged_you", "&aDodged!");
        def("msg.combat.they_dodged", "&7They dodged.");
        def("msg.combat.fumble", "&cYou fumble with the unfamiliar {item}...");
        // Shown when a hostile skill is refused because fighting is not allowed
        // where the target is standing. The other refusal -- "you two may not
        // fight each other" -- is worded by whichever mod knows why, so it is
        // not ours to phrase.
        def("msg.combat.pvp_off_here", "&cThere is no fighting here.");
        def("msg.combat.bad_armour", "&c{item} was not made for your kind — it will slow and tire you.");

        def("msg.station.crafting", "&cYour hands were never taught the maker's craft.");
        def("msg.station.smelting", "&cThe fire does not answer to your kind.");
        def("msg.station.brewing", "&cPotioncraft is beyond your discipline.");
        def("msg.station.enchanting", "&cThe runes squirm away from your gaze.");
        def("msg.station.repairing", "&cMending is a craft, and it is not yours.");
        def("msg.station.taming", "&cThe beast senses your kind cannot be trusted.");

        def("msg.boon.rebate", "&dRunic thrift: {levels} level(s) rebated.");
        def("msg.boon.refund", "&6The forge favours you — a {item} survives the making.");

        def("msg.loadout.empty", "&7{term.loadout} is empty — /loadout add <{term.skill}>");
        def("msg.loadout.active_only", "&cOnly active {term.skills} belong in a {term.loadout}.");
        def("msg.loadout.already_in", "&7Already in the {term.loadout}.");
        def("msg.loadout.added", "&6Added &l{skill}&r&6 to the {term.loadout}.");
        def("msg.loadout.bind_hint", "&7Now hold your {term.spellbook} item and run /loadout bind.");
        def("msg.loadout.removed", "&6Removed from the {term.loadout}.");
        def("msg.loadout.not_in", "&7That {term.skill} isn't in the {term.loadout}.");
        def("msg.loadout.empty_slot", "&7Nothing in {term.loadout} slot {slot} — /loadout add <{term.skill}>");
        def("msg.loadout.unbound", "&6{term.loadout} item unbound (the {term.skill} list is kept).");
        def("msg.loadout.bind_clash", "&cThat item type already has a single-{term.skill} /bind — /unbind it first.");
        def("msg.loadout.spellbook_set", "&6{item} is now your {term.spellbook}: right-click casts, sneak+right-click cycles.");
        def("msg.loadout.show_empty", "&7{term.loadout} empty. /loadout add <{term.skill}>, then hold an item and /loadout bind.");
        def("msg.loadout.show_header", "&6{term.loadout} &7({item}&7):");
        def("msg.loadout.no_item", "&cno item bound — /loadout bind");
        def("msg.loadout.cleared", "&6{term.loadout} cleared.");
        def("msg.loadout.hold_spellbook", "&cHold the item you want as your {term.spellbook}.");

        def("msg.bind.hold_item", "&cHold the item you want to bind first.");
        def("msg.bind.active_only", "&cOnly active {term.skills} can be bound to items.");
        def("msg.bind.is_spellbook", "&cThat item type is your {term.loadout} {term.spellbook} — /loadout unbind it first.");
        def("msg.bind.done", "&6Bound &l{skill}&r&6 to {item} &7(right-click to use, /unbind to clear)");
        def("msg.unbind.hold_item", "&cHold the item you want to unbind.");
        def("msg.unbind.done", "&6Unbound &l{skill}&r&6 from {item}.");
        def("msg.unbind.nothing", "&7Nothing is bound to {item}.");

        def("msg.party.info_invited", "&6You are invited to &l{name}&r&6 — /party accept or /party decline.");
        def("msg.party.info_none", "&7Not in a {term.party}. /party create <name> to start one.");
        def("msg.party.info_header", "&6{term.party} &l{name}&r&6 — members:");

        def("msg.list.races_header", "&6{term.races}:&r");
        def("msg.list.classes_header", "&6{term.classes}:&r");
        def("msg.list.skills_header", "&6{term.skills}:&r");
        def("msg.list.feats_header", "&6{term.feats} &7(browse the {term.handbook} for details):&r");
        def("msg.list.default_tag", "&7[default]");
        def("msg.list.locked_tag", "&c[locked]");
        def("msg.list.missing_def", "(missing definition!)");
        def("msg.list.no_skills", "&7Your {term.race} and {term.class} grant no {term.skills}.");
        def("msg.list.leader", "(leader)");
        def("msg.list.offline", "(offline)");

        def("msg.stats.undecided", "Undecided");
        def("msg.stats.citizen", "Citizen");
        def("msg.stats.header", "&6=== {player} — {race} {main}{sub} ===&r");
        def("msg.stats.line1", "&7{term.level} &f{level} &7{term.karma} &f{karma_name} &8({karma})");
        def("msg.stats.hp_mana", "&7HP &f{hp}&7/&f{maxhp} &7{term.mana} &b{mana}&7/&b{maxmana}");
        def("msg.stats.points", "&7{term.skill_points} spent &f{spent}&7/&f{total}");

        def("msg.karma.needs_min", "needs {term.karma} ≥ {value}");
        def("msg.karma.needs_max", "needs {term.karma} ≤ {value}");
        def("msg.karma.show", "&6{term.karma}: &f{name} &8({value})");
        def("msg.roll.broadcast", "&7{player} rolls a d20: &f{roll}{flair}");
        def("msg.roll.nat20", " &6— natural 20!");
        def("msg.roll.nat1", " &c— oof.");

        def("msg.cmd.unknown_race", "Unknown {term.race}: {id}");
        def("msg.cmd.unknown_class", "Unknown {term.class}: {id}");
        def("msg.cmd.unknown_skill", "Unknown {term.skill}: {id}");
        def("msg.cmd.unknown_feat", "Unknown {term.feat}: {id}");
        def("msg.cmd.ambiguous", "That short name matches more than one entry — use the full id: {ids}");

        def("msg.admin.race_illegal", "A {race} cannot be a {class} — append 'force' to make the illegal combo anyway.");
        def("msg.admin.class_blocked", "That {term.race} cannot take {class} — append 'force' to make the illegal combo anyway.");
        def("msg.admin.class_subonly", "{class} is sub-{term.class} only — append 'force' to make the illegal combo anyway.");
        def("msg.admin.set_race", "Set {player}'s {term.race} to {id}{forced}");
        def("msg.admin.set_class", "Set {player}'s {term.class} to {id}{forced}");
        def("msg.admin.gave_xp", "Gave {player} {amount} {term.class} XP ({term.level} {level})");
        def("msg.admin.set_karma", "Set {player}'s {term.karma} to {amount}");
        def("msg.admin.set_level", "{player} is now {term.level} {level} ({xp} {term.class} XP)");
        def("msg.admin.level_many", "Changed the {term.level} of {count} players");
        def("msg.admin.level_query", "{player} is {term.level} {level} ({xp} XP in {class})");
        def("msg.admin.level_none", "{player} has no {term.class} — nothing to level");
    }

    // --- nameplate.* : what floats above a player's head ---
    // Placeholders: {name} {race} {class} {sub_class} {level} {karma} {title} {epithet}
    // A blank value switches that half off. {title} comes from the class's own
    // `titles` block and {epithet} from the karma epithet lists in the config,
    // and both are empty when unset - so putting them in the default costs
    // nothing to a pack that has not defined any.
    static {
        def("nameplate.prefix", "&7[&f{race} &f{class} &7| &fLvl {level}&7]");
        // The class title, gold to match the prefix Standards puts on it in
        // chat, so a Knight is a Knight on both surfaces. Renders as nothing
        // for a character below their first band or in a class with no titles
        // at all -- Nameplate drops a line that is only formatting -- so this
        // costs an untitled player no stray gap.
        def("nameplate.suffix", " &6{title}");
        def("msg.nameplate.on", "&6Your nameplate is showing.");
        def("msg.nameplate.off", "&6Your nameplate is hidden.");
    }

    // --- hb.* : handbook page furniture ---
    static {
        def("hb.title", "{term.handbook}");
        def("hb.starting_race", "The starting {term.race}, until you choose.");
        def("hb.starting_class", "The starting {term.class}, until you choose.");
        def("hb.no_description", "(No description — add one in the YAML!)");
        def("hb.health", "Health");
        def("hb.regen", "Regen");
        def("hb.speed", "Speed");
        def("hb.lineage", "Lineage");
        def("hb.open_classes", "Open {term.classes}:");
        def("hb.open_to", "Open to:");
        def("hb.open_to_everyone", "Open to every {term.race}.");
        def("hb.main_only", "Main {term.class} only.");
        def("hb.sub_only", "Sub {term.class} only.");
        def("hb.requires_mastering", "Requires mastering:");
        def("hb.one_of", "one of:");
        def("hb.skills_header", "{term.skills}:");
        def("hb.taught_by", "Taught by:");
        def("hb.from_start", "from the start");
        def("hb.type", "Type");
        def("hb.toggleable", "Don't want it? /skill toggle {id}");
        def("hb.cost_line", "Cost: &f{cost} {term.skill_points}");
        def("hb.needs_level", "needs {term.level} &f{level}");
        def("hb.soul_line", "{term.soul}: &f{band} &8(drifting out of band suspends the {term.feat})");
        def("hb.requires_feats", "Requires the {term.feat}(s):");
        def("hb.only_for", "Only for:");
        def("hb.only_for_calling", "Only for the calling(s):");
        def("hb.weapons", "Weapons");
        def("hb.armour", "Armour");
        def("hb.tools", "Tools");
        def("hb.barred", "barred");
        def("hb.stations_barred", "Stations &cbarred&7: {list}");
        def("hb.karma_at_least", "{term.karma} ≥ {value}");
        def("hb.karma_at_most", "{term.karma} ≤ {value}");
        def("hb.karma_between", "{term.karma} {min}…{max}");
        def("hb.tag_covers", "Everything the tag &f{tag}&7 covers:");
        def("hb.empty_tag", "(empty tag)");
        def("hb.cooldown", "Cooldown");
        def("hb.buildup", "Buildup");
        def("hb.delay", "Delay");
        def("hb.lasts", "Lasts");
        def("hb.consumes", "Consumes");

        // What it does: one line per effect, generated from the skill's data so
        // the book can never disagree with the rules. {at} is a target phrase.
        def("hb.effects", "What it does:");
        def("hb.fx.unknown", "{type} — no description offered");
        def("hb.fx.at.self", "you");
        def("hb.fx.at.looking_at", "your target");
        def("hb.fx.at.trigger", "whoever set it off");
        def("hb.fx.at.nearby", "everything within {radius} blocks");
        def("hb.fx.at.party", "you and your {term.party}");
        def("hb.fx.damage", "Deals {amount} damage to {at}.");
        def("hb.fx.heal", "Heals {at} for {amount}.");
        def("hb.fx.potion", "Gives {at} {effect}{level} for {time}s.");
        def("hb.fx.leap", "Launches you the way you are facing.");
        def("hb.fx.teleport", "Blinks you to the block you are looking at, up to {range} away.");
        def("hb.fx.lightning", "Calls lightning down on {at}.");
        def("hb.fx.lightning_visual", "Calls down lightning that only looks the part.");
        def("hb.fx.summon", "Summons {count}x {entity}.");
        def("hb.fx.ignite", "Sets {at} on fire for {time}s.");
        def("hb.fx.give_item", "Puts {count}x {item} in your pack.");
        def("hb.fx.projectile", "Launches {entity}.");
        def("hb.fx.run_command", "Runs a command on the server.");
        def("hb.fx.run_command_timed", "Runs a command on the server, undone {time}s later.");
        def("hb.size", "Size");
        def("hb.karma_needs_at_least", "{term.karma}: needs at least {value}");
        def("hb.karma_needs_at_most", "{term.karma}: needs at most {value}");
        def("hb.stats", "Stats");
        def("hb.boon", "{term.boon}");
        def("hb.boon_enchant", "enchanting rebates {levels} XP level(s)");
        def("hb.boon_smith", "{pct}% chance to recover a material when crafting gear");
        def("hb.boon_goldtool", "golden tools harvest like netherite, burning {mana} {term.mana} per block");
        def("hb.grant_level", "{term.level} {level}");
        def("hb.grant_sp", "{cost} sp");
        def("hb.lineage_line", "— the {list} lineage(s)");
        def("hb.station_crafting", "crafting");
        def("hb.station_smelting", "smelting");
        def("hb.station_brewing", "brewing");
        def("hb.station_enchanting", "enchanting");
        def("hb.station_repair", "repair");
        def("hb.station_taming", "taming");
    }

    /** Resolve a key to its (term-substituted) template. Unknown key = the key itself, loudly. */
    public static String get(String key) {
        String template = active.getOrDefault(key, DEFAULTS.get(key));
        if (template == null) {
            LegendQuest.LOGGER.warn("Missing message key '{}'", key);
            return key;
        }
        return substituteTerms(template);
    }

    /** {@code fmt("skill.not_ready", "skill", name, "sec", 5)} — pairs. */
    public static String fmt(String key, Object... kv) {
        String out = get(key);
        for (int n = 0; n + 1 < kv.length; n += 2) {
            out = out.replace("{" + kv[n] + "}", String.valueOf(kv[n + 1]));
        }
        return out;
    }

    /** A term by short name: {@code term("race")} → "Race" or "Archetype". */
    public static String term(String name) {
        return get("term." + name);
    }

    private static String substituteTerms(String template) {
        if (!template.contains("{term.")) return template;
        String out = template;
        for (String key : DEFAULTS.keySet()) {
            if (!key.startsWith("term.")) continue;
            String marker = "{" + key + "}";
            if (out.contains(marker)) {
                out = out.replace(marker, active.getOrDefault(key, DEFAULTS.get(key)));
            }
        }
        return out;
    }

    /** Everything a modded client needs for its labels: the term.* and ui.* keys. */
    public static Map<String, String> clientVocab() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : DEFAULTS.keySet()) {
            if (key.startsWith("term.") || key.startsWith("ui.")) {
                out.put(key, get(key));
            }
        }
        return out;
    }

    // --- file lifecycle ---

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("legendquest").resolve("messages.yml");
    }

    /** Load overrides; write the full catalogue out if the file is absent. */
    public static synchronized void load() {
        Path path = file();
        try {
            if (!Files.exists(path)) {
                writeDefaults(path);
            }
            Map<String, String> loaded = new LinkedHashMap<>();
            Object parsed = new org.yaml.snakeyaml.Yaml().load(Files.readString(path));
            if (parsed instanceof Map<?, ?> map) {
                map.forEach((k, v) -> {
                    if (k != null && v != null) loaded.put(String.valueOf(k), String.valueOf(v));
                });
            }
            active = loaded;
            LegendQuest.LOGGER.info("Loaded {} message overrides from messages.yml "
                    + "({} keys in the catalogue)", loaded.size(), DEFAULTS.size());
        } catch (Exception e) {
            LegendQuest.LOGGER.error("Could not load messages.yml — using defaults", e);
            active = new LinkedHashMap<>();
        }
    }

    private static void writeDefaults(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# LegendQuest messages & vocabulary — every player-facing string.\n");
        sb.append("# Edit freely: translation, tone, or wholesale terminology.\n");
        sb.append("# A zombie server might set:  term.race: \"Archetype\"\n");
        sb.append("# A sci-fi server might set:  term.mana: \"Energy\"  term.karma: \"Alignment\"\n");
        sb.append("# {curly} placeholders are filled at runtime; {term.x} pulls a term from\n");
        sb.append("# this file; '&' colour codes work everywhere. Deleted keys fall back to\n");
        sb.append("# these defaults, so trimming the file to just your changes is fine.\n");
        sb.append("# Applied on restart and on /reload (text is not a frozen registry).\n\n");
        String section = "";
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            String prefix = entry.getKey().contains(".")
                    ? entry.getKey().substring(0, entry.getKey().indexOf('.')) : "";
            if (!prefix.equals(section)) {
                section = prefix;
                sb.append("\n# --- ").append(section).append(" ---\n");
            }
            sb.append(entry.getKey()).append(": \"")
                    .append(entry.getValue().replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\"\n");
        }
        Files.writeString(path, sb.toString());
        LegendQuest.LOGGER.info("Wrote default messages.yml with {} keys", DEFAULTS.size());
    }

    private Lang() {}
}
