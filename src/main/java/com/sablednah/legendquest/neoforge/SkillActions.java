package com.sablednah.legendquest.neoforge;

import com.sablednah.legendquest.character.PlayerCharacter;
import com.sablednah.legendquest.network.SkillActionPayload;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side execution of hotkey skill actions. Deliberately routes through
 * the same {@link SkillEngine} and action-bar feedback as the right-click
 * path — a keybind is just a faster finger, never a different rule set.
 */
public final class SkillActions {

    public static void handle(ServerPlayer player, int action, int slot, String id) {
        PlayerCharacter pc = CharacterService.data(player);
        switch (action) {
            case SkillActionPayload.CYCLE -> {
                if (cycleToUsable(player, pc).isEmpty()) {
                    Feedback.actionBar(player, Lang.get("msg.loadout.empty"));
                } else {
                    Feedback.actionBar(player, LQServerEvents.loadoutBar(player, pc, "&e"));
                }
            }
            case SkillActionPayload.USE_SELECTED -> useSelected(player, pc);
            case SkillActionPayload.BUY_SKILL -> {
                var skillId = net.minecraft.resources.Identifier.tryParse(id);
                if (skillId != null) CharacterActions.buySkill(player, skillId);
            }
            case SkillActionPayload.BUY_FEAT -> {
                var featId = net.minecraft.resources.Identifier.tryParse(id);
                if (featId != null) CharacterActions.buyFeat(player, featId);
            }
            case SkillActionPayload.BUY_STAT -> {
                for (com.sablednah.legendquest.core.Stat stat
                        : com.sablednah.legendquest.core.Stat.values()) {
                    if (stat.key().equals(id)) {
                        CharacterActions.buyStat(player, stat);
                        break;
                    }
                }
            }
            case SkillActionPayload.USE_SLOT -> {
                if (slot < 0 || slot >= pc.loadout().size()) {
                    Feedback.actionBar(player, Lang.fmt("msg.loadout.empty_slot", "slot", slot + 1));
                    return;
                }
                pc.selectLoadout(slot);
                useSelected(player, pc);
            }
            default -> { }
        }
    }

    /** Drag & drop from the skills panel; re-syncs immediately so the GUI
     *  never shows a stale loadout for the up-to-a-second between ticks. */
    public static void handleLoadoutEdit(ServerPlayer player,
            com.sablednah.legendquest.network.LoadoutEditPayload payload) {
        PlayerCharacter pc = CharacterService.data(player);
        var skillId = net.minecraft.resources.Identifier.tryParse(payload.skillId());
        switch (payload.action()) {
            case com.sablednah.legendquest.network.LoadoutEditPayload.ADD -> {
                if (skillId == null) return;
                if (CharacterActions.loadoutAdd(player, skillId) && payload.to() >= 0) {
                    pc.moveLoadout(pc.loadout().size() - 1, Math.min(payload.to(), pc.loadout().size() - 1));
                }
            }
            case com.sablednah.legendquest.network.LoadoutEditPayload.REMOVE -> {
                if (skillId == null) return;
                CharacterActions.loadoutRemove(player, skillId);
            }
            case com.sablednah.legendquest.network.LoadoutEditPayload.MOVE ->
                    pc.moveLoadout(payload.from(), payload.to());
            case com.sablednah.legendquest.network.LoadoutEditPayload.SELECT ->
                    pc.selectLoadout(payload.to());
            case com.sablednah.legendquest.network.LoadoutEditPayload.SET_ITEM -> {
                if (payload.skillId().isEmpty()) {
                    pc.setLoadoutItem(java.util.Optional.empty());
                    Feedback.chat(player, Lang.get("msg.loadout.unbound"));
                } else if (skillId != null) { // an ITEM id in this action
                    if (pc.bindingFor(skillId).isPresent()) {
                        Feedback.chat(player, Lang.get("msg.loadout.bind_clash"));
                    } else {
                        pc.setLoadoutItem(java.util.Optional.of(skillId));
                        Feedback.chat(player, Lang.fmt("msg.loadout.spellbook_set", "item", itemName(skillId)));
                    }
                }
            }
            default -> { }
        }
        CharacterSync.send(player);
    }

    private static String itemName(net.minecraft.resources.Identifier itemId) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId);
        return item.map(i -> new net.minecraft.world.item.ItemStack(i).getHoverName().getString())
                .orElse(itemId.toString());
    }

    /**
     * Cycle, skipping entries the character can't currently use (karma
     * suspension, lost levels). If NOTHING in the loadout is usable, land
     * one step on anyway so the bar still visibly moves.
     */
    static java.util.Optional<net.minecraft.resources.Identifier> cycleToUsable(
            ServerPlayer player, PlayerCharacter pc) {
        var grants = SkillEngine.grants(player);
        int size = pc.loadout().size();
        java.util.Optional<net.minecraft.resources.Identifier> selected = java.util.Optional.empty();
        for (int step = 0; step < size; step++) {
            selected = pc.cycleLoadout();
            if (selected.isEmpty()) return selected; // empty loadout
            var grant = grants.get(selected.get());
            if (grant != null && SkillEngine.owns(player, selected.get(), grant)) return selected;
        }
        return selected; // full lap, everything asleep — stay where we landed
    }

    private static void useSelected(ServerPlayer player, PlayerCharacter pc) {
        var selected = pc.selectedSkill();
        if (selected.isEmpty()) {
            Feedback.actionBar(player, Lang.get("msg.loadout.empty"));
            return;
        }
        var result = SkillEngine.use(player, selected.get());
        Feedback.actionBar(player, LQServerEvents.loadoutBar(player, pc,
                LQServerEvents.resultColour(result)));
    }

    private SkillActions() {}
}
