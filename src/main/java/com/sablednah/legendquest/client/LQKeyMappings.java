package com.sablednah.legendquest.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sablednah.legendquest.LegendQuest;
import com.sablednah.legendquest.network.SkillActionPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Keybinds (vanilla Controls screen, "LegendQuest" category):
 *
 * <ul>
 *   <li><b>K</b> — open the character sheet (inventory + panel)</li>
 *   <li><b>R</b> — use the selected loadout skill</li>
 *   <li><b>G</b> — cycle the loadout selection</li>
 *   <li><b>unbound ×5</b> — use loadout slot 1–5 directly. This is the
 *       per-skill binding: put Blink in slot 2, bind slot 2 to B, done.</li>
 * </ul>
 *
 * <p>Skill actions are only sent once the server has proven itself modded by
 * sending us a character summary — on a vanilla server the keys stay inert
 * instead of throwing "unknown payload" disconnects.</p>
 */
public final class LQKeyMappings {

    public static final int SLOT_COUNT = 5;

    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(LegendQuest.MODID, "main"));

    public static final KeyMapping OPEN_SHEET = new KeyMapping(
            "key.legendquest.character", InputConstants.KEY_K, CATEGORY);
    public static final KeyMapping USE_SELECTED = new KeyMapping(
            "key.legendquest.use_skill", InputConstants.KEY_R, CATEGORY);
    public static final KeyMapping CYCLE = new KeyMapping(
            "key.legendquest.cycle_skill", InputConstants.KEY_G, CATEGORY);
    public static final KeyMapping HANDBOOK = new KeyMapping(
            "key.legendquest.handbook", InputConstants.KEY_H, CATEGORY);
    public static final KeyMapping[] SLOTS = new KeyMapping[SLOT_COUNT];
    static {
        for (int n = 0; n < SLOT_COUNT; n++) {
            SLOTS[n] = new KeyMapping("key.legendquest.slot_" + (n + 1),
                    InputConstants.UNKNOWN.getValue(), CATEGORY);
        }
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_SHEET);
        event.register(USE_SELECTED);
        event.register(CYCLE);
        event.register(HANDBOOK);
        for (KeyMapping slot : SLOTS) event.register(slot);
    }

    /** Called on ClientTickEvent.Post from the client entrypoint. */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (OPEN_SHEET.consumeClick()) {
            if (mc.screen == null) {
                CharacterPanel.openOnNextInit();
                mc.setScreen(new InventoryScreen(mc.player));
            }
        }
        while (HANDBOOK.consumeClick()) {
            if (mc.screen == null) HandbookScreen.open();
        }
        while (USE_SELECTED.consumeClick()) {
            send(new SkillActionPayload(SkillActionPayload.USE_SELECTED, 0, ""));
        }
        while (CYCLE.consumeClick()) {
            send(new SkillActionPayload(SkillActionPayload.CYCLE, 0, ""));
        }
        for (int n = 0; n < SLOT_COUNT; n++) {
            while (SLOTS[n].consumeClick()) {
                send(new SkillActionPayload(SkillActionPayload.USE_SLOT, n, ""));
            }
        }
    }

    private static void send(SkillActionPayload payload) {
        if (ClientCharacterState.summary() == null) return; // vanilla server
        ClientPacketDistributor.sendToServer(payload);
    }

    private LQKeyMappings() {}
}
