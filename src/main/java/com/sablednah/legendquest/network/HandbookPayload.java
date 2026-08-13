package com.sablednah.legendquest.network;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The LegendQuest Players Handbook, server → client, once on login. Pages
 * are pre-rendered server-side into lines; a line may carry a link to
 * another entry ("race"/"class"/"skill"/"gear" + id) and/or an item icon.
 * The client stays a dumb, happy book.
 */
public record HandbookPayload(
        List<Entry> races,
        List<Entry> classes,
        List<Entry> skills,
        List<Entry> gear,
        List<Entry> feats) implements CustomPacketPayload {

    /** One page. Icon is an item id; {@code cost} is skill points for feat
     *  pages (0 elsewhere) so the client can offer a live Buy chip. */
    public record Entry(String id, String name, String icon, int cost, List<Line> lines) {}

    /** One page line. {@code linkSection} is ""/"race"/"class"/"skill"/"gear";
     *  {@code icon} is an item id rendered before the text ("" = none). */
    public record Line(String text, String icon, String linkSection, String linkId) {
        public static Line text(String text) { return new Line(text, "", "", ""); }
        public static Line link(String text, String section, String id) {
            return new Line(text, "", section, id);
        }
        public static Line icon(String text, String icon) { return new Line(text, icon, "", ""); }
        public boolean isLink() { return !linkSection.isEmpty(); }
    }

    public static final Type<HandbookPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LegendQuest.MODID, "handbook"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandbookPayload> CODEC =
            StreamCodec.of(HandbookPayload::encode, HandbookPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, HandbookPayload p) {
        writeEntries(buf, p.races);
        writeEntries(buf, p.classes);
        writeEntries(buf, p.skills);
        writeEntries(buf, p.gear);
        writeEntries(buf, p.feats);
    }

    private static HandbookPayload decode(RegistryFriendlyByteBuf buf) {
        return new HandbookPayload(readEntries(buf), readEntries(buf), readEntries(buf),
                readEntries(buf), readEntries(buf));
    }

    private static void writeEntries(RegistryFriendlyByteBuf buf, List<Entry> entries) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUtf(entry.id());
            buf.writeUtf(entry.name());
            buf.writeUtf(entry.icon());
            buf.writeVarInt(entry.cost());
            buf.writeVarInt(entry.lines().size());
            for (Line line : entry.lines()) {
                buf.writeUtf(line.text());
                buf.writeUtf(line.icon());
                buf.writeUtf(line.linkSection());
                buf.writeUtf(line.linkId());
            }
        }
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int n = 0; n < count; n++) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            String icon = buf.readUtf();
            int cost = buf.readVarInt();
            int lineCount = buf.readVarInt();
            List<Line> lines = new ArrayList<>(lineCount);
            for (int l = 0; l < lineCount; l++) {
                lines.add(new Line(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()));
            }
            entries.add(new Entry(id, name, icon, cost, lines));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
