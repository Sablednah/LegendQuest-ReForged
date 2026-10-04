# Parked ideas

Things wanted but not built, written down at the moment they were thought of so
they are not lost to a chat log. Nothing here is committed work, and nothing
here has a release attached to it.

Each entry says what the idea *is*, and — where it is already known — what would
make it hard, so the next person picking it up starts from the real problem
rather than rediscovering it.

---


## A roguelike dungeon for the fantasy setting

*Sable, 2026-10-03.*

**Wanted:** a dungeon builder in the spirit of Dungeon Crawl and the Android
roguelike Pixel Dungeon, played as live Minecraft but run like a HeroQuest or
D&D table:

- **Traps in rooms.** Each character makes a **perception check** as they enter,
  and whoever passes *sees* the trap. If it is triggered, a **dwarf can disarm**
  it.
- **The dice are shown.** The game rolls for you, on screen, so it feels like a
  table game played live rather than a hidden number.
- **Actions you choose:** search the room, loot a corpse, and so on, each one a
  roll.
- **StoryTeller integration:** a Storyteller can "pause" the dungeon for a
  narrative beat, take over its monsters, add more, and use dungeon-specific
  bonus presets.

**What already exists to build on.**

- The d20 core is in `core/Mechanics`. A perception check is `skillTest` against
  a WIS modifier, and a disarm is a DEX check with a dwarf bonus: no new
  mechanics, just new callers.
- `/roll` already reports every die and broadcasts it, so "show the dice" is
  reusing that output, or the dice tray (built: the die tab on the character pane).
- StoryTeller already possesses and moves creatures. Cast already owns NPC
  bodies. CityWorld already generates structures from schematics, and its
  author has generators to learn from.

**Decided (Sable, 2026-10-03).**

- **Its own mod, and it works without LegendQuest.** Standalone, it is a
  roguelike dungeon. With LegendQuest installed it gains the character layer:
  perception and disarm rolls against real stats, race and class abilities,
  shown dice. With StoryTeller it gains a games master. That is the same
  inward-pointing seam LegendQuest already uses for Simple Voice Chat
  (`PartyVoice` / `VoiceSupport`): the dungeon defines a small API, and each
  optional mod plugs into it. The dungeon never imports them directly.
- **Interoperability is the goal**, not a LegendQuest feature: other RPG mods
  should be able to plug into the same seam.
- **Why it is worth doing:** there is no good roguelike dungeon crawler for
  26.x.
- **The generator is CityWorld's lot planner at a different scale.** Roads
  become corridors and lots become rooms. CityWorld already plans and joins a
  procedural layout reliably, which is the hardest part of a dungeon generator.

**What would make it hard.**

- **It is not a genre pack.** Packs are data, and this needs code: trap
  logic, room state, actions. Hence its own mod.
- **"Only the people who passed see the trap"** has to work on a vanilla
  client. The server can send one player a block change or a glowing marker
  that nobody else receives, so it is possible, but every such fake has to be
  undone, survive chunk reloads, and stay consistent. A shared, visible "you
  spotted it" marker is far simpler and may be enough.
- **Reusing CityWorld's planner takes work.** It was built for city blocks:
  wide roads, big lots, open sky. Corridors are narrow, rooms are small and
  enclosed, and a dungeon must always be completable and scale with party
  level, which a city never had to be. Whether that is a CityWorld API, a
  shared library, or a copy adapted for the dungeon is the first decision,
  and it belongs with the CityWorld session.
- **The rolls need to read well live.** A roll that resolves before anyone has
  seen it is a hidden number again. The design choice is how long the game
  waits for the dice: a beat, not a pause.

---

## A derelict-ship mission mode for the sci-fi setting

*Sable, 2026-10-03.* Space Hulk is the inspiration, not the name. **Its own
mod** (decided the same day), built on the same generator as the dungeon.

**Working name: "Space Husk"** (Sable, 2026-10-04). It ties the derelict to
Minecraft's own husk, the desert zombie, so the name points at the dried-out
undead crew rather than at Space Hulk. The creatures are still our own designs,
but a husk-flavoured crew that died aboard is now an obvious place to start.

**Wanted:** the party starts aboard a ship. Taking the party to the **docking
bay** flies them to a **randomly generated derelict**. In practice that is a
teleport to a freshly generated place where the hulk is built on the fly. There
they explore corridors, complete a **random mission**, and fight off aliens that
are definitely *not* xenomorphs or genestealers. The creatures want designs of
our own, not borrowed ones.

**What already exists to build on.** Parties already move together
(`/party tp`) and share XP. The sci-fi pack already supplies the species, the
professions, the vocabulary and the ranks. CityWorld generates corridors and
rooms from schematics.

**What would make it hard.**

- **New worlds cannot be added while the server runs.** Vanilla and NeoForge
  both fix the list of dimensions at start-up. The workable shape is **one
  "derelicts" dimension declared up front**, with each mission given its own
  far-apart plot and wiped afterwards. To the players it is a new hulk every
  time.
- **Clean-up is the real work.** A plot has to be reset, its entities removed and
  stragglers sent home when a party wipes, quits or disconnects mid-mission. A
  party that logs out inside a hulk has to come back to somewhere that still
  makes sense.
- **Corridors on the fly:** CityWorld's lot planner again, as for the
  dungeon, with tighter geometry: corridors for roads, compartments for lots.
  The two mods should share that generator rather than each growing its own.
- **Missions need a vocabulary.** Reach a point, retrieve an item, hold a room
  for a time, escort an NPC (Cast). A small fixed set, combined at random, is
  the cheap first version.

**Shared with the dungeon above:** a room-and-corridor generator, temporary
instanced space, trap and objective state, and StoryTeller as the
"games master". Building one well builds most of the other.

---

## A Doom WAD to Minecraft structure library

*Sable, 2026-10-04.* For the sci-fi setting, but **its own standalone library
mod**: useful to anybody on its own, and used by Sable's sci-fi pack.

**Wanted:** read a Doom WAD file and build its level as a Minecraft structure.

- **A command** spawns a map from a file, either at the player's position or
  where they are looking, turned to face the way the player is facing.
- **API calls** do the same from code, so the sci-fi pack (and Space Husk above)
  can place a level without going through a command.

**Why it maps better than it sounds.** Doom levels are 2.5D: a floor plan of
*sectors*, each with one floor height and one ceiling height. That is a
heightmap with a roof, which is exactly what blocks are good at. Walls come from
*linedefs* and *sidedefs*, and what is in the level (enemies, pickups, the player
start) comes from *things*. There are no true rooms over rooms, so nothing has to
be guessed about overlapping space.

**What would make it hard.**

- **Licensing, before any code.** The commercial game data (`doom.wad`,
  `doom2.wad`) cannot be shipped or bundled. The library has to read files the
  user supplies, and anything shipped with the pack should come from
  **Freedoom** (BSD-licensed) or from maps whose authors allow it.
- **Scale.** The player is 56 Doom units tall and 1.8 blocks in Minecraft, so
  about 32 units to a block is the natural scale. Doom's narrow ledges and thin
  steps then round to nothing or to a full block, so the scale wants to be
  adjustable.
- **Textures to blocks.** Doom names its wall and floor textures (`STARTAN3`,
  `NUKAGE1`). A data-driven table from texture name to block, with a sensible
  fallback, keeps the look editable without code. Packs could supply their own
  table.
- **Rotation.** Minecraft structures turn in quarter steps, so "facing the way
  the player looks" means snapping to the nearest of the four directions.
- **Size.** A large map is millions of blocks. Placing it all in one tick would
  stall the server, so it has to be built a slice per tick, with a progress
  message to the player.
- **What moves.** Doors, lifts and crushers are linedef specials. The cheap
  first version builds them in one position, standing still. Working doors are a
  separate, later job.
- **Things to Minecraft.** Mapping Doom's monsters and pickups to mobs, chests
  and spawners is a table again, like textures. A first version can just leave
  them out.
- **Format variants.** Vanilla Doom's binary map lumps are the base to support.
  Hexen-format and UDMF (text) maps come later, if at all.

**Test files are already in hand** (Sable, 2026-10-04): the shareware
`doom1.wad`, so E1M1 and the rest of episode 1, plus several open-source WADs.
E1M1 is the natural first target, because everybody knows what it should look
like, so a wrong build is obvious at a glance. Using the shareware WAD to *test*
is fine; shipping it inside a jar or pack is a separate question, and the
answer above still stands: ship Freedoom or permissively licensed maps.

**Fits with Space Husk.** Derelict decks could be hand-made Doom-style maps,
built by this library, instead of (or alongside) layouts from CityWorld's
planner.
