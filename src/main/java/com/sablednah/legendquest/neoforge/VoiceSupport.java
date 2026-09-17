package com.sablednah.legendquest.neoforge;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.sablednah.legendquest.LQConfig;
import com.sablednah.legendquest.LegendQuest;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The one class that imports Simple Voice Chat — a party is a voice channel.
 *
 * <p><b>Nothing in LegendQuest names this class</b>, and that is what makes the
 * dependency soft. Simple Voice Chat finds it itself by scanning mods for
 * {@link ForgeVoicechatPlugin}, so on a server without the mod nothing ever
 * loads it and there is no {@code isLoaded} check to forget. {@link PartyVoice}
 * is the seam the rest of the mod talks to, and it does nothing until the voice
 * server starts and this installs itself.</p>
 *
 * <p><b>A channel is NORMAL, not ISOLATED</b>, decided with Sable: members hear
 * each other at any distance <i>and</i> still hear whoever is standing next to
 * them. An isolated channel would silence the tavern a player is standing in,
 * which for a roleplaying server is the wrong kind of quiet.</p>
 *
 * <p><b>Hidden and locked, so the channel cannot be walked into.</b> A visible
 * password-less group is joinable by anyone from the voice mod's own screen,
 * which would make "the party channel" a thing strangers can sit in. It is
 * hidden with a random password instead and only ever entered through this
 * class — and because that also means it cannot be rejoined from that screen,
 * {@code /party voice} is the door back in.</p>
 *
 * <p><b>Mutes are not honoured here, deliberately.</b> LegendQuest does not own
 * the mute — Standards does, and it has no notion of voice, while Simple Voice
 * Chat has moderation of its own. Half-enforcing somebody else's rule across a
 * boundary neither side models would promise something this code cannot keep.
 * It is written down rather than half-done.</p>
 */
@ForgeVoicechatPlugin
public class VoiceSupport implements VoicechatPlugin, PartyVoice.Listener {

    /**
     * The voice server's API, or null while it is down. Volatile because the
     * voice server starts and stops on its own thread, not the game's.
     */
    private volatile VoicechatServerApi api;

    /** Lowercased party name → its channel, matching how {@link Parties} keys parties. */
    private final Map<String, Group> groups = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return LegendQuest.MODID;
    }

    @Override
    public void initialize(VoicechatApi voicechatApi) {
        // Nothing yet: the SERVER api is what this needs, and that only exists
        // once the voice server has started. See registerEvents.
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            api = event.getVoicechat();
            // Channels do not survive a voice-server restart, and neither should
            // our memory of them: a stale Group here would be a channel nobody
            // can hear.
            groups.clear();
            PartyVoice.setListener(this);
            LegendQuest.LOGGER.info("Simple Voice Chat is present: parties now have voice channels");
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            PartyVoice.setListener(null);
            api = null;
            groups.clear();
        });
        // NOT the player's Minecraft login: at that moment their voice client
        // has not connected, so there is no connection to put in a channel. This
        // is the event that means "this player can hear things now".
        registration.registerEvent(PlayerConnectedEvent.class, this::onVoiceConnected);
    }

    private void onVoiceConnected(PlayerConnectedEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        UUID id = event.getConnection().getPlayer().getUuid();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            Parties.get(server).partyOf(id).ifPresent(party -> joined(player, party));
        });
    }

    // --- what a party does to its channel ------------------------------------

    @Override
    public void joined(ServerPlayer player, Parties.Party party) {
        VoicechatServerApi voice = api;
        if (voice == null || !LQConfig.PARTY_VOICE.get()) return;
        VoicechatConnection connection = voice.getConnectionOf(player.getUUID());
        if (connection == null) return;   // their voice client is not connected

        Group current = connection.getGroup();
        if (current != null && !isOurs(current)) {
            // They chose that group. Taking somebody out of a conversation they
            // joined on purpose is worse than not starting one, so this names
            // the way in and leaves them where they are.
            Feedback.notify(player, Lang.get("msg.party.voice.other_group"));
            return;
        }

        Group group = channelFor(voice, party);
        if (group == null) return;
        connection.setGroup(group);
        Feedback.notify(player, Lang.fmt("msg.party.voice.joined", "name", party.name()));
    }

    @Override
    public void left(ServerPlayer player, Parties.Party party) {
        VoicechatServerApi voice = api;
        if (voice == null) return;
        Group group = groups.get(key(party.name()));
        if (group == null) return;
        VoicechatConnection connection = voice.getConnectionOf(player.getUUID());
        if (connection == null) return;

        // Only ever take somebody out of OUR channel. If they had moved to
        // another group, leaving the party is no reason to end that call.
        Group current = connection.getGroup();
        if (current == null || !current.getId().equals(group.getId())) return;
        connection.setGroup(null);
        Feedback.notify(player, Lang.get("msg.party.voice.left"));
    }

    @Override
    public void dissolved(Parties.Party party) {
        VoicechatServerApi voice = api;
        Group group = groups.remove(key(party.name()));
        if (voice != null && group != null) voice.removeGroup(group.getId());
    }

    @Override
    public void renamed(Parties.Party party, String oldName) {
        VoicechatServerApi voice = api;
        if (voice == null) return;
        Group old = groups.remove(key(oldName));
        if (old == null) return;
        // A group's name cannot be changed, so the channel is rebuilt and
        // everybody carried across. Silently, because they asked to rename a
        // party, not to be told about plumbing.
        groups.remove(key(party.name()));
        Group fresh = channelFor(voice, party);
        if (fresh == null) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (UUID memberId : party.members()) {
                VoicechatConnection connection = voice.getConnectionOf(memberId);
                if (connection == null) continue;
                Group current = connection.getGroup();
                if (current != null && current.getId().equals(old.getId())) {
                    connection.setGroup(fresh);
                }
            }
        }
        voice.removeGroup(old.getId());
    }

    @Override
    public boolean rejoin(ServerPlayer player, Parties.Party party) {
        VoicechatServerApi voice = api;
        if (voice == null || !LQConfig.PARTY_VOICE.get()) return false;
        VoicechatConnection connection = voice.getConnectionOf(player.getUUID());
        if (connection == null) return false;
        Group group = channelFor(voice, party);
        if (group == null) return false;
        connection.setGroup(group);
        return true;
    }

    // --- channels ------------------------------------------------------------

    /**
     * This party's channel, made on first use.
     *
     * <p>Not persistent: a non-persistent group exists from the moment its first
     * member arrives until its last one leaves, which is exactly the life of a
     * party's conversation and saves cleaning up after a server that stopped
     * mid-session.</p>
     */
    private Group channelFor(VoicechatServerApi voice, Parties.Party party) {
        return groups.computeIfAbsent(key(party.name()), name -> voice.groupBuilder()
                .setName(party.name())
                .setType(Group.Type.NORMAL)
                .setPersistent(false)
                .setHidden(true)
                .setPassword(UUID.randomUUID().toString())
                .build());
    }

    private boolean isOurs(Group group) {
        for (Group known : groups.values()) {
            if (known.getId().equals(group.getId())) return true;
        }
        return false;
    }

    private static String key(String partyName) {
        return partyName.toLowerCase(Locale.ROOT);
    }
}
