package com.sablednah.legendquest.neoforge;

import com.sablednah.legendquest.LQConfig;
import com.sablednah.legendquest.character.PlayerCharacter;
import com.sablednah.legendquest.core.Mechanics;
import com.sablednah.legendquest.core.Stat;
import com.sablednah.legendquest.data.ItemRules;
import com.sablednah.legendquest.network.CombatIndicatorPayload;
import com.sablednah.legendquest.skills.TriggerSpec;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus handlers: character lifecycle, karma, XP, mana, combat and item
 * restrictions. Static methods; registered as a class in the mod constructor.
 */
public final class LQServerEvents {

    // --- lifecycle ---

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CharacterService.ensureInitialised(player);
        CharacterService.refresh(player);
        HandbookSync.send(player);
        var pc = CharacterService.data(player);
        String race = CharacterService.race(player).map(r -> r.name()).orElse("Undecided");
        String cls = CharacterService.mainClass(player).map(c -> c.name()).orElse("Citizen");
        Feedback.chat(player, "&6[LegendQuest]&r " + race + " " + cls
                + " &7(level " + CharacterService.level(player) + ", "
                + CharacterService.karmaName(pc.karma()) + ")");
    }

    // --- ticking: mana regen + skill engine ---

    private static int tickCounter = 0;

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        SkillEngine.tick(event.getServer());

        if (++tickCounter >= 20) { // once a second
            tickCounter = 0;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                PlayerCharacter pc = CharacterService.data(player);
                double max = CharacterService.maxMana(player);
                if (pc.mana() < max) {
                    pc.setMana(Math.min(max, pc.mana() + CharacterService.manaPerSecond(player)));
                }
                penaliseDisallowedArmour(player);
                CharacterSync.send(player); // mana + cooldowns tick visibly on modded clients
            }
        }
    }

    /**
     * Wearing armour your character can't handle doesn't confiscate it — it
     * hampers you: Slowness scaling with how many bad pieces you wear, plus
     * Mining Fatigue, because nothing says "take it off" like Mining Fatigue.
     * Re-applied each second with a 3s duration so it lapses ~2s after the
     * last offending piece comes off, and re-evaluated against the current
     * race/class so a class change bites within a second.
     */
    private static void penaliseDisallowedArmour(ServerPlayer player) {
        int pieces = RestrictionEngine.disallowedArmourCount(player);
        if (pieces <= 0) return;
        int slownessAmp = Math.min(pieces - 1, 2); // I..III
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, slownessAmp, true, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 0, true, false, true));
    }

    // --- karma + trigger: kills ---

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        LivingEntity victim = event.getEntity();

        long karma;
        // Order matters: villagers before Enemy (witches etc.), players first.
        if (victim instanceof Player) karma = LQConfig.KARMA_KILL_PLAYER.get();
        else if (victim instanceof AbstractVillager) karma = LQConfig.KARMA_KILL_VILLAGER.get();
        else if (victim instanceof Enemy) karma = LQConfig.KARMA_KILL_MONSTER.get();
        else if (victim instanceof Animal) karma = LQConfig.KARMA_KILL_ANIMAL.get();
        else karma = 0;
        if (karma != 0) CharacterService.data(killer).addKarma(karma);

        SkillEngine.trigger(killer, TriggerSpec.Kind.KILL, victim);
    }

    /** Class XP piggybacks on the mob's vanilla XP value (orbs still drop). */
    @SubscribeEvent
    static void onXpDrop(LivingExperienceDropEvent event) {
        if (event.getAttackingPlayer() instanceof ServerPlayer killer) {
            awardKillXp(killer, event.getDroppedExperience(), 100);

            // Party XP share: nearby members get a cut, full price for the killer.
            var parties = Parties.get(killer.level().getServer());
            parties.partyOf(killer.getUUID()).ifPresent(party -> {
                int share = LQConfig.PARTY_XP_SHARE.get();
                if (share <= 0) return;
                int range = LQConfig.PARTY_RANGE.get();
                for (UUID memberId : party.members()) {
                    if (memberId.equals(killer.getUUID())) continue;
                    ServerPlayer member = killer.level().getServer().getPlayerList().getPlayer(memberId);
                    if (member == null || member.level() != killer.level()) continue;
                    if (member.distanceTo(killer) > range) continue;
                    awardKillXp(member, event.getDroppedExperience(), share);
                }
            });
        }
    }

    private static void awardKillXp(ServerPlayer player, int droppedXp, int percent) {
        PlayerCharacter pc = CharacterService.data(player);
        pc.mainClassId().ifPresent(cls -> {
            double adjust = CharacterService.race(player)
                    .map(r -> r.progression().xpAdjustKill()).orElse(0.0D)
                    + CharacterService.mainClass(player)
                            .map(c -> c.progression().xpAdjustKill()).orElse(0.0D);
            long amount = Math.round(droppedXp * (1.0D + adjust / 100.0D) * percent / 100.0D);
            if (amount <= 0) return;
            int before = CharacterService.level(player);
            pc.addXp(cls, amount);
            int after = CharacterService.level(player);
            if (after > before) {
                CharacterService.refresh(player);
                Feedback.chat(player, "&6[LegendQuest]&a Level up! You are now level " + after + ".");
            }
        });
    }

    // --- combat: d20 hit/dodge, weapon gate, triggers ---

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        var attacker = event.getSource().getEntity();

        // No friendly fire inside a party.
        if (LQConfig.BLOCK_PARTY_PVP.get()
                && attacker instanceof ServerPlayer pa && victim instanceof ServerPlayer pv
                && Parties.get(pa.level().getServer()).sameParty(pa.getUUID(), pv.getUUID())) {
            event.setCanceled(true);
            Feedback.actionBar(pa, "&7" + pv.getName().getString() + " is in your party.");
            return;
        }

        // Weapon gate: a disallowed weapon hits like a fist.
        if (attacker instanceof ServerPlayer playerAttacker) {
            ItemStack weapon = playerAttacker.getMainHandItem();
            if (!RestrictionEngine.isAllowed(playerAttacker, ItemRules.Slot.WEAPON, weapon)) {
                event.setAmount(Math.min(event.getAmount(), 1.0F));
                Feedback.actionBar(playerAttacker, "&cYou fumble with the unfamiliar "
                        + weapon.getHoverName().getString() + "...");
                indicate(playerAttacker, victim, CombatIndicatorPayload.FUMBLE);
            }
        }

        // Opposed d20 DEX test between players/mobs when enabled.
        if (LQConfig.USE_D20_COMBAT.get() && attacker instanceof LivingEntity livingAttacker) {
            Integer attackMod = attacker instanceof ServerPlayer p
                    ? CharacterService.statModifier(p, Stat.DEX) : null;
            Integer dodgeMod = victim instanceof ServerPlayer p
                    ? CharacterService.statModifier(p, Stat.DEX) : null;
            if (attackMod != null || dodgeMod != null) {
                var outcome = Mechanics.opposedAttack(victim.getRandom()::nextInt,
                        attackMod != null ? attackMod : 0,
                        dodgeMod != null ? dodgeMod : 0);
                if (outcome == Mechanics.AttackOutcome.MISS) {
                    event.setCanceled(true);
                    if (victim instanceof ServerPlayer p) Feedback.actionBar(p, "&aDodged!");
                    if (attacker instanceof ServerPlayer p) {
                        Feedback.actionBar(p, "&7They dodged.");
                        indicate(p, victim, CombatIndicatorPayload.MISS);
                    }
                    return;
                }
                if (attacker instanceof ServerPlayer p) {
                    if (outcome == Mechanics.AttackOutcome.CRIT) {
                        // A natural 20 hits half again as hard. Tabletop law.
                        event.setAmount(event.getAmount() * 1.5F);
                        indicate(p, victim, CombatIndicatorPayload.CRIT);
                    } else {
                        indicate(p, victim, CombatIndicatorPayload.HIT);
                    }
                }
            }
            // STR bonus for player melee (direct hits only: attacker == direct entity).
            if (attacker instanceof ServerPlayer p && event.getSource().getDirectEntity() == attacker) {
                int strMod = CharacterService.statModifier(p, Stat.STR);
                if (strMod != 0) event.setAmount(Math.max(0.0F, event.getAmount() + strMod));
            }
        }

        // Triggered skills, after the hit is real.
        if (attacker instanceof ServerPlayer p && !event.isCanceled()
                && event.getSource().getDirectEntity() == attacker) {
            SkillEngine.trigger(p, TriggerSpec.Kind.MELEE_HIT, victim);
        }
        if (victim instanceof ServerPlayer p && !event.isCanceled()) {
            SkillEngine.trigger(p, TriggerSpec.Kind.HURT,
                    attacker instanceof LivingEntity la ? la : null);
        }
    }

    /** Floating combat word above the victim, for the attacker's eyes.
     *  Vanilla clients never registered the channel; the send just drops. */
    private static void indicate(ServerPlayer attacker, LivingEntity victim, int kind) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(attacker,
                new CombatIndicatorPayload(victim.getX(),
                        victim.getY() + victim.getBbHeight() * 0.9, victim.getZ(), kind));
    }

    // --- restrictions: armour and tools ---

    @SubscribeEvent
    static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        EquipmentSlot slot = event.getSlot();
        if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return;
        ItemStack stack = event.getTo();
        if (stack.isEmpty()) return;
        if (!RestrictionEngine.isAllowed(player, ItemRules.Slot.ARMOUR, stack)) {
            // You may wear it — you'll just regret it. Penalties apply on the
            // next penalty tick; this is the up-front warning.
            Feedback.actionBar(player, "&c" + stack.getHoverName().getString()
                    + " was not made for your kind — it will slow and tire you.");
        }
    }

    // --- item-bound skills (the old /link right-click trigger) ---

    /** Debounce: interact events fire per hand and per target under the cursor. */
    private static final java.util.Map<java.util.UUID, Long> lastBindFire = new java.util.HashMap<>();

    /**
     * @return true when the click hit a bound item and should suppress the
     *         vanilla use (no placing blocks / eating with a skill focus).
     *         Debounced so the block+item event pair costs one activation.
     */
    private static boolean fireBoundSkill(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return false;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return false;
        Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        var pc = CharacterService.data(player);

        // The loadout item outranks a plain binding on the same item type.
        boolean isLoadoutItem = pc.loadoutItem().map(itemId::equals).orElse(false);
        var bound = isLoadoutItem ? pc.selectedSkill() : pc.bindingFor(itemId);
        if (bound.isEmpty() && !isLoadoutItem) return false;

        long now = System.currentTimeMillis();
        Long last = lastBindFire.get(player.getUUID());
        if (last == null || now - last >= 250) {
            lastBindFire.put(player.getUUID(), now);
            if (isLoadoutItem && player.isShiftKeyDown()) {
                pc.cycleLoadout();
                Feedback.actionBar(player, loadoutBar(player, pc, "&e"));
            } else if (bound.isPresent()) {
                var result = SkillEngine.use(player, bound.get());
                if (isLoadoutItem) {
                    // The bar is the feedback: selected skill coloured by
                    // outcome (sent after use()'s message, so it wins).
                    Feedback.actionBar(player, loadoutBar(player, pc, resultColour(result)));
                }
            } else {
                Feedback.actionBar(player, "&7Loadout is empty — /loadout add <skill>");
            }
        }
        return true;
    }

    static String resultColour(SkillEngine.UseResult result) {
        return switch (result) {
            case FIRED -> "&a";                    // green: away it goes
            case NO_MANA -> "&9";                  // blue: mana problem
            case NO_ITEM -> "&b";                  // cyan: missing reagent
            case NOT_READY -> "&c";                // red: cooldown/phase
            default -> "&c";
        };
    }

    /**
     * "Heal  ◆Blink◆  Smite 3s" — the selected skill stands out in
     * {@code selectedColour}, and anything not READY shows seconds left.
     */
    static String loadoutBar(ServerPlayer player, PlayerCharacter pc, String selectedColour) {
        StringBuilder sb = new StringBuilder("&6");
        var skills = pc.loadout();
        long now = System.currentTimeMillis();
        for (int n = 0; n < skills.size(); n++) {
            Identifier id = skills.get(n);
            var def = SkillEngine.definition(player, id);
            String name = def.map(d -> d.name()).orElse(id.getPath());
            String suffix = "";
            if (def.isPresent()) {
                long waitMs = com.sablednah.legendquest.core.SkillPhase.remainingMs(
                        now, pc.lastUse(id), def.get().timing());
                if (waitMs > 0) suffix = " " + (waitMs / 1000 + 1) + "s";
            }
            if (n > 0) sb.append("  ");
            if (n == pc.loadoutIndex()) {
                sb.append(selectedColour).append("&l◆").append(name).append(suffix).append("◆&r&6");
            } else {
                sb.append(suffix.isEmpty() ? "&7" : "&8").append(name).append(suffix).append("&6");
            }
        }
        return sb.toString();
    }

    @SubscribeEvent
    static void onRightClickItem(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player
                && fireBoundSkill(player, event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && fireBoundSkill(player, event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;
        if (!RestrictionEngine.isAllowed(player, ItemRules.Slot.TOOL, tool)) {
            event.setNewSpeed(event.getNewSpeed() * 0.25F); // clumsy, not impossible
        }
    }

    private LQServerEvents() {}
}
