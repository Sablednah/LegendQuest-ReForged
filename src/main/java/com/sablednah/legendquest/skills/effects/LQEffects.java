package com.sablednah.legendquest.skills.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.legendquest.LegendQuest;
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
    }

    /** Magic damage to the target. The old Hurt/MightyBlow backbone. */
    public record Damage(float amount, TargetSpec target) implements SkillEffect {
        public static final Identifier TYPE = id("damage");
        public static final MapCodec<Damage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("amount").forGetter(Damage::amount),
                TargetSpec.CODEC.optionalFieldOf("target", TargetSpec.LOOKING_AT).forGetter(Damage::target))
                .apply(i, Damage::new));

        @Override public Identifier type() { return TYPE; }

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
        public void apply(SkillContext ctx) {
            for (LivingEntity e : target.resolveEntities(ctx)) {
                e.heal(amount);
                ctx.level().sendParticles(ParticleTypes.HEART,
                        e.getX(), e.getY() + e.getBbHeight() * 0.8D, e.getZ(), 4, 0.3D, 0.3D, 0.3D, 0.0D);
            }
        }
    }

    /** Apply a potion effect. Covers Aura/PassiveAura/Hex/Curse-style skills. */
    public record Potion(Holder<MobEffect> effect, long durationMs, int amplifier, TargetSpec target)
            implements SkillEffect {
        public static final Identifier TYPE = id("potion_effect");
        public static final MapCodec<Potion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(Potion::effect),
                Codec.LONG.optionalFieldOf("duration", 5000L).forGetter(Potion::durationMs),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Potion::amplifier),
                TargetSpec.CODEC.optionalFieldOf("target", TargetSpec.SELF).forGetter(Potion::target))
                .apply(i, Potion::new));

        @Override public Identifier type() { return TYPE; }

        @Override
        public void apply(SkillContext ctx) {
            int ticks = (int) Math.max(1, durationMs / 50);
            for (LivingEntity e : target.resolveEntities(ctx)) {
                e.addEffect(new MobEffectInstance(effect, ticks, amplifier));
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
        public void apply(SkillContext ctx) {
            Player p = ctx.caster();
            ItemStack stack = new ItemStack(item, count);
            if (!p.getInventory().add(stack)) {
                p.drop(stack, false);
            }
        }
    }

    /** Play a sound at the caster. */
    public record Sound(Holder<SoundEvent> sound, float volume, float pitch) implements SkillEffect {
        public static final Identifier TYPE = id("sound");
        public static final MapCodec<Sound> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.SOUND_EVENT.holderByNameCodec().fieldOf("sound").forGetter(Sound::sound),
                Codec.FLOAT.optionalFieldOf("volume", 1.0F).forGetter(Sound::volume),
                Codec.FLOAT.optionalFieldOf("pitch", 1.0F).forGetter(Sound::pitch))
                .apply(i, Sound::new));

        @Override public Identifier type() { return TYPE; }

        @Override
        public void apply(SkillContext ctx) {
            ctx.level().playSound(null, ctx.caster().getX(), ctx.caster().getY(), ctx.caster().getZ(),
                    sound.value(), SoundSource.PLAYERS, volume, pitch);
        }
    }

    private LQEffects() {}
}
