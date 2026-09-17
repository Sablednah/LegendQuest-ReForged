package com.sablednah.legendquest.neoforge;

import net.minecraft.server.level.ServerPlayer;

/**
 * Party voice, as the rest of LegendQuest sees it: four things that happen to a
 * party, and nothing here knows what answers them.
 *
 * <p><b>The dependency points inwards, never out.</b> This class loads on every
 * server, so naming {@code VoiceSupport} here would load the one class that
 * imports Simple Voice Chat and hand a {@code NoClassDefFoundError} to every
 * server without it. Simple Voice Chat's own plugin scanner reaches in instead.
 * It is the same arrangement {@link PartyChat#setNameStyler} uses for Standards
 * and for the same reason — with one thing better: a voice plugin is found by
 * an annotation <i>the voice mod itself scans for</i>, so the class cannot be
 * loaded on a server that does not have it. There is no {@code isLoaded} check
 * to get wrong.</p>
 *
 * <p>With no voice mod installed every call here is a no-op, which is the whole
 * of what a soft dependency has to mean.</p>
 *
 * <p><b>Voice follows the party; it is not a second party.</b> Nothing in here
 * decides who is in a party — {@link Parties} does, and this is told afterwards.
 * A voice channel that could disagree with the party list would be a second
 * source of truth about the same thing.</p>
 */
public final class PartyVoice {

    /**
     * What a voice mod does about a party's channel. Implemented once, by the
     * class that talks to Simple Voice Chat.
     */
    public interface Listener {

        /** This player is now in this party — put them in its channel. */
        void joined(ServerPlayer player, Parties.Party party);

        /**
         * This player is out of the party. {@code party} is the party as it
         * stood <i>before</i> they left, because that is the only version that
         * still names the channel they were in.
         */
        void left(ServerPlayer player, Parties.Party party);

        /** Nobody is left in this party: the channel goes with it. */
        void dissolved(Parties.Party party);

        /** Same members, new name. */
        void renamed(Parties.Party party, String oldName);

        /** {@code /party voice} — put them back in the channel they left. */
        boolean rejoin(ServerPlayer player, Parties.Party party);
    }

    /** What happens with no voice mod installed: nothing, quietly. */
    private static final Listener SILENCE = new Listener() {
        @Override public void joined(ServerPlayer player, Parties.Party party) {}
        @Override public void left(ServerPlayer player, Parties.Party party) {}
        @Override public void dissolved(Parties.Party party) {}
        @Override public void renamed(Parties.Party party, String oldName) {}
        @Override public boolean rejoin(ServerPlayer player, Parties.Party party) { return false; }
    };

    private static Listener listener = SILENCE;

    /** Installed by the voice plugin when the voice server starts; cleared when it stops. */
    public static void setListener(Listener installed) {
        listener = installed == null ? SILENCE : installed;
    }

    /** Whether a voice mod is present and its server is up. */
    public static boolean available() {
        return listener != SILENCE;
    }

    public static void joined(ServerPlayer player, Parties.Party party) {
        listener.joined(player, party);
    }

    public static void left(ServerPlayer player, Parties.Party party) {
        listener.left(player, party);
    }

    public static void dissolved(Parties.Party party) {
        listener.dissolved(party);
    }

    public static void renamed(Parties.Party party, String oldName) {
        listener.renamed(party, oldName);
    }

    /**
     * {@code /party voice}: the way back for somebody who left the channel from
     * the voice mod's own screen.
     *
     * <p>It exists because the channel is hidden and password-locked, so a
     * player who leaves it cannot find it again in their group list. A feature
     * that can be walked out of needs a door back in, and naming that door is
     * cheaper than explaining the lock.</p>
     */
    public static boolean rejoin(ServerPlayer player) {
        var party = Parties.get(player.level().getServer()).partyOf(player.getUUID());
        if (party.isEmpty()) {
            Feedback.notify(player, Lang.get("msg.party.not_in_one"));
            return false;
        }
        if (!available()) {
            Feedback.notify(player, Lang.get("msg.party.voice.none"));
            return false;
        }
        if (!listener.rejoin(player, party.get())) {
            Feedback.notify(player, Lang.get("msg.party.voice.not_connected"));
            return false;
        }
        Feedback.notify(player, Lang.fmt("msg.party.voice.rejoined", "name", party.get().name()));
        return true;
    }

    private PartyVoice() {}
}
