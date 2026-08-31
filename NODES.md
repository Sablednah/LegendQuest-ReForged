# Permission nodes

Every node LegendQuest registers, and what it does. Generated from the
registries and the shipped content, so it is the complete set rather than a
sample.

Permissions go through **NeoForge's `PermissionAPI`**, so any handler built on it
works. **SableCraft Standards ships one**, and LuckPerms is the other common
choice.

The active handler is an explicit server setting, not a contest — pick it in
`neoforge-server.toml`:

```toml
permissionHandler = "standards:permissions"
```

With no handler chosen, NeoForge's default answers every question with the
node's own default. That means the table below is exactly what you get and
**nobody can be granted anything** — `legendquest.party.spy` becomes
ungrantable, and restricted races stay restricted for everyone. `/lq admin`
still works, because it falls back to the vanilla op level.

---

## Fixed nodes

| Node | Default | What it allows |
|---|---|---|
| `legendquest.admin` | **deny** (op level 2 also passes) | The whole `/lq admin` tree: `setrace`, `setclass`, `addxp`, `setkarma`, `level`. |
| `legendquest.party.spy` | **deny**, *including for operators* | Permission to read other players' party chat. Holding it is not enough — the listener must also switch themselves on with `/lq party spy on`. |

`legendquest.party.spy` is deliberately not granted by op level. Listening in
on private conversations is something a server owner grants on purpose, not
something that should arrive attached to an op level. It is also **rechecked on
every message**, so revoking it silences a listener immediately rather than at
their next login.

---

## Race and class nodes

One node per race and per class, controlling whether a player may **select** it:

```
legendquest.race.<race>
legendquest.class.<class>
```

Content from a namespace other than `legendquest` (which is every genre pack)
folds the namespace in:

```
legendquest.race.<namespace>.<race>
legendquest.class.<namespace>.<class>
```

The lists below are one node per line inside a fenced block rather than a table,
because there are 73 of them and a 73-row table helps nobody.

**These default to allow.** A race or class is selectable by everyone unless its
data file declares a `perm` field, which flips it to deny-until-granted. Nothing
in the shipped content declares one, so out of the box every entry below is open
and the nodes only matter if you want to *restrict* something.

The nodes gate **player self-selection only** — `/lq race choose`, `/lq class
choose`, and the character GUI, which greys out what the player may not take.
`/lq admin setrace` and `/lq admin setclass` do not consult them, so an admin can
always assign a restricted race.

### Built-in (fantasy)

Namespace `legendquest` — ships in the jar. 10 races, 9 classes.

```
legendquest.race.dwarf
legendquest.race.elf
legendquest.race.gnome
legendquest.race.half_elf
legendquest.race.half_orc
legendquest.race.hobbit
legendquest.race.human
legendquest.race.orc
legendquest.race.tiefling
legendquest.race.undecided

legendquest.class.barbarian
legendquest.class.bard
legendquest.class.citizen
legendquest.class.cleric
legendquest.class.fighter
legendquest.class.mage
legendquest.class.ranger
legendquest.class.rogue
legendquest.class.warlord
```

### The Wasteland

Namespace `lq_apoc` — `legendquest-apocalypse.zip`. 8 races, 20 classes.

```
legendquest.race.lq_apoc.athlete
legendquest.race.lq_apoc.ex_military
legendquest.race.lq_apoc.immune
legendquest.race.lq_apoc.mechanic
legendquest.race.lq_apoc.nobody
legendquest.race.lq_apoc.paramedic
legendquest.race.lq_apoc.prepper
legendquest.race.lq_apoc.street_kid

legendquest.class.lq_apoc.builder
legendquest.class.lq_apoc.chemist
legendquest.class.lq_apoc.combat_medic
legendquest.class.lq_apoc.doc
legendquest.class.lq_apoc.drifter
legendquest.class.lq_apoc.enforcer
legendquest.class.lq_apoc.infiltrator
legendquest.class.lq_apoc.labourer
legendquest.class.lq_apoc.mercenary
legendquest.class.lq_apoc.miner
legendquest.class.lq_apoc.pathfinder
legendquest.class.lq_apoc.plague_caller
legendquest.class.lq_apoc.pyro
legendquest.class.lq_apoc.quartermaster
legendquest.class.lq_apoc.rancher
legendquest.class.lq_apoc.scavenger
legendquest.class.lq_apoc.scout
legendquest.class.lq_apoc.sharpshooter
legendquest.class.lq_apoc.trader
legendquest.class.lq_apoc.veteran
```

### Cold Frontier

Namespace `lq_scifi` — `legendquest-scifi.zip`. 8 races, 16 classes.

```
legendquest.race.lq_scifi.belter
legendquest.race.lq_scifi.clone
legendquest.race.lq_scifi.mutant
legendquest.race.lq_scifi.synth
legendquest.race.lq_scifi.terran
legendquest.race.lq_scifi.unregistered
legendquest.race.lq_scifi.uplift
legendquest.race.lq_scifi.vex

legendquest.class.lq_scifi.captain
legendquest.class.lq_scifi.commando
legendquest.class.lq_scifi.crewman
legendquest.class.lq_scifi.engineer
legendquest.class.lq_scifi.juggernaut
legendquest.class.lq_scifi.marine
legendquest.class.lq_scifi.medtech
legendquest.class.lq_scifi.pathjumper
legendquest.class.lq_scifi.psion
legendquest.class.lq_scifi.railgunner
legendquest.class.lq_scifi.recon
legendquest.class.lq_scifi.roboticist
legendquest.class.lq_scifi.scientist
legendquest.class.lq_scifi.surgeon
legendquest.class.lq_scifi.technician
legendquest.class.lq_scifi.xenobiologist
```

---

## The `perm` field in race/class data

A race or class JSON may carry an optional `perm` field:

```json
{ "name": "Tiefling", "perm": "anything" }
```

**Only its presence is read — the value is ignored.** It is a flag meaning *this
entry is restricted*, and the node checked is always the generated
`legendquest.race.<id>`. It does **not** name a custom node, which is what the
field did in the original Bukkit plugin and is the easy assumption to make.

---

## Granting these with Standards

Standards' handler is dormant until it is the one named in
`neoforge-server.toml`, and its `/perm` command only appears once it is active.
`/perm` and `/rank` are the same tree under two names.

A grant is a node plus an optional state, and the state defaults to *true*:

```
/perm group <group> set <node> [true|false]
/perm group <group> unset <node>
/perm user <player> set <node> [true|false]
/perm user <player> group add <group>
/perm check <player> <node>
```

Worked against the nodes in this document:

```
/perm user Sable set legendquest.party.spy
/perm group moderator set legendquest.admin
/perm group donor set legendquest.class.lq_apoc.plague_caller
/perm group storyteller set legendquest.race.*
/perm group storyteller set legendquest.race.tiefling false
```

`/perm user` takes a plain name rather than a selector, so granting to somebody
who is offline works — which is most of what this gets used for.

Three resolution rules, and the first one catches people out:

- **Tier beats specificity.** The order is the player's own grants, then the
  groups they are in, then parent groups, then the default group — and *the
  first tier with any opinion wins outright*. A wildcard grant on a group the
  player is in beats an exact deny sitting in the default group. Specificity
  never gets consulted, because the nearer tier already answered.
- **Within one tier, the most specific pattern wins** — exact beats
  `legendquest.race.*` beats `legendquest.*` beats `*`. That is why the two
  `storyteller` lines above work as a pair: to carve one race out of a wildcard,
  the exact deny has to sit *in the same place as the wildcard*. If two patterns
  in one tier match equally well and disagree, the deny wins.
- **`prefix.*` covers the prefix node itself**, not just its children, so
  `legendquest.race.*` also grants bare `legendquest.race`.

When nothing anywhere has an opinion, the node's own default applies — the
column in the tables above. Switching Standards on therefore changes nothing on
its own: a server that has granted nothing behaves exactly as it did before.

---

## Gotchas

- **Nodes are gathered once per server start.** A race or class added by
  `/reload` has no node until the next restart, and an entry with no node is
  treated as open. Restart after adding restricted content.
- **Wildcards are the handler's feature, not ours.** The mod only ever asks
  whether one specific node is held; `legendquest.race.*` works because the
  handler expands it. Both Standards and LuckPerms do.
- **A genre pack does not remove the built-in nodes.** Both sets are registered
  if both are loaded, so grants are namespaced and never collide.

---

## Command reference

Everything not listed here is open to all players.

| Command | Requirement |
|---|---|
| `/lq admin …` | `legendquest.admin` **or** op level 2 |
| `/lq party spy on` | `legendquest.party.spy` |
| `/lq race choose <race>` | `legendquest.race.<race>` |
| `/lq class choose <class>` | `legendquest.class.<class>` |
| `/lq class sub <class>` | `legendquest.class.<class>` |

