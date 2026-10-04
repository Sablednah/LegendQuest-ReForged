package com.sablednah.legendquest.client;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.legendquest.core.Dice;
import com.sablednah.legendquest.core.Stat;
import com.sablednah.legendquest.network.CharacterSummaryPayload;
import com.sablednah.legendquest.network.RollPayload;
import com.sablednah.legendquest.network.RollResultPayload;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The dice tab of the character pane: a tray that shows the last roll, a pool
 * of dice, and three controls underneath — which stat to add, an extra
 * modifier on a slider, and normal, advantage or disadvantage. Click a die, or drag one and let go, to throw it.
 *
 * <p><b>A control surface over {@code /roll}, nothing more.</b> The tray sends
 * the words a player would type ("d20 str adv") and the server rolls them with
 * the same code and the same broadcast, so everybody else at the table cannot
 * tell a tray roll from a typed one. {@code /roll} stays the whole feature for
 * a vanilla client.</p>
 *
 * <p><b>The server decides; the tray lands on it.</b> The rattle while the
 * number spins is a canned flicker that ends on the total the server sent back
 * — the client never picks a number anybody else sees.</p>
 *
 * <p>Everything that draws is in this class, because 26.x reworked GUI
 * rendering wholesale and a version drop should find it in one file.
 * {@link CharacterPanel} only routes clicks and releases here.</p>
 */
public final class DiceTray {

    private static final int[] SIDES = {4, 6, 8, 10, 12, 20, 100};

    /** A colour per die, so the pool reads at a glance rather than by label. */
    private static final int[] COLOURS = {
            0xFFE05A4A, 0xFFE89A3A, 0xFFD8D040, 0xFF5AC060, 0xFF4AB0D8, 0xFFDAA520, 0xFFB070E0};

    private static final String[] STAT_NAMES = {"STR", "DEX", "CON", "INT", "WIS", "CHR"};

    // Layout, as offsets from the top-left of the content area. Render and
    // hit-testing both read cells(), so they cannot disagree.
    private static final int WIDTH = 154;
    private static final int TRAY_Y = 6;
    private static final int TRAY_H = 46;
    private static final int DICE_Y = 58;
    private static final int DIE_W = 36;
    private static final int DIE_H = 20;
    private static final int MOD_LABEL_Y = 106;
    private static final int MOD_Y = 117;
    private static final int SLIDER_Y = 153;
    private static final int EDGE_Y = 171;
    private static final int CHIP_H = 14;

    /** Content height, for the pane to size itself. */
    static final int HEIGHT = EDGE_Y + CHIP_H + 8;

    /** How long the number spins before it lands. A beat, not a pause. */
    private static final long RATTLE_MS = 450;
    /** Dice dim this long after a throw. Longer than the server's cooldown,
     *  so a roll the tray lets you make is never one the server drops. */
    private static final long COOLDOWN_MS = 1000;
    /** Stop waiting for a result that is not coming. */
    private static final long GIVE_UP_MS = 2000;

    // The selectors survive the pane closing: somebody rolling DEX all evening
    // should not have to pick it again every time they open the inventory.
    private static int stat = -1; // Stat ordinal; -1 = none
    /** The situational bonus or penalty: cover, a blessing, the GM's say. */
    private static int extra = 0;
    private static final int EXTRA_MAX = 10;
    private static boolean sliding = false;
    private static Dice.Edge edge = Dice.Edge.NONE;

    private static int dragSides = 0; // 0 = nothing in hand
    private static int dragColour;

    private static long thrownAt = -COOLDOWN_MS;
    private static int thrownSides;
    private static boolean awaiting = false;
    private static RollResultPayload result;
    private static long landAt;

    private enum Kind { DIE, STAT, EDGE, SLIDER, RESET }

    /** {@code value}: sides for a die, stat ordinal (-1 none), or edge ordinal. */
    private record Cell(int x, int y, int w, int h, Kind kind, int value, int colour) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static int originX;
    private static int originY;

    // --- server results ---

    public static void accept(RollResultPayload payload) {
        long now = System.currentTimeMillis();
        result = payload;
        // Ours, just thrown: spin out the rest of the beat. Anything else (a
        // typed /roll) gets the whole beat, so every roll lands the same way.
        landAt = awaiting && now - thrownAt < GIVE_UP_MS
                ? Math.max(now, thrownAt + RATTLE_MS)
                : now + RATTLE_MS;
        awaiting = false;
    }

    // --- input, routed from CharacterPanel ---

    static void clicked(double mx, double my, int button) {
        if (button != 0) return;
        for (Cell cell : cells(originX, originY)) {
            if (!cell.contains(mx, my)) continue;
            switch (cell.kind()) {
                case DIE -> {
                    if (!cooling()) {
                        dragSides = cell.value();
                        dragColour = cell.colour();
                    }
                }
                case STAT -> stat = cell.value();
                case EDGE -> edge = Dice.Edge.values()[cell.value()];
                case SLIDER -> {
                    sliding = true;
                    extra = extraAt(cell, mx);
                }
                case RESET -> extra = 0;
            }
            return;
        }
    }

    /** A die in hand or the slider's handle held: either way the host must
     *  keep routing the drag and the release to us. */
    static boolean dragging() {
        return dragSides != 0 || sliding;
    }

    static void clearDrag() {
        dragSides = 0;
        sliding = false;
    }

    /**
     * The end of a press on a die, wherever it lands: a click and a drag both
     * throw it. Clears first, so the second of the two release paths (the host's
     * and the screen event's) finds nothing to do.
     */
    static void released() {
        sliding = false;
        int sides = dragSides;
        dragSides = 0;
        if (sides == 0 || cooling() || ClientCharacterState.summary() == null) return;
        StringBuilder notation = new StringBuilder("d").append(sides);
        if (stat >= 0) notation.append(' ').append(Stat.values()[stat].key());
        // "+3" / "-2" is a bonus to /roll, so the extra needs no new grammar.
        if (extra != 0) notation.append(' ').append(signed(extra));
        if (edge == Dice.Edge.ADVANTAGE) notation.append(" adv");
        if (edge == Dice.Edge.DISADVANTAGE) notation.append(" dis");
        ClientPacketDistributor.sendToServer(new RollPayload(notation.toString()));
        thrownAt = System.currentTimeMillis();
        thrownSides = sides;
        awaiting = true;
    }

    private static boolean cooling() {
        return System.currentTimeMillis() - thrownAt < COOLDOWN_MS;
    }

    // --- layout ---

    private static List<Cell> cells(int x0, int y0) {
        List<Cell> cells = new ArrayList<>();
        // Two rows of four columns; the d20 takes two, being the die that matters.
        int[][] placement = {{0, 0, 1}, {1, 0, 1}, {2, 0, 1}, {3, 0, 1}, {0, 1, 1}, {1, 1, 2}, {3, 1, 1}};
        for (int i = 0; i < SIDES.length; i++) {
            int[] p = placement[i];
            cells.add(new Cell(x0 + p[0] * (DIE_W + 3), y0 + DICE_Y + p[1] * (DIE_H + 3),
                    DIE_W * p[2] + 3 * (p[2] - 1), DIE_H, Kind.DIE, SIDES[i], COLOURS[i]));
        }
        // None stands tall on the left, the six stats sit in a 3×2 grid beside it.
        cells.add(new Cell(x0, y0 + MOD_Y, 34, CHIP_H * 2 + 3, Kind.STAT, -1, 0));
        for (int n = 0; n < 6; n++) {
            cells.add(new Cell(x0 + 36 + (n % 3) * 40, y0 + MOD_Y + (n / 3) * (CHIP_H + 3),
                    38, CHIP_H, Kind.STAT, n, 0));
        }
        // The extra modifier: a "+0" reset chip at the right end of the track.
        cells.add(new Cell(x0 + 30, y0 + SLIDER_Y, 94, CHIP_H, Kind.SLIDER, 0, 0));
        cells.add(new Cell(x0 + 128, y0 + SLIDER_Y, 26, CHIP_H, Kind.RESET, 0, 0));
        // Worse to better, left to right.
        Dice.Edge[] edges = {Dice.Edge.DISADVANTAGE, Dice.Edge.NONE, Dice.Edge.ADVANTAGE};
        for (int i = 0; i < edges.length; i++) {
            cells.add(new Cell(x0 + i * 52, y0 + EDGE_Y, 50, CHIP_H, Kind.EDGE, edges[i].ordinal(), 0));
        }
        return cells;
    }

    // --- drawing ---

    static void render(GuiGraphics g, Font font, int x0, int y0, double mx, double my,
            CharacterSummaryPayload s) {
        originX = x0;
        originY = y0;
        long now = System.currentTimeMillis();

        renderTray(g, font, x0, y0 + TRAY_Y, now, s);

        g.drawString(font, "§7" + ClientVocab.get("ui.dice_modifier", "Add a modifier"),
                x0, y0 + MOD_LABEL_Y, 0xFFFFFFFF);

        g.drawString(font, "§7" + ClientVocab.get("ui.dice_extra", "Extra"),
                x0, y0 + SLIDER_Y + 3, 0xFFFFFFFF);

        boolean cooling = cooling();
        for (Cell cell : cells(x0, y0)) {
            // Follow the cursor every frame while the handle is held, rather
            // than waiting on drag events: the render already has the position.
            if (sliding && cell.kind() == Kind.SLIDER) extra = extraAt(cell, mx);
            boolean hover = cell.contains(mx, my) && dragSides == 0 && !sliding;
            switch (cell.kind()) {
                case DIE -> {
                    drawDie(g, font, cell.x(), cell.y(), cell.w(), cell.h(), cell.value(),
                            cooling || cell.value() == dragSides ? 0xFF55555F : cell.colour(),
                            hover && !cooling);
                    if (hover) {
                        CharacterPanel.tooltip(g, font, describe(cell.value(), stat, modifier(s, stat), extra, edge),
                                ClientVocab.get("ui.dice_tray_tip",
                                        "Click or drag a die to roll it. Everyone sees the result, as with /roll."));
                    }
                }
                case STAT -> {
                    int n = cell.value();
                    String label = n < 0 ? ClientVocab.get("ui.dice_none", "None")
                            : STAT_NAMES[n] + " " + signed(modifier(s, n));
                    chip(g, font, cell, label, stat == n, hover);
                    if (hover) {
                        CharacterPanel.tooltip(g, font, label, n < 0
                                ? ClientVocab.get("ui.dice_none_tip", "Just the die.")
                                : ClientVocab.get("ui.dice_stat_tip", "Adds your modifier for this stat."));
                    }
                }
                case SLIDER -> {
                    drawSlider(g, cell, hover || sliding);
                    if (hover) {
                        CharacterPanel.tooltip(g, font, ClientVocab.get("ui.dice_extra", "Extra") + " " + signed(extra),
                                ClientVocab.get("ui.dice_extra_tip",
                                        "A bonus or penalty from the moment: cover, a blessing, the GM's say. Drag from -10 to +10."));
                    }
                }
                case RESET -> {
                    chip(g, font, cell, signed(extra), extra != 0, hover);
                    if (hover) {
                        CharacterPanel.tooltip(g, font, ClientVocab.get("ui.dice_extra", "Extra") + " " + signed(extra),
                                ClientVocab.get("ui.dice_extra_reset_tip", "Click to set back to 0."));
                    }
                }
                case EDGE -> {
                    Dice.Edge e = Dice.Edge.values()[cell.value()];
                    String label = switch (e) {
                        case ADVANTAGE -> ClientVocab.get("ui.dice_adv", "Adv");
                        case DISADVANTAGE -> ClientVocab.get("ui.dice_dis", "Disadv");
                        case NONE -> ClientVocab.get("ui.dice_normal", "Normal");
                    };
                    chip(g, font, cell, label, edge == e, hover);
                    if (hover) {
                        CharacterPanel.tooltip(g, font, label, switch (e) {
                            case ADVANTAGE -> ClientVocab.get("ui.dice_adv_tip",
                                    "Advantage: roll twice, keep the higher. Both dice are shown.");
                            case DISADVANTAGE -> ClientVocab.get("ui.dice_dis_tip",
                                    "Disadvantage: roll twice, keep the lower. Both dice are shown.");
                            case NONE -> ClientVocab.get("ui.dice_normal_tip", "Roll once.");
                        });
                    }
                }
            }
        }

        // The die in hand follows the cursor, and may leave the pane: the host
        // never scissors, and letting go anywhere throws it.
        if (dragSides != 0) {
            int w = DIE_W;
            drawDie(g, font, (int) mx - w / 2, (int) my - DIE_H / 2, w, DIE_H, dragSides, dragColour, true);
        }
    }

    /** The felt: the last roll, spinning or landed, or how to make one. */
    private static void renderTray(GuiGraphics g, Font font, int x, int y, long now,
            CharacterSummaryPayload s) {
        g.fill(x, y, x + WIDTH, y + TRAY_H, 0xFF5A4020);                  // rim
        g.fill(x + 2, y + 2, x + WIDTH - 2, y + TRAY_H - 2, 0xFF123222);  // felt
        int cx = x + WIDTH / 2;

        boolean waiting = awaiting && now - thrownAt < GIVE_UP_MS;
        if (awaiting && !waiting) awaiting = false; // the answer is not coming
        boolean spinning = waiting || (result != null && now < landAt);

        if (!spinning && result == null) {
            centred(g, font, "§7" + ClientVocab.get("ui.dice_hint_1", "Click or drag a die to roll."), cx, y + 13);
            centred(g, font, "§8" + ClientVocab.get("ui.dice_hint_2", "Everyone sees the result."), cx, y + 25);
            return;
        }

        String heading;
        String big;
        int bigColour;
        String footer;
        if (spinning) {
            int sides = waiting ? thrownSides : result.sides();
            heading = waiting ? describe(thrownSides, stat, modifier(s, stat), extra, edge)
                    : describe(result);
            // A new face every 60ms. Not random-looking on purpose: it only has
            // to read as tumbling for half a second.
            big = String.valueOf((int) ((now / 60) * 7919 % sides) + 1);
            bigColour = 0xFF8A9A8A;
            footer = "§8" + ClientVocab.get("ui.dice_rolling", "rolling…");
        } else {
            heading = describe(result);
            big = String.valueOf(result.total());
            bigColour = result.flair() == RollResultPayload.NAT_20 ? 0xFFFFD040
                    : result.flair() == RollResultPayload.NAT_1 ? 0xFFFF5555 : 0xFFFFFFFF;
            footer = footer(result);
        }

        centred(g, font, "§7" + trim(font, heading, WIDTH - 8), cx, y + 4);
        g.pose().pushMatrix();
        g.pose().translate(cx - font.width(big), y + 15);
        g.pose().scale(2.0F, 2.0F);
        g.drawString(font, big, 0, 0, bigColour);
        g.pose().popMatrix();
        if (!footer.isEmpty()) centred(g, font, trim(font, footer, WIDTH - 8), cx, y + TRAY_H - 12);
    }

    /** Under the total: the flair, then the dice and what advantage threw away. */
    private static String footer(RollResultPayload r) {
        StringBuilder sb = new StringBuilder();
        if (r.flair() == RollResultPayload.NAT_20) {
            sb.append("§6").append(ClientVocab.get("ui.dice_nat20", "Natural 20!")).append(' ');
        } else if (r.flair() == RollResultPayload.NAT_1) {
            sb.append("§c").append(ClientVocab.get("ui.dice_nat1", "Natural 1.")).append(' ');
        }
        int bonus = r.total();
        for (int d : r.dice()) bonus -= d;
        // A lone die with nothing added is already the big number.
        if (r.dice().size() > 1 || bonus != 0) {
            sb.append("§7").append(r.dice());
            if (bonus != 0) sb.append(' ').append(signed(bonus));
            sb.append(' ');
        }
        if (r.dropped() > 0) {
            sb.append("§8").append(ClientVocab.get("ui.dice_dropped", "dropped")).append(' ').append(r.dropped());
        }
        return sb.toString().trim();
    }

    /** "d20 + STR (+2), advantage" — what this die will roll, or did. */
    private static String describe(int sides, int statOrdinal, int mod, int bonus, Dice.Edge e) {
        StringBuilder sb = new StringBuilder("d").append(sides);
        if (statOrdinal >= 0) {
            sb.append(" + ").append(STAT_NAMES[statOrdinal]).append(" (").append(signed(mod)).append(')');
        }
        if (bonus != 0) sb.append(' ').append(signed(bonus));
        if (e == Dice.Edge.ADVANTAGE) sb.append(", ").append(ClientVocab.get("ui.dice_adv", "Adv"));
        if (e == Dice.Edge.DISADVANTAGE) sb.append(", ").append(ClientVocab.get("ui.dice_dis", "Disadv"));
        return sb.toString();
    }

    private static String describe(RollResultPayload r) {
        int statOrdinal = -1;
        for (Stat st : Stat.values()) {
            if (st.key().equals(r.statKey())) statOrdinal = st.ordinal();
        }
        // The payload carries the stat's share; whatever else the total holds
        // beyond the dice is the extra, or a typed "+3".
        int bonus = r.total() - r.statMod();
        for (int d : r.dice()) bonus -= d;
        String base = describe(r.sides(), statOrdinal, r.statMod(), bonus, Dice.Edge.values()[r.edge()]);
        // A typed 4d6 is not a d6.
        return r.dice().size() > 1 ? r.dice().size() + base : base;
    }

    /** The slider's value under {@code mx}, snapped to a whole number. */
    private static int extraAt(Cell track, double mx) {
        double t = (mx - (track.x() + 3)) / (track.w() - 6);
        t = Math.max(0.0, Math.min(1.0, t));
        return (int) Math.round(t * EXTRA_MAX * 2) - EXTRA_MAX;
    }

    private static int extraX(Cell track, int value) {
        return track.x() + 3 + (int) Math.round((value + EXTRA_MAX) * (track.w() - 6) / (EXTRA_MAX * 2.0));
    }

    /** A groove with ticks every 5, a fill from 0 to the value, and a handle. */
    private static void drawSlider(GuiGraphics g, Cell c, boolean active) {
        int mid = c.y() + c.h() / 2;
        g.fill(c.x() + 3, mid - 1, c.x() + c.w() - 3, mid + 1, 0xFF44445A);
        for (int v = -EXTRA_MAX; v <= EXTRA_MAX; v += 5) {
            int tx = extraX(c, v);
            int reach = v == 0 ? 4 : 2;
            g.fill(tx, mid - reach, tx + 1, mid + reach, v == 0 ? 0xFF8A8AA0 : 0xFF5A5A70);
        }
        int zero = extraX(c, 0);
        int at = extraX(c, extra);
        if (extra > 0) g.fill(zero, mid - 1, at, mid + 1, 0xFF5AC060);
        if (extra < 0) g.fill(at, mid - 1, zero + 1, mid + 1, 0xFFE05A4A);
        g.fill(at - 2, c.y() + 1, at + 3, c.y() + c.h() - 1, active ? 0xFFFFD040 : 0xFFDAA520);
        g.fill(at - 2, c.y() + c.h() - 2, at + 3, c.y() + c.h() - 1, 0xFF6A5010);
    }

    private static int modifier(CharacterSummaryPayload s, int statOrdinal) {
        if (s == null || statOrdinal < 0) return 0;
        return Stat.modifier(s.stats()[statOrdinal]);
    }

    private static String signed(int n) {
        return (n >= 0 ? "+" : "") + n;
    }

    private static void drawDie(GuiGraphics g, Font font, int x, int y, int w, int h, int sides,
            int colour, boolean hover) {
        g.fill(x, y, x + w, y + h, hover ? 0xFF2E2A38 : 0xFF1C1A24);
        frame(g, x, y, w, h, colour);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF0C0A10); // shadow under the face
        String label = (hover ? "§l" : "") + "d" + sides;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, colour);
    }

    /** A selector chip, styled like the pane's own tab chips. */
    private static void chip(GuiGraphics g, Font font, Cell c, String label, boolean selected, boolean hover) {
        g.fill(c.x(), c.y(), c.x() + c.w(), c.y() + c.h(),
                selected ? 0xFF3A2C10 : hover ? 0xFF33291E : 0xFF221A12);
        frame(g, c.x(), c.y(), c.w(), c.h(),
                selected ? 0xFFDAA520 : hover ? 0x80DAA520 : 0xFF44445A);
        String drawn = (selected ? "§6" : hover ? "§e" : "§7") + label;
        g.drawString(font, drawn, c.x() + (c.w() - font.width(drawn)) / 2,
                c.y() + (c.h() - 8) / 2 + 1, 0xFFFFFFFF);
    }

    /** The pane's tab-bar chip: a five-pip die face, since the word does not fit. */
    static void drawFace(GuiGraphics g, int x, int y, int colour) {
        g.fill(x, y, x + 8, y + 8, colour);
        int pip = 0xFF1C1A24;
        int[][] pips = {{1, 1}, {5, 1}, {3, 3}, {1, 5}, {5, 5}};
        for (int[] p : pips) g.fill(x + p[0], y + p[1], x + p[0] + 2, y + p[1] + 2, pip);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y, x + 1, y + h, colour);
        g.fill(x + w - 1, y, x + w, y + h, colour);
    }

    private static void centred(GuiGraphics g, Font font, String text, int cx, int y) {
        g.drawString(font, text, cx - font.width(text) / 2, y, 0xFFFFFFFF);
    }

    private static String trim(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    private DiceTray() {}
}
