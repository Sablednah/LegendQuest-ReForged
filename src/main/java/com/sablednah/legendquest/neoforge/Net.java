package com.sablednah.legendquest.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The one true clientbound send. NeoForge does NOT silently drop optional
 * payloads to clients that never negotiated the channel — it THROWS, which
 * on the login path kicked vanilla players with "Invalid player data"
 * (found by the first real vanilla-client test, as such things are).
 * Every clientbound payload goes through this guard.
 */
public final class Net {

    public static void sendIfAble(ServerPlayer player, CustomPacketPayload payload) {
        // Null check courtesy of the ZombieMod session: fake players (other
        // mods' automation, headless probes) sit in the player list with no
        // real connection — an NPE here has the same blast radius as the
        // original bug, from a different direction.
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private Net() {}
}
