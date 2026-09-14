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
        if (listening(player, payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * Whether this player's client negotiated {@code type} — and whether there
     * is a client there to ask at all.
     *
     * <p><b>A null check is not enough, and was the whole guard until
     * Chronicler's self-test died on it.</b> A NeoForge {@code FakePlayer} (other
     * mods' deployers and automation, API callers handing a fake player XP) HAS
     * a connection: a {@code FakePlayerNetHandler} over a connection whose netty
     * channel is null. {@code hasChannel} reads an attribute off that channel and
     * throws a {@code NullPointerException} out of whatever event asked. So a
     * fake player is refused by name ({@code isFakePlayer} covers subclasses),
     * and a hand-rolled fake {@code ServerPlayer} by its connection not being
     * connected. ZombieMod and Chronicler settled on this same shape.</p>
     *
     * <p>Sending to a fake player would have been harmless — its send is a
     * no-op. Only the question throws.</p>
     */
    public static boolean listening(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null
                && !player.isFakePlayer()
                && player.connection.getConnection().isConnected()
                && player.connection.hasChannel(type);
    }

    private Net() {}
}
