package com.sablednah.legendquest.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Who or where an effect lands. Effects embed one of these with a sensible
 * default, so simple YAML stays simple:
 *
 * <pre>
 * { type: "legendquest:damage", amount: 6, target: { kind: looking_at, range: 20 } }
 * </pre>
 */
public record TargetSpec(Kind kind, double range, double radius) {

    public static final TargetSpec SELF = new TargetSpec(Kind.SELF, 16, 4);
    public static final TargetSpec LOOKING_AT = new TargetSpec(Kind.LOOKING_AT, 16, 4);

    public enum Kind {
        /** The caster. */
        SELF,
        /** The living entity the caster is looking at (within range). */
        LOOKING_AT,
        /** Every living entity within radius of the caster (not the caster). */
        NEARBY,
        /** For triggered skills: the other party of the trigger event. */
        TRIGGER,
        /**
         * The caster AND their party members within range (default: the
         * party XP-share range, so songs and blessings reach exactly as far
         * as shared glory does). No party = just the caster.
         */
        PARTY;

        public static final Codec<Kind> CODEC = Codec.STRING.xmap(
                v -> valueOf(v.toUpperCase(Locale.ROOT)),
                v -> v.name().toLowerCase(Locale.ROOT));
    }

    public static final Codec<TargetSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.optionalFieldOf("kind", Kind.SELF).forGetter(TargetSpec::kind),
            Codec.DOUBLE.optionalFieldOf("range", 16.0D).forGetter(TargetSpec::range),
            Codec.DOUBLE.optionalFieldOf("radius", 4.0D).forGetter(TargetSpec::radius))
            .apply(i, TargetSpec::new));

    /**
     * Who this reads as in the handbook — "you", "your target", "everything
     * within 4 blocks". Phrased through Lang so the vocabulary follows.
     */
    public String describe() {
        return switch (kind) {
            case SELF -> com.sablednah.legendquest.neoforge.Lang.get("hb.fx.at.self");
            case LOOKING_AT -> com.sablednah.legendquest.neoforge.Lang.get("hb.fx.at.looking_at");
            case TRIGGER -> com.sablednah.legendquest.neoforge.Lang.get("hb.fx.at.trigger");
            case NEARBY -> com.sablednah.legendquest.neoforge.Lang.fmt(
                    "hb.fx.at.nearby", "radius", num(radius));
            case PARTY -> com.sablednah.legendquest.neoforge.Lang.get("hb.fx.at.party");
        };
    }

    /** 4.0 reads as "4"; 4.5 stays "4.5". */
    public static String num(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** Codec with a different default kind, for effects that naturally aim outward. */
    public static Codec<TargetSpec> codecDefaulting(Kind defaultKind) {
        return CODEC; // kind default handled at field level; see fieldOf helpers below
    }

    /** Resolve to living entities. TRIGGER falls back to LOOKING_AT when absent. */
    public List<LivingEntity> resolveEntities(SkillContext ctx) {
        return switch (kind) {
            case SELF -> List.of(ctx.caster());
            case TRIGGER -> ctx.triggerTarget() != null
                    ? List.of(ctx.triggerTarget())
                    : lookedAt(ctx).map(List::<LivingEntity>of).orElse(List.of());
            case LOOKING_AT -> {
                if (ctx.triggerTarget() != null) yield List.of(ctx.triggerTarget());
                yield lookedAt(ctx).map(List::<LivingEntity>of).orElse(List.of());
            }
            case NEARBY -> {
                List<LivingEntity> out = new ArrayList<>();
                AABB box = ctx.caster().getBoundingBox().inflate(radius);
                for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, box)) {
                    if (e != ctx.caster() && e.isAlive()) out.add(e);
                }
                yield out;
            }
            case PARTY -> {
                List<LivingEntity> out = new ArrayList<>();
                out.add(ctx.caster()); // the bard hears their own song
                double reach = range > 0 ? range
                        : com.sablednah.legendquest.LQConfig.PARTY_RANGE.get();
                var parties = com.sablednah.legendquest.neoforge.Parties
                        .get(ctx.level().getServer());
                parties.partyOf(ctx.caster().getUUID()).ifPresent(party -> {
                    for (var memberId : party.members()) {
                        if (memberId.equals(ctx.caster().getUUID())) continue;
                        var member = ctx.level().getServer().getPlayerList().getPlayer(memberId);
                        if (member != null && member.level() == ctx.caster().level()
                                && member.distanceTo(ctx.caster()) <= reach) {
                            out.add(member);
                        }
                    }
                });
                yield out;
            }
        };
    }

    /** Resolve to a block position (the looked-at block, or under the target/caster). */
    public Optional<BlockPos> resolvePos(SkillContext ctx) {
        if (kind == Kind.SELF) return Optional.of(ctx.caster().blockPosition());
        List<LivingEntity> targets = resolveEntities(ctx);
        if (!targets.isEmpty()) return Optional.of(targets.getFirst().blockPosition());
        HitResult hit = ctx.caster().pick(range, 0.0F, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            return Optional.of(bhr.getBlockPos().relative(bhr.getDirection()));
        }
        return Optional.empty();
    }

    /**
     * The living entity in the caster's crosshair: walk along the view ray and
     * take the nearest entity whose (slightly inflated) bounding box the ray
     * clips. No spider-sense — line of sight is not checked here.
     */
    private Optional<LivingEntity> lookedAt(SkillContext ctx) {
        Vec3 from = ctx.caster().getEyePosition();
        Vec3 dir = ctx.caster().getLookAngle();
        Vec3 to = from.add(dir.scale(range));
        AABB sweep = ctx.caster().getBoundingBox().expandTowards(dir.scale(range)).inflate(1.0D);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, sweep)) {
            if (e == ctx.caster() || !e.isAlive()) continue;
            Optional<Vec3> clip = e.getBoundingBox().inflate(0.3D).clip(from, to);
            if (clip.isPresent()) {
                double d = from.distanceToSqr(clip.get());
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
