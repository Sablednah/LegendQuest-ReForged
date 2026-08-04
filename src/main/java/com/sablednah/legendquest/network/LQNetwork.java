package com.sablednah.legendquest.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration (mod bus). {@code optional()} because vanilla clients
 * may connect without this channel — everything client-facing degrades to
 * chat and the action bar.
 */
public final class LQNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(CharacterSummaryPayload.TYPE, CharacterSummaryPayload.CODEC,
                LQNetwork::handleSummary);
    }

    /**
     * Client only (playToClient). The client state class is referenced only
     * inside the enqueued lambda, so it is loaded lazily on first packet and
     * never pulled in on a dedicated server during registration.
     */
    private static void handleSummary(CharacterSummaryPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.sablednah.legendquest.client.ClientCharacterState.accept(payload));
    }

    private LQNetwork() {}
}
