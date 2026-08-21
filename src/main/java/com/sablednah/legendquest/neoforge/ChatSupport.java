package com.sablednah.legendquest.neoforge;

import java.util.Optional;

import com.sablednah.legendquest.LegendQuest;
import com.sablednah.standards.api.chat.Chat;
import com.sablednah.standards.api.chat.ChatRouter;
import com.sablednah.standards.api.chat.NameDecorator;

import net.minecraft.server.level.ServerPlayer;

/**
 * Contributes LegendQuest's rank and epithet to a player's name in chat, via
 * the Standards decorator API.
 *
 * <pre>
 * [FACTION][PARTY] Lord Sablednah the saintly: says hello
 *                  ^^^^                ^^^^^^^^^^^
 * </pre>
 *
 * <p><b>This class is the only one that imports {@code com.sablednah.standards}
 * anywhere in the mod</b>, and it is only ever loaded when Standards is
 * installed — see the guarded call in {@link LQServerEvents}. Standards is a
 * soft dependency: without it LegendQuest simply does not decorate chat, and
 * nothing else changes. Touching the API from a class that loads unconditionally
 * would turn that into a {@code NoClassDefFoundError} on every server that has
 * not installed it.</p>
 *
 * <p>Registered at priority 100, the band Standards documents for
 * character-level things. Higher priority sits nearer the name, so a faction or
 * party tag (0–99) drifts out to the left while a rank stays welded to the
 * name, which is where a title belongs.</p>
 *
 * <p>Nothing here decorates the <em>nameplate</em> — that is
 * {@link Nameplate}'s job and a different surface entirely. Keeping them apart
 * is deliberate: a scoreboard team would have done both at once and made the
 * chat line unreadable.</p>
 */
public final class ChatSupport {

    /** Character-level band: rank and title. See NameDecorator's javadoc. */
    private static final int PRIORITY = 100;

    public static void register() {
        Chat.register(new NameDecorator() {
            @Override
            public String id() {
                return "legendquest:rank";
            }

            @Override
            public int priority() {
                return PRIORITY;
            }

            /** The class title for their level — "Squire", "Lord". */
            @Override
            public Optional<String> prefix(ServerPlayer player) {
                return nonBlank(CharacterService.classTitle(player)).map(title -> "&6" + title);
            }

            /** The karma epithet — "the saintly". */
            @Override
            public Optional<String> suffix(ServerPlayer player) {
                long karma = CharacterService.data(player).karma();
                return nonBlank(CharacterService.karmaEpithet(karma)).map(word -> "&7" + word);
            }
        });
        PartyChat.setNameStyler(ChatSupport::decorated);
        registerRouter();
        LegendQuest.LOGGER.info("Registered the LegendQuest chat decorator with Standards");
    }

    /**
     * Hands party-chat capture to Standards' router, and stands LegendQuest's
     * own chat listener down.
     *
     * <p>The listener could only ever cancel {@code ServerChatEvent} ahead of
     * Standards, which meant jumping their mute gate — party chat became a way
     * to talk while muted. By the time {@code route} is called the sender is
     * known not to be muted and their AFK is already cleared, so the thing that
     * could not be fixed by racing is simply not a question any more.</p>
     *
     * <p>Priority 0, the weak end: <b>higher wins here</b>, and a party is a
     * mild claim on a message. A staffchat or a dedicated channel mod
     * registering above us should take a message first, and does.</p>
     *
     * <p>Note this is the opposite sense to {@link NameDecorator}, where higher
     * priority sits nearer the name. Not a mistake in either: there priority
     * orders a list everyone appears in, here it decides who is asked first —
     * and a message has exactly one destination, so the first to claim it ends
     * the matter.</p>
     */
    private static void registerRouter() {
        Chat.registerRouter(new ChatRouter() {
            @Override
            public String id() {
                return "legendquest:party";
            }

            @Override
            public int priority() {
                return 0;
            }

            @Override
            public boolean route(ServerPlayer sender, String message) {
                return PartyChat.claim(sender, message);
            }
        });
        PartyChat.setRouted(true);
    }

    /**
     * A player's name wearing everything every decorator has to say about it.
     *
     * <p>Party chat renders itself instead of going through Standards, which
     * left it the one channel on the server showing an undressed name: a player
     * was "Lord Sablednah the saintly" in public chat and plain "Sablednah" to
     * their own party. Reading the same seam here keeps one identity across
     * both, and picks up faction or guild tags from mods this one has never
     * heard of for free.</p>
     *
     * <p>Built from {@link Chat}'s collected list rather than from LegendQuest's
     * own title and epithet directly — asking the registry is precisely what
     * makes the other mods' contributions appear.</p>
     */
    private static String decorated(ServerPlayer player) {
        StringBuilder out = new StringBuilder();
        for (String prefix : Chat.prefixes(player)) {
            out.append(prefix).append(' ');
        }
        // &f before the name so a prefix's colour cannot bleed into it.
        out.append("&f").append(player.getName().getString());
        for (String suffix : Chat.suffixes(player)) {
            out.append(' ').append(suffix);
        }
        return out.toString();
    }

    /**
     * Empty is the normal, expected answer: a character below their class's
     * first title band has no rank, and a neutral one has no epithet.
     */
    private static Optional<String> nonBlank(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private ChatSupport() {}
}
