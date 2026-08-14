package com.sablednah.legendquest.client;

import java.util.Map;

import com.sablednah.legendquest.network.VocabPayload;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The server's vocabulary, as last synced. Every label the panel, handbook
 * and HUD draw goes through {@link #get} — server says "Archetype", client
 * says "Archetype". Defaults cover single-player-before-sync and any key an
 * older server doesn't send.
 */
public final class ClientVocab {

    private static volatile Map<String, String> vocab = Map.of();

    public static void accept(VocabPayload payload) {
        vocab = Map.copyOf(payload.entries());
    }

    public static String get(String key, String fallback) {
        return vocab.getOrDefault(key, fallback);
    }

    /** Term by short name: {@code term("race", "Race")}. */
    public static String term(String name, String fallback) {
        return vocab.getOrDefault("term." + name, fallback);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        vocab = Map.of();
    }

    private ClientVocab() {}
}
