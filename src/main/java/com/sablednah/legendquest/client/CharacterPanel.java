package com.sablednah.legendquest.client;

import java.lang.reflect.Field;

import com.sablednah.legendquest.network.CharacterSummaryPayload;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The character sheet on the inventory screen. An "LQ" button sits beside the
 * vanilla recipe-book button; clicking it slides a panel out to the LEFT,
 * shifting the inventory right exactly the way the recipe book does (same
 * offset maths, same footprint: 147×166). The two are mutually exclusive —
 * opening one closes the other, recipe-book style tab switching.
 *
 * <p>The screen's {@code leftPos} is vanilla's own re-centring knob (the
 * recipe book writes it too); we set it reflectively since there's no setter.
 * Draws from {@link ClientCharacterState} — on a vanilla server (no data)
 * the panel says so instead of pretending.</p>
 */
public final class CharacterPanel {

    /** Same as RecipeBookComponent.IMAGE_WIDTH/HEIGHT — visual symmetry. */
    private static final int PANEL_WIDTH = 147;
    private static final int PANEL_HEIGHT = 166;
    private static final int GAP = 2;

    private static boolean open = false;
    private static boolean openOnInit = false;
    private static Button toggle;
    private static ImageButton recipeButton;

    /** Hotkey path: open the sheet as soon as the inventory screen inits. */
    public static void openOnNextInit() {
        openOnInit = true;
    }

    // --- vanilla has no setters for these; see class javadoc ---

    private static final Field LEFT_POS;
    private static final Field RECIPE_COMPONENT;
    static {
        try {
            LEFT_POS = AbstractContainerScreen.class.getDeclaredField("leftPos");
            LEFT_POS.setAccessible(true);
            RECIPE_COMPONENT = AbstractRecipeBookScreen.class.getDeclaredField("recipeBookComponent");
            RECIPE_COMPONENT.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("LegendQuest: inventory screen internals moved", e);
        }
    }

    private static RecipeBookComponent<?> recipeBook(InventoryScreen screen) {
        try {
            return (RecipeBookComponent<?>) RECIPE_COMPONENT.get(screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setLeftPos(InventoryScreen screen, int value) {
        try {
            LEFT_POS.setInt(screen, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- lifecycle ---

    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        toggle = null;
        recipeButton = null;
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;

        // The vanilla recipe-book button: the screen's only 20×18 ImageButton.
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof ImageButton button
                    && button.getWidth() == 20 && button.getHeight() == 18) {
                recipeButton = button;
                break;
            }
        }

        toggle = Button.builder(Component.literal("LQ"), b -> toggleSheet(screen))
                .bounds(0, 0, 20, 18) // positioned every frame below
                .tooltip(Tooltip.create(Component.literal("LegendQuest character sheet")))
                .build();
        event.addListener(toggle);

        if (openOnInit) {
            openOnInit = false;
            open = true;
            RecipeBookComponent<?> book = recipeBook(screen);
            if (book.isVisible()) book.toggleVisibility();
        } else if (open && recipeBook(screen).isVisible()) {
            open = false; // book state persists across screens; it was here first
        }
        applyShift(screen);
        positionButtons(screen);
    }

    private static void toggleSheet(InventoryScreen screen) {
        open = !open;
        if (open) {
            RecipeBookComponent<?> book = recipeBook(screen);
            if (book.isVisible()) book.toggleVisibility(); // tab switch: recipes → stats
        }
        applyShift(screen);
        positionButtons(screen);
    }

    /**
     * Re-centre the GUI. With the panel open we use the recipe book's own
     * shift formula so the inventory sits exactly where players expect;
     * otherwise we hand the decision back to the book (it knows whether IT
     * is open). Narrow windows (<379px, vanilla's cutoff) don't shift —
     * the panel overlays to the left instead, clamped on-screen.
     */
    private static void applyShift(InventoryScreen screen) {
        int leftPos;
        if (open && screen.width >= 379) {
            leftPos = 177 + (screen.width - screen.getXSize() - 200) / 2;
        } else {
            leftPos = recipeBook(screen).updateScreenPosition(screen.width, screen.getXSize());
        }
        setLeftPos(screen, leftPos);
    }

    /**
     * Both buttons chase {@code leftPos} every frame: vanilla only moves its
     * recipe button inside its own click handler, so it goes stale whenever
     * WE move the screen (and ours would go stale whenever IT does).
     */
    private static void positionButtons(InventoryScreen screen) {
        int y = screen.height / 2 - 22;
        if (recipeButton != null) recipeButton.setPosition(screen.getGuiLeft() + 104, y);
        if (toggle != null) toggle.setPosition(screen.getGuiLeft() + 126, y);
    }

    @SubscribeEvent
    static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        if (open && recipeBook(screen).isVisible()) {
            open = false; // recipe button was clicked: tab switch stats → recipes
        }
        positionButtons(screen);
    }

    /** Clicks on the open panel must not reach the screen — with an item on
     *  the cursor, "outside the GUI" means "throw it on the floor". */
    @SubscribeEvent
    static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!open || !(event.getScreen() instanceof InventoryScreen screen)) return;
        int x = panelX(screen);
        int y = screen.getGuiTop();
        if (event.getMouseX() >= x && event.getMouseX() < x + PANEL_WIDTH
                && event.getMouseY() >= y && event.getMouseY() < y + panelHeight()) {
            event.setCanceled(true);
        }
    }

    private static int panelX(InventoryScreen screen) {
        return Math.max(0, screen.getGuiLeft() - PANEL_WIDTH - GAP);
    }

    private static int panelHeight() {
        CharacterSummaryPayload s = ClientCharacterState.summary();
        if (s == null) return 32;
        // pad + title + level + mana + stats + sp header, then the skill list.
        return Math.max(PANEL_HEIGHT, 106 + s.skills().size() * 10 + 8);
    }

    // --- drawing ---

    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!open || !(event.getScreen() instanceof InventoryScreen screen)) return;
        GuiGraphics g = event.getGuiGraphics();
        Font font = screen.getMinecraft().font;

        int x = panelX(screen);
        int y = screen.getGuiTop();
        int h = panelHeight();

        // Dark plate with a gold frame.
        g.fill(x, y, x + PANEL_WIDTH, y + h, 0xE8101018);
        g.fill(x, y, x + PANEL_WIDTH, y + 1, 0xFFDAA520);
        g.fill(x, y + h - 1, x + PANEL_WIDTH, y + h, 0xFFDAA520);
        g.fill(x, y, x + 1, y + h, 0xFFDAA520);
        g.fill(x + PANEL_WIDTH - 1, y, x + PANEL_WIDTH, y + h, 0xFFDAA520);

        int tx = x + 8;
        int ty = y + 8;

        CharacterSummaryPayload s = ClientCharacterState.summary();
        if (s == null) {
            g.drawString(font, "No LegendQuest data", tx, ty, 0xFF8888AA);
            return;
        }

        String title = s.raceName() + " " + s.mainClassName()
                + (s.subClassName().isEmpty() ? "" : "/" + s.subClassName());
        g.drawString(font, "§6§l" + title, tx, ty, 0xFFFFFFFF);
        ty += 12;
        g.drawString(font, "§7Level §f" + s.level() + "  §7Karma §f" + s.karmaName(),
                tx, ty, 0xFFFFFFFF);
        ty += 12;

        // Mana bar.
        int barW = PANEL_WIDTH - 16;
        int filled = s.maxMana() <= 0 ? 0 : (int) (barW * Math.min(1.0F, s.mana() / s.maxMana()));
        g.fill(tx, ty, tx + barW, ty + 8, 0xFF16163A);
        g.fill(tx, ty, tx + filled, ty + 8, 0xFF3355FF);
        String manaText = (int) s.mana() + "/" + (int) s.maxMana();
        g.drawString(font, manaText, tx + (barW - font.width(manaText)) / 2, ty, 0xFFBBCCFF);
        ty += 12;

        // Stats: two roomy columns, three rows.
        String[] names = {"STR", "DEX", "CON", "INT", "WIS", "CHR"};
        for (int n = 0; n < 6; n++) {
            int col = n % 2;
            int row = n / 2;
            int score = s.stats()[n];
            int mod = (score / 2) - 5;
            String text = "§7" + names[n] + " §f" + score
                    + " §8(" + (mod >= 0 ? "+" : "") + mod + ")";
            g.drawString(font, text, tx + col * 66, ty + row * 11, 0xFFFFFFFF);
        }
        ty += 35;
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
