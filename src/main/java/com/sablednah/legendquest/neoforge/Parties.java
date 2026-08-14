package com.sablednah.legendquest.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Parties: shared XP, no friendly fire. One record per party (not the old
 * one-row-per-member table). Stored on the overworld so there is one ledger
 * per save; invites are in-memory only — an invite that doesn't survive a
 * restart is a feature, not a loss.
 */
public final class Parties extends SavedData {

    public record Party(String name, UUID owner, List<UUID> members) {
        public static final Codec<Party> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Party::name),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Party::owner),
                UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(Party::members))
                .apply(i, Party::new));

        public boolean isMember(UUID id) {
            return members.contains(id);
        }
    }

    private static final Codec<Parties> CODEC = Party.CODEC.listOf()
            .xmap(Parties::new, p -> new ArrayList<>(p.parties.values()))
            .fieldOf("parties").codec();

    public static final SavedDataType<Parties> TYPE =
            new SavedDataType<>("legendquest_parties", Parties::new, CODEC, null);

    /** Lowercased party name → party. */
    private final Map<String, Party> parties;
    /** Invitee → lowercased party name. In-memory by design. */
    private static final Map<UUID, String> INVITES = new HashMap<>();

    private Parties() {
        this.parties = new LinkedHashMap<>();
    }

    private Parties(List<Party> entries) {
        this.parties = new LinkedHashMap<>();
        entries.forEach(p -> parties.put(p.name().toLowerCase(Locale.ROOT), p));
    }

    public static Parties get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // --- queries ---

    public Optional<Party> byName(String name) {
        return Optional.ofNullable(parties.get(name.toLowerCase(Locale.ROOT)));
    }

    public Optional<Party> partyOf(UUID player) {
        return parties.values().stream().filter(p -> p.isMember(player)).findFirst();
    }

    public boolean sameParty(UUID a, UUID b) {
        return partyOf(a).map(p -> p.isMember(b)).orElse(false);
    }

    // --- mutations (all mark dirty) ---

    /** @return empty on success, or a reason the party couldn't be created. */
    public Optional<String> create(String name, ServerPlayer owner) {
        String key = name.toLowerCase(Locale.ROOT);
        if (partyOf(owner.getUUID()).isPresent()) return Optional.of(Lang.get("msg.party.already_in_one"));
        if (parties.containsKey(key)) return Optional.of(Lang.get("msg.party.name_taken"));
        if (!name.matches("[A-Za-z0-9_\\-]{2,24}")) {
            return Optional.of(Lang.get("msg.party.name_rules"));
        }
        parties.put(key, new Party(name, owner.getUUID(), List.of(owner.getUUID())));
        setDirty();
        return Optional.empty();
    }

    public void invite(Party party, UUID invitee) {
        INVITES.put(invitee, party.name().toLowerCase(Locale.ROOT));
    }

    public Optional<Party> pendingInvite(UUID invitee) {
        return Optional.ofNullable(INVITES.get(invitee)).flatMap(this::byName);
    }

    /** @return the joined party, or empty if the invite had gone stale. */
    public Optional<Party> accept(UUID invitee) {
        Optional<Party> target = pendingInvite(invitee);
        INVITES.remove(invitee);
        if (target.isEmpty()) return Optional.empty();
        Party old = target.get();
        List<UUID> members = new ArrayList<>(old.members());
        if (!members.contains(invitee)) members.add(invitee);
        Party updated = new Party(old.name(), old.owner(), List.copyOf(members));
        parties.put(old.name().toLowerCase(Locale.ROOT), updated);
        setDirty();
        return Optional.of(updated);
    }

    public void decline(UUID invitee) {
        INVITES.remove(invitee);
    }

    /** @return empty on success, or the reason the rename was refused. */
    public Optional<String> rename(Party party, String newName) {
        String oldKey = party.name().toLowerCase(Locale.ROOT);
        String newKey = newName.toLowerCase(Locale.ROOT);
        if (!newName.matches("[A-Za-z0-9_\\-]{2,24}")) {
            return Optional.of(Lang.get("msg.party.name_rules"));
        }
        if (!newKey.equals(oldKey) && parties.containsKey(newKey)) {
            return Optional.of(Lang.get("msg.party.name_taken"));
        }
        parties.remove(oldKey);
        parties.put(newKey, new Party(newName, party.owner(), party.members()));
        INVITES.replaceAll((invitee, key) -> key.equals(oldKey) ? newKey : key);
        setDirty();
        return Optional.empty();
    }

    /**
     * Leave (or be kicked). Empty parties dissolve; a departing owner hands
     * the party to the longest-standing remaining member.
     */
    public Optional<Party> remove(UUID member) {
        Optional<Party> current = partyOf(member);
        if (current.isEmpty()) return Optional.empty();
        Party old = current.get();
        List<UUID> members = new ArrayList<>(old.members());
        members.remove(member);
        String key = old.name().toLowerCase(Locale.ROOT);
        if (members.isEmpty()) {
            parties.remove(key);
            setDirty();
            return Optional.of(old);
        }
        UUID owner = old.owner().equals(member) ? members.getFirst() : old.owner();
        parties.put(key, new Party(old.name(), owner, List.copyOf(members)));
        setDirty();
        return Optional.of(old);
    }

}
