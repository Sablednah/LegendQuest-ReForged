# Writing a LegendQuest ReForged skill pack

In the Bukkit era a skill pack was a jar dropped in `plugins/LegendQuest/skills`,
classloaded by hand, with one Java class per skill sharing a single instance
across every player. In ReForged a skill pack is an ordinary **NeoForge mod**
that depends on LegendQuest — you get versioning, dependency checking, and
proper classloading for free, and per-player state is handled by the engine.

There are two layers, and most "new skills" only need the first:

## Layer 1: compose existing effects in data (no code)

A *skill* is a data file. Server owners (or your pack's bundled datapack) can
define new skills by composing registered effect types:

```yaml
# config/legendquest/skills/fire_dash.yml   (or JSON in any datapack)
name: Fire Dash
type: active            # active | passive | triggered
cooldown: 8000          # all times in milliseconds
mana_cost: 12
effects:
  - { type: "legendquest:leap", power: 2.2, lift: 0.3 }
  - { type: "legendquest:ignite", duration: 2000,
      target: { kind: nearby, radius: 3 } }
  - { type: "legendquest:sound", sound: "minecraft:entity.blaze.shoot" }
```

Built-in effect types (`legendquest:` namespace), all 14: `damage`, `heal`,
`potion_effect`, `leap`, `teleport`, `lightning`, `summon`, `message`,
`ignite`, `give_item`, `sound`, `particle_line`, `projectile`,
`run_command`.

The list that cannot go stale is `SkillEffectTypes.register(...)` in
`LQEffects.java` — count it there rather than trusting this paragraph, which
had drifted to 11 before.

Targets: `self`, `looking_at` (with `range`), `nearby` (with `radius`),
`trigger` (the other party of a triggered skill's event).

Triggered skills add a trigger block:

```yaml
type: triggered
trigger: { on: melee_hit, chance: 25.0 }   # melee_hit | hurt | kill | fall
```

Races and classes then grant the skill by id — this is deliberately the easy
part:

```yaml
skills:
  mypack:fire_dash: { level: 10, cost: 5 }
```

## Layer 2: new effect types (a small mod)

When a skill needs behaviour no existing effect covers, register a new effect
type. One record + one registration line:

```java
@Mod("firepack")
public class FirePack {
    public FirePack(IEventBus modEventBus, ModContainer container) {
        SkillEffectTypes.register(FlameRing.TYPE, FlameRing.CODEC);
    }
}

public record FlameRing(double radius, int flames) implements SkillEffect {
    public static final Identifier TYPE =
            Identifier.fromNamespaceAndPath("firepack", "flame_ring");

    public static final MapCodec<FlameRing> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("radius", 3.0).forGetter(FlameRing::radius),
            Codec.INT.optionalFieldOf("flames", 8).forGetter(FlameRing::flames))
            .apply(i, FlameRing::new));

    @Override public Identifier type() { return TYPE; }

    @Override
    public void apply(SkillContext ctx) {
        // Runs on the server thread. ctx.caster(), ctx.level(),
        // ctx.skillLevel(), ctx.triggerTarget() are available.
    }
}
```

Rules of the road:

- **Effects are immutable records.** Never store player state on the effect —
  fields are your YAML parameters, nothing else. Per-player state belongs in
  the engine (cooldowns are already handled) or your own attachment.
- Register from your mod constructor (before datapacks load). Duplicate ids
  are refused with a log line, not silently replaced.
- A typo'd `type:` in a data file produces a log message listing every known
  effect type — tell your users to read the server log.
- `mods.toml`: declare a required dependency on `legendquest` so load order
  and version ranges are enforced — the old pack system's "load order is pure
  luck" problem is gone.

## Gradle for a pack

Use the same ModDevGradle scaffold as LegendQuest itself and add:

```groovy
dependencies {
    // consume the published LegendQuest jar (or a maven repo / jarInJar later)
    implementation files("libs/legendquest-2.2.0.jar")
}
```
