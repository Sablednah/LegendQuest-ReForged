# LegendQuest-StoryTeller (future partner mod — banked idea)

Not part of LegendQuest ReForged. A separate companion mod so someone can be
a **GM** and tell/plan a story for a server of LQ characters. Banked here so
the idea survives; nothing below is scheduled.

**The model**: the Storyteller mode from *Vampire: The Masquerade –
Redemption* (2000) — a live human GM running the session from inside the
game. Most of what made it work was a small toolset:

- **Spectate**: drift invisibly through the scene.
- **Spawn** entities and items on cue.
- **Possess**: take over an entity to move it, speak as it, fight as it —
  puppeting an NPC beats scripting one for live play.
- **Set dressing**: prebuilt schematics/sets and staged events, placed live
  or pre-planned.

## What LegendQuest already provides for it

- **NPCs as characters**: races and classes are data with stats, skills,
  gear rules and boons — an NPC system gets a full character sheet for free
  by pointing at the same registries.
- **The `frequency` field** on races/classes (parsed today, consumed by
  nothing) was always meant for this: weighting the random NPC population.
  A city district rolls its inhabitants against `frequency` — humans
  common, tieflings rare, one gnome if you're lucky.
- **Permissions**: a `legendquest.storyteller` node slots into the existing
  LuckPerms-friendly scheme.
- **run_command / permskills**: staged events can already lean on any
  server command under costs/cooldowns.

## Sketch of the toolset (unscoped)

- GM role with its own panel tab or screen: spawn palette (races × classes
  × gear), event cues, scene notes.
- Possession: camera + input redirected to a mob; release returns it to AI.
  (Relatedly: ZombieMod already puppets vanilla mobs with data-driven goals
  — that codebase is prior art for half of this.)
- Schematic library: encounter sets (camp, ambush, shrine) placed/removed
  cleanly, CityWorld-aware placement a bonus.
- Story planner: ordered beats with triggers (location entered, mob slain,
  time) that fire spawns/commands/messages.

## Related banked ideas in PORTING.md

- Configurable karma triggers — a GM defining what counts as good/evil is
  the same system wearing a different hat.
- The vocabulary system (messages.yml) already lets a Storyteller re-theme
  every noun for their campaign.
