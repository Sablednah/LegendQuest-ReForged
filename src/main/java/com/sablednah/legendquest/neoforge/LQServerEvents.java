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

        // Skill damage (indirect magic) is a true strike: Magic Missile
        // never misses, never crits, and takes no STR bonus — the spell
        // already decided what it does.
        boolean trueStrike = event.getSource()
                .is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC);

        // Opposed d20 DEX test between players/mobs when enabled.
        if (!trueStrike && LQConfig.USE_D20_COMBAT.get()
                && attacker instanceof LivingEntity livingAttacker) {
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
                        victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), kind));
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

    // --- innate boons: runic thrift + forge favour ---

    /** Dwarven/gnomish thrift: some XP levels come back after enchanting. */
    @SubscribeEvent
    static void onEnchant(net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int rebate = (int) Math.round(CharacterService.totalBoon(player,
                b -> b.enchantRebate()));
        if (rebate <= 0) return;
        player.giveExperienceLevels(rebate);
        Feedback.actionBar(player, "&dRunic thrift: " + rebate + " level"
                + (rebate == 1 ? "" : "s") + " rebated.");
    }

    /** Forge favour: a chance that one material survives crafting gear. */
    @SubscribeEvent
    static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getCrafting().isDamageableItem()) return; // gear only
        double chance = Math.min(0.8D, CharacterService.totalBoon(player, b -> b.smithRefund()));
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) return;

        // Refund one of the most numerous smithing materials on the bench.
        ItemStack best = ItemStack.EMPTY;
        int bestCount = 0;
        var counts = new java.util.HashMap<net.minecraft.world.item.Item, Integer>();
        for (int slot = 0; slot < event.getInventory().getContainerSize(); slot++) {
            ItemStack stack = event.getInventory().getItem(slot);
            if (stack.isEmpty() || !isSmithingMaterial(stack)) continue;
            int count = counts.merge(stack.getItem(), 1, Integer::sum);
            if (count > bestCount) {
                bestCount = count;
                best = stack;
            }
        }
        if (best.isEmpty()) return;
        ItemStack refund = best.copyWithCount(1);
        String name = refund.getHoverName().getString();
        player.getInventory().placeItemBackInInventory(refund);
        Feedback.actionBar(player, "&6The forge favours you — a " + name + " survives the making.");
    }

    /** Golden tools as arcane conduits: they harvest like netherite for
     *  those with the boon, at a mana price per block that needed it. */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> ARCANE_CONDUITS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    Identifier.fromNamespaceAndPath(com.sablednah.legendquest.LegendQuest.MODID,
                            "arcane_conduit_tools"));

    /** True when this dig is a boon-powered golden tool doing netherite's job. */
    private static boolean arcaneConduitDig(ServerPlayer player,
            net.minecraft.world.level.block.state.BlockState state) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !held.is(ARCANE_CONDUITS)) return false;
        if (held.isCorrectToolForDrops(state)) return false; // gold managed alone
        double cost = CharacterService.totalBoon(player, b -> b.goldToolMana());
        return cost > 0 && CharacterService.data(player).mana() >= cost;
    }

    @SubscribeEvent
    static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.canHarvest()) return;
        if (event.getEntity() instanceof ServerPlayer player
                && arcaneConduitDig(player, event.getTargetBlock())) {
            event.setCanHarvest(true);
        }
    }

    @SubscribeEvent
    static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!arcaneConduitDig(player, event.getState())) return;
        double cost = CharacterService.totalBoon(player, b -> b.goldToolMana());
        var pc = CharacterService.data(player);
        pc.setMana(Math.max(0, pc.mana() - cost));
    }

    private static boolean isSmithingMaterial(ItemStack stack) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        return path.endsWith("_ingot") || path.endsWith("_scrap")
                || path.equals("diamond") || path.equals("leather") || path.equals("turtle_scute");
    }

    // --- station gates: the old core-ability booleans, enforced ---

    /** Deny wins across race + main + sub, matching the item-rule ethos. */
    private static boolean craftAllowed(ServerPlayer player,
            java.util.function.Predicate<com.sablednah.legendquest.data.CraftRules> allowed) {
        if (!CharacterService.race(player).map(r -> allowed.test(r.craftRules())).orElse(true)) return false;
        if (!CharacterService.mainClass(player).map(c -> allowed.test(c.craftRules())).orElse(true)) return false;
        return CharacterService.subClass(player).map(c -> allowed.test(c.craftRules())).orElse(true);
    }

    /** Which station (if any) this block is, and can the player use it? */
    @SubscribeEvent
    static void onOpenStation(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var state = event.getLevel().getBlockState(event.getPos());
        var block = state.getBlock();
        String refusal = null;
        if (block == net.minecraft.world.level.block.Blocks.CRAFTING_TABLE
                || block == net.minecraft.world.level.block.Blocks.CRAFTER) {
            if (!craftAllowed(player, com.sablednah.legendquest.data.CraftRules::crafting)) {
                refusal = "&cYour hands were never taught the maker's craft.";
            }
        } else if (block == net.minecraft.world.level.block.Blocks.FURNACE
                || block == net.minecraft.world.level.block.Blocks.BLAST_FURNACE
                || block == net.minecraft.world.level.block.Blocks.SMOKER) {
            if (!craftAllowed(player, com.sablednah.legendquest.data.CraftRules::smelting)) {
                refusal = "&cThe fire does not answer to your kind.";
            }
        } else if (block == net.minecraft.world.level.block.Blocks.BREWING_STAND) {
            if (!craftAllowed(player, com.sablednah.legendquest.data.CraftRules::brewing)) {
                refusal = "&cPotioncraft is beyond your discipline.";
            }
        } else if (block == net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE) {
            if (!craftAllowed(player, com.sablednah.legendquest.data.CraftRules::enchanting)) {
                refusal = "&cThe runes squirm away from your gaze.";
            }
        } else if (state.is(net.minecraft.tags.BlockTags.ANVIL)
                || block == net.minecraft.world.level.block.Blocks.GRINDSTONE
                || block == net.minecraft.world.level.block.Blocks.SMITHING_TABLE) {
            if (!craftAllowed(player, com.sablednah.legendquest.data.CraftRules::repairing)) {
                refusal = "&cMending is a craft, and it is not yours.";
            }
        }
        if (refusal != null) {
            event.setCanceled(true);
            Feedback.actionBar(player, refusal);
        }
    }

    @SubscribeEvent
    static void onTame(net.neoforged.neoforge.event.entity.living.AnimalTameEvent event) {
        if (!(event.getTamer() instanceof ServerPlayer player)) return;
        if (!craftAllowed(player, com.sablednah.legendquest.data.CraftRules::taming)) {
            event.setCanceled(true);
            Feedback.actionBar(player, "&cThe beast senses your kind cannot be trusted.");
        }
    }

    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;
        // Arcane conduit: granting the HARVEST isn't enough — gold's Tool
        // component still reports speed 1.0 on blocks above its tier, which
        // made obsidian a lifetime project. Dig like netherite instead.
        if (arcaneConduitDig(player, event.getState())) {
            event.setNewSpeed(Math.max(event.getNewSpeed(), 9.0F));
            return;
        }
        if (!RestrictionEngine.isAllowed(player, ItemRules.Slot.TOOL, tool)) {
            event.setNewSpeed(event.getNewSpeed() * 0.25F); // clumsy, not impossible
        }
    }

    private LQServerEvents() {}
}
