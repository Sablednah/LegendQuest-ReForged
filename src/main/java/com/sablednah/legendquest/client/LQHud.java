package com.sablednah.legendquest.client;

import com.sablednah.legendquest.network.CharacterSummaryPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * The always-on HUD, bottom-left: mana bar, level, and the loadout strip
 * with the selected skill highlighted and cooldowns counting down on the
 * icons. Draws nothing on vanilla servers (no summary), when the GUI is
 * hidden (F1), or while any screen is open (the inventory panel takes over).
 */
public final class LQHud {

    private static final int MARGIN = 6;
    private static final int BAR_WIDTH = 92;
    private static final int CHIP = 18;

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        CharacterSummaryPayload s = ClientCharacterState.summary();
        if (s == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (mc.getDebugOverlay().showDebugScreen()) return;

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int bottom = g.guiHeight() - MARGIN;
        int x = MARGIN;

        // Loadout strip (only once there's a loadout to show).
        int chips = s.loadout().size();
        if (chips > 0) {
            int y = bottom - CHIP;
            for (int i = 0; i < chips; i++) {
                int cx = x + i * (CHIP + 1);
                boolean selected = i == s.loadoutIndex();
                g.fill(cx, y, cx + CHIP, y + CHIP, selected ? 0xC0403010 : 0x90101018);
                int border = selected ? 0xFFDAA520 : 0x6044445A;
                g.fill(cx, y, cx + CHIP, y + 1, border);
                g.fill(cx, y + CHIP - 1, cx + CHIP, y + CHIP, border);
                g.fill(cx, y, cx + 1, y + CHIP, border);
                g.fill(cx + CHIP - 1, y, cx + CHIP, y + CHIP, border);

                var entry = find(s, s.loadout().get(i));
                if (entry != null) {
                    g.renderItem(icon(entry.icon()), cx + 1, y + 1);
                    if (entry.readyInSec() > 0) {
                        g.fill(cx + 1, y + 1, cx + CHIP - 1, y + CHIP - 1, 0xA0000000);
                        String secs = String.valueOf(entry.readyInSec());
                        g.drawString(font, secs, cx + CHIP - 1 - font.width(secs), y + 5, 0xFFFF5555);
                    }
                }
            }
            // Selected skill's name floats over its chip row.
            var selected = find(s, s.loadout().get(Math.min(s.loadoutIndex(), chips - 1)));
            if (selected != null) {
                g.drawString(font, "§6" + selected.name(), x + 1, y - 10, 0xFFFFFFFF);
            }
            bottom = y - 13;
        }

        // Mana bar with numbers, level tucked on the right.
        if (s.maxMana() > 0) {
            int y = bottom - 8;
            int filled = (int) (BAR_WIDTH * Math.min(1.0F, s.mana() / s.maxMana()));
            g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + 8, 0x90000000);
            g.fill(x, y, x + BAR_WIDTH, y + 7, 0xFF16163A);
            g.fill(x, y, x + filled, y + 7, 0xFF3355FF);
            String text = (int) s.mana() + "/" + (int) s.maxMana();
            g.drawString(font, text, x + (BAR_WIDTH - font.width(text)) / 2, y, 0xFFBBCCFF);
            g.drawString(font, "§7L" + s.level(), x + BAR_WIDTH + 5, y, 0xFFFFFFFF);
        }
    }

    private static CharacterSummaryPayload.SkillEntry find(CharacterSummaryPayload s, String id) {
        for (var skill : s.skills()) {
            if (skill.id().equals(id)) return skill;
        }
        return null;
    }

    private static net.minecraft.world.item.ItemStack icon(String id) {
        var rl = net.minecraft.resources.Identifier.tryParse(id);
        if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) {
            return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
        }
        return new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(rl));
    }

    private LQHud() {}
}
