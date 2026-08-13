package com.sablednah.legendquest.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.sablednah.legendquest.network.HandbookPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * The LegendQuest Players Handbook: Races / Classes / Skills tabs, an entry
 * list on the left, and a scrollable detail page on the right whose link
 * lines jump between entries (with a back stack, like any good book of
 * lore with too many footnotes). Content is whatever {@link ClientHandbook}
 * last received — pure data, zero client knowledge of the rule set.
 */
public final class HandbookScreen extends Screen {

    private static final int BOOK_W = 306;
    private static final int LIST_W = 92;
    private static final int HEADER_H = 34;

    private record Target(String section, String id) {}

    private String section; // "race" | "class" | "skill"
    private String selectedId;
    private double scroll = 0;
    private final Deque<Target> history = new ArrayDeque<>();

    /** Link/list hit regions, rebuilt every frame (immediate-mode style). */
    private record Hot(int x0, int y0, int x1, int y1, Target target) {}
    private final List<Hot> hotspots = new ArrayList<>();

    private Button backButton;

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
    private int bookH() { return Math.min(220, height - 24); }
    private int bookY() { return (height - bookH()) / 2; }
    private int paneX() { return bookX() + LIST_W + 10; }
    private int paneW() { return bookX() + BOOK_W - 8 - paneX(); }
    private int paneY() { return bookY() + HEADER_H; }
    private int paneH() { return bookY() + bookH() - 8 - paneY(); }

    private List<HandbookPayload.Entry> entries() {
        HandbookPayload book = ClientHandbook.get();
        if (book == null) return List.of();
        return switch (section) {
            case "class" -> book.classes();
            case "skill" -> book.skills();
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

    // --- widgets ---

    @Override
    protected void init() {
        int x = bookX();
        int y = bookY() + 14;
        backButton = Button.builder(Component.literal("«"), b -> goBack())
                .bounds(x + 6, y, 16, 16).build();
        addRenderableWidget(backButton);
        addRenderableWidget(tabButton(x + 26, y, "Races", "race"));
        addRenderableWidget(tabButton(x + 26 + 58, y, "Classes", "class"));
        addRenderableWidget(tabButton(x + 26 + 116, y, "Skills", "skill"));
        addRenderableWidget(Button.builder(Component.literal("✕"), b -> onClose())
                .bounds(x + BOOK_W - 24, y, 16, 16).build());
    }

    private Button tabButton(int x, int y, String label, String target) {
        return Button.builder(Component.literal(label), b -> {
            pushHistory();
            section = target;
            selectedId = null;
            scroll = 0;
        }).bounds(x, y, 56, 16).build();
    }

    private void pushHistory() {
        var current = selected();
        if (current != null) history.push(new Target(section, current.id()));
    }

    private void goBack() {
        Target target = history.poll();
        if (target != null) {
            section = target.section();
            selectedId = target.id();
            scroll = 0;
        }
    }

    private void navigate(Target target) {
        pushHistory();
        section = target.section();
        selectedId = target.id();
        scroll = 0;
    }

    // --- rendering ---

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // background + widgets
        hotspots.clear();

        int x = bookX();
        int y = bookY();
        int h = bookH();

        // The book plate.
        g.fill(x, y, x + BOOK_W, y + h, 0xF0121018);
        g.fill(x, y, x + BOOK_W, y + 1, 0xFFDAA520);
        g.fill(x, y + h - 1, x + BOOK_W, y + h, 0xFFDAA520);
        g.fill(x, y, x + 1, y + h, 0xFFDAA520);
        g.fill(x + BOOK_W - 1, y, x + BOOK_W, y + h, 0xFFDAA520);
        g.drawCenteredString(font, "§6§lLegendQuest Players Handbook", x + BOOK_W / 2, y + 4, 0xFFFFFFFF);

        backButton.active = !history.isEmpty();

        // Entry list.
        int ly = paneY();
        var current = selected();
        for (var entry : entries()) {
            boolean isSelected = current != null && entry.id().equals(current.id());
            boolean hover = mouseX >= x + 6 && mouseX < x + LIST_W
                    && mouseY >= ly && mouseY < ly + 12;
            if (hover) g.fill(x + 5, ly - 1, x + LIST_W, ly + 10, 0x28FFFFFF);
            String colour = isSelected ? "§6§l" : hover ? "§e" : "§7";
            g.drawString(font, colour + trim(entry.name(), LIST_W - 14), x + 8, ly, 0xFFFFFFFF);
            if (!isSelected) {
                hotspots.add(new Hot(x + 6, ly - 1, x + LIST_W, ly + 10,
                        new Target(section, entry.id())));
            }
            ly += 12;
        }

        // Divider.
        g.fill(x + LIST_W + 4, paneY() - 2, x + LIST_W + 5, y + h - 8, 0xFF44445A);

        if (current == null) {
            g.drawString(font, "§8Nothing here yet.", paneX(), paneY(), 0xFFFFFFFF);
            return;
        }

        // Detail pane, scrolled and scissored.
        int px = paneX();
        int py = paneY();
        int pw = paneW();
        int ph = paneH();
        g.enableScissor(px, py, px + pw, py + ph);
        int cy = py - (int) scroll;

        // Page title with optional icon.
        if (!current.icon().isEmpty()) {
            g.renderItem(iconStack(current.icon()), px, cy - 3);
            g.drawString(font, "§6§l" + current.name(), px + 20, cy, 0xFFFFFFFF);
        } else {
            g.drawString(font, "§6§l" + current.name(), px, cy, 0xFFFFFFFF);
        }
        cy += 14;

        for (var line : current.lines()) {
            if (line.isLink()) {
                boolean hover = mouseX >= px && mouseX < px + pw
                        && mouseY >= cy - 1 && mouseY < cy + 10
                        && mouseY >= py && mouseY < py + ph;
                if (hover) g.fill(px - 1, cy - 1, px + pw, cy + 10, 0x28FFFFFF);
                g.drawString(font, (hover ? "§b§n" : "§b") + line.text(), px, cy, 0xFFFFFFFF);
                hotspots.add(new Hot(px, cy - 1, px + pw, cy + 10,
                        new Target(line.linkSection(), line.linkId())));
                cy += 11;
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

        // Scroll shadow hints when there's more page than pane.
        int contentH = (cy + (int) scroll) - py;
        if (scroll > 0) g.fillGradient(px, py, px + pw, py + 8, 0xA0000000, 0x00000000);
        if (contentH - scroll > ph) {
            g.fillGradient(px, py + ph - 8, px + pw, py + ph, 0x00000000, 0xA0000000);
        }
        maxScroll = Math.max(0, contentH - ph);
    }

    private double maxScroll = 0;

    // --- input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        for (Hot hot : hotspots) {
            if (event.x() >= hot.x0() && event.x() < hot.x1()
                    && event.y() >= hot.y0() && event.y() < hot.y1()) {
                navigate(hot.target());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        scroll = Math.max(0, Math.min(maxScroll, scroll - scrollY * 12));
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
