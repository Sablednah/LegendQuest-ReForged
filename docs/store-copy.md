# Store page copy

Source text for the CurseForge and Modrinth listings. Keep this in step with
the README and CHANGELOG when features change, so the store pages never drift
into advertising something the mod no longer does.

CurseForge's description editor accepts pasted rich text and has a Markdown
mode; the headings, tables and lists below survive the paste. Modrinth takes
Markdown directly.

---

## Project name

LegendQuest ReForged

## Summary (the one-liner under the title)

> A complete RPG character system for Minecraft: races, classes, skills, feats
> and karma, all defined in datapacks rather than code. Server-authoritative —
> vanilla clients can join and play.

## Categories

- Adventure and RPG (primary)
- Server Utility
- Miscellaneous

## Suggested tags / keywords

rpg, classes, races, skills, character progression, datapack, server, karma,
party, magic, levelling, roleplay

## Licence

MIT — link to https://github.com/Sablednah/LegendQuest-ReForged/blob/main/LICENSE

## Links

| Field | Value |
|---|---|
| Source | https://github.com/Sablednah/LegendQuest-ReForged |
| Issues | https://github.com/Sablednah/LegendQuest-ReForged/issues |
| Wiki / docs | https://github.com/Sablednah/LegendQuest-ReForged#readme |

---

## Description

### LegendQuest ReForged

**Pick a race. Pick a class. Earn levels, learn skills, and live with the
consequences.** LegendQuest turns Minecraft into a character-driven RPG — six
rolled stats, classes that gate what you can wield, skills you buy with points
you earn, and a karma score that quietly opens some doors and closes others.

It began as a Bukkit plugin years ago. This is that design rebuilt from nothing
for NeoForge 1.21.11.

---

### It runs on the server, and that is the whole point

LegendQuest is **server-authoritative**. Install it on the server and you are
done. **Vanilla clients can join and play the entire game** through chat and the
action bar — no client install, no version negotiation, no "you must install
this modpack to play here".

Players who *do* install it get the extras on top: an in-game handbook, a
character panel on the inventory screen, a skill HUD and keybinds.

---

### The content is data, not code

Races, classes, skills and feats are **datapack registries**. Write a JSON file,
and the content exists — no Java, no compiling, no waiting on an update.

Prefer YAML? There is a front door for that too: drop the same content into
`config/legendquest/` and the mod converts it for you. Malformed files are
logged loudly and skipped rather than taking your world down.

A pack can also *replace* the built-in content instead of merely adding to it,
so you are never stuck with elves and wizards in a setting that has no room for
them.

---

### Two complete settings are included

Not samples — finished, playable content sets, each a drop-in datapack.

**The Wasteland** — 8 Archetypes, 20 Roles, 61 skills, 10 Perks. Post-
apocalyptic survival. Start as a Drifter and earn your way into a specialisation:
Scavenger, Enforcer, Doc, Scout, Labourer, and the harder roles behind them.

**Cold Frontier** — 8 Species, 16 Professions, 55 skills, 10 Augments. A cold,
hard sci-fi frontier. Deliberately setting-neutral: retune the vocabulary and
the same data plays as space-western, fleet-opera or salvage-horror.

---

### Re-theme every word in the game

Roughly **280 player-facing strings** live in one config file. Races become
"Archetypes". Mana becomes "Stamina". Karma becomes "Humanity". Parties become
"Crews".

The handbook, the HUD, the commands, the error messages — all of it follows.
Modded clients receive the server's vocabulary when they log in, so the interface
speaks your server's language rather than the mod's. Each genre pack ships its
vocabulary ready to paste in.

---

### Other mods can add skills

A skill-pack is a small jar that registers its own skill effect types and ships
its own content, without touching LegendQuest. `SkillEffectTypes.register()` in
your mod constructor is the entire API surface.

A worked example is attached to the files page as `examplepack-1.0.0.jar` —
install it beside LegendQuest to see custom effects in action, or read it as a
template for your own.

---

### What your players actually get

- **Six rolled stats** — STR, DEX, CON, INT, WIS, CHR — feeding derived HP and
  mana, modified by race and class.
- **Sub-classes** alongside a main class, each with its own XP bank, so
  switching class never throws away what you earned elsewhere.
- **Real class requirements** — prerequisite classes, mastery gates, either/or
  alternatives, and group restrictions.
- **A skill-point economy** — earn points per level, spend them on skills or
  stat boosts, respec when you change your mind.
- **A five-slot loadout bar** with drag-and-drop, cooldown readouts and cast
  feedback, plus binding a skill to a held item so right-click casts it.
- **Item proficiency per class** — wield what your class cannot use and you
  fumble.
- **Karma** as a real mechanic, with good and evil paths that lock each other
  out.
- **Parties** — invite, rename, teleport to members, and party-wide skills.
- **XP from kills, mining and smelting**, tag-based so modded ores count.
- **An in-game Players Handbook** that explains every race, class, skill and
  feat, including what each skill actually does.
- **Admin commands and permission nodes** for every race and class, for
  LuckPerms and friends.

---

### Installing

1. Put `legendquest-2.0.0.jar` in your server's `mods/` folder.
2. Start the server. That is genuinely it — the built-in fantasy content works
   out of the box.
3. Want a different setting? Drop `legendquest-apocalypse.zip` or
   `legendquest-scifi.zip` into `<world>/datapacks/` and restart, then paste
   that pack's vocabulary snippet into `config/legendquest/messages.yml`.

**Requires NeoForge 21.11.42+ on Minecraft 1.21.11.**

Content is read when the world loads, matching how vanilla handles data-driven
registries — edit your JSON or YAML and restart the server; `/reload` will not
apply it.

---

### Source, issues, licence

MIT licensed and developed in the open at
[GitHub](https://github.com/Sablednah/LegendQuest-ReForged). Bug reports and
content-pack contributions are welcome on the
[issue tracker](https://github.com/Sablednah/LegendQuest-ReForged/issues).

---

## Screenshot shot-list (still to capture)

The listing needs images; these are the shots that would sell it, roughly in
order of value:

1. **The Players Handbook** open on a class page — this is the most
   screenshot-worthy thing in the mod.
2. **The character panel** on the inventory screen, Stats tab, showing rolled
   stats and boost chips.
3. **The same handbook page under a genre pack**, so the re-theming is visible
   side by side with shot 1 — "Race → Archetype", "Mana → Stamina". Pair these
   two next to each other; the vocabulary system is the hardest feature to
   convey in words and the easiest to show.
4. **The skills tab with a loadout** being dragged into place, cooldowns
   showing.
5. **The HUD in play** — mana, XP bar and loadout strip, mid-combat.
6. **A level-up** with the title card on screen.
7. **The Wasteland class list** in chat, to show a full alternative setting.
8. Optional: a vanilla client sitting on the server playing via chat, captioned
   as such — proof of the headline claim.
