# LegendQuest ReForged

Races, classes, skills, karma and character progression for **NeoForge
1.21.11** — the modern rewrite of the classic
[LegendQuest](https://www.spigotmc.org/resources/legendquest-rpg.2120/)
Bukkit RPG plugin (and, eleven years late, the "LQ 2.0" it promised).

**Status: early alpha.** Server boots, content loads, characters persist;
balance and polish are in progress. See [PORTING.md](PORTING.md) for the live
checklist and every architecture decision.

## What works today

- **Races, classes and skills as data.** JSON in any datapack
  (`data/<pack>/legendquest/{race,class,skill}/*.json`) or YAML in
  `config/legendquest/{races,classes,skills}/*.yml` — same schema, two skins.
  `/reload` picks up edits. A YAML file with a built-in entry's name
  overrides it.
- **Characters.** Deterministic 4d6-drop-lowest statline (or flat 12s),
  D&D-style modifiers, race + main class + optional sub class, per-class XP
  banks with mastery, karma with log-scale titles, mana with regen. All
  stored in one codec-serialized attachment — no database.
- **Skills.** Active (`/skill use`), passive (self-reapplying), and triggered
  (chance on melee hit / being hurt / kills). Buildup → delay → active →
  cooldown phase machine, mana/karma/item costs, all times in milliseconds.
  Skills are composed from effect types in data; new effect types come from
  **skill-pack mods** — see [docs/SKILL-PACKS.md](docs/SKILL-PACKS.md).
- **Restrictions.** Allowed/disallowed weapons, tools and armour per race and
  class via item ids and tags (`#legendquest:fighter_weapons`); disallow
  always wins. Wrong weapon hits like a fist, wrong tools dig slow, wrong
  armour is politely handed back.
- **Combat.** Optional opposed-d20 hit/dodge (DEX) and STR damage modifiers.
- **Commands.** `/lq`, `/race list|choose`, `/class list|choose|sub`,
  `/stats`, `/skill list|use|buy`, `/karma`, `/roll`, and `/lq admin
  setrace|setclass|addxp|setkarma`.
- **Vanilla clients can join** — everything is server-side; feedback uses
  chat and the action bar.

## Building

```
./gradlew build       # jar in build/libs/
./gradlew runServer   # dev server in run/
```

Java 21, NeoForge 21.11.42, ModDevGradle 2.0.141. SnakeYAML is embedded via
jar-in-jar.

## Heritage

Original plugin, skills and docs: the three repos beside this one and
`../LegendQuest/docs/legacy-bukkit-docs/` (recovered from BukkitDev).
Reference NeoForge ports by the same author: MobHealth, ZombieMod,
CityWorld-ReForged, WoodDye.
