# LegendQuest ReForged — Porting Plan

**This file is the source of truth for the port.** Status boxes are updated as work lands.

LegendQuest (Bukkit 1.8, 2013–2015, v1.9.7) → **LegendQuest ReForged** (NeoForge 21.11.42, Minecraft 1.21.11, Java 21, mod id `legendquest`, version 2.0.0 — the "LQ 2.0" the Spigot page promised in 2015).

Rule of thumb from the owner: *a complete rewrite is fine as long as the intent is achieved.*
The intent, from the recovered docs (`../LegendQuest/docs/legacy-bukkit-docs/`):

> "Completely configurable races, classes and skills. It's **your** RPG experience — **your** way."

- Server owners configure races/classes/skills in **human-readable YAML** (or JSON datapacks — same schema).
- Developers extend the system by writing **skill-pack mods** — the modern replacement for "drop a jar in the skills folder".
- Using a skill in a race/class stays **easy**: reference it by id, set level/cost, done.
- Settings are content packs: D&D fantasy (default), zombie apocalypse (with CityWorld + ZombieMod), sci-fi.
- Vanilla clients must be able to join and play (server-authoritative; HUD is optional client sugar).

---

## Architecture decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | **Datapack registries + codecs** for races, classes, skills (`data/<pack>/legendquest/{race,class,skill}/*.json`) | ZombieMod-proven: free `/reload`, free client sync, free override semantics, validation with real error messages. `ZombieModRegistries.java` is the 38-line template. |
| 2 | **YAML front door**: `config/legendquest/{races,classes,skills}/*.yml` are converted (SnakeYAML → Gson `JsonElement`) into a generated datapack served via `AddPackFindersEvent` | Admins keep editing YAML; the codecs stay the single schema. YAML and JSON are two skins over one format. |
| 3 | **Skill = data; skill effect = code.** A skill definition (data) composes typed *effects* dispatched with `Identifier.CODEC.dispatch("type", ...)`. `SkillEffectTypes.register()` is public. | The old 62 skill classes are mostly parameter variants of ~a dozen behaviours. Skill-pack mods register new effect/trigger/target types; servers compose them in YAML. Replaces the hand-rolled `SkillClassLoader` with NeoForge's own mod loading (versioned, dependency-checked). |
| 4 | **Per-player state in one codec-serialized attachment** (`PlayerCharacter` on the player) | Skill definitions are immutable records; runtime state (cooldowns, mana, xp, karma) lives per-player. The Bukkit version's shared-singleton-skill and shared-`vars`-map bugs become structurally impossible. |
| 5 | **Stat mods as `AttributeModifier`s with per-source `Identifier`s** (`legendquest:race`, `legendquest:class_main`, …), never `setBaseValue` | Race + class + level + buffs compose and are removable. (ZombieMod's `setBaseValue` is right for one-shot mob stamps, wrong for stacking player sources.) |
| 6 | **No SQL.** World-scoped data (parties) in `SavedData`; player data in the attachment | SQLibrary is dead; vanilla persistence is free and transactional with the world save. |
| 7 | **Permissions via NeoForge `PermissionAPI`** (`PermissionNode`), LuckPerms-compatible | Same shape as Bukkit permissions; nodes like `legendquest.race.<id>` gate selection. |
| 8 | **Item/ability restrictions via item tags + events** | Old `data.yml` material groups map onto item tags (`#legendquest:swords`, or vanilla `#minecraft:swords`). Allow/deny lists accept items and tags via `HolderSet` codecs. |
| 9 | Package split `core` / (root registries+data) / `neoforge` / `network` / `client` | MobHealth convention: `core` has no Minecraft imports; a future loader port swaps the adapter. |
| 10 | Times in config stay **milliseconds** (converted to ticks internally) | Matches every recovered doc page and eases migrating old YAML. |

### Intentionally NOT ported (old bugs / dead weight)
- Shared skill singletons, shallow-copied `vars` maps, `skills.yml` global defaults (never worked), `skillname:` aliasing (broken in every shipped config — replaced by "make another skill file, skills are data now").
- Race-vs-class same-name skill collision by `HashSet` iteration order → now: explicit merge, class overrides race, warning logged.
- Bare metadata scratchpads (`"str"`, `"cursetimeout"`) → typed, per-source effect instances.
- SQLibrary/MySQL, MChat/DeluxeChat shims, WorldGuard-5-era plugin shims, NMS `Scry` skill.
- Vanilla-XP mirroring with hardcoded 1.8 curve constants → own XP pool fed by vanilla XP pickup.

### Deferred (revisit after core works)
- Economy costs (`pay:`) — no Vault equivalent on NeoForge; design a hook interface, ship no-op.
- Permskills (running other plugins' commands under LQ costs/cooldowns) — a `run_command`/`grant_permission` skill effect pair covers the intent later.
- Written-book character journal, chat prefixes/placeholders. (Loadouts: DONE — /loadout add/bind, sneak+right-click cycles, right-click casts.)
- Party centroid teleport + SafeLoc; parties themselves are in scope.
- Crafting/brewing/enchanting/smelting/repair/taming gates (needs per-station event survey); weapon/tool/armour gates are in scope now.

---

## Porting checklist

### Phase 0 — Repo & scaffold
- [x] New repo `LegendQuest-ReForged`, git init, `main` branch
- [x] Build scaffold from MobHealth (ModDevGradle 2.0.141, NeoForge 21.11.42, Java 21, templated mods.toml)
- [x] SnakeYAML 2.2 embedded via jarJar
- [x] This PORTING.md
- [x] First clean `gradlew build`
- [x] `deploy.sh` → CurseForge "MobHealth - Forge" instance (dedicated `server/` still later)

### Phase 1 — Data model & registries
- [x] `core/Stat.java` — STR/DEX/CON/INT/WIS/CHR + `(stat/2)-5` modifier
- [x] `core/Mechanics.java` — d20 rolls, skill tests, opposed tests (no MC imports)
- [x] `data/StatBlock.java` — six-stat record + codec (flat YAML `str:/dex:/...`)
- [x] `data/ItemRules.java` — allowed/disallowed weapons/tools/armour as `HolderSet<Item>` lists
- [x] `data/LevelBonuses.java` — per-level rewards (hp/mana/sp/stats/allow/disallow)
- [x] `data/SkillGrant.java` — how a race/class grants a skill: `{ skill: <id>, level, cost }`
- [x] `data/Race.java` — record + codec (name, plural, chattag, descriptions, size, frequency, default, base health/mana/speed/regen, statmods, groups, abilities, item rules, skill grants, level bonuses, xp adjusts, perm)
- [x] `data/CharClass.java` — record + codec (race fields + allowed_races/groups, requires/requires_one, main/sub-only, per-level health/mana, mods)
- [x] `data/SkillDefinition.java` — record + codec (type ACTIVE/PASSIVE/TRIGGERED, timing ms {buildup,delay,duration,cooldown}, costs {mana,karma...,consumes}, level/skill-point defaults, trigger spec, effect list)
- [x] `LQRegistries.java` — three datapack registries, synced to client
- [x] Registered via `DataPackRegistryEvent.NewRegistry` on the mod bus

### Phase 2 — YAML front door
- [x] `yaml/YamlToJson.java` — SnakeYAML → Gson tree
- [x] `yaml/YamlPackSource` — `AddPackFindersEvent` pack that converts `config/legendquest/**/*.yml` into an in-memory/generated datapack on each pack scan (so `/reload` picks up YAML edits)
- [x] Malformed YAML → loud log + skip file (do NOT take the world down; note vanilla registry loader hard-fails on bad JSON — YAML path pre-validates)

### Phase 3 — Player character
- [x] `PlayerCharacter` attachment: race id, main/sub class ids, per-class XP map, karma, mana, spent skill points, purchased skills, per-skill cooldown stamps, base statline
- [x] Codec-serialized `AttachmentType`, `copyOnDeath`
- [x] Statline: random-from-UUID or all-12s (config)
- [x] `CharacterManager` — resolve race/class holders, compute derived stats, apply `AttributeModifier`s (max health, movement speed) on login/change/level
- [x] Karma: kill monitoring (`LivingDeathEvent`), log-scale names from config
- [ ] XP: `PlayerXpEvent.PickupXp` feeds per-class XP with race/class kill/mine/smelt adjusts; level curve; `LevelUpEvent`-equivalent hook (level-up detection + bonuses + feedback)

### Phase 4 — Skill engine
- [x] `skills/SkillTarget.java` — dispatch registry: self, looking_at_entity, looking_at_block, nearby, party (party later)
- [x] `skills/SkillEffect.java` + `SkillEffectTypes` (public register, UnknownType error codec listing known types)
- [x] Starter effect types: damage, heal, potion_effect, teleport_to_block, leap, summon, message, lightning, give_item, extinguish/ignite
- [x] `skills/SkillTrigger.java` — for TRIGGERED skills: on_melee_hit, on_hurt, on_kill, on_fall (chance + conditions)
- [x] Phase machine READY→BUILDING→DELAYED→ACTIVE→COOLDOWN from per-player `lastUse` + definition timings (pure function, like old `checkPhase()`)
- [x] Cost gate: mana, karma cost/requirement, consumed items, level, skill points purchase
- [x] PASSIVE skills: periodic apply on server tick (interval from config)
- [x] Held-item skill binding — `/bind <skill>` (alias `/link`) binds held item type; right-click fires it, vanilla use suppressed; `/unbind`/`/unlink` clears (loadouts also done — see /loadout)

### Phase 5 — Commands & permissions
- [x] `/lq` root: info, reload note, admin subcommands (setrace/setclass/xp/karma)
- [x] `/race list|info|choose`, `/class list|info|choose [sub]` with `ResourceKeyArgument` + bare-name resolution (ZombieMod's ambiguity-detection pattern)
- [x] `/stats` — chat character sheet
- [x] `/skill list|info|use <id>|buy <id>`, `/karma`, `/roll`
- [x] `PermissionNode`s: `legendquest.admin` (node or op), dynamic `legendquest.race.<id>` / `legendquest.class.<id>` nodes enumerated from the registries at gather; a `perm:` field in data flips the default to locked; `/race list` shows [locked]
- [x] Race lock rule: default race may change once; others locked (admin override)

### Phase 6 — Combat & restrictions
- [x] d20 opposed hit/dodge on `LivingIncomingDamageEvent` (config toggle), STR damage mod, size difficulty
- [x] Weapon gate: disallowed weapon → damage reverts to fist-level + feedback
- [x] Tool gate: `PlayerEvent.BreakSpeed` slowdown / `BlockEvent.BreakEvent` deny
- [x] Armour gate: penalties not confiscation — Slowness (scales with pieces) + Mining Fatigue every second while wearing disallowed armour; warning on equip
- [x] Mana: regen tick (race/class/level rates), skill costs

### Phase 7 — Client & polish
- [ ] Network payloads: character summary + mana sync (registrar `.optional()` so vanilla clients connect)
- [ ] Client mod class (`dist=CLIENT`), mana bar + skill state HUD via `RenderGuiEvent.Post`
- [ ] Config screen (`IConfigScreenFactory` + lang keys for every option)
- [ ] Action-bar feedback helpers (`&`-code translation, like ZombieMod `Announce`)

### Phase 8 — Content & packs
- [ ] Default D&D pack as **YAML** shipped in `config` defaults + mirrored JSON datapack: races (human, elf, dwarf, orc, hobbit, gnome, half-elf, half-orc, ender, wither, undecided), classes (citizen, fighter, mage, ranger, warlord), core skills re-expressed as data
- [ ] Example skill-pack mod repo (`LegendQuest-SkillPack-Example`) proving third-party `SkillEffectTypes.register()`
- [ ] Zombie-apocalypse pack (ties into ZombieMod genera + CityWorld)
- [x] Parties — `/party create|invite|accept|decline|leave`, SavedData ledger on the overworld, XP share to nearby members (config %/range), friendly fire blocked

### Phase 9 — Verification
- [x] `runServer` boot smoke test (clean boot 9s, all registries parsed, YAML race served) — in-game `/reload` cycle still to verify
- [ ] Vanilla-client join test against dev server
- [ ] README.md (schema reference in the ZombieMod style: every field, default, and rationale)

---

## 1.21.11 gotchas that bite this mod specifically
(distilled from MobHealth / ZombieMod / CityWorld porting notes — verified there against decompiled sources)

- `ResourceLocation` is now `net.minecraft.resources.Identifier` (`fromNamespaceAndPath`, `parse`, `withDefaultNamespace`); `ResourceKey.location()` → `.identifier()`.
- `CompoundTag` getters return `Optional`; `getCompoundOrEmpty` may return a fresh tag — put it back.
- Datapack-registry JSON that fails to parse **stops the world loading** (vanilla `RegistryDataLoader`). The YAML converter must pre-validate and skip bad files loudly instead of emitting bad JSON.
- `RecordCodecBuilder.group` caps at 16 fields → nested `MapCodec` sub-records that stay **flat in the JSON/YAML** (Genus.Appearance pattern). Race/class configs need this.
- Loot tables are NOT in `registryAccess()` — `server.reloadableRegistries().getLootTable(key)`.
- Command arg for registry entries: `ResourceKeyArgument.key(...)`; Brigadier unquoted strings stop at `:`.
- Permission gate: `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`.
- Payload registrar `.optional()` or vanilla clients get kicked. Client classes referenced only inside `enqueueWork` lambdas.
- No `@OnlyIn`/`DistExecutor`: second `@Mod(dist = Dist.CLIENT)` class for the client entrypoint.
- Attributes: `Attributes.SCALE` exists (synced); movement speed modifiers multiply — use `ADD_MULTIPLIED_TOTAL` for race speed.
- `EventHooks.canEntityGrief`, never the gamerule directly (summon/block effects).
- Rebuilding AI/goals inside their own tick → CME (matters if skills touch mob AI: taunt, ward).
- Gradle: configuration-cache off; first build after adding an accesstransformer.cfg recompiles MC (10+ min, looks like a hang); lingering `runServer` holds the port and the crash report "reads like a code fault and isn't one".
- WSL2 `/mnt/d` can degrade → gradle hangs at `:compileJava` with no CPU → `wsl --shutdown`.
- Read APIs from `build/moddev/artifacts/neoforge-21.11.42-sources.jar` instead of trusting recall.

## Old-plugin reference map

| Old (Bukkit) | New (NeoForge) |
|---|---|
| `Main` service locator | `LegendQuest` @Mod + small managers |
| `races/*.yml`, `classes/*.yml` (Bukkit YamlConfiguration) | same YAML, parsed by codecs via the YAML front door; also plain JSON datapacks |
| `SkillPool`/`SkillLoader`/`SkillClassLoader` + `@SkillManifest` | skill definitions (data) + `SkillEffectTypes` dispatch registry (code, from skill-pack mods) |
| `SkillDataStore.checkPhase()` | `SkillPhases.phaseAt(now, lastUse, timing)` pure function |
| `PC` (2430-line god class) + SQL | `PlayerCharacter` attachment (codec) + `CharacterManager` |
| Scoreboard sidebar + BELOW_NAME | client HUD overlay (modded clients), action bar (everyone) |
| `PermissionAttachment` nodes | `PermissionAPI` nodes |
| `EffectManager`/`Effects` enum | `potion_effect` skill effect + vanilla `MobEffectInstance` |
| KarmaMonitorEvents | `LivingDeathEvent` handler on game bus |
| ManaTicker | `ServerTickEvent.Post` (interval) |
| data.yml material groups | item tags (`#legendquest:*`) + inline item lists |
