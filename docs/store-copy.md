# Store page copy

Everything a store listing needs **except the description itself**, which lives
in [`CURSEFORGE.md`](../CURSEFORGE.md) so it can be pasted without editing.
This file is the surrounding guide: name, summary, categories, tags, links, and
the screenshot upload order.

Keep both in step with the README and CHANGELOG when features change, so the
store pages never drift into advertising something the mod no longer does. This
one had gone a full three releases out of date before anybody noticed, which is
the argument for checking it at release time rather than when it feels stale.

CurseForge's description editor accepts pasted rich text and has a Markdown
mode; headings, tables and lists survive the paste. Modrinth takes Markdown
directly, so the same file serves both.

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

## Where it lives

| Platform | URL | State |
|---|---|---|
| GitHub | https://github.com/Sablednah/LegendQuest-ReForged | live |
| CurseForge | https://www.curseforge.com/minecraft/mc-mods/legendquest-reforged | live — approved 2026-08-20 |
| Modrinth | — | not listed yet; the workflow exists and skips cleanly until a project ID and token are configured |
| Website | https://sablecraft.co.uk/legendquest-reforged/ | live |

CurseForge project ID **1658748** (used by `CURSEFORGE_PROJECT_ID`, see
`.github/workflows/curseforge.yml`).

---

## Description

**Lives in [`CURSEFORGE.md`](../CURSEFORGE.md) at the repo root**, not here —
it is the only part of this document that gets pasted verbatim, so it is a file
you can select-all and copy without picking the description out of a guide
first. Same arrangement as ZombieMod, CityWorld and Standards.

The banner is the first line of that file on purpose: CurseForge's editor does
not keep an uploaded image across edits, so it has to be part of the pasted
text or it goes missing every time the copy changes.

**Keep it free of blockquotes and indented code blocks.** CurseForge renders
descriptions through the same HTML sanitiser as changelogs, and those two
constructs are known to make it fail — see
`scripts/curseforge-changelog.py`, which strips them out of changelogs
automatically. Nothing does that for the description, because nothing can:
it is pasted by hand.

---

## Screenshots

Captured 2026-08-18. The PNGs live in `screenshots/` **locally and are not in
git** (see `.gitignore`) — they go straight from disk into the store listing,
and 34MB of images in every clone would buy nobody anything. This table is the
tracked part: it is the record of which shot goes where and what it says.

Each setting was shot in a world that suits it — ruined overgrown city for The
Wasteland, an intact one for Cold Frontier — so the three sets read as three
different games rather than one mod with the nouns swapped.

### Upload order for the listing

The first three carry the whole pitch: *this is a real RPG*, and *the words are
yours*. Shots 2 and 3 must sit next to each other — the vocabulary system is the
hardest feature to explain in prose and the easiest to show, and the pairing
does the explaining by itself.

| # | File | Caption |
|---|---|---|
| 1 | `fantasy-handbook-skills-battle-hymn.png` | Every skill explains itself — cost, cooldown, and what it actually does. |
| 2 | `fantasy-handbook-races-dwarf.png` | The Players Handbook: ten races, nine classes, all defined in datapacks. |
| 3 | `wasteland-guide-archetypes-athlete.png` | The same screen under The Wasteland pack — Races became Archetypes, Mana became Stamina. Every word is yours. |
| 4 | `vanilla-client-character-sheet.png` | An unmodded vanilla client, playing. No mod install, no modpack — the whole RPG runs on the server. |
| 5 | `fantasy-panel-stats-race-class-pickers.png` | Roll your stats, pick a race and class from the inventory screen. |
| 6 | `fantasy-cast-fireball.png` | Skills you buy with points you earn. |
| 7 | `fantasy-hud-loadout-cooldown.png` | A five-slot loadout bar with live cooldowns. |
| 8 | `fantasy-levelup-title-card-150-mage.png` | 150 levels per class, each with its own XP bank. |
| 9 | `scifi-manual-species-belter.png` | Cold Frontier: a second complete setting, included. |
| 10 | `wasteland-guide-roles-builder.png` | Roles that must be earned — master the Labourer before you can be a Builder. |
| 11 | `fantasy-handbook-gear-barbarian-tools.png` | Item proficiency per class, driven by item tags. |

Shot 4 earns its high placement: "vanilla clients can join" is the line that
separates this from every other RPG mod, and it is the one claim a reader will
assume is marketing until they see it. Caption it explicitly as an unmodded
client — the image is only convincing if the viewer is told what they are
looking at, since a plain HUD is exactly what it looks like.

### Everything captured

**Fantasy (built-in D&D set)** — `fantasy-*`: handbook pages for races,
classes, skills, feats and gear; the character panel across its Stats, Skills
and Party tabs, including race/class tooltips and the buy chips; the loadout and
spellbook slot; the HUD mid-combat; Magic Missile and Fireball being cast; the
level-up title card; and the class list in chat.

**The Wasteland** — `wasteland-*`: the Survivor's Guide across Archetypes,
Roles, Skills and Perks, plus a character panel reading "Mechanic Miner —
Humanity Saintly" with a Crew tab.

**Cold Frontier** — `scifi-*`: the Ship's Manual across Species, Professions,
Skills and Augments, plus the Species/Profession picker.

**Vanilla client** — `vanilla-client-*`: a genuinely unmodded 1.21.11 client
(official launcher, no mod loader) connected to a LegendQuest server, showing
the character sheet and the class list in chat. Captured 2026-08-18 against the
`v2.0.0` release.
