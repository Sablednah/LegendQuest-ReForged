package com.sablednah.legendquest.neoforge;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sablednah.legendquest.LQConfig;
import com.sablednah.legendquest.LQRegistries;
import com.sablednah.legendquest.LegendQuest;
import com.sablednah.legendquest.character.PlayerCharacter;
import com.sablednah.legendquest.core.SkillPhase;
import com.sablednah.legendquest.data.SkillCosts;
import com.sablednah.legendquest.data.SkillDefinition;
import com.sablednah.legendquest.data.SkillGrant;
import com.sablednah.legendquest.skills.SkillContext;
import com.sablednah.legendquest.skills.SkillEffect;
import com.sablednah.legendquest.skills.SkillType;
import com.sablednah.legendquest.skills.TriggerSpec;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Activation, costs, cooldowns and effect execution.
 *
 * <p>Skill definitions are immutable registry entries; all mutable state is
 * per-player (the attachment) or in {@link #PENDING} — buildup/delay casts
 * waiting to fire, drained on the server tick.</p>
 */
public final class SkillEngine {

    /** Why a cast fired or fizzled — lets callers colour their feedback. */
    public enum UseResult {
        FIRED, NOT_KNOWN, NOT_LOADED, NOT_ACTIVE, LEVEL_LOCKED, NOT_PURCHASED,
        NOT_READY, KARMA_BLOCKED, NO_MANA, NO_ITEM;

        public boolean fired() {
            return this == FIRED;
        }
    }

    /** A cast that has been paid for and is waiting out buildup + delay. */
    private record Pending(UUID player, Identifier skillId, long fireAtMs) {}

    private static final List<Pending> PENDING = new CopyOnWriteArrayList<>();

    // --- grants ---

    /**
     * All skills this player's race and classes grant. Merge order race →
     * main class → sub class; later sources override on the same id (the old
     * version's nondeterministic HashSet collision, made deterministic).
     */
    public static Map<Identifier, SkillGrant> grants(ServerPlayer player) {
        Map<Identifier, SkillGrant> out = new HashMap<>();
        CharacterService.race(player).ifPresent(r -> out.putAll(r.skills()));
        CharacterService.mainClass(player).ifPresent(c -> out.putAll(c.skills()));
        CharacterService.subClass(player).ifPresent(c -> out.putAll(c.skills()));
        CharacterService.feats(player).forEach(f -> out.putAll(f.skills()));
        return out;
    }

    public static Optional<SkillDefinition> definition(ServerPlayer player, Identifier skillId) {
        return player.level().registryAccess().lookupOrThrow(LQRegistries.SKILL)
                .get(ResourceKey.create(LQRegistries.SKILL, skillId))
                .map(ref -> ref.value());
    }

    /** May this player use the skill at all (granted + level + purchase)? */
    public static boolean owns(ServerPlayer player, Identifier skillId, SkillGrant grant) {
        if (CharacterService.level(player) < grant.level()) return false;
        if (grant.cost() > 0 && !CharacterService.data(player).hasPurchased(skillId)) return false;
        // Karma bands suspend, never forget: drift out and the skill sleeps,
        // repent (or fall) back into the band and it wakes.
        if (!grant.karmaAllows(CharacterService.data(player).karma())) return false;
        return true;
    }

    // --- active use ---

    public static UseResult use(ServerPlayer player, Identifier skillId) {
        PlayerCharacter pc = CharacterService.data(player);
        SkillGrant grant = grants(player).get(skillId);
        if (grant == null) {
            Feedback.actionBar(player, "&cYou don't know that skill.");
            return UseResult.NOT_KNOWN;
        }
        Optional<SkillDefinition> defOpt = definition(player, skillId);
        if (defOpt.isEmpty()) {
            Feedback.actionBar(player, "&cSkill '" + skillId + "' is not loaded — tell an admin.");
            LegendQuest.LOGGER.warn("Player {} has a grant for unknown skill {}", player.getName(), skillId);
            return UseResult.NOT_LOADED;
        }
        SkillDefinition def = defOpt.get();
        if (def.type() != SkillType.ACTIVE) {
            Feedback.actionBar(player, "&e" + def.name() + " is " + def.type() + " — it works on its own.");
            return UseResult.NOT_ACTIVE;
        }
        if (CharacterService.level(player) < grant.level()) {
            Feedback.actionBar(player, "&c" + def.name() + " unlocks at level " + grant.level() + ".");
            return UseResult.LEVEL_LOCKED;
        }
        if (grant.cost() > 0 && !pc.hasPurchased(skillId)) {
            Feedback.actionBar(player, "&c" + def.name() + " must be bought first: /skill buy "
                    + skillId + " (" + grant.cost() + " points)");
            return UseResult.NOT_PURCHASED;
        }
        // The grant's karma band gates the CAST too — a suspended Holy Light
        // must not keep firing from a stale loadout slot.
        if (!grant.karmaAllows(pc.karma())) {
            Feedback.actionBar(player, pc.karma() < grant.karmaMin()
                    ? "&5" + def.name() + " has left you — your soul is too dark."
                    : "&5" + def.name() + " has left you — your soul is too bright.");
            return UseResult.KARMA_BLOCKED;
        }

        long now = System.currentTimeMillis();
        SkillPhase phase = SkillPhase.at(now, pc.lastUse(skillId), def.timing());
        if (phase != SkillPhase.READY) {
            long waitMs = SkillPhase.remainingMs(now, pc.lastUse(skillId), def.timing());
            Feedback.actionBar(player, "&c" + def.name() + " is " + phase.name().toLowerCase()
                    + " — ready in " + (waitMs / 1000 + 1) + "s");
            return UseResult.NOT_READY;
        }

        UseResult paid = payCosts(player, pc, def.costs(), def.name());
        if (paid != UseResult.FIRED) return paid;

        pc.stampUse(skillId, now);
        long fireAt = now + def.timing().buildupMs() + def.timing().delayMs();
        if (fireAt <= now) {
            fire(player, skillId, def);
        } else {
            if (def.timing().buildupMs() > 0) {
                Feedback.actionBar(player, "&d" + def.name() + " building up...");
            }
            // The charge cue: a rising hum now, swirling glyphs until it
            // fires (see tick) — no more "did that even work?".
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F);
            PENDING.add(new Pending(player.getUUID(), skillId, fireAt));
        }
        return UseResult.FIRED;
    }

    private static UseResult payCosts(ServerPlayer player, PlayerCharacter pc, SkillCosts costs, String name) {
        // Karma gate: positive required = must be at least this good;
        // negative = must be at least this evil (the old convention).
        if (costs.karmaRequired() > 0 && pc.karma() < costs.karmaRequired()) {
            Feedback.actionBar(player, "&cYou are not virtuous enough for " + name + ".");
            return UseResult.KARMA_BLOCKED;
        }
        if (costs.karmaRequired() < 0 && pc.karma() > costs.karmaRequired()) {
            Feedback.actionBar(player, "&cYou are not wicked enough for " + name + ".");
            return UseResult.KARMA_BLOCKED;
        }
        if (pc.mana() < costs.manaCost()) {
            Feedback.actionBar(player, "&9Not enough mana for " + name + " ("
                    + (int) pc.mana() + "/" + costs.manaCost() + ").");
            return UseResult.NO_MANA;
        }
        if (costs.consumes().isPresent()) {
            ItemStack needed = new ItemStack(costs.consumes().get(), costs.consumesQty());
            int slot = player.getInventory().findSlotMatchingItem(needed);
            if (slot < 0 || player.getInventory().getItem(slot).getCount() < costs.consumesQty()) {
                Feedback.actionBar(player, "&c" + name + " needs "
                        + costs.consumesQty() + "x " + needed.getHoverName().getString() + ".");
                return UseResult.NO_ITEM;
            }
            player.getInventory().getItem(slot).shrink(costs.consumesQty());
        }
        pc.setMana(pc.mana() - costs.manaCost());
        pc.addKarma(costs.karmaReward() - costs.karmaCost());
        return UseResult.FIRED;
    }

    private static void fire(ServerPlayer player, Identifier skillId, SkillDefinition def) {
        SkillContext ctx = SkillContext.of(player, CharacterService.level(player));
        runEffects(def, ctx, skillId);
        if (def.costs().xpAward() > 0) {
            CharacterService.data(player).mainClassId()
                    .ifPresent(cls -> CharacterService.data(player).addXp(cls, def.costs().xpAward()));
        }
    }

    private static void runEffects(SkillDefinition def, SkillContext ctx, Identifier skillId) {
        for (SkillEffect effect : def.effects()) {
            try {
                effect.apply(ctx);
            } catch (Exception e) {
                // One bad effect must not kill the tick or the rest of the skill.
                LegendQuest.LOGGER.error("Skill {} effect {} failed", skillId, effect.type(), e);
            }
        }
    }

    // --- ticking (pending casts + passives) ---

    private static long lastPassiveRun = 0;

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        com.sablednah.legendquest.skills.effects.LQEffects.RunCommand.tickUndos(server);
        com.sablednah.legendquest.skills.effects.LQEffects.Sound.tickStops(server);

        if (!PENDING.isEmpty()) {
            Iterator<Pending> it = PENDING.iterator();
            for (Pending p : PENDING) {
                if (p.fireAtMs() > now) {
                    // Still charging: swirl glyphs around the caster.
                    ServerPlayer caster = server.getPlayerList().getPlayer(p.player());
                    if (caster != null) {
                        caster.level().sendParticles(
                                net.minecraft.core.particles.ParticleTypes.ENCHANT,
                                caster.getX(), caster.getY() + 1.2D, caster.getZ(),
                                3, 0.35D, 0.4D, 0.35D, 0.05D);
                    }
                    continue;
                }
                PENDING.remove(p);
                ServerPlayer player = server.getPlayerList().getPlayer(p.player());
                if (player == null) continue; // logged out mid-cast; cost stays spent
                definition(player, p.skillId()).ifPresent(def -> fire(player, p.skillId(), def));
            }
        }

        if (now - lastPassiveRun >= LQConfig.PASSIVE_TICK_MS.get()) {
            lastPassiveRun = now;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                passiveTick(player);
            }
        }
    }

    // --- duration warnings: "no time to scramble for Featherlight" ---

    private static final java.util.Map<java.util.UUID, java.util.Set<String>> DURATION_WARNED =
            new java.util.HashMap<>();

    /** Called once a second per player: a heads-up 5s before an ACTIVE
     *  duration skill (flight!) runs out, with an urgent little pling. */
    public static void warnFadingDurations(ServerPlayer player) {
        long now = System.currentTimeMillis();
        PlayerCharacter pc = CharacterService.data(player);
        var warned = DURATION_WARNED.computeIfAbsent(player.getUUID(), u -> new java.util.HashSet<>());
        for (var entry : grants(player).entrySet()) {
            var def = definition(player, entry.getKey());
            if (def.isEmpty() || def.get().timing().durationMs() <= 0) continue;
            String key = entry.getKey().toString();
            long last = pc.lastUse(entry.getKey());
            var timing = def.get().timing();
            if (SkillPhase.at(now, last, timing) == SkillPhase.ACTIVE) {
                long remaining = last + timing.buildupMs() + timing.delayMs()
                        + timing.durationMs() - now;
                if (remaining <= 5500 && warned.add(key)) {
                    Feedback.actionBar(player, "&e⌛ " + def.get().name()
                            + " fades in 5 seconds!");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.6F);
                }
            } else {
                warned.remove(key);
            }
        }
    }

    private static void passiveTick(ServerPlayer player) {
        for (var entry : grants(player).entrySet()) {
            if (!owns(player, entry.getKey(), entry.getValue())) continue;
            definition(player, entry.getKey()).ifPresent(def -> {
                if (def.type() == SkillType.PASSIVE) {
                    runEffects(def, SkillContext.of(player, CharacterService.level(player)), entry.getKey());
                }
            });
        }
    }

    // --- triggered skills ---

    /**
     * Fire every granted TRIGGERED skill matching {@code kind}. The trigger's
     * other party rides in the context as the preferred target. Cooldowns
     * apply per skill via the same phase machine as active use.
     */
    public static void trigger(ServerPlayer player, TriggerSpec.Kind kind, LivingEntity other) {
        PlayerCharacter pc = CharacterService.data(player);
        long now = System.currentTimeMillis();
        for (var entry : grants(player).entrySet()) {
            Identifier skillId = entry.getKey();
            if (!owns(player, skillId, entry.getValue())) continue;
            Optional<SkillDefinition> defOpt = definition(player, skillId);
            if (defOpt.isEmpty()) continue;
            SkillDefinition def = defOpt.get();
            if (def.type() != SkillType.TRIGGERED || def.trigger().isEmpty()) continue;
            TriggerSpec trigger = def.trigger().get();
            if (trigger.on() != kind) continue;
            if (SkillPhase.at(now, pc.lastUse(skillId), def.timing()) != SkillPhase.READY) continue;
            if (player.getRandom().nextDouble() * 100.0D >= trigger.chance()) continue;
            pc.stampUse(skillId, now);
            SkillContext ctx = new SkillContext(player, player.level(),
                    CharacterService.level(player), other);
            runEffects(def, ctx, skillId);
        }
    }

    private SkillEngine() {}
}
