package com.sablednah.legendquest.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sablednah.legendquest.network.CharacterSummaryPayload;
import com.sablednah.legendquest.network.ChoosePayload;
import com.sablednah.legendquest.network.LoadoutEditPayload;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The character sheet on the inventory screen: two tab buttons beside the
 * vanilla recipe-book button — "LQ" (stats) and "✦" (skills & loadout).
 * Either slides a panel out to the LEFT, shifting the inventory right the
 * way the recipe book does; recipe book and our tabs are mutually exclusive.
 *
 * <p>The skills tab is the loadout workbench: drag a skill onto the slot
 * strip to add it, drag slots around to reorder, drag a slot off the strip
 * to remove it, click a slot to select. Hovering anything explains itself.
 * The stats tab grows race/class pickers while those choices are open.</p>
 *
 * <p>The screen's {@code leftPos} is vanilla's own re-centring knob (the
 * recipe book writes it too); we set it reflectively since there's no setter.
 * All edits go to the server as requests — the panel never mutates locally.</p>
 */
public final class CharacterPanel {

    /** A shade wider than the recipe book (147): breathing room for the Buy
     *  buttons and a clear slots↔spellbook gap. Still fits the shifted GUI's
     *  left margin at vanilla's narrowest shifting width (379px → 178px). */
    private static final int PANEL_WIDTH = 170;
    private static final int PANEL_HEIGHT = 166;
    private static final int GAP = 2;
    private static final int SLOT_COUNT = 5; // matches the five slot hotkeys
    private static final int SLOT_SIZE = 20;
    private static final int BOOK_SLOT_GAP = 5;
    private static final int ROW_HEIGHT = 18;

    private enum Tab { NONE, STATS, SKILLS }

    private static Tab tab = Tab.NONE;
    private static boolean openOnInit = false;
    private static Button statsButton;
    private static Button skillsButton;
    private static ImageButton recipeButton;

    /** A drag in progress on the skills tab. {@code fromSlot < 0} = from the list. */
    private record Drag(String skillId, int fromSlot, double pressX, double pressY) {}
    private static Drag drag = null;
    private static double mouseX;
    private static double mouseY;

    /** Click hotspots, rebuilt every rendered frame (immediate-mode).
     *  {@code button}: -1 = any mouse button, 0 = left only, 1 = right only
     *  (right-only regions share space with a left-click action). */
    private record Hot(int x0, int y0, int x1, int y1, int button, Runnable action) {}
    private static final List<Hot> HOTSPOTS = new ArrayList<>();

    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();

    /** Hotkey path: open the stats tab as soon as the inventory screen inits. */
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
        statsButton = null;
        skillsButton = null;
        recipeButton = null;
        drag = null;
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;

        // The vanilla recipe-book button: the screen's only 20×18 ImageButton.
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof ImageButton button
                    && button.getWidth() == 20 && button.getHeight() == 18) {
                recipeButton = button;
                break;
            }
        }

        statsButton = Button.builder(Component.literal("LQ"), b -> toggleTab(screen, Tab.STATS))
                .bounds(0, 0, 20, 18)
                .tooltip(Tooltip.create(Component.literal("Character sheet")))
                .build();
        skillsButton = Button.builder(Component.literal("✦"), b -> toggleTab(screen, Tab.SKILLS))
                .bounds(0, 0, 20, 18)
                .tooltip(Tooltip.create(Component.literal("Skills & loadout")))
                .build();
        event.addListener(statsButton);
        event.addListener(skillsButton);

        if (openOnInit) {
            openOnInit = false;
            tab = Tab.STATS;
            RecipeBookComponent<?> book = recipeBook(screen);
            if (book.isVisible()) book.toggleVisibility();
        } else if (tab != Tab.NONE && recipeBook(screen).isVisible()) {
            tab = Tab.NONE; // book state persists across screens; it was here first
        }
        applyShift(screen);
        positionButtons(screen);
    }

    private static void toggleTab(InventoryScreen screen, Tab which) {
        tab = tab == which ? Tab.NONE : which;
        drag = null;
        if (tab != Tab.NONE) {
            RecipeBookComponent<?> book = recipeBook(screen);
            if (book.isVisible()) book.toggleVisibility(); // tab switch: recipes → us
        }
        applyShift(screen);
        positionButtons(screen);
    }

    /**
     * Re-centre the GUI. With a tab open we use the recipe book's own shift
     * formula; otherwise the book decides (it knows whether IT is open).
     * Narrow windows (<379px, vanilla's cutoff) don't shift — the panel
     * overlays to the left instead, clamped on-screen.
     */
    private static void applyShift(InventoryScreen screen) {
        int leftPos;
        if (tab != Tab.NONE && screen.width >= 379) {
            leftPos = 177 + (screen.width - screen.getXSize() - 200) / 2;
        } else {
            leftPos = recipeBook(screen).updateScreenPosition(screen.width, screen.getXSize());
        }
        setLeftPos(screen, leftPos);
    }

    /**
     * All three buttons chase {@code leftPos} every frame: vanilla only moves
     * its recipe button inside its own click handler, so it goes stale
     * whenever WE move the screen (and ours would go stale whenever IT does).
     */
    private static void positionButtons(InventoryScreen screen) {
        int y = screen.height / 2 - 22;
        if (recipeButton != null) recipeButton.setPosition(screen.getGuiLeft() + 104, y);
        if (statsButton != null) statsButton.setPosition(screen.getGuiLeft() + 126, y);
        if (skillsButton != null) skillsButton.setPosition(screen.getGuiLeft() + 148, y);
    }

    @SubscribeEvent
    static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        if (tab != Tab.NONE && recipeBook(screen).isVisible()) {
            tab = Tab.NONE; // recipe button was clicked: tab switch us → recipes
            drag = null;
        }
        positionButtons(screen);
    }

    // --- geometry (pure functions of the summary; used by render AND clicks) ---

    private static int panelX(InventoryScreen screen) {
        return Math.max(0, screen.getGuiLeft() - PANEL_WIDTH - GAP);
    }

    /** The stat-boost chip row appears only when the next boost is payable. */
    private static boolean boostRowShown(CharacterSummaryPayload s) {
        return s.spTotal() - s.spSpent() >= s.statBoostCost();
    }

    /** Top of the panel: the GUI's top, but slid up if the content would
     *  run off the bottom of the screen (both pickers open, long skill list). */
    private static int panelY(InventoryScreen screen) {
        return Math.max(2, Math.min(screen.getGuiTop(), screen.height - panelHeight() - 2));
    }

    private static CharacterSummaryPayload summary() {
        return ClientCharacterState.summary();
    }

    private static boolean inPanel(InventoryScreen screen, double mx, double my) {
        int x = panelX(screen);
        int y = panelY(screen);
        return mx >= x && mx < x + PANEL_WIDTH && my >= y && my < y + panelHeight();
    }

    private static int panelHeight() {
        CharacterSummaryPayload s = summary();
        if (s == null) return 32;
        int h;
        if (tab == Tab.SKILLS) {
            // pad + title + slots + hint + divider, then the list.
            h = 8 + 12 + SLOT_SIZE + 4 + 12 + 6 + s.skills().size() * ROW_HEIGHT + 8;
        } else {
            h = 8 + 12 + 12 + 12 + 35 + 12 + 12; // core stats block
            if (boostRowShown(s)) h += 24; // label line + chip line
            if (!s.raceChoices().isEmpty()) h += 13 + s.raceChoices().size() * 11 + 4;
            if (!s.classChoices().isEmpty()) h += 13 + s.classChoices().size() * 11 + 4;
        }
        return Math.max(PANEL_HEIGHT, h);
    }

    /** Y of the loadout slot strip (skills tab). */
    private static int slotsY(InventoryScreen screen) {
        return panelY(screen) + 8 + 12;
    }

    /** Y of the first skill list row (skills tab). */
    private static int listY(InventoryScreen screen) {
        return slotsY(screen) + SLOT_SIZE + 4 + 12 + 6;
    }

    private static int slotAt(InventoryScreen screen, double mx, double my) {
        int y = slotsY(screen);
        if (my < y || my >= y + SLOT_SIZE) return -1;
        int x0 = panelX(screen) + 8;
        for (int i = 0; i < SLOT_COUNT; i++) {
            int sx = x0 + i * (SLOT_SIZE + 1);
            if (mx >= sx && mx < sx + SLOT_SIZE) return i;
        }
        return -1;
    }

    /** The spellbook slot hugs the panel's right edge, clearly apart. */
    private static boolean inBookSlot(InventoryScreen screen, double mx, double my) {
        int y = slotsY(screen);
        int sx = panelX(screen) + PANEL_WIDTH - 8 - SLOT_SIZE;
        return mx >= sx && mx < sx + SLOT_SIZE && my >= y && my < y + SLOT_SIZE;
    }

    private static int listRowAt(InventoryScreen screen, double mx, double my) {
        CharacterSummaryPayload s = summary();
        if (s == null) return -1;
        int y = listY(screen);
        int x = panelX(screen);
        if (mx < x + 4 || mx >= x + PANEL_WIDTH - 4) return -1;
        int row = (int) ((my - y) / ROW_HEIGHT);
        return my >= y && row >= 0 && row < s.skills().size() ? row : -1;
    }

    // --- mouse: clicks, drags, drops ---

    @SubscribeEvent
    static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (tab == Tab.NONE || !(event.getScreen() instanceof InventoryScreen screen)) return;
        double mx = event.getMouseX();
        double my = event.getMouseY();
        // The shield: clicks on the panel never reach the screen, and with an
        // item on the cursor the ENTIRE region left of the GUI is safe ground
        // — carrying your would-be spellbook to the slot must never count as
        // "clicked outside, throw it on the floor".
        boolean carrying = !screen.getMenu().getCarried().isEmpty();
        boolean shielded = inPanel(screen, mx, my) || (carrying && mx < screen.getGuiLeft());
        if (!shielded) return;
        event.setCanceled(true);
        if (!inPanel(screen, mx, my)) return; // shielded gap: swallow, do nothing
        CharacterSummaryPayload s = summary();
        if (s == null) return;

        // Hotspots first — buy chips, handbook links, right-click lookups.
        for (Hot hot : HOTSPOTS) {
            if (mx >= hot.x0() && mx < hot.x1() && my >= hot.y0() && my < hot.y1()
                    && (hot.button() == -1 || hot.button() == event.getButton())) {
                hot.action().run();
                return;
            }
        }
        if (event.getButton() != 0) return; // everything below is left-click

        if (tab == Tab.SKILLS) {
            // Spellbook slot: click with an item on the cursor to set it,
            // click with an empty cursor to unbind.
            if (inBookSlot(screen, mx, my)) {
                ItemStack carried = screen.getMenu().getCarried();
                if (!carried.isEmpty()) {
                    send(new LoadoutEditPayload(LoadoutEditPayload.SET_ITEM,
                            BuiltInRegistries.ITEM.getKey(carried.getItem()).toString(), -1, -1));
                } else if (!s.loadoutItem().isEmpty()) {
                    send(new LoadoutEditPayload(LoadoutEditPayload.SET_ITEM, "", -1, -1));
                }
                return;
            }
            int slot = slotAt(screen, mx, my);
            if (slot >= 0 && slot < s.loadout().size()) {
                drag = new Drag(s.loadout().get(slot), slot, mx, my);
                return;
            }
            int row = listRowAt(screen, mx, my);
            if (row >= 0) {
                var skill = s.skills().get(row);
                if (skill.owned() && "ACTIVE".equals(skill.type())
                        && !s.loadout().contains(skill.id())) {
                    drag = new Drag(skill.id(), -1, mx, my);
                }
            }
            return;
        }

        // Stats tab: picker rows.
        PickerHit hit = pickerRowAt(screen, mx, my);
        if (hit != null && hit.entry().available()) {
            send(new ChoosePayload(hit.race() ? ChoosePayload.RACE : ChoosePayload.MAIN_CLASS,
                    hit.entry().id()));
        }
    }

    @SubscribeEvent
    static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        // Releases over the shield are swallowed too — quickcraft's
        // release-outside path is another way to fling a carried item.
        if (drag == null) {
            if (tab != Tab.NONE
                    && (inPanel(screen, event.getMouseX(), event.getMouseY())
                            || (!screen.getMenu().getCarried().isEmpty()
                                    && event.getMouseX() < screen.getGuiLeft()))) {
                event.setCanceled(true);
            }
            return;
        }
        Drag d = drag;
        drag = null;
        CharacterSummaryPayload s = summary();
        if (s == null) return;
        double mx = event.getMouseX();
        double my = event.getMouseY();
        boolean moved = Math.abs(mx - d.pressX()) > 4 || Math.abs(my - d.pressY()) > 4;
        if (inPanel(screen, mx, my)) event.setCanceled(true);

        int slot = slotAt(screen, mx, my);
        if (d.fromSlot() < 0) {
            // From the list: drop on the strip inserts there; a plain click appends.
            if (slot >= 0) {
                send(new LoadoutEditPayload(LoadoutEditPayload.ADD, d.skillId(), -1,
                        Math.min(slot, s.loadout().size())));
            } else if (!moved) {
                send(new LoadoutEditPayload(LoadoutEditPayload.ADD, d.skillId(), -1, -1));
            }
            return;
        }
        // From a slot: click selects, drop on the strip reorders, drop anywhere else removes.
        if (!moved) {
            send(new LoadoutEditPayload(LoadoutEditPayload.SELECT, "", -1, d.fromSlot()));
        } else if (slot >= 0) {
            send(new LoadoutEditPayload(LoadoutEditPayload.MOVE, "", d.fromSlot(),
                    Math.min(slot, s.loadout().size() - 1)));
        } else {
            send(new LoadoutEditPayload(LoadoutEditPayload.REMOVE, d.skillId(), -1, -1));
        }
    }

    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (summary() == null) return; // vanilla server
        ClientPacketDistributor.sendToServer(payload);
    }

    // --- drawing ---

    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Post event) {
        if (tab == Tab.NONE || !(event.getScreen() instanceof InventoryScreen screen)) return;
        GuiGraphics g = event.getGuiGraphics();
        Font font = screen.getMinecraft().font;
        mouseX = event.getMouseX();
        mouseY = event.getMouseY();
        HOTSPOTS.clear();

        int x = panelX(screen);
        int y = panelY(screen);
        int h = panelHeight();

        // Dark plate with a gold frame.
        g.fill(x, y, x + PANEL_WIDTH, y + h, 0xE8101018);
        g.fill(x, y, x + PANEL_WIDTH, y + 1, 0xFFDAA520);
        g.fill(x, y + h - 1, x + PANEL_WIDTH, y + h, 0xFFDAA520);
        g.fill(x, y, x + 1, y + h, 0xFFDAA520);
        g.fill(x + PANEL_WIDTH - 1, y, x + PANEL_WIDTH, y + h, 0xFFDAA520);

        CharacterSummaryPayload s = summary();
        if (s == null) {
            g.drawString(font, "No LegendQuest data", x + 8, y + 8, 0xFF8888AA);
            return;
        }
        if (tab == Tab.SKILLS) {
            renderSkillsTab(g, font, screen, s, x, y);
        } else {
            renderStatsTab(g, font, screen, s, x, y);
        }

        // The Players Handbook button, top-right corner (the bottom one sat
        // on the last skill row of long lists).
        if (ClientHandbook.get() != null) {
            int bx = x + PANEL_WIDTH - 20;
            int by = y + 5;
            boolean hover = mouseX >= bx && mouseX < bx + 14 && mouseY >= by && mouseY < by + 12;
            g.fill(bx, by, bx + 14, by + 12, hover ? 0xFF403010 : 0xFF26263A);
            g.fill(bx, by, bx + 14, by + 1, 0xFFDAA520);
            g.fill(bx, by + 11, bx + 14, by + 12, 0xFFDAA520);
            g.fill(bx, by, bx + 1, by + 12, 0xFFDAA520);
            g.fill(bx + 13, by, bx + 14, by + 12, 0xFFDAA520);
            g.drawString(font, "§6?", bx + 5, by + 2, 0xFFFFFFFF);
            HOTSPOTS.add(new Hot(bx, by, bx + 14, by + 12, -1, HandbookScreen::open));
            if (hover) {
                tooltip(g, font, "Players Handbook",
                        "Races, classes and skills — everything a legend needs to know. §8(Key: H)");
            }
        }

        // The carried item again, on top: the screen drew it before us, so
        // the panel was painting over it — your would-be spellbook seemed
        // to vanish the moment it crossed onto the panel.
        ItemStack carried = screen.getMenu().getCarried();
        if (!carried.isEmpty() && mouseX < screen.getGuiLeft()) {
            g.renderItem(carried, (int) mouseX - 8, (int) mouseY - 8);
            g.renderItemDecorations(font, carried, (int) mouseX - 8, (int) mouseY - 8);
        }

        drawPendingTooltip(g, font); // last, so nothing paints over it
        ClientNotices.draw(g, font); // server notices beat even the tooltip
    }

    /** A proper little button: filled, framed, bevelled — not text in brackets. */
    static void buyButton(GuiGraphics g, Font font, int x0, int y0, int w, int h,
            String label, boolean hover) {
        g.fill(x0, y0, x0 + w, y0 + h, hover ? 0xFF2E4A1E : 0xFF223A18);
        g.fill(x0, y0, x0 + w, y0 + 1, hover ? 0xFF8CD05A : 0xFF5A9038);   // top bevel
        g.fill(x0, y0 + h - 1, x0 + w, y0 + h, 0xFF12200C);                // bottom shadow
        g.fill(x0, y0, x0 + 1, y0 + h, hover ? 0xFF8CD05A : 0xFF5A9038);
        g.fill(x0 + w - 1, y0, x0 + w, y0 + h, 0xFF12200C);
        String drawn = (hover ? "§f§l" : "§a") + label;
        g.drawString(font, drawn, x0 + (w - font.width(drawn)) / 2, y0 + (h - 8) / 2 + 1, 0xFFFFFFFF);
    }

    /** Open the handbook at the entry whose display name matches. */
    private static void openHandbookByName(String section, String name) {
        var book = ClientHandbook.get();
        if (book == null) return;
        var list = switch (section) {
            case "class" -> book.classes();
            case "skill" -> book.skills();
            default -> book.races();
        };
        for (var entry : list) {
            if (entry.name().equalsIgnoreCase(name)) {
                HandbookScreen.open(section, entry.id());
                return;
            }
        }
        HandbookScreen.open(section, null);
    }

    private static void renderStatsTab(GuiGraphics g, Font font, InventoryScreen screen,
            CharacterSummaryPayload s, int x, int y) {
        int tx = x + 8;
        int ty = y + 8;

        // Race and class are handbook links (hover to see, click to read).
        String raceText = "§6§l" + s.raceName();
        String classText = "§6§l" + s.mainClassName()
                + (s.subClassName().isEmpty() ? "" : "/" + s.subClassName());
        int raceW = font.width(raceText);
        boolean raceHover = mouseX >= tx && mouseX < tx + raceW && mouseY >= ty - 1 && mouseY < ty + 10;
        boolean classHover = mouseX >= tx + raceW + 4 && mouseX < tx + raceW + 4 + font.width(classText)
                && mouseY >= ty - 1 && mouseY < ty + 10;
        String raceName = s.raceName();
        String className = s.mainClassName();
        g.drawString(font, raceHover ? "§e§l§n" + raceName : raceText, tx, ty, 0xFFFFFFFF);
        g.drawString(font, classHover ? "§e§l§n" + s.mainClassName()
                + (s.subClassName().isEmpty() ? "" : "/" + s.subClassName()) : classText,
                tx + raceW + 4, ty, 0xFFFFFFFF);
        if (ClientHandbook.get() != null) {
            HOTSPOTS.add(new Hot(tx, ty - 1, tx + raceW, ty + 10, -1,
                    () -> openHandbookByName("race", raceName)));
            HOTSPOTS.add(new Hot(tx + raceW + 4, ty - 1, tx + raceW + 4 + font.width(classText),
                    ty + 10, -1, () -> openHandbookByName("class", className)));
            if (raceHover) tooltip(g, font, raceName, "§7Open in the Players Handbook");
            if (classHover) tooltip(g, font, className, "§7Open in the Players Handbook");
        }
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
            int score = s.stats()[n];
            int mod = (score / 2) - 5;
            String text = "§7" + names[n] + " §f" + score
                    + " §8(" + (mod >= 0 ? "+" : "") + mod + ")";
            g.drawString(font, text, tx + (n % 2) * 66, ty + (n / 2) * 11, 0xFFFFFFFF);
        }
        ty += 35;
        g.drawString(font, "§7Skill points §f" + (s.spTotal() - s.spSpent()) + "§7/§f" + s.spTotal(),
                tx, ty, 0xFFFFFFFF);
        ty += 12;

        // Stat boost chips: a +1 wherever it hurts least, at a stinging price.
        // Label and chips on separate lines — six chips never fit beside it.
        if (boostRowShown(s)) {
            g.drawString(font, "§7Boost a stat §8(" + s.statBoostCost() + "sp):", tx, ty, 0xFFFFFFFF);
            ty += 11;
            int bx = tx + 4;
            String[] keys = {"str", "dex", "con", "int", "wis", "chr"};
            String[] labels = {"S", "D", "C", "I", "W", "Ch"}; // CON vs CHR
            for (int n = 0; n < 6; n++) {
                String chip = "+" + labels[n];
                int cw = font.width("§l" + chip) + 6;
                boolean chipHover = mouseX >= bx && mouseX < bx + cw
                        && mouseY >= ty - 2 && mouseY < ty + 10;
                buyButton(g, font, bx, ty - 2, cw, 12, chip, chipHover);
                String key = keys[n];
                HOTSPOTS.add(new Hot(bx, ty - 2, bx + cw, ty + 10, 0,
                        () -> send(new com.sablednah.legendquest.network.SkillActionPayload(
                                com.sablednah.legendquest.network.SkillActionPayload.BUY_STAT,
                                0, key))));
                if (chipHover) {
                    tooltip(g, font, "+1 " + key.toUpperCase(),
                            "Permanently raises the stat for " + s.statBoostCost()
                            + " skill points.\n§8Each boost bought raises the next one's price."
                            + "\n§8Regret it later? /lq respec");
                }
                bx += cw + 3;
            }
            ty += 13;
        }

        g.drawString(font, "§8Skills live on the ✦ tab", tx, ty, 0xFFFFFFFF);
        ty += 12;

        // Race/class pickers, while those choices are open.
        ty = renderPicker(g, font, s.raceChoices(), true, tx, ty);
        renderPicker(g, font, s.classChoices(), false, tx, ty);
    }

    private static int renderPicker(GuiGraphics g, Font font,
            List<CharacterSummaryPayload.PickEntry> choices, boolean race, int tx, int ty) {
        if (choices.isEmpty()) return ty;
        g.drawString(font, "§6§lChoose your " + (race ? "race:" : "class:"), tx, ty, 0xFFFFFFFF);
        ty += 13;
        for (CharacterSummaryPayload.PickEntry entry : choices) {
            boolean hover = mouseX >= tx && mouseX < tx + PANEL_WIDTH - 16
                    && mouseY >= ty - 1 && mouseY < ty + 10;
            if (hover) g.fill(tx - 2, ty - 1, tx + PANEL_WIDTH - 14, ty + 10, 0x30FFFFFF);
            String colour = !entry.available() ? "§8" : hover ? "§e" : "§a";
            g.drawString(font, colour + "▸ " + entry.name()
                    + (entry.available() ? "" : " §8[locked]"), tx, ty, 0xFFFFFFFF);
            HOTSPOTS.add(new Hot(tx - 2, ty - 1, tx + PANEL_WIDTH - 14, ty + 10, 1,
                    () -> HandbookScreen.open(race ? "race" : "class", entry.id())));
            if (hover) {
                tooltip(g, font, entry.name(),
                        (entry.description().isEmpty() ? "" : entry.description() + "\n")
                                + (entry.available() ? "§eClick to choose!" : "§cNot open to you.")
                                + "\n§8Right-click: handbook");
            }
            ty += 11;
        }
        return ty + 4;
    }

    private static void renderSkillsTab(GuiGraphics g, Font font, InventoryScreen screen,
            CharacterSummaryPayload s, int x, int y) {
        int tx = x + 8;
        g.drawString(font, "§6§lLoadout", tx, y + 8, 0xFFFFFFFF);

        // The slot strip.
        int sy = slotsY(screen);
        for (int i = 0; i < SLOT_COUNT; i++) {
            int sx = tx + i * (SLOT_SIZE + 1);
            boolean filled = i < s.loadout().size();
            boolean selected = filled && i == s.loadoutIndex();
            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF26263A);
            int border = selected ? 0xFFDAA520 : 0xFF44445A;
            g.fill(sx, sy, sx + SLOT_SIZE, sy + 1, border);
            g.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, border);
            g.fill(sx, sy, sx + 1, sy + SLOT_SIZE, border);
            g.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, border);
            if (filled) {
                String id = s.loadout().get(i);
                var entry = findSkill(s, id);
                boolean beingDragged = drag != null && drag.fromSlot() == i;
                if (!beingDragged) {
                    g.renderItem(icon(entry != null ? entry.icon() : ""), sx + 2, sy + 2);
                    if (entry != null && entry.activeForSec() > 0 && entry.durationSec() > 0) {
                        float frac = Math.min(1.0F, entry.activeForSec() / (float) entry.durationSec());
                        g.fill(sx + 2, sy + SLOT_SIZE - 4, sx + SLOT_SIZE - 2, sy + SLOT_SIZE - 2, 0x80000000);
                        g.fill(sx + 2, sy + SLOT_SIZE - 4, sx + 2 + (int) ((SLOT_SIZE - 4) * frac),
                                sy + SLOT_SIZE - 2, LQHud.durationColour(frac));
                    } else if (entry != null && entry.readyInSec() > 0) {
                        g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x90000000);
                        String secs = LQHud.cooldownText(entry.readyInSec());
                        g.drawString(font, secs, sx + (SLOT_SIZE - font.width(secs)) / 2, sy + 6, 0xFFFF5555);
                    }
                }
                boolean hover = mouseX >= sx && mouseX < sx + SLOT_SIZE
                        && mouseY >= sy && mouseY < sy + SLOT_SIZE;
                if (entry != null) {
                    var slotEntry = entry;
                    HOTSPOTS.add(new Hot(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 1,
                            () -> HandbookScreen.open("skill", slotEntry.id())));
                }
                if (hover && drag == null && entry != null) {
                    tooltip(g, font, entry.name(), skillTooltip(entry)
                            + "\n§eClick to select · drag off to remove"
                            + "\n§8Right-click: handbook");
                }
            }
        }

        // The spellbook slot: purple-framed, right-aligned, clearly apart.
        int bx = x + PANEL_WIDTH - 8 - SLOT_SIZE;
        boolean bookSet = !s.loadoutItem().isEmpty();
        g.fill(bx, sy, bx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF2A2038);
        int bBorder = bookSet ? 0xFF9060C0 : 0xFF554570;
        g.fill(bx, sy, bx + SLOT_SIZE, sy + 1, bBorder);
        g.fill(bx, sy + SLOT_SIZE - 1, bx + SLOT_SIZE, sy + SLOT_SIZE, bBorder);
        g.fill(bx, sy, bx + 1, sy + SLOT_SIZE, bBorder);
        g.fill(bx + SLOT_SIZE - 1, sy, bx + SLOT_SIZE, sy + SLOT_SIZE, bBorder);
        if (bookSet) {
            g.renderItem(icon(s.loadoutItem()), bx + 2, sy + 2);
        } else {
            g.drawString(font, "?", bx + 8, sy + 6, 0xFF9060C0);
        }
        boolean bookHover = mouseX >= bx && mouseX < bx + SLOT_SIZE
                && mouseY >= sy && mouseY < sy + SLOT_SIZE;
        if (bookHover && drag == null) {
            tooltip(g, font, "Spellbook item",
                    (bookSet ? "§f" + itemName(s.loadoutItem())
                            + "§7 — right-click casts the selected skill, sneak+right-click cycles."
                            : "§7No item bound yet.")
                    + "\n§ePick an item up from your inventory and click here to set it."
                    + (bookSet ? "\n§8Click with an empty cursor to unbind." : ""));
        }

        // Spellbook hint.
        int hy = sy + SLOT_SIZE + 4;
        String book = bookSet
                ? "§7Spellbook: §f" + itemName(s.loadoutItem())
                : "§8No spellbook — drop an item on the ? slot";
        g.drawString(font, trim(font, book, PANEL_WIDTH - 16), tx, hy, 0xFFFFFFFF);

        g.fill(x + 4, hy + 12, x + PANEL_WIDTH - 4, hy + 13, 0xFF44445A);

        // The skill list.
        int ly = listY(screen);
        for (int i = 0; i < s.skills().size(); i++) {
            var skill = s.skills().get(i);
            int ry = ly + i * ROW_HEIGHT;
            boolean hover = mouseX >= x + 4 && mouseX < x + PANEL_WIDTH - 4
                    && mouseY >= ry && mouseY < ry + ROW_HEIGHT;
            boolean inLoadout = s.loadout().contains(skill.id());
            if (hover) g.fill(x + 4, ry, x + PANEL_WIDTH - 4, ry + ROW_HEIGHT, 0x28FFFFFF);

            g.renderItem(icon(skill.icon()), tx, ry + 1);
            if (!skill.owned()) {
                g.fill(tx, ry + 1, tx + 16, ry + 17, 0xA0101018); // greyed-out icon
            }
            String line;
            boolean soulLocked = !skill.owned() && !skill.karmaNote().isEmpty()
                    && s.level() >= skill.levelReq();
            if (soulLocked) {
                // The honest lock reason: it's the soul, not the level.
                line = "§8" + skill.name() + " §5[soul]";
            } else if (!skill.owned()) {
                line = "§8" + skill.name() + " §7[lvl " + skill.levelReq()
                        + (skill.cost() > 0 ? ", " + skill.cost() + "sp" : "") + "]";
            } else if (skill.readyInSec() > 0) {
                line = "§c" + skill.name() + " §7" + skill.readyInSec() + "s";
            } else {
                line = (inLoadout ? "§6" : "§a") + skill.name()
                        + (inLoadout ? " §8◆" : "")
                        + " §8" + skill.type().toLowerCase().charAt(0);
            }

            // The Buy chip: unowned, purchasable, level met, points in hand.
            boolean buyable = !skill.owned() && skill.cost() > 0
                    && s.level() >= skill.levelReq()
                    && (s.spTotal() - s.spSpent()) >= skill.cost();
            // Text never runs under the button: reserve its width up front.
            int reserved = buyable ? font.width("§lBuy " + skill.cost()) + 18 : 10;
            g.drawString(font, trim(font, line, PANEL_WIDTH - 24 - reserved), tx + 20, ry + 5, 0xFFFFFFFF);
            if (buyable) {
                String chip = "Buy " + skill.cost();
                int cw = font.width("§l" + chip) + 8;
                int cx = x + PANEL_WIDTH - 6 - cw;
                boolean chipHover = mouseX >= cx && mouseX < cx + cw
                        && mouseY >= ry + 3 && mouseY < ry + 15;
                buyButton(g, font, cx, ry + 3, cw, 12, chip, chipHover);
                HOTSPOTS.add(new Hot(cx, ry + 3, cx + cw, ry + 15, 0,
                        () -> send(new com.sablednah.legendquest.network.SkillActionPayload(
                                com.sablednah.legendquest.network.SkillActionPayload.BUY_SKILL,
                                0, skill.id()))));
            }

            HOTSPOTS.add(new Hot(x + 4, ry, x + PANEL_WIDTH - 4, ry + ROW_HEIGHT, 1,
                    () -> HandbookScreen.open("skill", skill.id())));
            if (hover && drag == null) {
                tooltip(g, font, skill.name(), skillTooltip(skill) + rowHint(skill, inLoadout)
                        + (buyable ? "\n§aThe green chip buys it." : "")
                        + "\n§8Right-click: handbook");
            }
        }

        // The drag ghost rides the cursor.
        if (drag != null) {
            var entry = findSkill(s, drag.skillId());
            if (entry != null) {
                g.renderItem(icon(entry.icon()), (int) mouseX - 8, (int) mouseY - 8);
            }
        }
    }

    private static String rowHint(CharacterSummaryPayload.SkillEntry skill, boolean inLoadout) {
        if (!skill.owned()) {
            return skill.cost() > 0 ? "\n§8Buy with /skill buy when you have the points" : "";
        }
        if (!"ACTIVE".equals(skill.type())) {
            return "\n§8" + (skill.type().equals("PASSIVE") ? "Always on." : "Fires on its trigger.");
        }
        return inLoadout ? "\n§8Already in the loadout" : "\n§eClick or drag to the loadout";
    }

    private static String skillTooltip(CharacterSummaryPayload.SkillEntry skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("§7").append(skill.type().toLowerCase());
        if (skill.manaCost() > 0) sb.append(" §9· ").append(skill.manaCost()).append(" mana");
        if (skill.cooldownSec() > 0) sb.append(" §7· ").append(skill.cooldownSec()).append("s cooldown");
        if (!skill.description().isEmpty()) sb.append("\n§f").append(skill.description());
        if (!skill.karmaNote().isEmpty()) {
            sb.append("\n§5Soul-bound: ").append(skill.karmaNote());
        }
        if (!skill.owned() && skill.karmaNote().isEmpty()) {
            sb.append("\n§cRequires level ").append(skill.levelReq());
            if (skill.cost() > 0) sb.append(" + ").append(skill.cost()).append(" skill points");
        }
        return sb.toString();
    }

    /**
     * Queue a tooltip: the vanilla deferred-tooltip hook is flushed before
     * Render.Post handlers run, so anything we defer to IT never shows —
     * we draw our own at the very end of the panel render instead (so no
     * later row paints over it). Word-wrapped, gold-framed, kept on screen.
     */
    private static String[] pendingTooltip;

    private static void tooltip(GuiGraphics g, Font font, String title, String body) {
        pendingTooltip = new String[] {title, body};
    }

    private static void drawPendingTooltip(GuiGraphics g, Font font) {
        if (pendingTooltip == null) return;
        String title = pendingTooltip[0];
        String body = pendingTooltip[1];
        pendingTooltip = null;
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal("§6" + title).getVisualOrderText());
        for (String para : body.split("\n")) {
            lines.addAll(font.split(net.minecraft.network.chat.FormattedText.of(para), 160));
        }
        int w = 0;
        for (FormattedCharSequence line : lines) w = Math.max(w, font.width(line));
        int h = lines.size() * 10 + 6;
        int x = (int) mouseX + 12;
        int y = (int) mouseY - 6;
        if (x + w + 6 > g.guiWidth()) x = (int) mouseX - w - 14;
        if (y + h > g.guiHeight()) y = g.guiHeight() - h - 2;
        if (x < 2) x = 2;
        if (y < 2) y = 2;

        g.fill(x - 3, y - 3, x + w + 3, y + h - 1, 0xF0100C14);
        g.fill(x - 3, y - 3, x + w + 3, y - 2, 0xFFDAA520);
        g.fill(x - 3, y + h - 2, x + w + 3, y + h - 1, 0xFFDAA520);
        g.fill(x - 3, y - 3, x - 2, y + h - 1, 0xFFDAA520);
        g.fill(x + w + 2, y - 3, x + w + 3, y + h - 1, 0xFFDAA520);
        int ty = y;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x, ty, 0xFFFFFFFF);
            ty += 10;
        }
    }

    // --- small helpers ---

    private static CharacterSummaryPayload.SkillEntry findSkill(CharacterSummaryPayload s, String id) {
        for (var skill : s.skills()) {
            if (skill.id().equals(id)) return skill;
        }
        return null;
    }

    private static ItemStack icon(String id) {
        return ICON_CACHE.computeIfAbsent(id, key -> {
            Identifier rl = Identifier.tryParse(key);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
                return new ItemStack(Items.ENCHANTED_BOOK);
            }
            return new ItemStack(BuiltInRegistries.ITEM.getValue(rl));
        });
    }

    private static String itemName(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return id;
        return new ItemStack(BuiltInRegistries.ITEM.getValue(rl)).getHoverName().getString();
    }

    private static String trim(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String out = text;
        while (!out.isEmpty() && font.width(out + "…") > width) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    private record PickerHit(CharacterSummaryPayload.PickEntry entry, boolean race) {}

    /** Mirrors {@link #renderStatsTab}'s layout maths for click hit-testing. */
    private static PickerHit pickerRowAt(InventoryScreen screen, double mx, double my) {
        CharacterSummaryPayload s = summary();
        if (s == null) return null;
        int tx = panelX(screen) + 8;
        if (mx < tx - 2 || mx >= tx + PANEL_WIDTH - 14) return null;
        int ty = panelY(screen) + 8 + 12 + 12 + 12 + 35 + 12 + 12 // top of picker block
                + (boostRowShown(s) ? 24 : 0);
        if (!s.raceChoices().isEmpty()) {
            ty += 13;
            for (CharacterSummaryPayload.PickEntry entry : s.raceChoices()) {
                if (my >= ty - 1 && my < ty + 10) return new PickerHit(entry, true);
                ty += 11;
            }
            ty += 4;
        }
        if (!s.classChoices().isEmpty()) {
            ty += 13;
            for (CharacterSummaryPayload.PickEntry entry : s.classChoices()) {
                if (my >= ty - 1 && my < ty + 10) return new PickerHit(entry, false);
                ty += 11;
            }
        }
        return null;
    }

    private CharacterPanel() {}
}
