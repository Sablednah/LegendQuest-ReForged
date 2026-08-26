# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## What this is

**LegendQuest ReForged** — an RPG mod for NeoForge: races, classes, skills, feats,
karma, parties and levelling. It began as a Bukkit plugin; this is that *design*
rebuilt for modern NeoForge, not a line-by-line port. Nothing is shared with the
1.x plugin.

**The mod is server-authoritative.** A NeoForge server is required; clients are
optional. **Vanilla clients can join and play the whole game** through chat, the
action bar and vanilla-visible entities. A modded client adds the handbook,
character panel and HUD on top. See "Design principles".

## Versions — one branch per Minecraft version

Minecraft is on quarterly calendar versioning, so this is a treadmill, not a
port. **Branch per version** is the decided mechanism across all of Sable's
mods (CityWorld and ZombieMod reached it independently).

| Branch | Minecraft | NeoForge | Java | ModDevGradle |
|---|---|---|---|---|
| `main` | 1.21.11 | 21.11.42 | 21 | 2.0.141 |
| `mc26.1` | 26.1.2 | 26.1.2.95 | **25** | 2.0.141 |
| `mc26.2` | 26.2 | 26.2.0.59 | **25** | 2.0.144 |

- **Docs, workflows and scripts live on `main` only.** Version branches carry
  code, so a write-up never has to be merged three ways. `docs/VERSIONS.md` is
  the full record — read it before touching version work.
- **Port forwards, not sideways.** `main` → `mc26.1` → `mc26.2`. 26.2 contains
  the whole 26.1 rendering change, so cherry-picking the 26.1 fix commit cleared
  36 of 26.2's 53 errors. Each drop then pays only its own delta.
- Jars carry the target (`legendquest-2.2.0+mc26.2.jar`); the version inside
  `neoforge.mods.toml` stays a plain `2.2.0`. Three identically-named files in a
  mods folder are indistinguishable.
- Java 25 is not optional on 26.x — it ships `java-runtime-epsilon` to players.
  ModDevGradle moves independently of the game version.

## Build & run

**There is no system Java.** Set this every time, *before* the gradle command:

```bash
export JAVA_HOME=/home/sable/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2

./gradlew compileJava   # fast inner loop
./gradlew build         # -> build/libs/legendquest-<version>.jar
./build-packs.sh        # -> build/packs/*.zip (the genre datapacks)
```

- Never report success from a command that prints it unconditionally. Use
  `if ./gradlew build -q 2>&1 | grep -iE "error:|BUILD FAIL"; then ...` — an
  `echo "OK"` after a build announces success over a real failure.
- Versions and metadata live in `gradle.properties` and expand into
  `src/main/templates/META-INF/neoforge.mods.toml` at build time. **Edit the
  template, never a generated `mods.toml`.**

### Mod-list artwork (26.2 reshaped this)

Three keys, all declared on every branch; older loaders ignore what they don't
know. `iconFile` = the small **square** beside the name (26.2+; without it our
row is the only one with no icon). `bannerFile` = the wide info-panel image
(26.2+). `logoFile` = what 1.21.x and 26.1 show, and 26.2's fallback.

`legendquest-icon.png` is the wordmark **padded** to a square, not resampled.
Replacing it with bespoke square art needs no other change.

## Three worktrees, and the test loop

| Path | Purpose |
|---|---|
| `LegendQuest-ReForged` | jar builds; `main` |
| `LegendQuest-ReForged-srv` | WSL dev server (`./gradlew runServer`) |
| `LegendQuest-ReForged-buddy` | Windows TestBuddy client (`TestClient.cmd`) |

Worktrees are **detached** — advance with `git checkout --detach <sha>`, not
`git checkout main` (main is held by the primary worktree).

- **RCON is port 25576**, password `lqdev`. **25575 is Standards** — check
  `ss -ltnp | grep 2557` before assuming a port is yours, and never kill a
  process without checking whose it is.
- Drive the server with a small RCON client; `stop` shuts it down. Launch the
  buddy with `TestClient.cmd < nul` (the trailing `pause` blocks otherwise). It
  auto-joins; wait for "TestBuddy joined the game" in the server log.
- Kill the buddy via PowerShell `Get-CimInstance Win32_Process` filtered on
  `*runBuddy*` — plain `pgrep` cannot see it.
- `TestClient.cmd` silences every sound category before launching. Leave that
  in: the buddy is driven from a script while its owner may be on a call.
- **The dev server is a clean default-D&D baseline.** If you add a genre pack to
  test something, remove it afterwards.

### NEVER copy a jar into a running instance

Windows does not lock it, so the copy silently succeeds — and the live JVM then
dies the moment it lazily loads a class it had not already touched
(`NoClassDefFoundError` ← `ZipException: invalid LOC header`, with a clean jar
on disk). Always confirm nothing is running first:

```bash
powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process | \
  Where-Object { \$_.Name -like 'java*' } | ForEach-Object { \
  [regex]::Match(\$_.CommandLine,'Instances\\\\([^\\\\\"]+)').Groups[1].Value }"
```

## Design principles

Two standing requirements, not preferences to trade off:

**Vanilla first, modded as sugar.** Can an unmodded client use the whole
feature? That question settles build-vs-adopt arguments and shapes every
feature. The nameplate is a `text_display` entity partly for this reason; party
chat is plain chat rather than a GUI panel for the same reason. It has also
turned out to be a *portability* strategy — the client is where version churn
concentrates, and it is the part players can do without.

**"Don't make me think."** Clear feedback at the moment it is needed; never make
anyone decode what a thing did. A player cannot read the source — everything
they know, they know from what the game told them at that moment.

- Explain at the moment of the change, not in documentation. Switching class
  drops level, health, title and skills at once and looks like a wiped
  character, so the game says the old class is kept *as the bar drops*.
- **Alarming-and-harmless is the worst combination.** Say so immediately and
  name the way back.
- A message that names the remedy beats one that names the problem.
- **Never ship output that needs decoding** — `§7CHR: §f14` in a console is
  literally that. Colour codes become real component styles, never section signs
  in the text.
- **A visible correction is itself a defect.** Repair before the client is told,
  not after.

## Content is data

Races, classes, skills and feats are **datapack registries** fed from YAML.
Genre packs (`packs/apocalypse`, `packs/scifi`) declare `min_format: 82` /
`max_format: 999` and ride version bumps untouched. This is the single biggest
reason version drops are cheaper here than for a mod that names blocks.

- **Titles live in pack data**, so "update the jar" does not update ranks —
  say so in release notes whenever packs change.
- All player-facing wording is in `Lang.java`, generated into
  `config/legendquest/messages.yml`. `messages.yml` only pins keys it actually
  contains, so new defaults reach existing servers.

## Standards integration (optional)

`ChatSupport` is the **only** class importing `com.sablednah.standards`, and the
`ModList.isLoaded("standards")` guard sits outside it in `LegendQuest.java` —
naming the class is what loads it, so an unguarded call is a
`NoClassDefFoundError` everywhere Standards is absent.

- Class titles and karma epithets decorate chat via `NameDecorator`.
- Party chat routes through their `ChatRouter`, so a muted player cannot talk to
  their party. **Priority runs opposite to `NameDecorator`**: routers are
  higher-wins (first claim ends it, one destination); decorators are
  higher-means-nearer-the-name (additive). Easy to "fix" wrongly.
- Without Standards, `PartyChat.onChat` handles capture itself. Exactly one path
  is ever live.

## Known traps

- **`/party` collides with FTB Teams**, which registers the same literal and
  gates it on "officer" rank. `/lq party ...` is unambiguous. Not fixed — FTB
  Teams is being dropped for Faction-ReForged.
- **Transient attribute modifiers do not survive respawn.** Repair on
  `PlayerEvent.Clone` (before the respawn packet), never `PlayerRespawnEvent`
  (after it, so the client draws the wrong value first).
- **`doImmediateRespawn` is the wrong death to test with** — it takes a
  different path from clicking the button, and reported a real bug as absent.
- **After a rewrite, grep for the name of the thing you removed**, not the thing
  you added. Three stale comments this way in one week, two found by other
  people reading our source.
- **A count and the thing counted are different questions.** "36 errors"
  overstated distinct problems fourfold; `head -30` on a 36-line list under-
  reported a figure that was then quoted.

## Releasing

`CHANGELOG.md`, tag, then a GitHub release — publishing it fires the CurseForge
and Modrinth workflows. Modrinth skips cleanly until a project ID and token
exist (still not created).

- **CurseForge rejects non-jar files** *after* returning HTTP 200. Packs ship
  from GitHub only.
- **A 200 is acceptance, not publication.** Check the file is approved.
- CurseForge's changelog sanitiser 500s on some Markdown — blockquotes, indented
  code blocks with pipes, angle-bracket autolinks. Keep release notes to plain
  paragraphs, lists and simple tables.
- Screenshots stay out of git.
