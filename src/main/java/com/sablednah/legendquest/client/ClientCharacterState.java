package com.sablednah.legendquest.client;

import com.sablednah.legendquest.network.CharacterSummaryPayload;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The last character summary the server pushed. Cleared on logout so one
 * server's character doesn't linger into the next; null means "no data"
 * (vanilla server, or nothing received yet).
 */
public final class ClientCharacterState {

    private static volatile @Nullable CharacterSummaryPayload summary;

    public static void accept(CharacterSummaryPayload payload) {
        summary = payload;
    }

    public static @Nullable CharacterSummaryPayload summary() {
        return summary;
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        summary = null;
    }

    private ClientCharacterState() {}
}
