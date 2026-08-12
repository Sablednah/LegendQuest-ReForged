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

    public static void handle(ServerPlayer player, int action, int slot) {
        PlayerCharacter pc = CharacterService.data(player);
        switch (action) {
            case SkillActionPayload.CYCLE -> {
                if (pc.cycleLoadout().isEmpty()) {
                    Feedback.actionBar(player, "&7Loadout is empty — /loadout add <skill>");
                } else {
                    Feedback.actionBar(player, LQServerEvents.loadoutBar(player, pc, "&e"));
                }
            }
            case SkillActionPayload.USE_SELECTED -> useSelected(player, pc);
            case SkillActionPayload.USE_SLOT -> {
                if (slot < 0 || slot >= pc.loadout().size()) {
                    Feedback.actionBar(player, "&7Nothing in loadout slot " + (slot + 1)
                            + " — /loadout add <skill>");
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
            default -> { }
        }
        CharacterSync.send(player);
    }

    private static void useSelected(ServerPlayer player, PlayerCharacter pc) {
        var selected = pc.selectedSkill();
        if (selected.isEmpty()) {
            Feedback.actionBar(player, "&7Loadout is empty — /loadout add <skill>");
            return;
        }
        var result = SkillEngine.use(player, selected.get());
        Feedback.actionBar(player, LQServerEvents.loadoutBar(player, pc,
                LQServerEvents.resultColour(result)));
    }

    private SkillActions() {}
}
