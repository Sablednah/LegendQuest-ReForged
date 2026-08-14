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
            Feedback.notify(player, "&c" + error.get());
            return false;
        }
        Feedback.notify(player, "&6Party &l" + name + "&r&6 created. Invite from the party tab or /party invite.");
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
            Feedback.notify(player, "&c" + inviteeName + " is not online.");
            return false;
        }
        var parties = Parties.get(server);
        var party = parties.partyOf(player.getUUID());
        if (party.isEmpty()) {
            Feedback.notify(player, "&cYou are not in a party. Create one first.");
            return false;
        }
        if (invitee == player || party.get().isMember(invitee.getUUID())) {
            Feedback.notify(player, "&7They are already in the party.");
            return false;
        }
        if (parties.partyOf(invitee.getUUID()).isPresent()) {
            Feedback.notify(player, "&c" + invitee.getName().getString() + " is already in a party.");
            return false;
        }
        parties.invite(party.get(), invitee.getUUID());
        Feedback.notify(player, "&6Invited " + invitee.getName().getString() + ".");
        Feedback.notify(invitee, "&6" + player.getName().getString() + " invites you to party &l"
                + party.get().name() + "&r&6 — party tab or /party accept.");
        CharacterSync.send(invitee); // the invite appears in their GUI now
        return true;
    }

    public static boolean accept(ServerPlayer player) {
        var server = player.level().getServer();
        var joined = Parties.get(server).accept(player.getUUID());
        if (joined.isEmpty()) {
            Feedback.notify(player, "&cNo open invitation (it may have expired with a restart).");
            return false;
        }
        Feedback.notify(player, "&6You joined &l" + joined.get().name() + "&r&6.");
        for (var memberId : joined.get().members()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member == null) continue;
            if (!memberId.equals(player.getUUID())) {
                Feedback.notify(member, "&6" + player.getName().getString() + " joined the party.");
            }
            CharacterSync.send(member);
        }
        return true;
    }

    public static boolean decline(ServerPlayer player) {
        Parties.get(player.level().getServer()).decline(player.getUUID());
        Feedback.notify(player, "&7Invitation declined.");
        CharacterSync.send(player);
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        var server = player.level().getServer();
        var left = Parties.get(server).remove(player.getUUID());
        if (left.isEmpty()) {
            Feedback.notify(player, "&7You are not in a party.");
            return false;
        }
        Feedback.notify(player, "&6You left &l" + left.get().name() + "&r&6.");
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
            Feedback.notify(player, "&7You are not in a party.");
            return false;
        }
        if (!party.get().owner().equals(player.getUUID())) {
            Feedback.notify(player, "&cOnly the party leader may rename it.");
            return false;
        }
        String oldName = party.get().name();
        var error = parties.rename(party.get(), newName);
        if (error.isPresent()) {
            Feedback.notify(player, "&c" + error.get());
            return false;
        }
        for (var memberId : party.get().members()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member == null) continue;
            Feedback.notify(member, "&6The party &l" + oldName + "&r&6 is now &l" + newName + "&r&6.");
            CharacterSync.send(member);
        }
        return true;
    }

    // --- teleport (the old party gather) ---

    private static final java.util.Map<java.util.UUID, Long> TP_LAST = new java.util.HashMap<>();

    public static boolean teleport(ServerPlayer player) {
        int cooldown = LQConfig.PARTY_TP_COOLDOWN.get();
        if (cooldown <= 0) {
            Feedback.notify(player, "&cParty teleport is disabled on this server.");
            return false;
        }
        var server = player.level().getServer();
        var party = Parties.get(server).partyOf(player.getUUID());
        if (party.isEmpty()) {
            Feedback.notify(player, "&7You are not in a party.");
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = TP_LAST.get(player.getUUID());
        if (last != null && now - last < cooldown * 1000L) {
            long wait = (cooldown * 1000L - (now - last)) / 1000 + 1;
            Feedback.notify(player, "&cThe party bond needs " + wait + "s to regather.");
            return false;
        }

        java.util.List<ServerPlayer> mates = new ArrayList<>();
        for (var memberId : party.get().members()) {
            if (memberId.equals(player.getUUID())) continue;
            ServerPlayer mate = server.getPlayerList().getPlayer(memberId);
            if (mate != null && mate.level() == player.level()) mates.add(mate);
        }
        if (mates.isEmpty()) {
            Feedback.notify(player, "&7No party members are online in this dimension.");
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
            Feedback.notify(player, "&cNowhere safe to arrive — your party stands in strange places.");
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
        Feedback.notify(player, "&6The party bond draws you across the world.");
        return true;
    }

    private PartyActions() {}
}
