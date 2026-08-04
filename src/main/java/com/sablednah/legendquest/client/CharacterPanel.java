package com.sablednah.legendquest.client;

import com.sablednah.legendquest.network.CharacterSummaryPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The character sheet on the inventory screen: a small "LQ" toggle button
 * beside the GUI, and (when open) a panel with stats, mana and the skill
 * list. Draws from {@link ClientCharacterState} — on a vanilla server (no
 * data) the panel says so instead of pretending.
 *
 * <p>Survival inventory only; the creative screen is cramped enough. Known
 * alpha quirk: the panel shares the right-hand margin with potion effect
 * icons — toggle it off when you're swimming in buffs.</p>
 */
public final class CharacterPanel {

    private static final int PANEL_WIDTH = 130;
    private static boolean open = false;

    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        int x = screen.getGuiLeft() + screen.getXSize() + 4;
        int y = screen.getGuiTop();
        Button toggle = Button.builder(Component.literal("LQ"), b -> open = !open)
                .bounds(x, y, 24, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal("LegendQuest character sheet")))
                .build();
        event.addListener(toggle);
    }

    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!open || !(event.getScreen() instanceof InventoryScreen screen)) return;
        GuiGraphics g = event.getGuiGraphics();
        Font font = screen.getMinecraft().font;

        int x = screen.getGuiLeft() + screen.getXSize() + 4;
        int y = screen.getGuiTop() + 20;

        CharacterSummaryPayload s = ClientCharacterState.summary();
        int height = s == null ? 24 : 96 + s.skills().size() * 10;
        g.fill(x, y, x + PANEL_WIDTH, y + height, 0xD0101018);
        g.fill(x, y, x + PANEL_WIDTH, y + 1, 0xFFDAA520);
        g.fill(x, y + height - 1, x + PANEL_WIDTH, y + height, 0xFFDAA520);

        int tx = x + 4;
        int ty = y + 4;
        if (s == null) {
            g.drawString(font, "No LegendQuest data", tx, ty, 0xFF8888AA);
            return;
        }

        String title = s.raceName() + " " + s.mainClassName()
                + (s.subClassName().isEmpty() ? "" : "/" + s.subClassName());
        g.drawString(font, "§6§l" + title, tx, ty, 0xFFFFFFFF);
        ty += 11;
        g.drawString(font, "§7Level §f" + s.level() + "  §7Karma §f" + s.karmaName(), tx, ty, 0xFFFFFFFF);
        ty += 11;

        // Mana bar.
        int barW = PANEL_WIDTH - 8;
        int filled = s.maxMana() <= 0 ? 0 : (int) (barW * Math.min(1.0F, s.mana() / s.maxMana()));
        g.fill(tx, ty, tx + barW, ty + 7, 0xFF16163A);
        g.fill(tx, ty, tx + filled, ty + 7, 0xFF3355FF);
        String manaText = (int) s.mana() + "/" + (int) s.maxMana();
        g.drawString(font, manaText, tx + (barW - font.width(manaText)) / 2, ty - 1, 0xFFBBCCFF);
        ty += 10;

        // Stat line, two rows of three.
        String[] names = {"STR", "DEX", "CON", "INT", "WIS", "CHR"};
        for (int n = 0; n < 6; n++) {
            int col = n % 3;
            int row = n / 3;
            int score = s.stats()[n];
            int mod = (score / 2) - 5;
            String text = "§7" + names[n] + " §f" + score
                    + "§8(" + (mod >= 0 ? "+" : "") + mod + ")";
            g.drawString(font, text, tx + col * 42, ty + row * 10, 0xFFFFFFFF);
        }
        ty += 21;
        g.drawString(font, "§7Skill points §f" + (s.spTotal() - s.spSpent()) + "§7/§f" + s.spTotal(),
                tx, ty, 0xFFFFFFFF);
        ty += 12;

        // Skill list: green ready, red cooling (with seconds), grey unowned.
        for (CharacterSummaryPayload.SkillEntry skill : s.skills()) {
            String line;
            if (!skill.owned()) {
                line = "§8" + skill.name() + " §7[lvl " + skill.levelReq()
                        + (skill.cost() > 0 ? ", " + skill.cost() + "sp" : "") + "]";
            } else if (skill.readyInSec() > 0) {
                line = "§c" + skill.name() + " §7" + skill.readyInSec() + "s";
            } else {
                line = "§a" + skill.name() + " §8" + skill.type().toLowerCase().charAt(0);
            }
            g.drawString(font, line, tx, ty, 0xFFFFFFFF);
            ty += 10;
        }
    }

    private CharacterPanel() {}
}
