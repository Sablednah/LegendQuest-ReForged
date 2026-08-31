# Building LegendQuest for more than one Minecraft version

Minecraft moved to calendar versioning with quarterly drops. Supporting 1.21.11,
26.1 and 26.2 is therefore not a port to be finished — it is a treadmill to be
made cheap. This is the plan for making it cheap, and the evidence it rests on.

**Status: 26.1 and 26.2 both measured (2026-08-25).** See "The measured deltas"
below — that section is the point of this document, and it corrected the
prediction the rest of it was built on.

## The targets

| | Minecraft | NeoForge | Java | ModDevGradle |
|---|---|---|---|---|
| today | 1.21.11 | 21.11.42 | 21 | 2.0.141 |
| next | 26.1.2 | 26.1.2.95 | **25** | 2.0.141 |
| next | 26.2 | 26.2.0.59 | **25** | 2.0.144 |

The Java bump is not optional: 26.1 ships the `java-runtime-epsilon` JRE to
players, so a mod targeting 21 is targeting a runtime nobody has.

## What CityWorld already proved

CityWorld crossed all three first, with a much harder problem — it names
hundreds of blocks and items directly. Worth reading `PORTING.md` in that repo
in full; the load-bearing findings are:

- **A quarterly drop is not reliably cheap.** 26.1 cost 12 lines across 6 files.
  26.2 cost a block-declaration model rewrite. Planning for "a few lines every
  quarter" would have been the wrong lesson from the first data point.
- **What saved them was not the build setup.** It was two design decisions made
  earlier: a `compat/` seam (only 16% of their files touch `net.minecraft`), and
  *generating* `Material.java` rather than hand-writing it. 26.2 broke 145 of its
  691 constants; the repair was rules in one Python file, and none of the 3,096
  call sites moved.
- **Data beat code.** Their building palettes had moved from compiled constants
  to block tags, so 26.2 deleting 144 dyed-block fields did not touch them at all.
- **Branch per version, docs on one branch.** `master` = 1.21.11, `mc26.1`,
  `mc26.2`. CI builds all three from a matrix. Documentation lives only on
  `master`, so a write-up never has to be merged three ways.
- **The jar filename carries the target** (`cityworld-5.0.3+mc26.2.jar`) while
  the version inside `neoforge.mods.toml` stays a plain `5.0.3`. Three files
  with the same name in a mods folder are indistinguishable.
- **Their own recommendation is to wait.** Keep branch-per-version for one more
  drop before merging to a single tree, because merging is cheap now and cheap
  later, and guessing wrong about the mechanism is not.

## How LegendQuest differs

Our shape is close to the inverse of CityWorld's, and it cuts both ways.

| | CityWorld | LegendQuest |
|---|---|---|
| Java files | 388 | 71 |
| touch `net.minecraft` | 62 (16%) | **49 (69%)** |
| a big table of block/item constants | yes, generated | **no** |
| content lives in | code + tags | **datapack registries and YAML** |

**Bad news first: proportionally we are far more exposed.** There is no large
pure-logic core here to protect behind a seam. LegendQuest *is* integration —
commands, events, attributes, entities, networking, screens.

**But the absolute surface is small** (49 files, not 349), and the two defences
that carried CityWorld are already in place in the form that matters to us:

- **There is no `Material.java` to break.** We name almost no blocks or items.
  The single biggest source of 26.2 breakage in CityWorld simply does not exist
  here.
- **Our content is already data.** Races, classes, skills and feats are datapack
  registries fed from YAML. The genre packs declare `min_format: 82` and
  `max_format: 999`, so they should ride version bumps untouched — the same win
  CityWorld got from tag-backed palettes, except ours covers the whole content
  layer rather than eight palettes.

So the risk is concentrated in code, not content, and within code it is
concentrated further:

| Surface | Files | Expected volatility |
|---|---|---|
| `client/` — handbook, character panel, HUD | 6 | **highest** — 26.2 already moved `Minecraft.setScreen` to `minecraft.gui.setScreen`; we have 5 call sites |
| networking payloads + codecs | ~6 | medium — codec and `RegistryFriendlyByteBuf` signatures move between drops |
| datapack registries, YAML front door | ~5 | medium — `DataPackRegistryEvent`, `packs.*` |
| entities/attributes/display | ~10 | medium — `TagValueInput` already changed shape once during 1.21.11 |
| commands, events, everything else | ~22 | low — Brigadier and the NeoForge event bus are stable |

Known concrete breaks today: **5 `setScreen` call sites** and **2 position
field accesses** of the `pos.x` → `pos.x()` kind that bit CityWorld.

## The measured deltas

Branch, set versions and toolchain from the table above, `compileJava`. Both
drops measured the same way:

| | 26.1 | 26.2 |
|---|---|---|
| `client/` errors | 25 | 37 |
| server errors | 11 | 16 |
| **total** | **36** | **53** |
| files touched | 10 | 12 |

**The same six server files break in both drops**, and no others:
`LQEffects`, `Feedback`, `Nameplate`, `LQServerEvents`, `Parties`,
`RestrictionEngine`. That stability is the most useful thing either
measurement produced — see "What this means for the mechanism".

### 26.1: the rendering pipeline moved

The raw count is the wrong number — the distinct API changes behind it are about
eight, and one accounts for 23 of the errors.

### The rendering pipeline moved, and that is the whole story

`GuiGraphics` is gone. 26.1 splits GUI drawing into extract-then-render:

| 1.21.11 | 26.1 |
|---|---|
| `GuiGraphics` | `GuiGraphicsExtractor` |
| `Renderable.render(GuiGraphics, int, int, float)` | `Renderable.extractRenderState(GuiGraphicsExtractor, int, int, float)` |

That single change produced 23 of the 36 errors and every one of them is in
`client/`. The good news is that the drawing surface came across nearly intact
— of 112 call sites, all but four map to a method that still exists:

| ours | 26.1 | sites |
|---|---|---|
| `fill` | `fill` | 87 |
| `drawString` | `text` | 59 |
| `renderItem` | `item` | 8 |
| `guiWidth` / `guiHeight` | unchanged | 10 |
| `enableScissor` / `disableScissor` | unchanged | 4 |
| `fillGradient` | `fillGradient` | 3 |
| `renderItemDecorations` | `itemDecorations` | 1 |
| `drawCenteredString` | `centeredText` | 1 |
| **`pose()`** | **no equivalent** | **4** |

The four `pose()` calls are one block in `CombatIndicators` —
`pushMatrix/translate/scale/popMatrix` around the floating damage numbers. The
extractor exposes no transform stack, so that is the one piece needing thought
rather than a rename.

### Outside the client, 26.1 is eleven ordinary moves

`displayClientMessage(Component, boolean)`, a relocated `BreakEvent`,
`SavedDataType<>` no longer inferring in `Parties`, `getTags()`,
`getProjectionMatrix(Integer)`, `getItemHolder()`, and four in `LQEffects`.

### 26.2: the same pipeline change, plus a holder split

26.2 carries the whole 26.1 rendering change *and* adds its own:

- **`Minecraft.setScreen` is gone** — 5 call sites, plus the `screen` field (4)
  and `hideGui` (2) moving with it. This is the change CityWorld hit.
- **`EntityType` split into `EntityType` and `EntityTypes`.** The constants moved
  to a holder class, `Blocks`-style, so `EntityType.LIGHTNING_BOLT` becomes
  `EntityTypes.LIGHTNING_BOLT`. Four sites — `Nameplate` spawning its display and
  `LQEffects` doing lightning and fireballs. Mechanical, but it is the same
  *class* of change that cost CityWorld its material table, arriving in the one
  place we do name vanilla content.
- **`ChatFormatting.isFormat()` is gone**, which lands squarely in the colour-code
  fix made earlier the same week. Worth noting as a reminder that new code is not
  safer code.

### What this changes about the plan

**The prediction was right about the location and wrong about the size.**
Divergence does concentrate in `client/` — 30 of 36 errors — but it is not "five
`setScreen` call sites". It is the signature of every render method in every
screen.

**That kills the compat-shim idea, at least for the client.** A
`src/compat/<version>/java` holding a few diverging methods works when the
divergence is a few methods. It cannot work when the divergence is the
*supertype signature* of five whole screens: there is no shared code left to
keep in the common tree.

**But it points at something better, and the mod's own design principle already
argues for it.** The client is optional sugar — vanilla clients play the whole
game — while the server is the part everyone runs.

## What this means for the mechanism

Two drops now agree on the thing that matters:

- **The client cannot be shared.** `Renderable`'s method signature changed, so
  every screen's override changed with it. There is no common code left to keep
  in a shared tree, which rules out a `src/compat/<version>/java` shim for
  `client/` — a shim holds the diverging *methods*, and here the divergence is
  the supertype.
- **The server can be.** 11 errors then 16, in **the same six files both times**,
  and every one of them a rename or a moved member rather than a reshaped API.
  That is precisely the "handful of diverging call sites" case a compat shim
  exists for.

So the steady state to aim at is **server-common with a small per-version compat
shim, and `client/` per-version**. That is not a compromise between the two
measurements; it is what both of them say, and it happens to fall along the line
the mod is already designed on — the seam between "what every server runs" and
"what a modded client adds".

**Not adopting it yet.** Two drops is two, the restructure is not free, and
branch-per-version costs nothing to keep while 26.3 arrives (~Sept 2026) to test
whether the six-file server set holds. If it does, restructure. If a third drop
reshapes something server-side, branch-per-version was the right answer all
along and the shim would have been a trap.

## Recommended mechanism

**Adopt branch-per-version now, exactly as CityWorld runs it. Decide the steady
state after one more drop.**

Not because it is elegant — it isn't — but because it is the only option that
does not require knowing our divergence set before we have measured it, and
because it is proven in this repo family already.

- `main` = 1.21.11, `mc26.1` = 26.1.2, `mc26.2` = 26.2
- Docs, workflows and scripts live on `main` only; version branches carry code
- Jars become `legendquest-2.2.0+mc26.2.jar`; `neoforge.mods.toml` keeps a plain
  `2.2.0`
- CI matrix over the three branches, building each on its own Java

**Where we land afterwards is probably a single tree**, because our expected
divergence is concentrated in one package rather than smeared across the
codebase. If the client screens are most of it, the shim boundary writes itself:
a shared tree plus `src/compat/<version>/java` holding only the diverging
methods, selected by a Gradle property. That is CityWorld's own recommendation
for themselves, and our shape suits it better than theirs does.

**Do not reach for a source preprocessor.** The divergences have no syntax valid
on both versions, which is what tempts people toward one; a preprocessor buys
that at the cost of source that no IDE understands and no compiler checks until
the substitution runs.

## The steps, in order

1. **Measure, before choosing anything.** Branch `mc26.1`, set the versions and
   toolchain from the table above, and compile. The count of errors is the
   number this whole document is currently guessing at.
2. **Repeat for 26.2** — expected to be the expensive one, on CityWorld's
   evidence.
3. **Check the content layer separately.** Boot each version with a genre pack
   and confirm the registries load. `max_format: 999` should hold, but "should"
   is doing work there and a boot proves it.
   **DONE for BOTH versions, apocalypse pack (2026-08-31)** — the unmodified
   pack was dropped into the `26.2` and `26.1.2` instances and its races and
   classes showed in game on each. The prediction held with **no pack edit of
   any kind**, across two different data formats (101 and 107). Verified
   statically alongside it: `min_format` / `max_format` are still the field
   names in 26.2's `PackFormat`, the data format runs 94 (1.21.11) → 101
   (26.1.2) → 107 (26.2), and every vanilla id the two packs name (88 items, 17
   effects, 12 entities, 8 attributes, 7 particles, 10 sounds) resolves on both.
   **Still unbooted: the sci-fi pack on either version** — same mcmeta, same
   schema, ids checked, so this is bookkeeping rather than risk.

   So the content layer costs nothing per drop, and that is now measured rather
   than hoped. The version treadmill is a **code** problem only.

   The `82` in `min_format` is load-bearing, not a round number.
   `PackFormat.lastPreMinorVersion(SERVER_DATA)` is **81** on both 26.1 and
   26.2, and declaring one above it is exactly what exempts a pack from the
   legacy `pack_format` / `supported_formats` fields. Lower `min_format` below
   82 and the pack is rejected until those old fields are added back.
4. **CI matrix**, modelled on CityWorld's `selftest.yml`, so a version cannot
   silently rot between drops.
5. **Release plumbing** — jar naming, and CurseForge/Modrinth uploads that
   declare the right game versions per file.
6. **Revisit the steady state after 26.3** (~Sept 2026), with three drops of
   evidence instead of nought.

## What would change the recommendation

**Answered for 26.1 (see above):** the delta was not trivial and the compat-shim
idea does not survive it. Branch-per-version stands.

Still open:

- If 26.2 also leaves the server side near-untouched, **server-common /
  client-per-version** becomes the serious candidate for the steady state, and it
  fits the mod's vanilla-first design rather than fighting it.
- If 26.2 breaks networking, registries or the datapack front door, the server
  side is not the stable core this measurement suggests, and branch-per-version
  is simply the answer rather than a holding position.

## A note on counting

The first number out of this exercise was "36 errors", and it was nearly the
number reported. It would have been misleading in both directions: 23 of the 36
are one class rename, and the 59 `drawString` calls that also need changing
produced *no* error at all, because the class they hang off had already failed
to resolve.

So the error count overstated the distinct problems by roughly four times, and
simultaneously understated the edit count by about half. Neither figure is the
cost. The cost is the eight API changes and the ~112 call sites they touch —
which is a day of mechanical work plus one genuine puzzle in `CombatIndicators`.

**And the first report of the 26.1 server figure was simply wrong** — "six errors
in four files", when it is eleven in six. The error list was read through
`head -30` on a 36-line output, and the visible part was reported as the whole.
The conclusion happened to survive; the number did not. Both halves of that are
worth remembering, because a truncated command is a much quieter way to get a
number wrong than a bad measurement is.
