package com.sablednah.legendquest.client;

import com.sablednah.standards.client.panels.InventoryPanel;
import com.sablednah.standards.client.panels.PanelTheme;
import com.sablednah.standards.client.panels.Panels;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * The character sheet as a Standards inventory panel.
 *
 * <p><b>This is the only class in LegendQuest that imports Standards' panel
 * seam</b>, and the only one that knows the host exists. {@link CharacterPanel}
 * keeps every pixel of drawing and every click rule; this hands it a rectangle
 * and forwards the events. Same division as {@code ChatSupport}, and for a
 * second reason Standards itself argued for: 26.x reworked GUI rendering
 * wholesale, so everything coupled to the host belongs in one small file that
 * can be ported in one edit rather than hunted through five.</p>
 *
 * <p><b>What moving here bought.</b> The panel used to reflect into
 * {@code AbstractContainerScreen.leftPos} and
 * {@code AbstractRecipeBookScreen.recipeBookComponent}, from a static
 * initialiser that threw {@code IllegalStateException} if either field moved —
 * which meant a vanilla rename did not degrade LegendQuest, it took the
 * inventory screen away from every player the first time one was opened.
 * Standards carries a single access transformer instead, so that failure is now
 * a broken build rather than a broken game.</p>
 *
 * <p>It also buys arbitration this mod could not do for itself: two mods both
 * shifting the inventory by vanilla's own formula are indistinguishable from
 * each other, so LegendQuest and Factions could overlap. The host decides who
 * is showing.</p>
 */
public final class CharacterPane implements InventoryPanel {

    public static final String ID = "legendquest:character";
    public static final CharacterPane INSTANCE = new CharacterPane();

    /** Gold on near-black: LegendQuest's, not decoration. The host paints the
     *  frame with these two so several mods' panes share one shape without
     *  sharing a palette — every other colour in the panel stays in
     *  {@link CharacterPanel}, where the other thirteen references live. */
    private static final PanelTheme THEME = new PanelTheme(0xE8101018, 0xFFDAA520);

    private CharacterPane() {}

    public static void register() {
        Panels.register(ID, Panels.Area.LEFT, INSTANCE);
    }

    /** Show this tab, or hide the pane if that tab is already showing. */
    static void toggle(CharacterPanel.Tab which) {
        if (Panels.isOpen(ID) && CharacterPanel.tabShowing() == which) {
            Panels.close();
            return;
        }
        CharacterPanel.showTab(which);
        Panels.open(ID);
    }

    /** Open straight onto a tab — the hotkey path, which must not toggle shut
     *  if the pane happens to be open on something else already. */
    static void open(CharacterPanel.Tab which) {
        CharacterPanel.showTab(which);
        Panels.open(ID);
    }

    static boolean isOpen() {
        return Panels.isOpen(ID);
    }

    // --- InventoryPanel ---

    @Override
    public int preferredWidth() {
        return CharacterPanel.panelWidth();
    }

    /**
     * Content-driven, and the host asks every frame — which it promises to do,
     * because this answer changes while the pane is open: the skills list grows
     * with the skill count and the stats tab grows again with each of the race
     * and class pickers.
     */
    @Override
    public int preferredHeight() {
        return CharacterPanel.contentHeight();
    }

    @Override
    public PanelTheme theme() {
        return THEME;
    }

    @Override
    public boolean available() {
        // No character sheet without a character. On a vanilla server the
        // summary never arrives, and an empty panel would be worse than none.
        return ClientCharacterState.summary() != null;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int h,
            int mouseX, int mouseY) {
        InventoryScreen screen = screen();
        if (screen != null) CharacterPanel.renderInto(screen, graphics, font, x, y, h, mouseX, mouseY);
    }

    /**
     * Tooltips, drawn after every pane and every frame the host paints.
     *
     * <p>They are positioned in screen coordinates and deliberately leave the
     * pane — flipping to the left of the cursor near the right edge, clamped
     * 2px from every side — which works because the host guarantees it never
     * scissors.</p>
     */
    @Override
    public void renderOverlay(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        CharacterPanel.renderTooltip(graphics, font);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        InventoryScreen screen = screen();
        return screen != null && CharacterPanel.clicked(screen, mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        // Nothing to do per-frame: the drag is recorded on press and resolved
        // on release, and the ghost follows the mouse position the render
        // already has. Answering false would tell the host we are not dragging.
        return CharacterPanel.dragging();
    }

    /**
     * A release the host routed to us, and the end of a loadout drag.
     *
     * <p>Returning true is what stops it reaching vanilla, which reads a release
     * outside its own bounds with an item on the cursor as "throw it on the
     * floor" — and a pane is outside those bounds by construction. That is not
     * hypothetical: this method returned {@code void} in Standards 1.7.0, so a
     * panel could not consume a release at all, and carrying a spellbook to the
     * slot dropped it on the ground. Fixed in 1.8.0, which is why the dependency
     * range demands it.</p>
     *
     * <p>Only true when the release was actually over the pane. A pane that
     * swallows releases it did not use leaves a button somewhere else stuck
     * down.</p>
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        InventoryScreen screen = screen();
        if (screen == null) return false;
        return CharacterPanel.released(screen, mouseX, mouseY);
    }

    /** Every call means start again, so an interrupted drag never survives. */
    @Override
    public void onOpen() {
        CharacterPanel.clearDrag();
    }

    /**
     * Unconditional on the host's side — the player closing it, another pane
     * opening, the recipe book opening, another mod taking the space, or
     * {@link #available()} going false all arrive here. The one exception is
     * the inventory screen closing, which deliberately leaves the pane open so
     * reopening finds it where it was left.
     */
    @Override
    public void onClose() {
        CharacterPanel.clearDrag();
    }

    private static InventoryScreen screen() {
        return Minecraft.getInstance().screen instanceof InventoryScreen s ? s : null;
    }
}
