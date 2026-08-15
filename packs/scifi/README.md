# LegendQuest: Cold Frontier

A science-fiction content pack for
[LegendQuest ReForged](../../README.md). Species instead of races,
Professions instead of classes, Augments instead of feats, Charge instead
of mana, and Standing where karma used to be.

The pack is deliberately setting-neutral: nothing in the data names an
empire or a franchise. Retune `messages-snippet.yml` and the same content
plays as space-western, fleet-opera or salvage-horror — that's the vocab
system doing its job.

## Install

1. Copy this folder (or a zip of it) into your world's `datapacks/`
   directory.
2. Merge `messages-snippet.yml` into `config/legendquest/messages.yml`.
3. **Restart the server** — content registries are frozen at world load;
   `/reload` is not enough.

The pack's `pack.mcmeta` filter hides the built-in D&D races, classes and
feats (the built-in skills stay registered but ungranted, and the
`#legendquest:...` gear tags are reused by this pack).

## The Professions

Master a base Profession (reach the level cap with it) to rate for its
specialist billets. Specialists inherit the base kit from level 0.
Mastering **any** base Profession qualifies you for the Captain's chair.

```
Crewman (start)
├── Marine ─────┬── Commando       (cloak fields, silent takedowns)
│               └── Juggernaut     (walking cover; slow to rate up)
├── Technician ─┬── Engineer       (party shielding, redlined tools)
│               └── Roboticist     (sentry turrets, security bots)
├── Medtech ────┴── Surgeon        (resuscitation, guardian protocols)
├── Recon ──────┬── Pathjumper     (translocation rig - actual teleports)
│               └── Railgunner     (very long, very final arguments)
├── Scientist ──┬── Xenobiologist  (spores, serums, gene-hounds)
│               └── Psion          (mind and storm - see below)
└── (any mastered) ──► Captain     (fights with their voice)
```

**The Psion is double-gated**: it needs a mastered Scientist *and* a
Species carrying the `Psionic` trait group — Mutants and the Vexi.
Baseline brains just get headaches. Inside the wing, Standing splits the
deep techniques: Empathic Mend needs `karma_min 20`, Void Whisper needs
`karma_max -20`, and each use pulls you further along its own road — one
character can never hold both.

## The Species

| Species | The pitch |
|---|---|
| Unregistered | No file on record. The blank start. |
| Terran | Baseline human; adapts to anything, complains throughout. Extra skill points. |
| Synth | Built, not born. Dense chassis, internal battery, no brewing. |
| Mutant | The radiation took; it also gave. Psionic-receptive. |
| Vex | Grey, tall, out here first. Fragile body, cathedral mind. Psionic. |
| Belter | Spaceborn. Falls professionally. |
| Uplift | Gene-raised from animal stock. Strong; do not mention the lab. |
| Clone | Decanted with the training pre-loaded. Learns absurdly fast. |

Trait groups: `Crew`, `Adaptable`, `Synthetic`, `Psionic`, `Touched`,
`Voidborn`, `Gene-Modded`, `Tough` — the hooks Professions and Augments
gate on.

## Balance notes

- Tuned to the same envelope as the built-in D&D pack and the Wasteland
  pack: base Professions modest, specialists get the numbers, the
  Juggernaut rates up 20% slower.
- The Pathjumper's rig is the only teleport in the pack; the Vexi get the
  biggest Charge pool but the thinnest hull.
- Everything is YAML-overridable per server: copy a file into
  `config/legendquest/<kind>s/<name>.yml` and edit.
