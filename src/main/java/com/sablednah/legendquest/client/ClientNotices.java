package com.sablednah.legendquest.client;

import com.sablednah.legendquest.network.NoticePayload;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The last server notice, drawn top-centre over whatever LegendQuest UI is
 * showing (panel, handbook, or plain HUD) for a few seconds — readable in
 * front of the GUI blur that eats chat messages.
 */
public final class ClientNotices {

    private static final long SHOW_MS = 4000;

    private static volatile String message;
    private static volatile long since;

    public static void accept(NoticePayload payload) {
        message = payload.message().replace('&', '§');
        since = System.currentTimeMillis();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        message = null;
    }

    /** Draw the active notice, if any. Call late so nothing paints over it. */
    public static void draw(GuiGraphics g, Font font) {
        String text = message;
        if (text == null) return;
        long age = System.currentTimeMillis() - since;
        if (age > SHOW_MS) return;
        float alpha = age < SHOW_MS - 600 ? 1.0F : (SHOW_MS - age) / 600.0F;
        int a = Math.max(16, Math.round(alpha * 255));

        int w = font.width(text);
        int x = (g.guiWidth() - w) / 2;
        int y = 8;
        g.fill(x - 5, y - 4, x + w + 5, y + 12, (Math.round(a * 0.88F) << 24) | 0x100C14);
        int gold = (a << 24) | 0xDAA520;
        g.fill(x - 5, y - 4, x + w + 5, y - 3, gold);
        g.fill(x - 5, y + 11, x + w + 5, y + 12, gold);
        g.drawString(font, text, x, y, (a << 24) | 0xFFFFFF);
    }

    private ClientNotices() {}
}
