package com.sablednah.legendquest.neoforge;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

import com.sablednah.legendquest.LQConfig;

import net.minecraft.server.level.ServerPlayer;

/**
 * The single rule set for party actions, shared by /party and the panel's
 * party tab. Mutating actions re-sync every affected member's summary so
 * both GUIs update within the click, not the second.
 */
public final class PartyActions {

    public static boolean create(ServerPlayer player, String name) {
        var error = Parties.get(player.level().getServer()).create(name, player);
        if (error.isPresent()) {
            Feedback.notify(player, error.get());
            return false;
        }
        Feedback.notify(player, Lang.fmt("msg.party.created", "name", name));
        CharacterSync.send(player);
        return true;
    }

    /** GUI create: auto-named after the founder, regex-safe. */
    public static boolean createAuto(ServerPlayer player) {
        String base = player.getName().getString().replaceAll("[^A-Za-z0-9_\\-]", "");
        if (base.isEmpty()) base = "party";
        String name = (base + "s_party");
        if (name.length() > 24) name = name.substring(0, 24);
        return create(player, name);
    }

    public static boolean invite(ServerPlayer player, String inviteeName) {
        var server = player.level().getServer();
        ServerPlayer invitee = server.getPlayerList().getPlayerByName(inviteeName);
        if (invitee == null) {
            Feedback.notify(player, Lang.fmt("msg.party.not_online", "name", inviteeName));
            return false;
        }
        var parties = Parties.get(server);
        var party = parties.partyOf(player.getUUID());
        if (party.isEmpty()) {
            Feedback.notify(player, Lang.get("msg.party.create_first"));
            return false;
        }
        if (invitee == player || party.get().isMember(invitee.getUUID())) {
            Feedback.notify(player, Lang.get("msg.party.already_member"));
            return false;
        }
        if (parties.partyOf(invitee.getUUID()).isPresent()) {
            Feedback.notify(player, Lang.fmt("msg.party.already_partied", "name", invitee.getName().getString()));
            return false;
        }
        parties.invite(party.get(), invitee.getUUID());
        Feedback.notify(player, Lang.fmt("msg.party.invited", "name", invitee.getName().getString()));
        Feedback.notify(invitee, Lang.fmt("msg.party.invitation", "name", player.getName().getString(), "party", party.get().name()));
        CharacterSync.send(invitee); // the invite appears in their GUI now
        return true;
    }

    public static boolean accept(ServerPlayer player) {
        var server = player.level().getServer();
        var joined = Parties.get(server).accept(player.getUUID());
        if (joined.isEmpty()) {
            Feedback.notify(player, Lang.get("msg.party.no_invite"));
            return false;
        }
        Feedback.notify(player, Lang.fmt("msg.party.joined", "name", joined.get().name()));
        for (var memberId : joined.get().members()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member == null) continue;
            if (!memberId.equals(player.getUUID())) {
                Feedback.notify(member, Lang.fmt("msg.party.member_joined", "name", player.getName().getString()));
            }
            CharacterSync.send(member);
        }
        return true;
    }

    public static boolean decline(ServerPlayer player) {
        Parties.get(player.level().getServer()).decline(player.getUUID());
        Feedback.notify(player, Lang.get("msg.party.declined"));
        CharacterSync.send(player);
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        var server = player.level().getServer();
        var left = Parties.get(server).remove(player.getUUID());
        if (left.isEmpty()) {
            Feedback.notify(player, Lang.get("msg.party.not_in_one"));
            return false;
        }
        Feedback.notify(player, Lang.fmt("msg.party.left", "name", left.get().name()));
        // Before anything else they might type: capture outliving the party is
        // how a private remark ends up in public chat.
        PartyChat.partyEnded(player);
        CharacterSync.send(player);
        for (var memberId : left.get().members()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) CharacterSync.send(member);
        }
        return true;
    }

    public static boolean rename(ServerPlayer player, String newName) {
        var server = player.level().getServer();
        var parties = Parties.get(server);
        var party = parties.partyOf(player.getUUID());
        if (party.isEmpty()) {
            Feedback.notify(player, Lang.get("msg.party.not_in_one"));
            return false;
        }
        if (!party.get().owner().equals(player.getUUID())) {
            Feedback.notify(player, Lang.get("msg.party.leader_only_rename"));
            return false;
        }
        String oldName = party.get().name();
        var error = parties.rename(party.get(), newName);
        if (error.isPresent()) {
            Feedback.notify(player, error.get());
            return false;
        }
        for (var memberId : party.get().members()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member == null) continue;
            Feedback.notify(member, Lang.fmt("msg.party.renamed", "old", oldName, "new", newName));
            CharacterSync.send(member);
        }
        return true;
    }

    // --- teleport (the old party gather) ---

    private static final java.util.Map<java.util.UUID, Long> TP_LAST = new java.util.HashMap<>();

    public static boolean teleport(ServerPlayer player) {
        int cooldown = LQConfig.PARTY_TP_COOLDOWN.get();
        if (cooldown <= 0) {
            Feedback.notify(player, Lang.get("msg.party.tp_disabled"));
            return false;
        }
        var server = player.level().getServer();
        var party = Parties.get(server).partyOf(player.getUUID());
        if (party.isEmpty()) {
            Feedback.notify(player, Lang.get("msg.party.not_in_one"));
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = TP_LAST.get(player.getUUID());
        if (last != null && now - last < cooldown * 1000L) {
            long wait = (cooldown * 1000L - (now - last)) / 1000 + 1;
            Feedback.notify(player, Lang.fmt("msg.party.tp_cooldown", "sec", wait));
            return false;
        }

        java.util.List<ServerPlayer> mates = new ArrayList<>();
        for (var memberId : party.get().members()) {
            if (memberId.equals(player.getUUID())) continue;
            ServerPlayer mate = server.getPlayerList().getPlayer(memberId);
            if (mate != null && mate.level() == player.level()) mates.add(mate);
        }
        if (mates.isEmpty()) {
            Feedback.notify(player, Lang.get("msg.party.tp_nobody"));
            return false;
        }

        double cx = 0, cy = 0, cz = 0;
        for (ServerPlayer mate : mates) {
            cx += mate.getX();
            cy += mate.getY();
            cz += mate.getZ();
        }
        var centroid = net.minecraft.core.BlockPos.containing(
                cx / mates.size(), cy / mates.size(), cz / mates.size());
        Optional<net.minecraft.core.BlockPos> safe = SafeLoc.find(player.level(), centroid)
                .or(() -> SafeLoc.find(player.level(), mates.getFirst().blockPosition()));
        if (safe.isEmpty()) {
            Feedback.notify(player, Lang.get("msg.party.tp_unsafe"));
            return false;
        }

        TP_LAST.put(player.getUUID(), now);
        var from = player.position();
        player.teleportTo(player.level(),
                safe.get().getX() + 0.5D, safe.get().getY(), safe.get().getZ() + 0.5D,
                java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        player.level().playSound(null, from.x, from.y, from.z,
                net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        Feedback.notify(player, Lang.get("msg.party.tp_done"));
        return true;
    }

    private PartyActions() {}
}
