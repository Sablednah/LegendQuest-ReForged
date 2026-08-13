package com.sablednah.legendquest.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.sablednah.legendquest.network.HandbookPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The LegendQuest Players Handbook, dressed as the old magical tome it is:
 * bronze-and-gold frame, ornamented header, glowing tabs (Races / Classes /
 * Skills / Gear), an entry list, and a scrollable page whose link lines jump
 * between entries with a back stack. Gear pages list what a tag actually
 * contains, icons and all. Content is whatever {@link ClientHandbook} last
 * received — pure data, zero client knowledge of the rule set.
 */
public final class HandbookScreen extends Screen {

    private static final int BOOK_W = 306;
    private static final int LIST_W = 92;
    private static final int HEADER_H = 40;

    // The tome palette.
    private static final int PARCHMENT_TOP = 0xF81E1610;
    private static final int PARCHMENT_BOTTOM = 0xF8120C08;
    private static final int BRONZE = 0xFF6B4A1B;
    private static final int GOLD = 0xFFDAA520;
    private static final int GOLD_DIM = 0x80DAA520;
    private static final int INK_DIVIDER = 0xFF44382A;

    private record Target(String section, String id) {}

    private String section; // "race" | "class" | "skill" | "feat" | "gear"
    private String selectedId;
    private double scroll = 0;
    private double maxScroll = 0;
    private double listScroll = 0;
    private double maxListScroll = 0;
    private final Deque<Target> history = new ArrayDeque<>();

    /** Clickable regions, rebuilt every frame (immediate-mode style). */
    private record Hot(int x0, int y0, int x1, int y1, Runnable action) {}
    private final List<Hot> hotspots = new ArrayList<>();

    private HandbookScreen(String section, String id) {
        super(Component.literal("LegendQuest Players Handbook"));
        this.section = section;
        this.selectedId = id;
    }

    /** Open at a section (id may be null = first entry). No-op without data. */
    public static void open(String section, String id) {
        if (ClientHandbook.get() == null) return;
        Minecraft.getInstance().setScreen(new HandbookScreen(section, id));
    }

    public static void open() {
        open("race", null);
    }

    // --- layout ---

    private int bookX() { return (width - BOOK_W) / 2; }
    private int bookH() { return Math.min(226, height - 20); }
    private int bookY() { return (height - bookH()) / 2; }
    private int paneX() { return bookX() + LIST_W + 12; }
    private int paneW() { return bookX() + BOOK_W - 12 - paneX(); }
    private int paneY() { return bookY() + HEADER_H; }
    private int paneH() { return bookY() + bookH() - 10 - paneY(); }

    private List<HandbookPayload.Entry> entries() {
        HandbookPayload book = ClientHandbook.get();
        if (book == null) return List.of();
        return switch (section) {
            case "class" -> book.classes();
            case "skill" -> book.skills();
            case "gear" -> book.gear();
            case "feat" -> book.feats();
            default -> book.races();
        };
    }

    private HandbookPayload.Entry selected() {
        var list = entries();
        if (list.isEmpty()) return null;
        if (selectedId != null) {
            for (var entry : list) {
                if (entry.id().equals(selectedId)) return entry;
            }
        }
        return list.getFirst();
    }

    private void pushHistory() {
        var current = selected();
        if (current != null) history.push(new Target(section, current.id()));
    }

    private void goBack() {
        Target target = history.poll();
        if (target != null) {
            boolean sameList = section.equals(target.section());
            section = target.section();
            selectedId = target.id();
            scroll = 0;
            if (!sameList) listScroll = 0;
        }
    }

    private void navigate(String toSection, String toId) {
        pushHistory();
        if (!section.equals(toSection)) listScroll = 0;
        section = toSection;
        selectedId = toId;
        scroll = 0;
    }

    // --- rendering ---

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // dimmed background
        hotspots.clear();

        int x = bookX();
        int y = bookY();
        int h = bookH();

        // The tome: aged leather gradient in a bronze frame with gold inlay.
        g.fillGradient(x, y, x + BOOK_W, y + h, PARCHMENT_TOP, PARCHMENT_BOTTOM);
        frame(g, x - 2, y - 2, x + BOOK_W + 2, y + h + 2, BRONZE, 2);
        frame(g, x + 2, y + 2, x + BOOK_W - 2, y + h - 2, GOLD_DIM, 1);
        // Corner stars, because every proper grimoire has them.
        g.drawString(font, "§6✦", x + 4, y + 4, 0xFFFFFFFF);
        g.drawString(font, "§6✦", x + BOOK_W - 12, y + 4, 0xFFFFFFFF);
        g.drawString(font, "§6✦", x + 4, y + h - 13, 0xFFFFFFFF);
        g.drawString(font, "§6✦", x + BOOK_W - 12, y + h - 13, 0xFFFFFFFF);

        // Title with flourishes.
        String title = "§6§l Players Handbook ";
        int titleW = font.width(title);
        int titleX = x + (BOOK_W - titleW) / 2;
        g.drawString(font, "§8── ✦ ──", titleX - 42, y + 7, 0xFFFFFFFF);
        g.drawString(font, title, titleX, y + 6, 0xFFFFFFFF);
        g.drawString(font, "§8── ✦ ──", titleX + titleW + 2, y + 7, 0xFFFFFFFF);

        // Tabs, back and close — hand-drawn chips, no vanilla grey here.
        int tabY = y + 19;
        chip(g, x + 8, tabY, 14, "«", !history.isEmpty(), false, mouseX, mouseY, this::goBack);
        int tx = x + 28;
        tx = tab(g, tx, tabY, "Races", "race", mouseX, mouseY);
        tx = tab(g, tx, tabY, "Classes", "class", mouseX, mouseY);
        tx = tab(g, tx, tabY, "Skills", "skill", mouseX, mouseY);
        tx = tab(g, tx, tabY, "Feats", "feat", mouseX, mouseY);
        tab(g, tx, tabY, "Gear", "gear", mouseX, mouseY);
        chip(g, x + BOOK_W - 22, tabY, 14, "✕", true, false, mouseX, mouseY, this::onClose);

        // Divider under the header, with a centre ornament.
        int divY = y + HEADER_H - 5;
        g.fill(x + 8, divY, x + BOOK_W - 8, divY + 1, INK_DIVIDER);
        g.drawString(font, "§6❖", x + BOOK_W / 2 - 3, divY - 4, 0xFFFFFFFF);

        // The feat shop shows your purse, centred in the book's footer.
        if (section.equals("feat")) {
            var summary = ClientCharacterState.summary();
            if (summary != null) {
                String purse = "§7Points to spend: §6§l" + (summary.spTotal() - summary.spSpent());
                g.drawCenteredString(font, purse, x + BOOK_W / 2, y + h - 13, 0xFFFFFFFF);
            }
        }

        // Entry list on its own inset panel — scissored and scrollable, so
        // sixteen gear tags don't spill out of the book's binding.
        int listX = x + 6;
        int listTop = paneY() - 1;
        int listBottom = y + h - 8;
        int listH = listBottom - listTop;
        maxListScroll = Math.max(0, entries().size() * 12 + 2 - listH);
        listScroll = Math.max(0, Math.min(listScroll, maxListScroll));
        g.fill(listX, paneY() - 2, listX + LIST_W - 4, listBottom, 0x30000000);
        g.enableScissor(listX, listTop, listX + LIST_W - 4, listBottom);
        int ly = paneY() + 1 - (int) listScroll;
        var current = selected();
        for (var entry : entries()) {
            boolean rowVisible = ly + 10 > listTop && ly - 1 < listBottom;
            boolean isSelected = current != null && entry.id().equals(current.id());
            boolean hover = rowVisible && mouseX >= listX && mouseX < listX + LIST_W - 4
                    && mouseY >= Math.max(ly - 1, listTop) && mouseY < Math.min(ly + 10, listBottom);
            if (isSelected) g.fill(listX, ly - 1, listX + LIST_W - 4, ly + 10, 0x40DAA520);
            else if (hover) g.fill(listX, ly - 1, listX + LIST_W - 4, ly + 10, 0x28FFFFFF);
            String colour = isSelected ? "§6§l" : hover ? "§e" : "§7";
            String owned = "";
            if (section.equals("feat")) {
                var summary = ClientCharacterState.summary();
                if (summary != null && summary.ownedFeats().contains(entry.id())) owned = "§a✔ ";
            }
            g.drawString(font, owned + colour + trim(entry.name(), LIST_W - 16), listX + 3, ly, 0xFFFFFFFF);
            if (!isSelected && rowVisible) {
                String toId = entry.id();
                hotspots.add(new Hot(listX, Math.max(ly - 1, listTop),
                        listX + LIST_W - 4, Math.min(ly + 10, listBottom),
                        () -> navigate(section, toId)));
            }
            ly += 12;
        }
        g.disableScissor();
        if (maxListScroll > 0) {
            int barX = listX + LIST_W - 6;
            g.fill(barX, listTop, barX + 2, listBottom, 0x50000000);
            int contentH = entries().size() * 12 + 2;
            int thumbH = Math.max(10, listH * listH / contentH);
            int thumbY = listTop + (int) ((listH - thumbH) * (listScroll / maxListScroll));
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xC0DAA520);
        }

        if (current == null) {
            g.drawString(font, "§8Nothing here yet.", paneX(), paneY(), 0xFFFFFFFF);
            return;
        }

        // Detail pane, scrolled and scissored.
        int px = paneX();
        int py = paneY();
        int pw = paneW() - 6; // room for the scrollbar
        int ph = paneH();
        g.enableScissor(px, py, px + pw, py + ph);
        int cy = py - (int) scroll;

        if (!current.icon().isEmpty()) {
            g.renderItem(iconStack(current.icon()), px, cy - 3);
            g.drawString(font, "§6§l" + current.name(), px + 20, cy, 0xFFFFFFFF);
        } else {
            g.drawString(font, "§6§l" + current.name(), px, cy, 0xFFFFFFFF);
        }
        // Feat pages are shops: a live Buy chip (or a proud ✔) beside the title.
        if (section.equals("feat")) {
            var summary = ClientCharacterState.summary();
            if (summary != null) {
                if (summary.ownedFeats().contains(current.id())) {
                    g.drawString(font, "§a✔ Known", px + pw - font.width("✔ Known"), cy, 0xFFFFFFFF);
                } else if (summary.spTotal() - summary.spSpent() >= current.cost()) {
                    String chip = "Buy " + current.cost();
                    int cw = font.width("§l" + chip) + 8;
                    int chipX = px + pw - cw - 2;
                    boolean chipHover = mouseX >= chipX && mouseX < chipX + cw
                            && mouseY >= cy - 3 && mouseY < cy + 9;
                    CharacterPanel.buyButton(g, font, chipX, cy - 3, cw, 12, chip, chipHover);
                    String featId = current.id();
                    hotspots.add(new Hot(chipX, cy - 3, chipX + cw, cy + 9, () ->
                            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                                    new com.sablednah.legendquest.network.SkillActionPayload(
                                            com.sablednah.legendquest.network.SkillActionPayload.BUY_FEAT,
                                            0, featId))));
                }
            }
        }
        cy += 14;

        for (var line : current.lines()) {
            boolean hasIcon = !line.icon().isEmpty();
            if (line.isLink()) {
                boolean hover = mouseX >= px && mouseX < px + pw
                        && mouseY >= cy - 1 && mouseY < cy + 10
                        && mouseY >= py && mouseY < py + ph;
                if (hover) g.fill(px - 1, cy - 1, px + pw, cy + 10, 0x28FFFFFF);
                g.drawString(font, (hover ? "§b§n" : "§b") + line.text(), px, cy, 0xFFFFFFFF);
                String toSection = line.linkSection();
                String toId = line.linkId();
                hotspots.add(new Hot(px, cy - 1, px + pw, cy + 10,
                        () -> navigate(toSection, toId)));
                cy += 11;
            } else if (hasIcon) {
                g.renderItem(iconStack(line.icon()), px + 2, cy - 2);
                g.drawString(font, line.text(), px + 22, cy + 2, 0xFFFFFFFF);
                cy += 15;
            } else if (line.text().isEmpty()) {
                cy += 5;
            } else {
                for (FormattedCharSequence wrapped : font.split(FormattedText.of(line.text()), pw)) {
                    g.drawString(font, wrapped, px, cy, 0xFFFFFFFF);
                    cy += 10;
                }
            }
        }
        g.disableScissor();

        // Scroll affordances: edge shadows plus an honest little scrollbar.
        int contentH = (cy + (int) scroll) - py;
        maxScroll = Math.max(0, contentH - ph);
        if (scroll > 0) g.fillGradient(px, py, px + pw, py + 8, 0xA0000000, 0x00000000);
        if (maxScroll - scroll > 0) {
            g.fillGradient(px, py + ph - 8, px + pw, py + ph, 0x00000000, 0xA0000000);
        }
        if (maxScroll > 0) {
            int barX = px + pw + 3;
            g.fill(barX, py, barX + 2, py + ph, 0x50000000);
            int thumbH = Math.max(12, (int) ((long) ph * ph / contentH));
            int thumbY = py + (int) ((ph - thumbH) * (scroll / maxScroll));
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xC0DAA520);
        }

        ClientNotices.draw(g, font); // buy rejections must beat the blur
    }

    /** A tab chip; returns the next tab's x. */
    private int tab(GuiGraphics g, int x, int y, String label, String target,
            int mouseX, int mouseY) {
        int w = font.width("§l" + label) + 12; // sized for the bold active state
        chip(g, x, y, w, label, true, section.equals(target), mouseX, mouseY, () -> {
            if (!section.equals(target)) {
                pushHistory();
                section = target;
                selectedId = null;
                scroll = 0;
            }
        });
        return x + w + 4;
    }

    private void chip(GuiGraphics g, int x, int y, int w, String label, boolean enabled,
            boolean active, int mouseX, int mouseY, Runnable action) {
        int h = 14;
        boolean hover = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bg = active ? 0xFF3A2C10 : hover ? 0xFF33291E : 0xFF221A12;
        g.fill(x, y, x + w, y + h, bg);
        frame(g, x, y, x + w, y + h, active ? GOLD : hover ? GOLD_DIM : BRONZE, 1);
        String colour = !enabled ? "§8" : active ? "§6§l" : hover ? "§e" : "§7";
        String drawn = colour + label;
        g.drawString(font, drawn, x + (w - font.width(drawn)) / 2, y + 3, 0xFFFFFFFF);
        if (enabled) hotspots.add(new Hot(x, y, x + w, y + h, action));
    }

    /** A hollow rectangle of the given line thickness. */
    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int colour, int t) {
        g.fill(x0, y0, x1, y0 + t, colour);
        g.fill(x0, y1 - t, x1, y1, colour);
        g.fill(x0, y0, x0 + t, y1, colour);
        g.fill(x1 - t, y0, x1, y1, colour);
    }

    // --- input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        for (Hot hot : hotspots) {
            if (event.x() >= hot.x0() && event.x() < hot.x1()
                    && event.y() >= hot.y0() && event.y() < hot.y1()) {
                hot.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        // The wheel scrolls whichever column it hovers over.
        if (mouseX < paneX() - 2) {
            listScroll = Math.max(0, Math.min(maxListScroll, listScroll - scrollY * 12));
        } else {
            scroll = Math.max(0, Math.min(maxScroll, scroll - scrollY * 12));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- helpers ---

    private static ItemStack iconStack(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return new ItemStack(Items.ENCHANTED_BOOK);
        }
        return new ItemStack(BuiltInRegistries.ITEM.getValue(rl));
    }

    private String trim(String text, int width) {
        if (font.width(text) <= width) return text;
        String out = text;
        while (!out.isEmpty() && font.width(out + "…") > width) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }
}
