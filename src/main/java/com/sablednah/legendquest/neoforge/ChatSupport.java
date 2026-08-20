package com.sablednah.legendquest.neoforge;

import java.util.Optional;

import com.sablednah.legendquest.LegendQuest;
import com.sablednah.standards.api.chat.Chat;
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
        LegendQuest.LOGGER.info("Registered the LegendQuest chat decorator with Standards");
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
