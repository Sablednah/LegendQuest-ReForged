package com.sablednah.legendquest.neoforge;

import java.util.UUID;

import com.sablednah.legendquest.character.PlayerCharacter;
import com.sablednah.legendquest.neoforge.Parties.Party;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

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
 *
 * <h2>Capture: ordinary chat routed to the party</h2>
 *
 * <p>Typing {@code /pc} before every line is fine for a remark and miserable
 * for a conversation, so capture makes plain chat go to the party until it is
 * switched off. That is the whole feature, and all of its risk: a player who
 * has forgotten it is on says something to their party that goes to the
 * server, or believes they told the server something only four people heard.
 * Everything defensive here exists for that one failure — the reminder on the
 * way in, the state clearing itself when the party goes away, and capture
 * never silently activating.</p>
 *
 * <p><b>Mutes are honoured.</b> Where Standards is installed, capture goes
 * through its {@code ChatRouter} seam, which offers a message to routers only
 * after the mute gate has already turned it away — so a muted player cannot
 * talk to their party, and {@link #claim} is never even called for them.
 * Without Standards there is no mute to honour and the listener in
 * {@link #onChat} does the routing instead. See {@code docs/CHAT-ROUTING.md}.</p>
 *
 * <p>An earlier version of this class cancelled {@code ServerChatEvent} ahead
 * of Standards and did bypass mutes — the hole {@code /pc} had carried from the
 * day it was written, made far easier to reach. That is fixed rather than
 * outstanding; the seam was built for it.</p>
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
        // Decorated for display, plain for the log: a rank belongs beside the
        // name a player sees, and nowhere near the string an admin greps for.
        String shown = nameStyler.apply(sender);
        String said = strip(message);
        String toMembers = Lang.fmt("msg.party.chat.line", "player", shown, "message", said);
        String toSpies = Lang.fmt("msg.party.chat.spy_line",
                "party", party.name(), "player", shown, "message", said);

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

    // --- how a name is dressed ---

    /**
     * How a speaker's name is rendered. Plain by default; Standards installs a
     * decorated one through {@link ChatSupport} when it is present.
     *
     * <p>A function rather than a direct call because this class loads on every
     * server, and naming {@code ChatSupport} here would load the one class that
     * imports Standards — a {@code NoClassDefFoundError} everywhere it is not
     * installed. The dependency points the other way round instead: Standards'
     * side reaches in, this side never reaches out.</p>
     */
    private static java.util.function.Function<ServerPlayer, String> nameStyler =
            player -> player.getName().getString();

    static void setNameStyler(java.util.function.Function<ServerPlayer, String> styler) {
        nameStyler = styler;
    }

    /**
     * Drop format codes from what a player typed.
     *
     * <p>Lang substitutes placeholders before {@link Feedback#colored} runs, so
     * an untreated message is a formatting injection. The obvious half is
     * griefing — {@code &k} for unreadable text, a wall of {@code &l&n}. The
     * worse half is impersonation: {@code &r} and a plausible prefix dresses
     * your words up as somebody else's, or as the server's. Server text keeps
     * its codes because the server wrote it; player text does not, because the
     * player did.</p>
     *
     * <p>The literal section sign goes too, not just the ampersand form. A
     * client cannot type one — but this text also arrives from books, signs and
     * command blocks, and "the client cannot send that" is exactly the kind of
     * assumption that quietly stops being true. (Credit to the Standards
     * session, which found this half after we compared notes on the other.)</p>
     */
    private static String strip(String message) {
        StringBuilder out = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            // Drop the pair, not just the marker -- leaving the letter turns
            // "&cred" into "cred" and quietly edits what they said.
            if ((c == '&' || c == '§') && i + 1 < message.length()
                    && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(message.charAt(i + 1)) >= 0) {
                i++;
                continue;
            }
            // A trailing marker with nothing after it cannot colour anything,
            // but a bare section sign has no business in player text either.
            if (c == '§') continue;
            out.append(c);
        }
        return out.toString();
    }

    // --- capture: ordinary chat routed to the party ---

    /** True when this player's plain chat is going to their party. */
    public static boolean capturing(ServerPlayer player) {
        return CharacterService.data(player).partyChatCapture();
    }

    /**
     * Turns capture on or off, and says so plainly.
     *
     * <p>Switching it on requires being in a party. Capture with nowhere to go
     * is the exact state that gets someone caught out — they believe they are
     * talking privately while every word is public — so it is refused at the
     * door rather than armed and left to misfire.</p>
     */
    public static boolean setCapture(ServerPlayer player, boolean on) {
        if (on) {
            MinecraftServer server = player.level().getServer();
            if (server == null || Parties.get(server).partyOf(player.getUUID()).isEmpty()) {
                Feedback.chat(player, Lang.get("msg.party.chat.no_party"));
                return false;
            }
        }
        CharacterService.data(player).setPartyChatCapture(on);
        Feedback.chat(player, Lang.get(on ? "msg.party.capture.on" : "msg.party.capture.off"));
        return true;
    }

    /** Flips it — what a bare {@code /pc} does. */
    public static boolean toggleCapture(ServerPlayer player) {
        return setCapture(player, !capturing(player));
    }

    /**
     * Switches capture off because the party is gone, telling them why.
     *
     * <p>Called when a player leaves, is removed, or the party disbands. Left
     * alone the flag would outlive the party and the next thing they typed
     * would land in public chat wearing a private intention.</p>
     */
    public static void partyEnded(ServerPlayer player) {
        PlayerCharacter data = CharacterService.data(player);
        if (!data.partyChatCapture()) return;
        data.setPartyChatCapture(false);
        Feedback.chat(player, Lang.get("msg.party.capture.dropped"));
    }

    /**
     * The one routing decision: is this message the party's, and did we take it?
     *
     * <p>Both delivery paths call this and nothing else decides, so the two can
     * never disagree about what capture means.</p>
     *
     * @return true when the party has taken the message and nobody else should
     *         deliver it
     */
    public static boolean claim(ServerPlayer player, String message) {
        if (!capturing(player)) return false;

        MinecraftServer server = player.level().getServer();
        if (server == null || Parties.get(server).partyOf(player.getUUID()).isEmpty()) {
            // The party went away by a route partyEnded did not see. Let the
            // line go public -- swallowing it would be worse -- but never
            // silently: they are owed the knowledge that this one was heard.
            partyEnded(player);
            return false;
        }
        return send(player, message);
    }

    /**
     * Set once Standards' router has the job, which makes {@link #onChat} stand
     * down. Exactly one of the two paths is ever live.
     */
    private static boolean routed = false;

    static void setRouted(boolean handled) {
        routed = handled;
    }

    /**
     * Ordinary chat, on its way to the party — the path for servers with no
     * Standards installed.
     *
     * <p>{@code HIGH} because on a server that <em>does</em> have Standards this
     * would otherwise be a race it has to win: their handler sits at
     * {@code NORMAL} and cancels the event to deliver the line itself, after
     * which redirecting is too late. That race is no longer run — Standards
     * ships a {@link com.sablednah.standards.api.chat.ChatRouter} seam and
     * {@link ChatSupport} registers for it, which sets {@link #routed} and
     * stands this listener down. Going through their router means the message
     * has already passed the mute gate and cleared AFK before it arrives, which
     * cancelling ahead of them could never achieve.</p>
     *
     * <p>This remains for the plain-NeoForge case, where there is no mute to
     * respect and nothing to collide with. Standards is a soft dependency and
     * capture is not allowed to need it.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChat(ServerChatEvent event) {
        if (routed) return;
        if (claim(event.getPlayer(), event.getRawText())) {
            event.setCanceled(true);
        }
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
