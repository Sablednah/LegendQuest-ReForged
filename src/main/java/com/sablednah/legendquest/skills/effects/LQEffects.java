package com.sablednah.legendquest.skills.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.legendquest.LegendQuest;
import com.sablednah.legendquest.neoforge.CombatTagging;
import com.sablednah.legendquest.skills.SkillContext;
import com.sablednah.legendquest.skills.SkillEffect;
import com.sablednah.legendquest.skills.SkillEffectTypes;
import com.sablednah.legendquest.skills.TargetSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The built-in skill effect types. Each is an immutable record whose fields
 * mirror its YAML keys; most of the old plugin's 62 skill classes are
 * expressible as combinations of these.
 *
 * <p>Third-party packs add their own via {@link SkillEffectTypes#register}.</p>
 */
public final class LQEffects {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(LegendQuest.MODID, path);
    }

    // --- describe() plumbing: the handbook's "what it does" lines are built from
    // the data, so they can never contradict it. Phrased through Lang so a
    // re-themed server and a translated server both read correctly.

    private static String fx(String key, Object... kv) {
        return com.sablednah.legendquest.neoforge.Lang.fmt(key, kv);
    }

    /** 6.0 reads as "6"; 6.5 stays "6.5". */
    private static String num(double value) {
        return TargetSpec.num(value);
    }

    /** Milliseconds as the seconds a player thinks in. */
    private static String secs(long ms) {
        return num(ms / 1000.0D);
    }

    /** Potion levels are roman in vanilla; match it rather than inventing "Haste 2". */
    private static String roman(int value) {
        if (value < 1 || value > 10) return String.valueOf(value);
        return new String[] {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"}[value - 1];
    }

    /** Called once from SkillEffectTypes' static init. */
    public static void registerBuiltin() {
        SkillEffectTypes.register(Damage.TYPE, Damage.CODEC);
        SkillEffectTypes.register(Heal.TYPE, Heal.CODEC);
        SkillEffectTypes.register(Potion.TYPE, Potion.CODEC);
        SkillEffectTypes.register(Leap.TYPE, Leap.CODEC);
        SkillEffectTypes.register(Teleport.TYPE, Teleport.CODEC);
        SkillEffectTypes.register(Lightning.TYPE, Lightning.CODEC);
        SkillEffectTypes.register(Summon.TYPE, Summon.CODEC);
        SkillEffectTypes.register(Message.TYPE, Message.CODEC);
        SkillEffectTypes.register(Ignite.TYPE, Ignite.CODEC);
        SkillEffectTypes.register(GiveItem.TYPE, GiveItem.CODEC);
        SkillEffectTypes.register(Sound.TYPE, Sound.CODEC);
        SkillEffectTypes.register(ParticleLine.TYPE, ParticleLine.CODEC);
        SkillEffectTypes.register(ProjectileEffect.TYPE, ProjectileEffect.CODEC);
        SkillEffectTypes.register(RunCommand.TYPE, RunCommand.CODEC);
    }

    /** Magic damage to the target. The old Hurt/MightyBlow backbone. */
    public record Damage(float amount, TargetSpec target) implements SkillEffect {
        public static final Identifier TYPE = id("damage");
        public static final MapCodec<Damage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("amount").forGetter(Damage::amount),
                TargetSpec.CODEC.optionalFieldOf("target", TargetSpec.LOOKING_AT).forGetter(Damage::target))
                .apply(i, Damage::new));

        @Override public Identifier type() { return TYPE; }

        /** Dealing damage is the plainest act of aggression there is. */
        @Override public boolean hostile() { return true; }

        @Override
        public String describe() {
            return fx("hb.fx.damage", "amount", num(amount), "at", target.describe());
        }

        @Override
        public void apply(SkillContext ctx) {
            for (LivingEntity e : target.resolveEntities(ctx)) {
                e.hurtServer(ctx.level(),
                        ctx.level().damageSources().indirectMagic(ctx.caster(), ctx.caster()), amount);
            }
        }
    }

    /** Heal the target. The old Heal/HealingTouch. */
    public record Heal(float amount, TargetSpec target) implements SkillEffect {
        public static final Identifier TYPE = id("heal");
        public static final MapCodec<Heal> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("amount").forGetter(Heal::amount),
                TargetSpec.CODEC.optionalFieldOf("target", TargetSpec.SELF).forGetter(Heal::target))
                .apply(i, Heal::new));

        @Override public Identifier type() { return TYPE; }

        @Override
        public String describe() {
            return fx("hb.fx.heal", "amount", num(amount), "at", target.describe());
        }

        @Override
        public void apply(SkillContext ctx) {
            for (LivingEntity e : target.resolveEntities(ctx)) {
                e.heal(amount);
                ctx.level().sendParticles(ParticleTypes.HEART,
                        e.getX(), e.getY() + e.getBbHeight() * 0.8D, e.getZ(), 4, 0.3D, 0.3D, 0.3D, 0.0D);
            }
        }
    }

    /**
     * Apply a potion effect. Covers Aura/PassiveAura/Hex/Curse-style skills.
     * {@code particles: false} for always-on passives — nobody wants to
     * live inside a lava lamp; the HUD icon stays so the buff is visible.
     */
    public record Potion(Holder<MobEffect> effect, long durationMs, int amplifier,
            boolean particles, boolean showIcon, TargetSpec target) implements SkillEffect {
        public static final Identifier TYPE = id("potion_effect");
        public static final MapCodec<Potion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(Potion::effect),
                Codec.LONG.optionalFieldOf("duration", 5000L).forGetter(Potion::durationMs),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Potion::amplifier),
                Codec.BOOL.optionalFieldOf("particles", true).forGetter(Potion::particles),
                Codec.BOOL.optionalFieldOf("show_icon", true).forGetter(Potion::showIcon),
                TargetSpec.CODEC.optionalFieldOf("target", TargetSpec.SELF).forGetter(Potion::target))
                .apply(i, Potion::new));

        @Override public Identifier type() { return TYPE; }

        @Override
        public String describe() {
            return fx("hb.fx.potion",
                    "effect", effect.value().getDisplayName().getString(),
                    "level", amplifier > 0 ? " " + roman(amplifier + 1) : "",
                    "at", target.describe(), "time", secs(durationMs));
        }

        /**
         * Hostile when the effect is harmful AND aimed at somebody other than
         * the caster or their own party. Haste on yourself is not a fight;
         * blindness on the person you are robbing very much is — and it
         * produces no damage event to give the game away.
         */
        @Override
        public boolean hostile() {
            return effect.value().getCategory() == MobEffectCategory.HARMFUL
                    && target.kind() != TargetSpec.Kind.SELF
                    && target.kind() != TargetSpec.Kind.PARTY;
        }

        @Override
        public void apply(SkillContext ctx) {
            int ticks = (int) Math.max(1, durationMs / 50);
            boolean hostile = hostile();
            for (LivingEntity e : target.resolveEntities(ctx)) {
                e.addEffect(new MobEffectInstance(effect, ticks, amplifier,
                        !particles, particles, showIcon));
                // The victim's half. Effects that deal damage need no help --
                // their damage event tags whoever it lands on -- but a pure
                // debuff would otherwise leave the target free to teleport out
                // of a fight they are unmistakably in.
                if (hostile) CombatTagging.skillVictim(e, TYPE);
            }
        }
    }

    /** Launch the caster along their look direction. The old Leap. */
    public record Leap(double power, double lift) implements SkillEffect {
        public static final Identifier TYPE = id("leap");
        public static final MapCodec<Leap> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("power", 1.5D).forGetter(Leap::power),
                Codec.DOUBLE.optionalFieldOf("lift", 0.6D).forGetter(Leap::lift))
                .apply(i, Leap::new));

        @Override public Identifier type() { return TYPE; }

        @Override
        public String describe() {
            return fx("hb.fx.leap");
        }

        @Override
        public void apply(SkillContext ctx) {
            Vec3 look = ctx.caster().getLookAngle();
            ctx.caster().setDeltaMovement(look.x * power, lift, look.z * power);
            ctx.caster().hurtMarked = true; // or the client never sees the launch
        }
    }

    /** Teleport the caster to the looked-at block. The old Teleport/blink. */
    public record Teleport(double maxRange) implements SkillEffect {
        public static final Identifier TYPE = id("teleport");
        public static final MapCodec<Teleport> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("max_range", 32.0D).forGetter(Teleport::maxRange))
                .apply(i, Teleport::new));

        @Override public Identifier type() { return TYPE; }

        @Override
        public String describe() {
            return fx("hb.fx.teleport", "range", num(maxRange));
        }

        @Override
        public void apply(SkillContext ctx) {
            var spec = new TargetSpec(TargetSpec.Kind.LOOKING_AT, maxRange, 0);
            spec.resolvePos(ctx).ifPresent(pos -> {
                Vec3 from = ctx.caster().position();
                ctx.caster().teleportTo(ctx.level(),
                        pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                        java.util.Set.of(), ctx.caster().getYRot(), ctx.caster().getXRot(), false);
                ctx.level().playSound(null, from.x, from.y, from.z,
                        net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                ctx.level().sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0D, from.z,
                        24, 0.4D, 0.6D, 0.4D, 0.1D);
            });
        }
    }

    /** Strike the target with lightning. */
    public record Lightning(boolean visualOnly, TargetSpec target) implements SkillEffect {
        public static final Identifier TYPE = id("lightning");
        public static final MapCodec<Lightning> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("visual_only", false).forGetter(Lightning::visualOnly),
                TargetSpec.CODEC.optionalFieldOf("target", TargetSpec.LOOKING_AT).forGetter(Lightning::target))
                .apply(i, Lightning::new));

        @Override public Identifier type() { return TYPE; }

        /** A real bolt is an attack; a visual-only one is theatre. */
        @Override public boolean hostile() { return !visualOnly; }

        @Override
        public String describe() {
            return fx(visualOnly ? "hb.fx.lightning_visual" : "hb.fx.lightning",
                    "at", target.describe());
        }

        @Override
        public void apply(SkillContext ctx) {
            target.resolvePos(ctx).ifPresent(pos -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(ctx.level(), EntitySpawnReason.TRIGGERED);
                if (bolt == null) return;
                bolt.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
                bolt.setVisualOnly(visualOnly);
                ctx.level().addFreshEntity(bolt);
            });
        }
    }

    /** Summon entities at the looked-at block. The old Summon. */
    public record Summon(EntityType<?> entity, int count, TargetSpec target) implements SkillEffect {
        public static final Identifier TYPE = id("summon");
        public static final MapCodec<Summon> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(Summon::entity),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Summon::count),
                TargetSpec.CODEC.optionalFieldOf("target", TargetSpec.LOOKING_AT).forGetter(Summon::target))
                .apply(i, Summon::new));

        @Override public Identifier type() { return TYPE; }

        @Override
        public String describe() {
            return fx("hb.fx.summon", "count", count,
                    "entity", entity.getDescription().getString());
        }

        @Override
        public void apply(SkillContext ctx) {
            BlockPos pos = target.resolvePos(ctx).orElse(ctx.caster().blockPosition());
            for (int n = 0; n < count; n++) {
                var spawned = entity.create(ctx.level(), EntitySpawnReason.MOB_SUMMONED);
                if (spawned == null) return;
                spawned.snapTo(pos.getX() + 0.5D + (ctx.level().random.nextDouble() - 0.5D),
                        pos.getY(), pos.getZ() + 0.5D + (ctx.level().random.nextDouble() - 0.5D),
                        ctx.level().random.nextFloat() * 360.0F, 0.0F);
                if (spawned instanceof Mob mob) {
                    EventHooks.finalizeMobSpawn(mob, ctx.level(),
                            ctx.level().getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
                }
                ctx.level().addFreshEntity(spawned);
            }
        }
    }

    /** Send the caster a message (action bar). '&' colour codes supported. */
    public record Message(String text, boolean actionBar) implements SkillEffect {
        public static final Identifier TYPE = id("message");
        public static final MapCodec<Message> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(Message::text),
                Codec.BOOL.optionalFieldOf("action_bar", true).forGetter(Message::actionBar))
                .apply(i, Message::new));

        @Override public Identifier type() { return TYPE; }

        @Override
        public String describe() {
            return ""; // flavour the player is about to read anyway
        }

        @Override
        public void apply(SkillContext ctx) {
            ctx.caster().displayClientMessage(Component.literal(text.replace('&', '§')), actionBar);
        }
    }

    /** Set the target on fire. */
    public record Ignite(long durationMs, TargetSpec target) implements SkillEffect {
        public static final Identifier TYPE = id("ignite");
        public static final MapCodec<Ignite> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.LONG.optionalFieldOf("duration", 5000L).forGetter(Ignite::durationMs),
                TargetSpec.CODEC.optionalFieldOf("target", TargetSpec.LOOKING_AT).forGetter(Ignite::target))
                .apply(i, Ignite::new));

        @Override public Identifier type() { return TYPE; }

        /** Setting somebody alight is an act of aggression. */
        @Override public boolean hostile() { return true; }

        @Override
        public String describe() {
            return fx("hb.fx.ignite", "at", target.describe(), "time", secs(durationMs));
        }

        @Override
        public void apply(SkillContext ctx) {
            for (LivingEntity e : target.resolveEntities(ctx)) {
                e.igniteForSeconds(durationMs / 1000.0F);
            }
        }
    }

    /** Conjure items into the caster's inventory. The old SummonItem. */
    public record GiveItem(Item item, int count) implements SkillEffect {
        public static final Identifier TYPE = id("give_item");
        public static final MapCodec<GiveItem> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(GiveItem::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(GiveItem::count))
                .apply(i, GiveItem::new));

        @Override public Identifier type() { return TYPE; }

        @Override
        public String describe() {
            return fx("hb.fx.give_item", "count", count,
                    "item", new ItemStack(item).getHoverName().getString());
        }

        @Override
        public void apply(SkillContext ctx) {
            Player p = ctx.caster();
            ItemStack stack = new ItemStack(item, count);
            if (!p.getInventory().add(stack)) {
                p.drop(stack, false);
            }
        }
    }

    /**
     * Play a sound at the caster. {@code stop_after} (ms) cuts it off — a
     * music disc outlives any skill duration by minutes, and nobody wants
     * Pigstep STILL going when the Battle Hymn's strength ran out. The cut
     * is a vanilla stop-sound packet, so it works on vanilla clients too.
     */
    public record Sound(Holder<SoundEvent> sound, float volume, float pitch, long stopAfterMs)
            implements SkillEffect {
        public static final Identifier TYPE = id("sound");
        public static final MapCodec<Sound> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.SOUND_EVENT.holderByNameCodec().fieldOf("sound").forGetter(Sound::sound),
                Codec.FLOAT.optionalFieldOf("volume", 1.0F).forGetter(Sound::volume),
                Codec.FLOAT.optionalFieldOf("pitch", 1.0F).forGetter(Sound::pitch),
                Codec.LONG.optionalFieldOf("stop_after", 0L).forGetter(Sound::stopAfterMs))
                .apply(i, Sound::new));

        private record PendingStop(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                Identifier soundId, long atMs) {}
        private static final java.util.List<PendingStop> STOPS =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override public Identifier type() { return TYPE; }

        @Override
        public String describe() {
            return ""; // audible, not readable
        }

        @Override
        public void apply(SkillContext ctx) {
            ctx.level().playSound(null, ctx.caster().getX(), ctx.caster().getY(), ctx.caster().getZ(),
                    sound.value(), SoundSource.PLAYERS, volume, pitch);
            if (stopAfterMs > 0) {
                Identifier soundId = BuiltInRegistries.SOUND_EVENT.getKey(sound.value());
                if (soundId != null) {
                    STOPS.add(new PendingStop(ctx.level().dimension(), soundId,
                            System.currentTimeMillis() + stopAfterMs));
                }
            }
        }

        /** Called from SkillEngine.tick: the needle lifts when the song ends. */
        public static void tickStops(net.minecraft.server.MinecraftServer server) {
            if (STOPS.isEmpty()) return;
            long now = System.currentTimeMillis();
            for (PendingStop stop : STOPS) {
                if (stop.atMs() > now) continue;
                STOPS.remove(stop);
                var packet = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                        stop.soundId(), SoundSource.PLAYERS);
                for (var player : server.getPlayerList().getPlayers()) {
                    if (player.level().dimension() == stop.dimension()) {
                        player.connection.send(packet);
                    }
                }
            }
        }
    }

    /**
     * A line of particles from the caster's eyes to the target — the visual
     * language of "I cast a thing at you". Magic Missile's whole aesthetic.
     */
    public record ParticleLine(net.minecraft.core.particles.ParticleOptions particle,
            double perBlock, TargetSpec target) implements SkillEffect {
        public static final Identifier TYPE = id("particle_line");

        /** Vanilla's particle codec insists on {@code {"type": ...}}; skill
         *  authors get to write plain {@code "minecraft:end_rod"} too.
         *  (Bare strings only work for simple particles — dust and friends
         *  need the object form for their options.) */
        private static final Codec<net.minecraft.core.particles.ParticleOptions> PARTICLE =
                Codec.withAlternative(
                        net.minecraft.core.particles.ParticleTypes.CODEC,
                        Codec.STRING.comapFlatMap(
                                s -> {
                                    Identifier rl = Identifier.tryParse(s);
                                    var type = rl == null ? null
                                            : BuiltInRegistries.PARTICLE_TYPE.getValue(rl);
                                    return type instanceof net.minecraft.core.particles.SimpleParticleType simple
                                            ? com.mojang.serialization.DataResult.success(
                                                    (net.minecraft.core.particles.ParticleOptions) simple)
                                            : com.mojang.serialization.DataResult.error(() -> s
                                                    + " is not a simple particle — use the {\"type\": ...} form");
                                },
                                options -> BuiltInRegistries.PARTICLE_TYPE
                                        .getKey(options.getType()).toString()));

        public static final MapCodec<ParticleLine> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                PARTICLE.optionalFieldOf("particle", ParticleTypes.END_ROD).forGetter(ParticleLine::particle),
                Codec.DOUBLE.optionalFieldOf("per_block", 4.0D).forGetter(ParticleLine::perBlock),
                TargetSpec.CODEC.optionalFieldOf("target", TargetSpec.LOOKING_AT).forGetter(ParticleLine::target))
                .apply(i, ParticleLine::new));

        @Override public Identifier type() { return TYPE; }

        @Override
        public String describe() {
            return ""; // decoration; the effect it accompanies is the story
        }

        @Override
        public void apply(SkillContext ctx) {
            // Start ahead of and below the eyes — wand height, not eyeball
            // height — so the caster's own screen isn't full of sparkle.
            Vec3 from = ctx.caster().getEyePosition()
                    .add(ctx.caster().getLookAngle().scale(0.9D))
                    .add(0, -0.4D, 0);
            Vec3 to = target.resolveEntities(ctx).stream().findFirst()
                    .map(e -> e.position().add(0, e.getBbHeight() * 0.6D, 0))
                    .or(() -> target.resolvePos(ctx).map(p ->
                            new Vec3(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D)))
                    .orElse(from.add(ctx.caster().getLookAngle().scale(target.range())));
            double length = from.distanceTo(to);
            int steps = Math.max(2, (int) (length * perBlock));
            for (int n = 0; n <= steps; n++) {
                Vec3 p = from.lerp(to, n / (double) steps);
                ctx.level().sendParticles(particle, p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
    }

    /**
     * Launch a projectile along the caster's look. Fireballs get their real
     * constructors (owner, direction, explosion power); anything else spawns
     * with {@code speed} as straight velocity. Fireball, the skill, at last.
     */
    public record ProjectileEffect(EntityType<?> entity, double speed, int power)
            implements SkillEffect {
        public static final Identifier TYPE = id("projectile");
        public static final MapCodec<ProjectileEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(ProjectileEffect::entity),
                Codec.DOUBLE.optionalFieldOf("speed", 1.5D).forGetter(ProjectileEffect::speed),
                Codec.INT.optionalFieldOf("power", 1).forGetter(ProjectileEffect::power))
                .apply(i, ProjectileEffect::new));

        @Override public Identifier type() { return TYPE; }

        /** Firing a projectile at someone is an act of aggression. */
        @Override public boolean hostile() { return true; }

        @Override
        public String describe() {
            return fx("hb.fx.projectile", "entity", entity.getDescription().getString());
        }

        @Override
        public void apply(SkillContext ctx) {
            Vec3 look = ctx.caster().getLookAngle();
            Vec3 spawn = ctx.caster().getEyePosition().add(look.scale(1.2D));
            net.minecraft.world.entity.Entity launched;
            if (entity == EntityType.FIREBALL) {
                launched = new net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball(
                        ctx.level(), ctx.caster(), look, power);
            } else if (entity == EntityType.SMALL_FIREBALL) {
                launched = new net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball(
                        ctx.level(), ctx.caster(), look);
            } else {
                launched = entity.create(ctx.level(), EntitySpawnReason.TRIGGERED);
                if (launched == null) return;
                if (launched instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
                    projectile.setOwner(ctx.caster());
                }
                launched.setDeltaMovement(look.scale(speed));
            }
            launched.snapTo(spawn.x, spawn.y, spawn.z,
                    ctx.caster().getYRot(), ctx.caster().getXRot());
            ctx.level().addFreshEntity(launched);
        }
    }

    /**
     * The old "permskills" intent, modernised: run any command under LQ's
     * costs and cooldowns — server-installed /fly, /home, whatever — with an
     * optional undo command after a duration ("temp flight" is exactly
     * {@code command} + {@code undo_command} + {@code duration}). Runs with
     * permission level 2 as the caster ({@code @s} works), output silenced.
     * {@code %player%} expands to the caster's name for plugin-style syntax.
     *
     * <p>An undo whose player is offline waits for them; pending undos do
     * not survive a server restart (alpha note in SKILL-PACKS.md).</p>
     */
    public record RunCommand(String command, String undoCommand, long durationMs)
            implements SkillEffect {
        public static final Identifier TYPE = id("run_command");
        public static final MapCodec<RunCommand> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("command").forGetter(RunCommand::command),
                Codec.STRING.optionalFieldOf("undo_command", "").forGetter(RunCommand::undoCommand),
                Codec.LONG.optionalFieldOf("duration", 0L).forGetter(RunCommand::durationMs))
                .apply(i, RunCommand::new));

        private record DelayedUndo(java.util.UUID player, String command, long atMs) {}
        private static final java.util.List<DelayedUndo> UNDOS =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override public Identifier type() { return TYPE; }

        @Override
        public String describe() {
            // Deliberately vague: the command can name spoilers, or plainly be
            // none of a player's business.
            return fx(durationMs > 0 && !undoCommand.isEmpty()
                    ? "hb.fx.run_command_timed" : "hb.fx.run_command",
                    "time", secs(durationMs));
        }

        @Override
        public void apply(SkillContext ctx) {
            runAs(ctx.caster(), command);
            if (!undoCommand.isEmpty() && durationMs > 0) {
                UNDOS.add(new DelayedUndo(ctx.caster().getUUID(), undoCommand,
                        System.currentTimeMillis() + durationMs));
            }
        }

        private static void runAs(net.minecraft.server.level.ServerPlayer player, String command) {
            String expanded = command.replace("%player%", player.getName().getString());
            player.level().getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack()
                            .withPermission(net.minecraft.server.permissions
                                    .LevelBasedPermissionSet.GAMEMASTER)
                            .withSuppressedOutput(),
                    expanded);
        }

        /** Called from SkillEngine.tick. Undos wait for offline players. */
        public static void tickUndos(net.minecraft.server.MinecraftServer server) {
            if (UNDOS.isEmpty()) return;
            long now = System.currentTimeMillis();
            for (DelayedUndo undo : UNDOS) {
                if (undo.atMs() > now) continue;
                var player = server.getPlayerList().getPlayer(undo.player());
                if (player == null) continue; // holds until they return
                UNDOS.remove(undo);
                runAs(player, undo.command());
            }
        }
    }

    private LQEffects() {}
}
