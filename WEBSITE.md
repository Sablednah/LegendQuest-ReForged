# sablecraft.co.uk — what the site needs from this repo

The docs site section is proposed at **<https://sablecraft.co.uk/legendquest-reforged/>** and is
maintained by a separate session, so this file is the handover: what to build, where the source text
lives, and what must stay in sync with the mod.

This follows the shape proven for CityWorld (`../CityWorld-ReForged/WEBSITE.md`) and ZombieMod
(`../ZombieMod/ZombieMod/WEBSITE.md`). Read one of those first if you are the site session — the
conventions below are theirs.

## Status — ✅ BUILT AND DEPLOYED (2026-08-20) by the site session

All six pages are live at the proposed URLs, plus a card on `/game-plugins/`. What the site session
did, for the record:

- `/legendquest-reforged/` and its five children exist as real WP pages (ids 140–145, children on
  `post_parent = 140`), each on its own theme template, sharing a `legendquest-subnav.php` tab strip
  — the same shape as CityWorld's and ZombieMod's.
- **Version facts are on the landing page only**, and the hero tags are version-agnostic
  ("10 races, 9 classes" / "NeoForge" / "MIT" / "No client mod required") as recommended.
- All five "careful to get right" points are honoured: the landing page leads with *"your players do
  not need the mod"* rather than "server-side only"; `/packs/` states in a callout that the packs are
  **not on CurseForge** and links `/releases/latest`; the restart rule is called out on `/content/`
  **and** `/settings/`; the server-and-every-modded-client rule is a bordered callout on
  `/skill-packs/`; and both datapack traps (zip root, and tags only in the bare string form) are
  stated on `/content/` and `/packs/`.
- Both "known limitations" are stated honestly rather than omitted — the mining-XP farm note is on
  `/settings/` next to `mineXpBlock`.
- The screenshot pair is adjacent and captioned as a pair on the landing page, and
  `vanilla-client-character-sheet.png` is captioned as **a genuinely unmodded client** in bold.
- Artwork used: `legendquest-reforged-white (850px).png` as the landing wordmark and the
  `/game-plugins/` card logo, plus 23 of the 33 screenshots resized to 1200px JPEG.
- The old Bukkit card on `/game-plugins/` was renamed **LegendQuest (Legacy)** with a line pointing
  at the successor, matching how MobHealth's two cards already read.

### Nameplate correction — applied to the site 2026-08-21

`0972a97` corrected the `[nameplate]` config comment, which still described the first
implementation's scoreboard team. The site's `/settings/` page had been built from that stale comment
and so carried the same wrong warning; it is now rewritten from the corrected source and redeployed.

The section no longer reads as a caution. It now says the nameplate is a **text display entity** an
unmodded client renders as an ordinary vanilla entity, that chat / tab list / `/list` are untouched,
and that it does **not** take the scoreboard team slot, so it does not compete with LuckPerms, FTB
Ranks or any chat-prefix mod. The `enabled` row gained the `{name} {race} {class} {sub_class} {level}
{karma} {title} {epithet}` placeholder list and the `/lq nameplate off` note, and `/commands/` now
describes that verb as showing or hiding the text display above your head.

Worth noting for future releases, since it is the failure mode this file already warns about: the
site's settings page is generated from the `.comment(...)` strings, so **a stale config comment
becomes a stale public page**. The `/skill-packs/` page happened to escape the parallel
`SKILL-PACKS.md` drift only because it was built by counting `LQEffects.java` instead.

### ⚠ Two counts in this file were wrong — corrected below from source

Both were caught by counting off the source as this file instructs, and the site uses the corrected
numbers:

| | This file said | Source says | Counted from |
|---|---:|---:|---|
| Config settings | 24 | **25** | `.define*(` in `LQConfig.java` |
| Built-in effect types listed in `docs/SKILL-PACKS.md` | 11 | **14** | `SkillEffectTypes.register(` in `LQEffects.java` |

`docs/SKILL-PACKS.md` is missing `particle_line`, `projectile` and `run_command` from its built-in
list — worth fixing there, since it is the page a pack author reads first. `README.md` has all 14.

Also note `docs/store-copy.md` says "roughly 280 player-facing strings" where `Lang.java` has **318**;
the site uses 318.

---

## Original brief (kept for reference)

LegendQuest ReForged **2.0.1** is published and live:

- **CurseForge approved 2026-08-20** and already has real downloads.
- GitHub releases `v2.0.0` and `v2.0.1` are public, `v2.0.1` marked Latest.
- Modrinth: the publish workflow exists and is ready, but **the project has not been created**.

### The section URL is a free choice — unlike ZombieMod's

Nothing published points at a site URL yet. The jar's `displayURL` and `issueTrackerURL` both point
at GitHub, and the CurseForge description links GitHub too. So there are no links in the wild to
break, and `/legendquest-reforged/` is a recommendation for consistency rather than a constraint.

**But the jar's `displayURL` cannot be changed retroactively** — 2.0.0 and 2.0.1 are out with GitHub
in the mods-list Homepage button. Point it at the site in the next release once the pages exist.

### Canonical outbound links

| | |
|---|---|
| CurseForge | `https://www.curseforge.com/minecraft/mc-mods/legendquest-reforged` — **live** |
| GitHub | `https://github.com/Sablednah/LegendQuest-ReForged` |
| Latest release | `https://github.com/Sablednah/LegendQuest-ReForged/releases/latest` |
| Direct jar | the `legendquest-2.0.1.jar` asset on that release |
| Genre packs | the `legendquest-apocalypse.zip` / `legendquest-scifi.zip` assets on that release |
| Example skill pack | the `examplepack-1.0.0.jar` asset, and `https://github.com/Sablednah/LegendQuest-SkillPack-Example` |
| Modrinth | not created yet — leave the link out rather than pointing at a 404 |

## Where the site's facts come from

| Site page | Source in this repo |
|---|---|
| Landing — what it is, requirements, the pitch | `docs/store-copy.md` (written for CurseForge/Modrinth; it is the same pitch, already edited) |
| Content authoring — every field of a race/class/skill/feat | `README.md`, the schema reference section |
| Settings reference | `src/main/java/com/sablednah/legendquest/LQConfig.java` — the `.comment(...)` strings ARE the documentation |
| Commands reference | `src/main/java/com/sablednah/legendquest/neoforge/LQCommands.java` |
| The genre packs | `packs/apocalypse/README.md`, `packs/scifi/README.md` |
| Skill-pack mods | `docs/SKILL-PACKS.md` |
| Vocabulary / re-theming | `src/main/java/com/sablednah/legendquest/neoforge/Lang.java` — the `def("term.*")` block |
| What is deliberately not built | `PORTING.md`, "Deferred" section |
| Release history | `CHANGELOG.md` |
| Artwork | `docs/branding/` — see below |

**Do not copy the schema reference into a new markdown file.** It is the largest section of
`README.md`, it is current, and a second copy starts drifting on the first new field. Build the page
from it and treat `README.md` as upstream — the same call ZombieMod made for its genus reference.

## ⚠ The screenshots are NOT in this repo

33 screenshots exist locally at `screenshots/` but the folder is **gitignored on purpose** (34MB of
PNGs that only ever get dragged into a store listing). A clone will not have them.

**`docs/store-copy.md` IS tracked**, and contains the full upload-ordered table: filename, what each
shot is, and a written caption for each. Use it as the manifest and **ask Sable for the image files
directly**.

The two that matter most, and they must be used *as a pair, adjacent*:

- `fantasy-handbook-races-dwarf.png`
- `wasteland-guide-archetypes-athlete.png`

Same screen, one stock and one under a genre pack — "Races" becomes "Archetypes", "Mana" becomes
"Stamina". The vocabulary system is the hardest feature to explain in prose and the easiest to show,
and that pairing does the explaining by itself. Do not separate them.

Also note `vanilla-client-character-sheet.png` needs its caption to say *unmodded client* explicitly.
A plain vanilla HUD looks like nothing special unless the reader is told that is the entire point.

Artwork: `docs/branding/legendquest-reforged-white.png` (wordmark, for dark backgrounds),
`-black.png` (light backgrounds), and `legendquest-reforged-white (850px).png` (already sized to
CurseForge's 850px description limit, so likely the right one for a banner).

## Proposed page tree

Six pages, split by *what the reader is trying to do*. A server owner evaluating the mod and a
content author writing a class are almost never the same visit.

| Path | For | Source |
|---|---|---|
| `/legendquest-reforged/` | Landing. What it is, requirements, install, the four headline features, links out. | `docs/store-copy.md` |
| `/legendquest-reforged/content/` | **Write your own races, classes, skills and feats.** Every field, both JSON and YAML, with worked examples. The big one. | `README.md` |
| `/legendquest-reforged/packs/` | The Wasteland and Cold Frontier — what is in each, how to install, how to re-theme the vocabulary. | `packs/*/README.md` |
| `/legendquest-reforged/skill-packs/` | **For mod developers.** Adding new skill effect types from your own jar. | `docs/SKILL-PACKS.md` |
| `/legendquest-reforged/settings/` | Every setting in `legendquest-common.toml`. | `LQConfig.java` |
| `/legendquest-reforged/commands/` | Every command, and the permission nodes. | `LQCommands.java` |

### Keep version facts on the landing page only

`/content/`, `/packs/`, `/skill-packs/`, `/settings/` and `/commands/` should be **version-agnostic**
so a release does not invalidate them. Make the landing hero tags version-agnostic too ("10 races,
9 classes", "NeoForge", "MIT", "No client mod required"), as CityWorld's now are — then a release
that does not move the support matrix needs no site edit at all.

Requirements block for the landing page:

> **Requirements**
>
> | Minecraft | NeoForge | Java |
> |---|---|---|
> | 1.21.11 | 21.11.42+ | 21 |
>
> Install on the server. Your players do not need the mod — they can join on a stock client from the
> Mojang launcher and play the whole game. Installing it client-side too is optional and adds the
> handbook, character panel and HUD; people with and without it play together on the same server.

## ⚠ Correction issued 2026-08-20 — the `[nameplate]` setting text

The `enabled` comment in `LQConfig.java` described a **scoreboard team**. That
implementation was replaced by a **text display entity** before release, and the config comment was
not updated with it — so the site's `/settings/` page faithfully reproduced a stale description, as
did the generated TOML on every server.

Fixed in the config. The corrected text: it is a text display entity above the player, an unmodded
client renders it because it is an ordinary vanilla entity, it occupies only the space above the
head — chat, tab list and `/list` are untouched — and it does **not** use the scoreboard team slot,
so it does **not** compete with LuckPerms, FTB Ranks or chat-prefix mods.

**The old text said the opposite of the truth on the one point a server owner would act on.** Anyone
who read it would have left the feature off to protect their ranks, for a conflict that does not
exist. Rebuild `/settings/` from the current `LQConfig.java`.

## Five things the site should be careful to get right

These are the points where a casual summary would say something false.

1. **"Server-side only" is the wrong phrase.** The jar has an optional client half. The promise that
   survives — and has now been tested twice with a genuinely unmodified client from the Mojang
   launcher against a dedicated server — is that **players do not need it**. Say that.

2. **The genre packs are NOT on CurseForge.** A CurseForge *mod* project accepts `.jar`/`.litemod`
   only; both pack ZIPs were uploaded and killed in moderation. They ship from the **GitHub
   release**. If the packs page does not say this clearly, readers on CurseForge will conclude the
   settings do not exist. Link `/releases/latest`.

3. **Content is frozen at world load. `/reload` does not apply it.** Edit a race/class/skill and you
   must restart the server — this matches vanilla's own data-driven registries, and ops get a chat
   notice explaining it. This was a real "the feature doesn't work" report during development and is
   the single most likely support question. Say it on `/content/` and `/settings/`.

4. **A skill-pack jar must be installed on the server AND on every modded client.** Miss a client and
   it fails registry sync with *"Unknown skill effect type"*. Vanilla clients are unaffected. This
   belongs on `/skill-packs/` in bold.

5. **Two datapack traps, both silent.** In a pack ZIP, `pack.mcmeta` and `data/` must sit at the
   **zip root** — wrap them in a folder and Minecraft ignores the pack with no error and no log line.
   And in gear-proficiency fields, a tag is only legal in the **bare string form**
   (`"allowed_weapons": "#legendquest:fighter_weapons"`); a tag inside a JSON list fails registry
   load. The README example was wrong about this once.

## Four hooks for the landing page

The things nothing else does:

- **Vanilla clients can join and play.** Not "mostly" — the whole game, through chat and the action
  bar. Lead with it.
- **Re-theme every word.** ~318 message keys, 20 of them vocabulary terms. Call races "Archetypes",
  mana "Stamina", karma "Humanity" — handbook, HUD, commands and errors all follow, and modded
  clients receive the server's vocabulary on login.
- **Two complete settings included**, not samples: The Wasteland and Cold Frontier.
- **Other mods can add skills.** `SkillEffectTypes.register()` in your mod constructor is the entire
  API surface.

## The numbers, and where to get them

Correct as of 2026-08-20, counted off the source:

| | | Where it comes from |
|---|---:|---|
| Built-in races | **10** | `data/legendquest/legendquest/race/*.json` |
| Built-in classes | **9** | `.../class/*.json` |
| Built-in skills | **26** | `.../skill/*.json` |
| Built-in feats | **13** | `.../feat/*.json` |
| Skill effect types | **14** | `SkillEffectTypes.register(` calls in `LQEffects.java` |
| Skill types | **3** | `SkillType.java` — active, passive, triggered |
| Combat triggers | **3** | `TriggerSpec.java` — melee hit, hurt, kill |
| Message keys | **318** | `def("` in `Lang.java` |
| Vocabulary terms | **20** | `def("term.` in `Lang.java` |
| Config settings | **25** in 7 sections | `.define*(` in `LQConfig.java` |
| **The Wasteland** | 8 Archetypes, 20 Roles, 61 skills, 10 Perks | `packs/apocalypse/data/lq_apoc/legendquest/` |
| **Cold Frontier** | 8 Species, 16 Professions, 55 skills, 10 Augments | `packs/scifi/data/lq_scifi/legendquest/` |

**Count these off the source, never off prose.** Every count in a hand-written doc goes wrong
silently, and nothing about a wrong count looks wrong. ZombieMod's had drifted in four places.

Two caveats for the content page: the 10 races include **Undecided** and the 9 classes include
**Citizen**, which are the "until you choose" defaults rather than things you pick — so "10 races to
play" overstates it. And the built-in skills are all registered but only *granted* ones are visible
to a player, so a genre pack showing 61 skills is not adding to the 26.

## Known limitations, worth stating honestly

- Mining XP can be farmed by placing and re-breaking a block. The values are tuned low enough to make
  it a poor use of time, but there is no anti-farm machinery.
- After a race/class change that lowers max mana, current mana is not clamped to the new maximum for
  one regen tick. Cosmetic.

## Checklist for future releases

1. Update the requirements block **only if** the supported version matrix changed.
2. Add anything genuinely new and player-visible.
3. If a race/class/skill/feat was added to the built-in set, update the counts table.
4. If a **skill effect type** was added, update `/content/` and `/skill-packs/` from `README.md`.
5. If a config setting or command changed, update that page — otherwise leave the child pages alone.
6. When Modrinth goes live, add it to the links table on the landing page.

**⚠ Deployment is not visibility: Cloudflare must be purged** before changes reach real visitors. Do
not conclude from a fetch that a deploy failed.
