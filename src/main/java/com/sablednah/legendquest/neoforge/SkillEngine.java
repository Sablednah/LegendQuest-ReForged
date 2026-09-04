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

    /** What {@link #toggle} did, or why it did nothing. */
    public enum ToggleResult {
        SWITCHED_ON, SWITCHED_OFF, NOT_KNOWN, NOT_LOADED, ACTIVE_TYPE, FIXED
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

    // --- switching a skill off ---

    /**
     * Can a player switch this skill off at all?
     *
     * <p>Two ways to be un-switchable, and they are different questions. An
     * ACTIVE skill has nothing to silence — it only ever happens because
     * somebody pressed something. A skill whose data says
     * {@code toggleable: false} is one the author meant to be unconditional:
     * a racial drawback is part of the bargain, not a setting.</p>
     */
    public static boolean toggleable(SkillDefinition def) {
        return def.toggleable() && def.type() != SkillType.ACTIVE;
    }

    /**
     * The shortest form of a skill id that a command will still resolve — the
     * bare path unless another skill in the registry shares it. A message that
     * tells a player what to type must not hand them something ambiguous.
     */
    public static String friendlyId(ServerPlayer player, Identifier skillId) {
        long sharing = player.level().registryAccess().lookupOrThrow(LQRegistries.SKILL)
                .listElements()
                .filter(h -> h.key().identifier().getPath().equals(skillId.getPath()))
                .count();
        return sharing == 1 ? skillId.getPath() : skillId.toString();
    }

    /** Flip a skill on or off, taking back what it left behind on the way out. */
    public static ToggleResult toggle(ServerPlayer player, Identifier skillId) {
        SkillGrant grant = grants(player).get(skillId);
        if (grant == null) return ToggleResult.NOT_KNOWN;
        Optional<SkillDefinition> defOpt = definition(player, skillId);
        if (defOpt.isEmpty()) return ToggleResult.NOT_LOADED;
        SkillDefinition def = defOpt.get();
        if (def.type() == SkillType.ACTIVE) return ToggleResult.ACTIVE_TYPE;
        if (!def.toggleable()) return ToggleResult.FIXED;

        PlayerCharacter pc = CharacterService.data(player);
        boolean turningOn = !pc.skillEnabled(skillId);
        pc.setSkillEnabled(skillId, turningOn);
        // Both directions land NOW rather than at the next passive tick: up to
        // three seconds of "did that do anything?" is exactly the doubt the
        // toggle exists to remove.
        if (def.type() == SkillType.PASSIVE) {
            SkillContext ctx = SkillContext.of(player, CharacterService.level(player));
            if (turningOn) {
                // Only if they'd have had it anyway. Firing the effects on the
                // way in is a courtesy against the tick delay, and a courtesy
                // that hands a level-1 character one free application of a
                // level-20 passive is a duplication bug wearing a nice coat.
                if (owns(player, skillId, grant)) runEffects(def, ctx, skillId);
            } else {
                for (SkillEffect effect : def.effects()) {
                    try {
                        effect.revoke(ctx);
                    } catch (Exception e) {
                        LegendQuest.LOGGER.error("Skill {} effect {} failed to revoke", skillId, effect.type(), e);
                    }
                }
            }
        }
        return turningOn ? ToggleResult.SWITCHED_ON : ToggleResult.SWITCHED_OFF;
    }

    /**
     * Every switched-off skill the player currently has, in id order — the
     * login reminder's material. Only skills they actually hold: a preference
     * kept from a class they no longer play is nobody's business today.
     */
    public static java.util.List<java.util.Map.Entry<Identifier, SkillDefinition>> disabled(ServerPlayer player) {
        java.util.List<java.util.Map.Entry<Identifier, SkillDefinition>> out = new java.util.ArrayList<>();
        PlayerCharacter pc = CharacterService.data(player);
        grants(player).entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (pc.skillEnabled(entry.getKey())) return;
                    definition(player, entry.getKey()).ifPresent(def ->
                            out.add(java.util.Map.entry(entry.getKey(), def)));
                });
        return out;
    }

    // --- active use ---

    public static UseResult use(ServerPlayer player, Identifier skillId) {
        PlayerCharacter pc = CharacterService.data(player);
        SkillGrant grant = grants(player).get(skillId);
        if (grant == null) {
            Feedback.actionBar(player, Lang.get("msg.skill.not_known"));
            return UseResult.NOT_KNOWN;
        }
        Optional<SkillDefinition> defOpt = definition(player, skillId);
        if (defOpt.isEmpty()) {
            Feedback.actionBar(player, Lang.fmt("msg.skill.not_loaded", "id", skillId));
            LegendQuest.LOGGER.warn("Player {} has a grant for unknown skill {}", player.getName(), skillId);
            return UseResult.NOT_LOADED;
        }
        SkillDefinition def = defOpt.get();
        if (def.type() != SkillType.ACTIVE) {
            // Naming the toggle here is the discovery path for a passive
            // somebody wants rid of — but only where there IS one: telling a
            // player to switch off a drawback that cannot be switched off is
            // worse than saying nothing.
            Feedback.actionBar(player, toggleable(def)
                    ? Lang.fmt("msg.skill.not_active_type", "skill", def.name(), "type", def.type(),
                            "id", friendlyId(player, skillId))
                    : Lang.fmt("msg.skill.not_active_fixed", "skill", def.name(), "type", def.type()));
            return UseResult.NOT_ACTIVE;
        }
        if (CharacterService.level(player) < grant.level()) {
            Feedback.actionBar(player, Lang.fmt("msg.skill.level_locked", "skill", def.name(), "level", grant.level()));
            return UseResult.LEVEL_LOCKED;
        }
        if (grant.cost() > 0 && !pc.hasPurchased(skillId)) {
            Feedback.actionBar(player, Lang.fmt("msg.skill.not_purchased", "skill", def.name(), "id", skillId, "cost", grant.cost()));
            return UseResult.NOT_PURCHASED;
        }
        // The grant's karma band gates the CAST too — a suspended Holy Light
        // must not keep firing from a stale loadout slot.
        if (!grant.karmaAllows(pc.karma())) {
            Feedback.actionBar(player, Lang.fmt(pc.karma() < grant.karmaMin()
                    ? "msg.skill.soul_dark" : "msg.skill.soul_bright", "skill", def.name()));
            return UseResult.KARMA_BLOCKED;
        }

        long now = System.currentTimeMillis();
        SkillPhase phase = SkillPhase.at(now, pc.lastUse(skillId), def.timing());
        if (phase != SkillPhase.READY) {
            long waitMs = SkillPhase.remainingMs(now, pc.lastUse(skillId), def.timing());
            Feedback.actionBar(player, Lang.fmt("msg.skill.phase_wait", "skill", def.name(), "phase", phase.name().toLowerCase(), "sec", waitMs / 1000 + 1));
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
                Feedback.actionBar(player, Lang.fmt("msg.skill.building", "skill", def.name()));
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
            Feedback.actionBar(player, Lang.fmt("msg.skill.not_virtuous", "skill", name));
            return UseResult.KARMA_BLOCKED;
        }
        if (costs.karmaRequired() < 0 && pc.karma() > costs.karmaRequired()) {
            Feedback.actionBar(player, Lang.fmt("msg.skill.not_wicked", "skill", name));
            return UseResult.KARMA_BLOCKED;
        }
        if (pc.mana() < costs.manaCost()) {
            Feedback.actionBar(player, Lang.fmt("msg.skill.no_mana", "skill", name, "have", (int) pc.mana(), "need", costs.manaCost()));
            return UseResult.NO_MANA;
        }
        if (costs.consumes().isPresent()) {
            ItemStack needed = new ItemStack(costs.consumes().get(), costs.consumesQty());
            int slot = player.getInventory().findSlotMatchingItem(needed);
            if (slot < 0 || player.getInventory().getItem(slot).getCount() < costs.consumesQty()) {
                Feedback.actionBar(player, Lang.fmt("msg.skill.needs_item", "skill", name, "qty", costs.consumesQty(), "item", needed.getHoverName().getString()));
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
            int before = CharacterService.level(player);
            CharacterService.data(player).mainClassId()
                    .ifPresent(cls -> CharacterService.data(player).addXp(cls, def.costs().xpAward()));
            CharacterService.afterXpChange(player, before);
        }
    }

    private static void runEffects(SkillDefinition def, SkillContext ctx, Identifier skillId) {
        // One tag for the whole skill rather than one per effect: a spell that
        // damages, ignites and blinds is a single act of aggression, and the
        // server log should read that way too. Tagged before the effects run,
        // so a skill that kills its target outright still tags the caster.
        if (def.effects().stream().anyMatch(SkillEffect::hostile)) {
            CombatTagging.skill(ctx.caster(), skillId);
        }
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
                    Feedback.actionBar(player, Lang.fmt("msg.skill.fades_soon", "skill", def.get().name()));
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
        PlayerCharacter pc = CharacterService.data(player);
        for (var entry : grants(player).entrySet()) {
            if (!owns(player, entry.getKey(), entry.getValue())) continue;
            if (!pc.skillEnabled(entry.getKey())) continue; // switched off by its owner
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
            if (!pc.skillEnabled(skillId)) continue; // switched off by its owner
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
