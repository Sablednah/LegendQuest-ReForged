# Parked ideas

Things wanted but not built, written down at the moment they were thought of so
they are not lost to a chat log. Nothing here is committed work, and nothing
here has a release attached to it.

Each entry says what the idea *is*, and — where it is already known — what would
make it hard, so the next person picking it up starts from the real problem
rather than rediscovering it.

---


## A dice tray

**Wanted:** a screen with a pool of dice down the left that can be dragged into
a tray, a way to click a stat to roll against, and a Roll button that throws
what is in the tray and shows the result. Physical dice, handled, rather than a
command typed.

**Where it sits.** `/roll` already does the mechanics — `Dice` parses tabletop
notation, rolls it, and reports every die rather than just the total. The tray
is a *control surface* over that, exactly as the action buttons are over `/st`.
It should therefore drive the same code and produce the same broadcast, so a
tray roll and a typed roll are indistinguishable to everybody else at the table.

**Vanilla-first still holds.** The tray is a modded-client luxury. `/roll`
remains the whole feature for an unmodded client, and the tray must never be the
only way to reach something.

**The natural home is Standards' inventory-panel seam**, the one `CharacterPane`
already uses — a panel beside the inventory rather than a fullscreen GUI, so it
coexists with everything else that wants that space instead of fighting it.

### On the 3D dice, since that is the part that sounds impossible

It is feasible, and the way to make it feasible is to stop thinking of it as a
physics problem.

- **The server decides the result. The animation lands on it.** `Dice.roll`
  produces the number; the client then plays a tumble that finishes with that
  face up. Every digital dice roller works this way, and it is the only version
  that keeps the roll authoritative — which matters, because `/roll` is
  broadcast to the whole server and a client-decided number would be a client
  deciding what everyone sees.
- **A tumble is a canned animation, not a simulation.** Spin the model on two
  axes, decay the rotation, and end on the transform that shows the right face.
  No collision, no bounce, no rigid-body maths, and no risk of a die settling on
  an edge and hanging the screen.
- **Rendering the die is ordinary.** A d20 is twenty triangles; a GUI screen can
  draw arbitrary geometry through the matrix stack. The work is in the art and
  the easing, not the graphics.
- **Real physics is the trap.** Dice that actually bounce around a tray need a
  physics step, a fixed timestep, and a rule for what happens when one lands
  cocked — a great deal of work to reach the same number the server already
  chose. If it is ever wanted, it should be wanted for its own sake and costed
  as its own feature.
- **Keep it in one class.** 26.x reworked GUI rendering wholesale; anything that
  draws belongs where a version drop finds it in one file. Standards ported its
  entire client half by touching one file for that reason.

### Open questions

- Does the pool hold the dice a character *has*, or every die? A pool that is
  just a palette is simpler; a pool that means something is a new mechanic.
- Does a tray roll broadcast like `/roll` does, or only to a party? Broadcast is
  the honest default, since the point of rolling in the open is being seen.
- What does clicking a stat do to dice already in the tray — add the modifier,
  or replace the tray with `d20 + modifier`?

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
  reusing that output, or the dice tray above if it gets built.
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
