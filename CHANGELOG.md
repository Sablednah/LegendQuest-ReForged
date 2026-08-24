# Changelog

All notable changes to LegendQuest ReForged are documented here.
This project follows [Semantic Versioning](https://semver.org/).

## 2.2.0 — 2026-08-24

The titles release. 2.1.0 added class ranks; this is the release where you
can actually see them.

### Added

- **Rank titles for all 34 classes in both genre packs.** 2.1.0 shipped the
  titles feature with no pack content using it — 8 of 9 built-in classes had a
  chain and none of the 36 pack classes did, so on a Wasteland or Cold Frontier
  server the feature existed and was invisible. Every class now has five bands
  at levels 1, 25, 50, 100 and 140, tiered so a specialisation ends grander
  than the base it grew from. A Wasteland Veteran runs Still Here →
  Old Hand → Veteran → Grizzled → Legend.

  Drifter and Crewman are deliberately left untitled, as Citizen is: they are
  the default classes, so no title reliably means "has not chosen a role yet".

- **The class title now appears on the nameplate by default.** The shipped
  format contained neither `{title}` nor `{epithet}`, so without the optional
  Standards integration the whole feature had nowhere to show. It is appended
  as `nameplate.suffix`, which was previously empty — additive, so no existing
  plate changes shape — and gold, matching what Standards already puts on a
  title in chat, so a Knight reads the same on both surfaces.

  **Existing servers pick this up automatically.** `messages.yml` only pins the
  keys it actually contains, and a file generated before 2.1.0 has no
  `nameplate.*` entries. A server whose `messages.yml` was created fresh under
  2.1.0 will have `nameplate.suffix: ""` pinned; delete that line to get the
  new default.

- **Switching main class now says the old one is kept.** Changing class drops
  your level, max health, title and skills to the new class's level 0 in the
  same instant, which reads exactly like losing the character — but class XP is
  banked per class and nothing ever clears a bank. The old life is intact and
  one command away, and the game now says so at the moment the bar drops
  instead of leaving you to find out. Sub-class changes stay silent, since they
  do not move your level.

### Fixed

- A nameplate line that resolves to nothing but colour codes is no longer
  drawn. Previously a format ending in `{title}` left a dangling code for any
  character below their first band — invisible, but it would have bitten any
  owner writing `{title}` into their own format.

### Changed

- `displayURL` points at <https://sablecraft.co.uk/legendquest-reforged/>
  rather than the repository. It is the link in the mods list, and the people
  who click it want to know what the mod does; `issueTrackerURL` still goes
  straight to GitHub.

## 2.1.0 — 2026-08-22

Two features, and one fix that matters more than either.

### Fixed

- **Race and class bonuses were lost on death.** Max health and speed are
  applied as transient attribute modifiers, and respawning builds a new player
  entity that copies base values but not those modifiers — so a Dwarf Fighter
  with 57 max health respawned on vanilla 20 and stayed there until they logged
  out and back in. This has been true for the entire life of the mod. Bonuses
  are now restored during the respawn itself, before the client is told
  anything, so the health bar is simply correct rather than visibly corrected.
  Dimension changes get the same repair.
- **Colour codes reached consoles and RCON as raw section signs.** Command
  output was built with the codes inside the text, which renders correctly in
  game and hands `§7CHR: §f14 §8(+2)` to anything that is not a client. They
  are real component styles now, so a server console, the log and any admin
  tool read plain sentences. In-game appearance is unchanged.
- An ampersand in ordinary text no longer eats the character after it — "Tom &
  Jerry" survives as written. Format codes in *player*-typed text are stripped
  rather than honoured, which closes an impersonation route: `&r` and a
  plausible prefix could dress a player's words up as a server message.

### Added

- **Nameplates.** Character info floats above each player's head — by default
  `[Race Class | Lvl N]`, worded by `nameplate.prefix` and `nameplate.suffix`
  in `messages.yml`, so it re-themes with the rest of the vocabulary.
  Placeholders: `{name} {race} {class} {sub_class} {level} {karma} {title}
  {epithet}`. Players can hide their own with `/lq nameplate off`.

  It is a text display entity, which an **unmodded client renders** because it
  is an ordinary vanilla entity. It occupies only the space above the head —
  chat, the tab list and `/list` are untouched — and it does **not** use the
  scoreboard team slot, so it does not compete with LuckPerms, FTB Ranks or any
  chat-prefix mod.

- **Class titles and karma epithets.** A class can name its ranks by level
  (`titles`: Squire → Man-at-Arms → Knight → Champion → Lord), and karma earns
  an epithet — "the good", "the saintly", "the diabolic". Both feed the
  nameplate, and chat where Standards is installed.

- **Party chat.** `/pc <message>` or `/lq party chat <message>` talks to your
  party alone. **`/pc` on its own toggles capture**, after which everything you
  type goes to the party until you switch it off — typing `/pc` before every
  line is fine for a remark and miserable for a conversation.

  Capture will not switch on without a party, names the way out in the same
  breath as confirming it, and clears itself if the party goes away, because
  the way this feature hurts someone is being on when they have forgotten it.

  Operators can listen in, but only deliberately: the
  `legendquest.party.spy` permission defaults to false *even for operators*,
  and there is a per-player toggle on top of it. A listener's copy names which
  party is speaking; a member's copy does not. Party chat is echoed to the
  server log, because a channel that never reaches the log cannot be moderated
  afterwards.

### Changed

- **Optional Standards integration.** With
  [Standards](https://github.com/Sablednah/SableCraft-Standards) installed,
  class titles and karma epithets decorate chat, and party lines wear the same
  name as public chat — including tags contributed by other mods, since it
  reads the shared registry rather than its own fields. Party chat routes
  through Standards' chat seam, so a muted player cannot talk to their party.
  Standards stays entirely optional; without it, party chat and nameplates work
  exactly as described above.
- `/ignore` deliberately does not apply inside party chat. A party is small,
  opt-in and has a leader with a kick command, so the better remedy already
  exists.

## 2.0.1 — 2026-08-18

### Fixed

- **Handbook class pages ignored the vocabulary setting for mana.** The mana
  bonus was labelled "Mana" no matter what the server called it, so under Cold
  Frontier a Species page read "Charge 10" while a Profession page read
  "Mana +15". Every label on that line now goes through the vocabulary system,
  and a Profession page reads "Charge +15" as it always should have.
- The same line opened with a stray `·` separator when a class had no health
  modifier.
- `Regen` and `Speed` on class pages are now translatable (`hb.regen`,
  `hb.speed`); they had been hardcoded English.

## 2.0.0 — 2026-08-17

First public release, and a complete rewrite. LegendQuest began as a Bukkit RPG
plugin; this is not that code ported line by line but that *design* rebuilt for
NeoForge 1.21.11 — the same races, classes, skills, karma and progression, on
modern data-driven foundations. Version 2.0.0 marks the break: nothing is shared
with the 1.x plugin, and worlds/configs do not carry across.

The mod is **server-authoritative**. A NeoForge server is required; clients are
optional. Vanilla clients can join and play the whole game through chat and the
action bar, and a modded client adds the handbook, character panel and HUD on
top.

### Characters

- Ten races and nine classes in the built-in D&D-flavoured set, all defined as
  data rather than code.
- Six rolled stats (STR, DEX, CON, INT, WIS, CHR) with per-race and per-class
  modifiers feeding derived HP and mana.
- Sub-classes alongside a main class, each with its own XP bank, so switching
  main class keeps the progress you earned elsewhere.
- Class requirements: prerequisite classes, "mastery" gates (per-class XP at the
  level cap), `requires_one` alternatives, and group restrictions.
- Karma, tracked as a running score and used to gate content — good and evil
  paths that lock each other out.
- Levels derive from the class XP bank against a configurable curve.
- Characters whose saved race or class no longer exists (pack removed, id
  renamed) reset to defaults on login with a notice to the player and a warning
  to ops; XP banks are left untouched, so restoring the pack restores the
  character.

### Skills

- Active, passive and triggered skills, with mana costs, cooldowns, level
  requirements, skill-point prices and karma gates.
- A skill-point economy: points earned per level, spent on skills or stat
  boosts, refundable with respec.
- A five-slot loadout bar with drag-and-drop assignment, cooldown readouts and
  cast-result flashes; suspended skills grey out and are skipped by the cycle.
- Bind a skill to a held item type so right-click casts it.
- Item proficiency per class — allowed and disallowed weapons, tools and armour,
  as item ids or tags. Wielding what your class cannot use makes you fumble.
- The arcane conduit boon: golden tools harvest at netherite speed, paid for in
  mana.

### Feats

- Bought with skill points for bespoke characters, separate from the class tree,
  and gateable on karma the same way skills are.

### Content is data, not code

- Races, classes, skills and feats are **datapack registries**. Drop JSON into a
  datapack and the content exists — no code, no restart beyond the world load.
- A **YAML front door**: the same content can be written as YAML in
  `config/legendquest/`, which is converted on the fly. Malformed YAML is logged
  loudly and skipped rather than taking the world down.
- A `pack.mcmeta` `filter` block lets a pack hide the built-in content entirely
  and replace it, rather than only adding to it.
- The README carries a field-by-field schema reference for content authors.

### Genre packs

Two complete, drop-in settings ship in `packs/`, each a world datapack that
replaces the built-in fantasy content:

- **The Wasteland** (`lq_apoc`) — 8 Archetypes, 20 Roles, 61 skills, 10 Perks.
  Post-apocalyptic survival, with a Drifter start and specialisations behind
  base-role mastery.
- **Cold Frontier** (`lq_scifi`) — 8 Species, 16 Professions, 55 skills, 10
  Augments. Deliberately setting-neutral: retune the vocabulary and the same
  data plays as space-western, fleet-opera or salvage-horror.

### Skill packs

- Skills are extensible from other mods. A separate jar can register its own
  skill effect types and ship its own content, without touching LegendQuest.
  `SkillEffectTypes.register()` in the mod constructor is the whole API surface.
- The worked example, [LegendQuest-SkillPack-Example](https://github.com/Sablednah/LegendQuest-SkillPack-Example),
  is attached to this release as `examplepack-1.0.0.jar`. It also carries a
  Stormcaller class under `examples/` in the jar, outside `data/` so it never
  auto-loads. Install skill-pack jars on the server **and** on every modded
  client, or clients fail registry sync with "Unknown skill effect type".
- Skill pages describe themselves — built-in effects generate their description
  from their own fields, and a pack's effects can supply their own.

### Vocabulary and re-theming

- Every player-facing string is translatable and re-themeable through
  `config/legendquest/messages.yml` — roughly 280 keys.
- The genre vocabulary is a first-class feature: call races "Archetypes", mana
  "Stamina", karma "Humanity", and the handbook, HUD, commands and messages all
  follow. Each genre pack ships a `messages-snippet.yml` to paste in.
- Modded clients receive the server's vocabulary on login, so the GUI speaks the
  server's language.

### Interface (modded clients)

- **The Players Handbook** — an in-game tome covering races, classes, skills,
  feats and gear, with scrolling entry lists, eligibility shown honestly, a
  "what it does" block on every skill, and navigation by arrow keys, Tab and
  mouse-back.
- **Character panel** on the inventory screen, with Stats, Skills and Party
  tabs, buy chips and working tooltips.
- **HUD** — mana, XP progress and the loadout strip.
- **Keybinds** (Controls → LegendQuest): open sheet, use selected skill, cycle
  loadout, per-slot casts. Inert when connected to a server without the mod.
- A NeoForge-generated **client config screen**.
- Level-ups announce themselves with a title card, a chime and a chat line.

### Parties

- Create, invite, accept, decline, leave, rename, and teleport to party members
  with a safe-location check.
- Party-wide skills, including bard songs that stop when the track ends.

### Commands and permissions

- `/lq` covers character sheet, race/class choice, skill list/buy/use, loadout,
  feats, karma, party and binding.
- `/lq admin` covers `setrace`, `setclass`, `addxp`, `setkarma` and
  `level set|add|remove|query`, with `force` to override requirements. Level
  writes carry partial progress and clamp to the configured cap.
- Race and class permission nodes are registered for LuckPerms and friends.
- Friendly names throughout, including in command selectors and suggestions.

### XP sources

- Kills, mining and smelting all pay class XP (`mineXpOre`, `mineXpBlock`,
  `smeltXpItem`), tag-based via `#c:ores` so modded ores count.

### Server and compatibility

- No SQL. Player data lives in a NeoForge data attachment; party data in
  `SavedData` — transactional with the world save.
- Network payloads are registered as optional and every clientbound send is
  guarded, which is what lets vanilla clients connect at all.
- Requires NeoForge 21.11.42+ on Minecraft 1.21.11.

### Known limitations

- Mining XP can be farmed by placing and re-breaking a block. The XP values are
  tuned low enough to make this a poor use of time; there is no anti-farm
  machinery yet.
- Content is frozen at world load, matching vanilla's data-driven registries.
  Editing YAML or JSON and running `/reload` will not apply it — restart the
  server. Ops get a chat notice explaining this.
- The Cold Frontier pack has been boot-checked and reviewed but has had less
  play-testing than the built-in set and The Wasteland.
