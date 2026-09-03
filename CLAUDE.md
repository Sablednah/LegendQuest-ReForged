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

### Dev-server ports — one pair per project

Sable has five mods with a dev server, and they were nearly all on the vanilla
defaults: **four on game port 25565 and four on RCON 25575.** Whichever started
second lost, and the symptom is not obvious — the game port collision fails
loudly, but RCON just reports "Unable to initialise RCON" in the boot spam and
then every RCON command answers "auth failed", which reads like a wrong
password.

| Project | game | RCON |
|---|---|---|
| Standards | 25565 | 25575 |
| **LegendQuest** | **25566** | **25576** |
| ZombieMod | 25567 | 25577 |
| CityWorld | 25568 | 25578 |
| MobHealth | 25569 | 25579 |

RCON password is `lqdev`. The buddy client's connect address is in
`build.gradle`, so **it and `run/server.properties` have to agree** — `run/` is
gitignored, so a fresh checkout needs the port setting applied by hand.

**Before assuming a port is yours**, check who holds it — and never kill the
process that does without finding out whose it is:

```bash
ss -ltnp | grep -E '2556|2557'
tr '\0' ' ' < /proc/<pid>/cmdline | grep -oE 'modFolders=[^ ]*'
```

That last line is the one that matters: `fml.modFolders` names the repo, so it
tells you whether the server on your port is yours or another session's.
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

### Deploying to one instance, or to all of them

`./deploy.sh` builds the current branch and copies it to one instance, routed by
the jar's `+mc` tag. That is the mid-loop tool.

`./deploy-all.sh <dir-of-jars>` is the after-a-release tool: it updates **every**
instance that already has a LegendQuest jar, from a directory holding all three
tagged jars. It never installs the mod somewhere new.

**Route on the instance's own `gameVersion`, never its folder name.** Seven
instances carry LegendQuest and two of them (`MobHealth - Forge`, `Standards`)
are named after a mod rather than a version, so name-guessing gets both wrong.
It is in `minecraftinstance.json` — written **UTF-8 with BOM**, so read it as
`utf-8-sig` or it fails on the first character looking like a corrupt file.

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
reason version drops are cheaper here than for a mod that names blocks. **Proven,
not assumed** — the unmodified apocalypse pack booted on both 26.1.2 and 26.2
(2026-08-31, data formats 101 and 107); see `docs/VERSIONS.md` step 3. A version
drop is a code problem, not a content problem.

- **`min_format: 82` is load-bearing.** `lastPreMinorVersion(SERVER_DATA)` is 81
  on 26.1/26.2, and sitting one above it is what exempts a pack from the legacy
  `pack_format` / `supported_formats` fields. Never lower it to widen support —
  that narrows it.

- **Titles live in pack data**, so "update the jar" does not update ranks —
  say so in release notes whenever packs change.
- **Never rename a shipped pack zip; overwrite it in place.** A world records
  its enabled packs by filename in `level.dat` (`file/legendquest-wasteland.zip`),
  so a rename silently *disables* the pack and dangles every saved race/class id
  in that world. The `legendquest-wasteland.zip` → `legendquest-apocalypse.zip`
  rename already stranded one world this way; it still carries the old filename
  because that is the only thing keeping its characters alive.
- All player-facing wording is in `Lang.java`, generated into
  `config/legendquest/messages.yml`. `messages.yml` only pins keys it actually
  contains, so new defaults reach existing servers.

## Permissions

**`NODES.md` is the full reference** — every node, its default, and how to grant
it. Regenerate the race/class lists from the registries rather than editing them
by hand; the generated lists stay right while hand-typed totals beside them
drift (shipped "75 nodes" once when it is 73).

Nodes go through NeoForge's `PermissionAPI`. Two fixed — `legendquest.admin`
(op level 2 also passes) and `legendquest.party.spy` (deny even for ops, and
rechecked per message) — plus one per race and class, built at runtime.

Three things the code knows and nothing else says:

- **The `perm` field in race/class data is a presence flag; its value is
  ignored.** It marks an entry restricted, and the node checked is always the
  generated `legendquest.race.<id>`. The Bukkit plugin used it to *name* a node,
  so this is the assumption people arrive with.
- **Nodes are gathered once per server start**, and an entry with no node counts
  as open — content added by `/reload` is unrestricted until a restart.
- **They gate player self-selection only.** `/lq admin setrace` never consults
  them, and its `force` flag is about race/class *legality*, not permissions.

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
- **Standards is also a permissions handler** (`permissionHandler =
  "standards:permissions"` in `neoforge-server.toml`), so it can grant our nodes
  on a server with no LuckPerms. It resolves **tier-first** — nearest tier with
  any opinion wins outright, and specificity only decides *within* a tier, which
  is the opposite of what "exact beats wildcard" suggests. `NODES.md` has the
  worked `/perm` examples.

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

`CHANGELOG.md`, **`CURSEFORGE.md`**, `mod_version` on all three branches, tag,
then a GitHub release — publishing it fires the CurseForge and Modrinth
workflows. Modrinth skips cleanly until a project ID and token exist (still not
created).

- **The store copy is part of the release, not a chore for later.** The
  `store-copy` job fails the release when `CURSEFORGE.md` has not changed since
  the previous tag. It had drifted three releases behind before anyone noticed,
  which is why it is enforced rather than remembered. A release that genuinely
  changes nothing a reader would care about can say `[no-store-update]` in the
  tag message.
- **CurseForge rejects non-jar files** *after* returning HTTP 200. Packs ship
  from GitHub only.
- **A 200 is acceptance, not publication.** Check the file is approved.
- CurseForge's changelog sanitiser 500s on some Markdown — blockquotes, indented
  code blocks with pipes, angle-bracket autolinks. Keep release notes to plain
  paragraphs, lists and simple tables.
- Screenshots stay out of git.
