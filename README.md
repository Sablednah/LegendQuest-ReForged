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
it. The default pack is a trope-heavy D&D-flavoured set: ten races (Human,
Elf, Dwarf, Orc, Hobbit, Gnome, Half-Elf, Half-Orc, Tiefling — SRD 5.1
CC-BY-safe — plus the starting Undecided) and eight classes (Citizen,
Fighter, Mage, Ranger, Cleric, Rogue, Barbarian, and the earned-only Warlord).

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

## Commands
`/lq` is the root; classic shorthands (`/race`, `/class`, `/skill`, `/stats`,
`/karma`, `/roll`, `/bind`, `/loadout`, `/party`) are registered as aliases.
Admin: `/lq admin setrace|setclass|addxp|setkarma`. Bare names work
everywhere — `dwarf`, not `legendquest:dwarf`.

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
