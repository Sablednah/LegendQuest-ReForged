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

    public static Component colored(String text) {
        return Component.literal(text.replace('&', '§'));
    }

    private Feedback() {}
}
