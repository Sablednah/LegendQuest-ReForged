# Building LegendQuest for more than one Minecraft version

Minecraft moved to calendar versioning with quarterly drops. Supporting 1.21.11,
26.1 and 26.2 is therefore not a port to be finished — it is a treadmill to be
made cheap. This is the plan for making it cheap, and the evidence it rests on.

**Status: nothing built yet.** The numbers below for 1.21.11 are measured; the
26.x costs are *predicted* from CityWorld's experience and from counting our own
call sites. Nobody has compiled this mod against 26.1 yet, and until somebody
has, every estimate here should be read as a hypothesis.

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
4. **CI matrix**, modelled on CityWorld's `selftest.yml`, so a version cannot
   silently rot between drops.
5. **Release plumbing** — jar naming, and CurseForge/Modrinth uploads that
   declare the right game versions per file.
6. **Revisit the steady state after 26.3** (~Sept 2026), with three drops of
   evidence instead of nought.

## What would change the recommendation

- If step 1 shows the 26.1 delta is genuinely trivial (single figures) **and**
  confined to `client/`, skip straight to the single tree with a compat source
  set — the branch overhead would cost more than it saves.
- If step 2 shows 26.2 breaks the networking or registry layers rather than just
  the screens, the shim boundary is wrong and branch-per-version should stay
  until the shape is clearer.
