package com.sablednah.legendquest.neoforge;

import java.util.UUID;

import com.sablednah.legendquest.neoforge.Parties.Party;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A chat channel only your party can read.
 *
 * <p>Routing on top of state that already exists — membership, a leader, a
 * name — rather than anything new to persist. It is deliberately built as
 * plain chat rather than a GUI panel: vanilla clients joining and playing the
 * whole game is a headline claim of this mod, and a channel they could not use
 * would quietly make that untrue.</p>
 *
 * <h2>Ops eavesdrop by opt-in</h2>
 *
 * <p>An op who <em>chooses</em> to listen is a different thing to players than
 * an op who cannot avoid hearing, and only the first is defensible. Spying is
 * off by default, behind {@link LQPermissions#PARTY_SPY} so it is a capability
 * a server grants rather than a side effect of being op, and behind a
 * per-player toggle on top of that.</p>
 *
 * <p>A listener hears <em>every</em> party at once, so their copy of the line
 * carries the party name while a member's copy does not — the same message
 * renders two ways depending on who is receiving it. Without that a spy sees
 * an unattributable stream of overlapping conversations.</p>
 */
public final class PartyChat {

    /**
     * Sends {@code message} to the sender's party, and to any eligible spies.
     *
     * @return false if they are in no party, having told them so
     */
    public static boolean send(ServerPlayer sender, String message) {
        MinecraftServer server = sender.level().getServer();
        if (server == null) return false;

        Parties parties = Parties.get(server);
        Party party = parties.partyOf(sender.getUUID()).orElse(null);
        if (party == null) {
            Feedback.chat(sender, Lang.get("msg.party.chat.no_party"));
            return false;
        }

        String name = sender.getName().getString();
        String toMembers = Lang.fmt("msg.party.chat.line", "player", name, "message", message);
        String toSpies = Lang.fmt("msg.party.chat.spy_line",
                "party", party.name(), "player", name, "message", message);

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            UUID id = online.getUUID();
            if (party.isMember(id)) {
                Feedback.chat(online, toMembers);
            } else if (isSpying(online)) {
                // Only ever the spy copy for a non-member, so a listening op
                // in their own party still reads their own party normally.
                Feedback.chat(online, toSpies);
            }
        }

        // Party chat is still chat: it belongs in the log like anything else a
        // player said, or moderating it after the fact is impossible.
        com.sablednah.legendquest.LegendQuest.LOGGER.info(
                "[party:{}] <{}> {}", party.name(), name, message);
        return true;
    }

    /** Switches a listener on or off, refusing without the permission. */
    public static boolean setSpying(ServerPlayer player, boolean listening) {
        if (listening && !LQPermissions.canPartySpy(player)) {
            Feedback.chat(player, Lang.get("msg.party.spy.denied"));
            return false;
        }
        CharacterService.data(player).setPartySpy(listening);
        Feedback.chat(player, Lang.get(listening ? "msg.party.spy.on" : "msg.party.spy.off"));
        return true;
    }

    /**
     * The permission is rechecked on every message, not just when the toggle is
     * set: revoking it should silence a listener immediately rather than at
     * their next login.
     */
    private static boolean isSpying(ServerPlayer player) {
        return CharacterService.data(player).partySpy() && LQPermissions.canPartySpy(player);
    }

    private PartyChat() {}
}
