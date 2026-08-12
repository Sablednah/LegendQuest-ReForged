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
        registrar.playToServer(SkillActionPayload.TYPE, SkillActionPayload.CODEC,
                LQNetwork::handleSkillAction);
    }

    /** Server only (playToServer): hotkey skill actions from modded clients. */
    private static void handleSkillAction(SkillActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                com.sablednah.legendquest.neoforge.SkillActions.handle(
                        player, payload.action(), payload.slot());
            }
        });
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
