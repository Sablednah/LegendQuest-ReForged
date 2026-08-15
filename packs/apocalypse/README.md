# LegendQuest: The Wasteland

A zombie-apocalypse content pack for
[LegendQuest ReForged](../../README.md). Archetypes instead of races, Roles
instead of classes, Stamina instead of mana, and a Humanity scale that the
worst skills spend. Pairs nicely with a ruined-city world (CityWorld's
apocalypse preset) and a zombie mod.

The Role tree is a modernised descendant of the class hierarchy from the
original server's Heroes config (2012): master a base trade to unlock its
specialists, each of whom starts with everything the base trade taught.

## Install

1. Copy this folder (or a zip of it) into your world's `datapacks/`
   directory.
2. Merge `messages-snippet.yml` into `config/legendquest/messages.yml` for
   the Wasteland vocabulary (Archetype / Role / Perk / Stamina / Humanity /
   Crew).
3. **Restart the server** — races/classes/skills/feats are frozen
   registries; `/reload` is not enough.

The pack's `pack.mcmeta` carries a datapack *filter* that hides the built-in
D&D races, classes and feats, so your pickers show only Wasteland content.
The built-in D&D *skills* stay registered (nothing grants them, so they
never appear); the `#legendquest:...` gear tags stay too — this pack reuses
them.

Optional flavour: in the server config, rename the karma bands
(`positiveNames` / `negativeNames`) to something like
`Decent, Good Egg, Samaritan, Saint` and `Hard, Cold, Ruthless, Feral`.

## The Roles

Master a base Role (reach the level cap with it) to unlock its specialists.
Specialists inherit the base Role's skills from level 0 — they start where
the base finished.

```
Drifter (start)
├── Scavenger ──┬── Infiltrator      (stealth kills)
│               ├── Chemist          (fire, poison, stims)
│               └── Trader ◄──────┐  (contacts, hired muscle)
├── Labourer ───┬── Builder        │ (fortification, toughness)
│               ├── Miner          │ (deep work, haste)
│               └──────────────────┘  Trader accepts either mastery
├── Doc ────────┬── Combat Medic    (heals under fire)
│               └── Quartermaster   (buffs, morale, mercy)
├── Enforcer ───┬── Mercenary ──── Pyro   (the only tier-3: flamethrower)
│               └── Veteran         (slow to level, absurd to kill)
└── Scout ──────┬── Sharpshooter    (ranged precision)
                ├── Rancher         (brings livestock back from the dead world)
                ├── Pathfinder      (moves whole crews)
                └── Plague Caller   (raises walkers; costs Humanity)
```

The Plague Caller's deeper skills carry `karma_max` gates — they are simply
unavailable to anyone still holding much Humanity, and every casting spends
more. The Quartermaster's Mercy is the mirror: `karma_min 20`, and using it
gives Humanity back.

## The Archetypes

Who you were before. Archetypes tweak stats, add an edge or two, and their
trait groups (`Tough`, `Quick`, `Clever`, `Steady`, `Handy`, `Sly`,
`Touched`) are the hooks feats and future content can gate on.

| Archetype | The pitch |
|---|---|
| Nobody | The blank start. Pick one when you're ready. |
| Ex-Military | Discipline, hardware habits, flat heart rate. |
| Athlete | Rule one: cardio. |
| Paramedic | Kept people alive for a living; still does. |
| Mechanic | Things make more sense than people. |
| Street Kid | Was surviving before it was mandatory. |
| Prepper | Told you so. |
| Immune | Got bitten. Got better. Nobody relaxes around them. |

## Balance notes

- Base Roles are deliberately modest; specialists get the numbers. The
  Veteran levels 20% slower (`xp_adjust_kill: -20`) and the Labourer line
  levels 20–40% faster from mining, both straight from the Heroes config.
- The Flamethrower consumes one coal per burst, as is right and proper.
- Everything here is YAML-overridable per server: copy a file into
  `config/legendquest/<kind>s/<name>.yml` and edit.
