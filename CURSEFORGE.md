![Legendquest](https://media.forgecdn.net/attachments/description/1658748/description_e97262ef-6787-4591-b311-00304c4e3f3a.png)

# LegendQuest ReForged

**Pick a race. Pick a class. Earn levels, learn skills, and live with the
consequences.** LegendQuest turns Minecraft into a character-driven RPG — six
rolled stats, classes that gate what you can wield, skills you buy with points
you earn, and a karma score that quietly opens some doors and closes others.

It began as a Bukkit plugin years ago. This is that design rebuilt from nothing
for NeoForge — and built for **Minecraft 1.21.11, 26.1.2 and 26.2**. Each
download names the version it is for, and refuses to load on the wrong one
rather than misbehaving quietly.

---

## It runs on the server, and that is the whole point

LegendQuest is **server-authoritative**. Install it on the server and you are
done. **Vanilla clients can join and play the entire game** through chat and the
action bar — no client install, no version negotiation, no "you must install
this modpack to play here".

Players who *do* install it get the extras on top: an in-game handbook, a
character panel on the inventory screen, a skill HUD and keybinds.

---

## The content is data, not code

Races, classes, skills and feats are **datapack registries**. Write a JSON file,
and the content exists — no Java, no compiling, no waiting on an update.

Prefer YAML? There is a front door for that too: drop the same content into
`config/legendquest/` and the mod converts it for you. Malformed files are
logged loudly and skipped rather than taking your world down.

A pack can also *replace* the built-in content instead of merely adding to it,
so you are never stuck with elves and wizards in a setting that has no room for
them.

---

## Two complete settings are included

Not samples — finished, playable content sets, each a drop-in datapack.

**The Wasteland** — 8 Archetypes, 20 Roles, 61 skills, 10 Perks. Post-
apocalyptic survival. Start as a Drifter and earn your way into a specialisation:
Scavenger, Enforcer, Doc, Scout, Labourer, and the harder roles behind them.

**Cold Frontier** — 8 Species, 16 Professions, 55 skills, 10 Augments. A cold,
hard sci-fi frontier. Deliberately setting-neutral: retune the vocabulary and
the same data plays as space-western, fleet-opera or salvage-horror.

Both are datapacks rather than mods, and CurseForge only hosts `.jar` files on
a mod project, so they are downloaded from the GitHub release:

**[Download the genre packs](https://github.com/Sablednah/LegendQuest-ReForged/releases/latest)**
— `legendquest-apocalypse.zip` and `legendquest-scifi.zip`, attached to every
release alongside the mod.

---

## Re-theme every word in the game

Roughly **280 player-facing strings** live in one config file. Races become
"Archetypes". Mana becomes "Stamina". Karma becomes "Humanity". Parties become
"Crews".

The handbook, the HUD, the commands, the error messages — all of it follows.
Modded clients receive the server's vocabulary when they log in, so the interface
speaks your server's language rather than the mod's. Each genre pack ships its
vocabulary ready to paste in.

---

## Other mods can add skills

A skill-pack is a small jar that registers its own skill effect types and ships
its own content, without touching LegendQuest. `SkillEffectTypes.register()` in
your mod constructor is the entire API surface.

A worked example is attached to the files page as `examplepack-1.0.0+mc<version>.jar`
— one per Minecraft version, same as the mod. Install it beside LegendQuest to
see custom effects in action, or read it as a template for your own. Two of its
three effects declare themselves hostile and one deliberately does not, which
is the part worth copying.

---

## What your players actually get

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
- **Party chat** — `/pc` for a line, or `/pc` alone to send everything you type
  to your party until you switch it off. Operators can listen in only if the
  server grants it *and* they turn it on.
- **Nameplates** — race, class and level above each player's head, worded from
  your own vocabulary file. It is a display entity, so unmodded clients see it,
  and it does not touch chat, the tab list, or the scoreboard team slot every
  other prefix mod wants. It knows when to get out of the way: no plate in
  spectator or creative, none on a corpse, and none floating where a hidden
  player used to be.
- **Class ranks and karma epithets** — a Fighter climbs Squire, Man-at-Arms,
  Knight, Champion, Lord; karma earns you "the good" or "the diabolic". Both
  genre packs have their own ranks throughout.
- **XP from kills, mining and smelting**, tag-based so modded ores count.
- **An in-game Players Handbook** that explains every race, class, skill and
  feat, including what each skill actually does.
- **Admin commands and permission nodes** for every race and class, for
  LuckPerms and friends.

## Optional: better together with Standards

Install [Standards](https://www.curseforge.com/minecraft/mc-mods/sablecraft-standards)
alongside it and LegendQuest reports things nothing else can see. A **missed**
attack counts as combat, so nobody teleports away the instant they dodge you.
Hostile skills mark the caster, and a blind or a curse marks the victim — even
though neither deals damage. And LegendQuest asks permission before landing a
hostile skill, so a peaceful faction or a safe zone can refuse it.

Standards' vanish is honoured too: hide a player and their nameplate goes with
them, instead of hovering over empty air announcing exactly where they are.

Entirely optional. Without Standards none of it runs and everything above works
exactly as described.

---

## Installing

1. Put the LegendQuest jar in your server's `mods/` folder.
2. Start the server. That is genuinely it — the built-in fantasy content works
   out of the box.
3. Want a different setting? Grab `legendquest-apocalypse.zip` or
   `legendquest-scifi.zip` from the
   [GitHub release](https://github.com/Sablednah/LegendQuest-ReForged/releases/latest),
   drop it into `<world>/datapacks/` and restart, then paste that pack's
   vocabulary snippet into `config/legendquest/messages.yml`.

**Pick the download matching your server:**

| File | Minecraft | NeoForge |
|---|---|---|
| `legendquest-<version>+mc1.21.11.jar` | 1.21.11 | any 21.11 build |
| `legendquest-<version>+mc26.1.2.jar` | 26.1.2 | any 26.1 build |
| `legendquest-<version>+mc26.2.jar` | 26.2 | any 26.2 build |

Any build within the series will do — updating NeoForge does not mean waiting
for a LegendQuest release that changes nothing else.

Content is read when the world loads, matching how vanilla handles data-driven
registries — edit your JSON or YAML and restart the server; `/reload` will not
apply it.

---

## Source, issues, licence

MIT licensed and developed in the open at
[GitHub](https://github.com/Sablednah/LegendQuest-ReForged). Bug reports and
content-pack contributions are welcome on the
[issue tracker](https://github.com/Sablednah/LegendQuest-ReForged/issues).

