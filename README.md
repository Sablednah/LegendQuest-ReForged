# LegendQuest ReForged

Races, classes, skills, feats, karma and character progression for
**NeoForge 1.21.11** — the modern rewrite of the classic
[LegendQuest](https://www.spigotmc.org/resources/legendquest-rpg.2120/)
Bukkit RPG plugin (and, eleven years late, the "LQ 2.0" it promised).

**Status: playable alpha.** Server boots, content loads, characters persist,
and there's a lot more mod than there used to be; balance and polish are in
progress. See [PORTING.md](PORTING.md) for the live checklist and every
architecture decision.

## What works today

### Content as data
Races, classes, skills and feats are all datapack registries: JSON in any
datapack (`data/<pack>/legendquest/{race,class,skill,feat}/*.json`) or
human-friendly YAML in `config/legendquest/{races,classes,skills,feats}/*.yml`
— same schema, two skins. A YAML file with a built-in entry's name overrides
it. The complete field-by-field reference is below in
[Writing content](#writing-content). The default pack is a trope-heavy D&D-flavoured set: ten races (Human,
Elf, Dwarf, Orc, Hobbit, Gnome, Half-Elf, Half-Orc, Tiefling — SRD 5.1
CC-BY-safe — plus the starting Undecided) and eight classes (Citizen,
Fighter, Mage, Ranger, Cleric, Rogue, Barbarian, and the earned-only Warlord).

Two full genre packs ship in [`packs/`](packs/) as drop-in world datapacks,
each with its own vocabulary snippet and a `pack.mcmeta` filter that hides
the D&D content: **[The Wasteland](packs/apocalypse/README.md)**
(zombie-apocalypse Archetypes and a mastery-gated Role tree descended from
the original server's 2012 Heroes config) and
**[Cold Frontier](packs/scifi/README.md)** (setting-neutral sci-fi Species
and crew Professions, with a double-gated Psion branch).

### Characters
Deterministic 4d6-drop-lowest statline (or flat 12s), D&D-style modifiers,
race + main class + optional sub class, per-class XP banks with mastery
unlocking dependent classes, karma with log-scale titles, mana with regen.
All stored in one codec-serialized attachment — no database.

**Restrictions with flavour, balanced by boons.** Trait groups gate classes
(dwarves can't work magic; only the Wild rage as barbarians; temples won't
have tieflings) and every closed door is paid for: dwarven skin turns blades
and their forge refunds materials, orcs hit harder every level, hobbits are
innately lucky, humans get fortune and every calling. Golden tools in arcane
hands harvest like netherite — for mana.

**Proficiencies, not padlocks.** Only items on the proficiency master lists
are ever checked (swords/axes/bows/maces; diamond and netherite tools) —
stone and iron stay open to everyone, and picking up clutter never fumbles.
Wrong weapon hits like a fist, wrong tools dig slow, unfamiliar armour slows
and tires you (never confiscated).

**Skill points are choices.** Buy skills, buy permanent +1 stat boosts at an
escalating price, or buy **feats** — data-defined bundles of passives,
proficiencies and skills, gated by level, feat chains and lineage
(Toughness → Battle Hardened; Second Breakfast is Stout-folk only).
`/lq respec` refunds everything for the price of a level.

### Skills
Active, passive (self-reapplying) and triggered (chance on melee hit / being
hurt / kills). Buildup → delay → active → cooldown phase machine,
mana/karma/item costs. Skills are composed from effect types in data; new
effect types come from **skill-pack mods** — see
[docs/SKILL-PACKS.md](docs/SKILL-PACKS.md). Fire them by command, by
item binding (`/bind`), or through the loadout: an ordered skill list cycled
on one "spellbook" item, with five direct-use hotkeys.

### Modded clients get the works
*(all optional — vanilla clients join and play with chat/action-bar feedback)*
- **Character panel** on the inventory screen: recipe-book-style slide-out
  tabs for stats (with race/class pickers while the choice is open) and
  skills — a drag-and-drop loadout workbench with a spellbook slot,
  buy chips, and tooltips everywhere.
- **The Players Handbook** (H): a magical-tome GUI built server-side from
  live data — Races / Classes / Skills / Feats / Gear tabs, every mention a
  clickable cross-link, feats purchasable right off the page, gear pages
  showing exactly what "fighter weapons" means.
- **HUD**: mana bar with class-XP progress, loadout chips with cooldown
  countdowns.
- **Combat words**, 60s-Batman style: Kapow! Thwack! CRITICAL! (natural 20,
  ×1.5 damage) Miss! Clang! — floating, colourful, drifting up near the mob.
- **Keybinds**: open sheet (K), handbook (H), use/cycle skill (R/G),
  loadout slots 1–5.

### Multiplayer
Parties (`/party create|invite|accept|leave`) with XP-sharing to nearby
members and friendly fire blocked; permission nodes per race/class
(`legendquest.race.<name>`, LuckPerms-friendly); a `perm:` field in a data
file locks that entry behind its node.

## Writing content

Everything below is data. There are two front doors with one schema:

- **YAML**, for server owners: `config/legendquest/races/<name>.yml`,
  `classes/`, `skills/`, `feats/` — registered as `legendquest:<name>`.
- **JSON**, for datapacks and mods:
  `data/<pack>/legendquest/{race,class,skill,feat}/<name>.json` — registered
  as `<pack>:<name>`.

A YAML file with a built-in entry's name **overrides** it (the whole file
replaces the entry — copy the original and edit, don't write a fragment).

**The restart rule, before anything else:** races, classes, skills and feats
are *frozen registries* — like vanilla enchantments, they load once when the
world starts. `/reload` refreshes item tags and `messages.yml` but **content
changes need a server restart**. Ops get a chat notice saying exactly this
when someone reloads. All times everywhere are **milliseconds** (1000 = 1s),
as in every original LegendQuest doc.

### Races

Here is a self-contained race, in YAML:

```yaml
name: Dwarf
plural: Dwarves
chat_tag: "[Dw]"
description: "Stone remembers them."
long_description: "Dwarves cannot work magic — the Arcane door is closed —
  but blades turn on dwarven skin and the forge gives back what they put in."
size: 1.4
base_health: 24
base_mana: 6
mana_per_second: 0.6
base_speed: 0.18
statmods:
  con: 2
  chr: -1
groups:
  - Humanoid
  - Stout
  - Dwarven
skill_points: 10
skill_points_per_level: 0.2
allow_enchanting: false
disallowed_weapons: ["#legendquest:mage_weapons"]
attributes:
  minecraft:armor_toughness: { base: 1.0, per_level: 0.05 }
smith_refund: 0.2
skills:
  legendquest:stone_skin:
    level: 8
levels:
  10: { hp: 2 }
  25: { con: 1 }
```

| Field | Default | Meaning |
|-------|---------|---------|
| `name` | *(required)* | Display name. |
| `plural` | *(none)* | "Dwarves", for text that needs it. |
| `chat_tag` | *(none)* | Short tag for chat prefixes. |
| `description` | *(none)* | One-liner for pickers and tooltips. |
| `long_description` | *(none)* | The handbook page's second paragraph. |
| `size` | `1.8` | Visual scale in blocks of height — 1.8 is a vanilla player. Hobbits are 1.1. |
| `base_health` | `20.0` | Max health before class and level bonuses. |
| `base_mana` | `10.0` | Mana pool before class bonuses. |
| `mana_per_second` | `1.0` | Regen. |
| `base_speed` | `0.2` | Old-scale walking speed: `0.2` is normal, `0.4` twice as fast. |
| `statmods` | all 0 | `str/dex/con/int/wis/chr` adjustments to the rolled statline. |
| `groups` | `[]` | Trait groups (`Arcane`, `Wild`, `Stout`, …) — free-form strings that class `allowed_groups` and feat gates match against. |
| `frequency` | `100` | Relative population weight — parsed, consumed by nothing yet. Reserved for NPC population rolls in the future [StoryTeller companion](docs/STORYTELLER.md). |
| `default` | `false` | The starting race players hold before choosing. Exactly one race should say so. |
| `perm` | *(none)* | Permission node that locks this entry (`legendquest.race.<name>` style, LuckPerms-friendly). |
| `allow_crafting` … `allow_taming` | all `true` | Station gates — see [Stations](#gear-and-stations). |
| `allowed_weapons` … `disallowed_armour` | *(no statement)* | Proficiencies — see [Gear](#gear-and-stations). |
| `skills` | `{}` | What this race teaches — see [Granting skills](#granting-skills). |
| `skill_points` | `0` | Starting skill points. |
| `skill_points_per_level` | `0` | Fractional accrual is fine: `0.25` is one point every 4 levels. |
| `levels` | `{}` | Milestone rewards — see [Level rewards](#level-rewards). |
| `xp_adjust_kill` / `_mine` / `_smelt` | `0` | Percentage tweak to class XP from that source; race and class adjustments sum. |
| `attributes`, `enchant_rebate`, `smith_refund`, `gold_tool_mana` | — | Boons — see [Boons](#boons). |

### Classes

Classes share the race schema's identity, statmods, gear rules, skills,
levels and boons, and add **growth** (how the class scales the race's base
numbers) and **eligibility** (who may take it, and where it may sit —
main or sub):

```yaml
name: Warlord
description: "Somebody has to point the sword."
health_mod: 4
health_per_level: 0.5
mana_bonus: 0
statmods: { str: 1, chr: 1 }
allowed_groups: [Humanoid]
requires_one: [legendquest:fighter, legendquest:ranger]
main_only: true
skills:
  legendquest:rally:
    level: 5
```

| Field | Default | Meaning |
|-------|---------|---------|
| `health_mod` | `0` | Flat health on top of the race's base. |
| `health_per_level` | `0` | Health growth per character level. |
| `mana_bonus` | `0` | Flat mana on top of the race's pool. |
| `mana_per_level` | `0` | Mana growth per level. |
| `mana_per_second` | `0` | Extra regen, added to the race's. |
| `speed_mod` | `0` | Added to the race's `base_speed`. |
| `allowed_races` | `[]` | Explicit race ids that may take this class. |
| `allowed_groups` | `[]` | Trait groups that may. Either list matching opens the door; both empty = open to all. |
| `requires` | `[]` | Classes that must ALL be **mastered** (max level) first. |
| `requires_one` | `[]` | Classes of which ANY ONE mastered suffices. |
| `main_only` / `sub_only` | `false` | Restrict which slot the class may occupy. |
| `default` | `false` | The starting class (Citizen). |

Stat modifiers from main and sub class combine by the original plugin's
rule, kept for balance continuity: same sign → the larger magnitude wins;
opposite signs → they sum.

### Granting skills

Races, classes and feats all grant skills the same way — a map from skill id
to the terms of the grant:

```yaml
skills:
  legendquest:holy_light:
    level: 12       # character level required
    cost: 0         # skill points to buy; 0 = free at level
    karma_min: 50   # only the good may hold the light...
  legendquest:darkness:
    level: 12
    karma_max: -50  # ...and only the wicked may quench it.
```

| Field | Default | Meaning |
|-------|---------|---------|
| `level` | `0` | Character level at which the grant opens. |
| `cost` | `0` | Skill points to buy it (`/skill buy`, or the green chip in the panel). `0` = automatic. |
| `karma_min` / `karma_max` | *(unbounded)* | The soul band. |

Karma bands make paired good/evil choices real: grant both sides to one
class and each character can only ever hold one. Drifting out of a band
**suspends** the skill (greyed with a purple `[soul]` note, unusable,
skipped when cycling the loadout) rather than refunding it — redemption and
corruption both do exactly what they say, in both directions.

### Level rewards

Milestone bonuses, cumulative up to the player's current level. Keys are
levels; values are what arrives:

```yaml
levels:
  10: { hp: 5 }
  50: { dex: 1, hp: 5 }
  100: { hp: 5, mana: 5, sp: 5, manaregen: 2.5 }
```

`hp`, `mana`, `sp` (skill points), `manaregen`, and `stats:
{ str: …, … }` all work.

### Gear and stations

Six fields control proficiency: `allowed_weapons`,
`disallowed_weapons`, `allowed_tools`, `disallowed_tools`,
`allowed_armour`, `disallowed_armour`. Each takes **either** a single item
tag as a string **or** a list of item ids — a tag cannot appear inside a
list (vanilla's registry-list codec rejects it):

```yaml
allowed_weapons: "#legendquest:mage_weapons"   # tag form: one string
allowed_tools: ["minecraft:bow", "minecraft:crossbow"]  # list form: ids only
```

Need a tag *plus* extra ids? Make a datapack item tag that includes both
(tags may contain other tags) and reference that.

The semantics are the original plugin's, worth restating because they're
subtle:

- An **absent** allowed list is *no statement* — nothing is restricted by it.
- If any source (race, main class, sub class, feat) makes a statement for a
  slot, the **union** of statements becomes the allowlist and everything
  else on the master list is denied.
- `disallowed_*` always wins, even against another source's allow.
- Only items on the **proficiency master lists** are ever checked
  (`#legendquest:...` tags cover swords/axes/bows/maces, diamond and
  netherite tools) — stone and iron tools stay open to everyone, and
  clutter never fumbles.

Getting it wrong hurts, never confiscates: wrong weapons hit like fists
(fumble), wrong tools dig slow, wrong armour slows and tires.

Stations are six booleans, all defaulting to `true`: `allow_crafting`,
`allow_smelting`, `allow_brewing`, `allow_enchanting`, `allow_repairing`,
`allow_taming`. A deny from any source wins. The handbook prints barred
stations on every race/class page.

### Boons

The balancing weights behind flavourful restrictions — innate perks a race,
class or feat grants just by being what it is. Race and class boons stack
additively.

```yaml
attributes:
  minecraft:armor_toughness: { base: 1.0, per_level: 0.05 }
  minecraft:luck: { base: 0.5 }
enchant_rebate: 1      # XP levels handed back after enchanting
smith_refund: 0.2      # chance to recover a material when crafting gear
gold_tool_mana: 0.5    # golden tools harvest like netherite, per-block mana cost
```

`attributes` takes any attribute id — including other mods' — with a flat
`base` and an optional `per_level` ramp. Ids are deliberately *not*
codec-validated: a typo costs you the boon, not the world load.

### Skills

A skill is purely data; behaviour comes from the composed effects. Here is
a whole one:

```yaml
name: Blink
description: "Elsewhere, immediately."
icon: minecraft:ender_pearl
type: active
cooldown: 6000
mana_cost: 5
effects:
  - { type: "legendquest:teleport", max_range: 24 }
```

| Field | Default | Meaning |
|-------|---------|---------|
| `name` | *(required)* | Display name. |
| `description` | *(none)* | Tooltip and handbook text. |
| `icon` | `minecraft:enchanted_book` | Item id drawn in the panel and HUD. A plain string on purpose: a typo'd icon falls back to the book instead of failing the world load. |
| `type` | `active` | `active` (player fires it), `passive` (always on, re-applied on a tick), `triggered` (fires from combat events). |
| `buildup` | `0` | ms of wind-up before the effects land — telegraphed casting. |
| `delay` | `0` | ms between buildup and landing. |
| `duration` | `0` | ms the skill counts as active (drives the HUD's green→amber→red bar and the 5-second fade warning). |
| `cooldown` | `0` | ms after that before it's ready again. Phases run READY → BUILDING → DELAYED → ACTIVE → COOLDOWN. |
| `mana_cost` | `0` | Mana per use. |
| `karma_cost` | `0` | Karma spent per use. |
| `karma_required` | `0` | Positive: "karma must be at least this". Negative: "must be at least this evil". |
| `karma_reward` | `0` | Karma granted on use — casting Darkness should probably cost your soul something. |
| `consumes` / `consumes_qty` | *(none)* / `1` | An item eaten per use: `consumes: minecraft:ender_pearl`. |
| `xp` | `0` | Class XP awarded on a successful use. |
| `trigger` | *(none)* | For `triggered` skills — see below. |
| `passive_interval` | `3000` | ms between re-applications of a passive's effects. |
| `effects` | `[]` | The list that IS the skill — see [Effects](#effects). |

Triggers: `trigger: { on: melee_hit, chance: 25.0 }`. `on` is one of
`melee_hit` (you land a hit; trigger target = victim), `hurt` (you take
damage; target = attacker), `kill` (target = victim), `fall` (landing,
before damage applies). `chance` is a percentage, default 100.

### Targets

Most effects take a `target` block saying who or where they land, with a
sensible per-effect default so simple YAML stays simple:

```yaml
target: { kind: looking_at, range: 20 }
```

| Kind | Meaning |
|------|---------|
| `self` | The caster. |
| `looking_at` | The living entity in the crosshair, within `range` (default 16). |
| `nearby` | Every living entity within `radius` (default 4) of the caster, not the caster. |
| `trigger` | The other party of the trigger event — the mob you hit, the thing that hurt you. |
| `party` | The caster AND party members within `range` — defaulting to the party XP-share range, so songs and blessings reach exactly as far as shared glory does. No party = just the caster. |

### Effects

The built-in effect types. Compose freely — a skill's `effects` list runs in
order, and each entry picks its own target.

**`legendquest:damage`** — magic damage.
`amount` (required), `target` (default `looking_at`).

**`legendquest:heal`** — hearts and heart particles.
`amount` (required), `target` (default `self`).

**`legendquest:potion_effect`** — any mob effect; the workhorse for auras,
hexes, songs and passives.
`effect` (required, e.g. `minecraft:strength`), `duration` (ms, default
5000), `amplifier` (default 0), `particles` (default `true` — set `false`
on always-on passives, nobody wants to live in a lava lamp), `show_icon`
(default `true`), `target` (default `self`).

**`legendquest:leap`** — launch the caster along their look.
`power` (default 1.5), `lift` (default 0.6).

**`legendquest:teleport`** — blink to the looked-at block, with portal
particles and the enderman noise. `max_range` (default 32).

**`legendquest:lightning`** — strike the target's position.
`visual_only` (default `false` — `true` is all flash, no damage),
`target` (default `looking_at`).

**`legendquest:summon`** — spawn entities at the looked-at block.
`entity` (required), `count` (default 1), `target` (default `looking_at`).

**`legendquest:message`** — text to the caster, `&` colours supported.
`text` (required), `action_bar` (default `true`; `false` = chat).

**`legendquest:ignite`** — set the target on fire.
`duration` (ms, default 5000), `target` (default `looking_at`).

**`legendquest:give_item`** — conjure items into the caster's inventory
(overflow drops at their feet). `item` (required), `count` (default 1).

**`legendquest:sound`** — play a sound at the caster.
`sound` (required), `volume` (default 1.0), `pitch` (default 1.0),
`stop_after` (ms, default 0 = let it ring). `stop_after` exists because a
music disc outlives any skill by minutes: the Bard's Battle Hymn plays
Pigstep with `stop_after` matching its duration, and the cut is a vanilla
stop-sound packet, so it works on vanilla clients too.

**`legendquest:particle_line`** — a line of particles from the caster's
wand-height to the target; the visual language of "I cast a thing at you".
`particle` (default `minecraft:end_rod`), `per_block` (density, default 4),
`target` (default `looking_at`). Simple particles can be written as a bare
string; dust and friends need vanilla's `{"type": ...}` object form for
their options.

**`legendquest:projectile`** — launch a projectile along the caster's look.
`entity` (required), `speed` (default 1.5), `power` (default 1, fireballs
only). `minecraft:fireball` and `minecraft:small_fireball` get their real
constructors (owner, direction, explosion power); anything else spawns with
`speed` as straight velocity.

**`legendquest:run_command`** — the old *permskills*, modernised: run any
command under LegendQuest's costs and cooldowns — a server-installed
`/fly`, `/home`, whatever. `command` (required), `undo_command` (default
none), `duration` (ms until the undo runs). Runs as the caster with
gamemaster permission, output silenced; `%player%` expands to the caster's
name. Temp flight is exactly `command` + `undo_command` + `duration`. An
undo whose player logged off waits for their return; pending undos don't
survive a restart.

New effect types come from **skill-pack mods** — a tiny jar that registers
a codec and ships skill JSONs. See [docs/SKILL-PACKS.md](docs/SKILL-PACKS.md)
and the worked example at
[LegendQuest-SkillPack-Example](https://github.com/Sablednah/LegendQuest-SkillPack-Example):
`SkillEffectTypes.register()` in the mod constructor is the entire API
surface.

### Feats

A feat is a purchasable bundle of character, bought with skill points. It
reuses every engine above — boons for passives, gear rules for
proficiencies, skill grants for actives — so one file can say "+1
toughness", "may wear heavy armour" or "learns Blink", or all three, with
zero new code:

```yaml
name: Toughness
description: "Hard to put down."
icon: minecraft:shield
cost: 8
level: 5
requires: [legendquest:battle_hardened]
allowed_groups: [Humanoid]
attributes:
  minecraft:max_health: { base: 4 }
```

| Field | Default | Meaning |
|-------|---------|---------|
| `name` | *(required)* | Display name. |
| `description` | *(none)* | Handbook prose. |
| `icon` | `minecraft:nether_star` | Panel/handbook icon. |
| `cost` | `5` | Skill points. |
| `level` | `0` | Minimum character level. |
| `karma_min` / `karma_max` | *(unbounded)* | Soul band; drifting out **suspends** the feat's benefits, exactly like skill grants. |
| `requires` | `[]` | Feat chain — all listed feats must be owned first. |
| `allowed_races` / `allowed_groups` / `allowed_classes` | `[]` | Eligibility; empty = everyone. |
| `attributes` / `enchant_rebate` / `smith_refund` / `gold_tool_mana` | — | Boons. |
| `skills` | `{}` | Skill grants. |
| `allowed_*` / `disallowed_*` | *(no statement)* | Gear proficiencies. |

This is what makes two level-10 elf fighters different people.

### Renaming everything

Every player-facing string — including the words "Race", "Class", "Skill",
"Feat", "Mana" and "Karma" themselves — lives in
`config/legendquest/messages.yml`, written in full (~280 keys) on first
boot. Edit for translation, tone, or wholesale re-theming: a zombie server
sets `term.race: "Archetype"`, a sci-fi one `term.mana: "Energy"`.
`{term.x}` cross-references, `{placeholder}` runtime values and `&` colours
work in every string; deleted keys fall back to defaults; applied on
restart **and** `/reload`. Modded clients receive the vocabulary on login,
so the panel and handbook follow the server's genre too.

### Traps worth knowing about

- **Content needs a restart; text doesn't.** Registries freeze at world
  load. `/reload` = tags and `messages.yml` only. The classic symptom of
  forgetting: your YAML edit "did nothing".
- **A registry parse error can pass the server boot and still kill the
  client's world load.** If a fresh entry crashes people on join, suspect
  the newest data file, not the network.
- **Bare-string particles only work for simple particles** —
  `minecraft:dust` and friends carry options and need the
  `{"type": ...}` object form.
- **`consumes`, `effect`, `entity`, `item` and `sound` ARE codec-validated**
  — a typo in those fails the entry at load. `icon` and boon `attributes`
  deliberately are not, and fail soft.
- **Milliseconds, everywhere.** `cooldown: 6` is six thousandths of a
  second, and the panel will cheerfully show you a skill with no cooldown.

## Commands
`/lq` is the root; classic shorthands (`/race`, `/class`, `/skill`, `/stats`,
`/karma`, `/roll`, `/bind`, `/loadout`, `/party`) are registered as aliases.
Admin: `/lq admin setrace|setclass|addxp|setkarma|level` (append `force` to
setrace/setclass to allow illegal combos). Bare names work
everywhere — `dwarf`, not `legendquest:dwarf`.

Levels are derived from the main class's XP bank, so the level verbs do the
arithmetic for you and accept any entity selector — handy from a command
block or another mod:

```
/lq admin level set    @p 20     # exactly level 20, XP snapped to the threshold
/lq admin level add    @a 1      # everyone up one, part-levelled progress kept
/lq admin level remove Steve 3   # down three, floor 0
/lq admin level query  Steve     # prints it, and returns it as the command result
```

`set`/`add`/`remove` clamp to `maxLevel` and return the number of players
changed; `query` returns the level itself, so a comparator or
`execute store result` can read it.

## Building

```
./gradlew build       # jar in build/libs/
./gradlew runServer   # dev server in run/
```

Java 21, NeoForge 21.11.42, ModDevGradle 2.0.141. SnakeYAML is embedded via
jar-in-jar.

## Heritage

Original plugin: [Sablednah/LegendQuest](https://github.com/Sablednah/LegendQuest),
with the recovered BukkitDev documentation preserved in the old repo's
workspace as ground truth for intended behaviour. Reference NeoForge ports by
the same author: MobHealth, ZombieMod, CityWorld-ReForged, WoodDye.

## Licence

MIT — see [LICENSE](LICENSE).
