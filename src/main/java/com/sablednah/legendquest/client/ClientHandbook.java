package com.sablednah.legendquest.client;

import com.sablednah.legendquest.network.HandbookPayload;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** The Players Handbook as last sent by this server; null on vanilla ones. */
public final class ClientHandbook {

    private static volatile HandbookPayload book;

    public static void accept(HandbookPayload payload) {
        book = payload;
    }

    public static HandbookPayload get() {
        return book;
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        book = null;
    }

    private ClientHandbook() {}
}
