package com.sablednah.legendquest.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Player-facing messages. '&' colour codes translate to '§'. */
public final class Feedback {

    public static void actionBar(ServerPlayer player, String text) {
        player.displayClientMessage(colored(text), true);
    }

    public static void chat(ServerPlayer player, String text) {
        player.displayClientMessage(colored(text), false);
    }

    /**
     * A notice that must survive an open GUI: modded clients draw it over
     * the screen (chat is blurred behind inventories); vanilla clients get
     * ordinary chat.
     */
    public static void notify(ServerPlayer player, String text) {
        if (player.connection.hasChannel(com.sablednah.legendquest.network.NoticePayload.TYPE)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.sablednah.legendquest.network.NoticePayload(text));
        } else {
            chat(player, text);
        }
    }

    public static Component colored(String text) {
        return Component.literal(text.replace('&', '§'));
    }

    private Feedback() {}
}
