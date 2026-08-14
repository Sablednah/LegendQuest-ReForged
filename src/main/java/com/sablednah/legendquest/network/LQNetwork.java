package com.sablednah.legendquest.network;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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
        registrar.playToClient(CombatIndicatorPayload.TYPE, CombatIndicatorPayload.CODEC,
                LQNetwork::handleIndicator);
        registrar.playToClient(HandbookPayload.TYPE, HandbookPayload.CODEC,
                LQNetwork::handleHandbook);
        registrar.playToClient(VocabPayload.TYPE, VocabPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.sablednah.legendquest.client.ClientVocab.accept(payload)));
        registrar.playToClient(NoticePayload.TYPE, NoticePayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.sablednah.legendquest.client.ClientNotices.accept(payload)));
        registrar.playToServer(SkillActionPayload.TYPE, SkillActionPayload.CODEC,
                LQNetwork::handleSkillAction);
        registrar.playToServer(LoadoutEditPayload.TYPE, LoadoutEditPayload.CODEC,
                LQNetwork::handleLoadoutEdit);
        registrar.playToServer(ChoosePayload.TYPE, ChoosePayload.CODEC,
                LQNetwork::handleChoose);
        registrar.playToServer(PartyActionPayload.TYPE, PartyActionPayload.CODEC,
                LQNetwork::handlePartyAction);
    }

    /**
     * Client only (playToClient). The client state classes are referenced
     * only inside the enqueued lambdas, so they load lazily on first packet
     * and are never pulled in on a dedicated server during registration.
     */
    private static void handleSummary(CharacterSummaryPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.sablednah.legendquest.client.ClientCharacterState.accept(payload));
    }

    private static void handleIndicator(CombatIndicatorPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.sablednah.legendquest.client.CombatIndicators.accept(payload));
    }

    private static void handleHandbook(HandbookPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.sablednah.legendquest.client.ClientHandbook.accept(payload));
    }

    // --- serverbound (all validated server-side; the GUI only requests) ---

    private static void handleSkillAction(SkillActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.sablednah.legendquest.neoforge.SkillActions.handle(
                        player, payload.action(), payload.slot(), payload.id());
            }
        });
    }

    private static void handleLoadoutEdit(LoadoutEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.sablednah.legendquest.neoforge.SkillActions.handleLoadoutEdit(player, payload);
            }
        });
    }

    private static void handlePartyAction(PartyActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            switch (payload.action()) {
                case PartyActionPayload.CREATE ->
                        com.sablednah.legendquest.neoforge.PartyActions.createAuto(player);
                case PartyActionPayload.INVITE ->
                        com.sablednah.legendquest.neoforge.PartyActions.invite(player, payload.name());
                case PartyActionPayload.ACCEPT ->
                        com.sablednah.legendquest.neoforge.PartyActions.accept(player);
                case PartyActionPayload.DECLINE ->
                        com.sablednah.legendquest.neoforge.PartyActions.decline(player);
                case PartyActionPayload.LEAVE ->
                        com.sablednah.legendquest.neoforge.PartyActions.leave(player);
                case PartyActionPayload.TP ->
                        com.sablednah.legendquest.neoforge.PartyActions.teleport(player);
                default -> { }
            }
        });
    }

    private static void handleChoose(ChoosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Identifier id = Identifier.tryParse(payload.id());
            if (id == null) return;
            switch (payload.kind()) {
                case ChoosePayload.RACE ->
                        com.sablednah.legendquest.neoforge.CharacterActions.chooseRace(player, id);
                case ChoosePayload.MAIN_CLASS ->
                        com.sablednah.legendquest.neoforge.CharacterActions.chooseClass(player, id, false);
                default -> { }
            }
        });
    }

    private LQNetwork() {}
}
