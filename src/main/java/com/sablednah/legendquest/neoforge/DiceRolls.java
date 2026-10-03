package com.sablednah.legendquest.neoforge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sablednah.legendquest.core.Dice;
import com.sablednah.legendquest.core.Stat;
import com.sablednah.legendquest.network.RollResultPayload;

import net.minecraft.server.level.ServerPlayer;

/**
 * One roll, however it was asked for: typed as {@code /roll}, or thrown from
 * the dice tray.
 *
 * <p><b>The tray sends notation, not numbers.</b> A die picked with STR and
 * advantage arrives as {@code "d20 str adv"} and goes through {@link Dice#parse}
 * exactly as if it had been typed, so a tray roll and a typed roll are the same
 * roll to everybody at the table — same parser, same modifier lookup, same
 * broadcast. The client decides nothing but which words to send.</p>
 */
public final class DiceRolls {

    /** Longest tray request worth parsing; "d100 dex disadvantage" is 21. */
    private static final int MAX_TRAY_INPUT = 64;

    /**
     * A click is far cheaper than typing a command, and every roll goes to the
     * whole server. The tray dims its dice for a second after each throw, so a
     * player using it as drawn never meets this; it is here for a client that
     * does not.
     */
    private static final long TRAY_COOLDOWN_MS = 750;
    private static final Map<UUID, Long> LAST_TRAY_ROLL = new HashMap<>();

    /** The tray's door in. Silently drops a roll inside the cooldown. */
    public static void fromTray(ServerPlayer player, String notation) {
        if (notation.length() > MAX_TRAY_INPUT) return;
        long now = System.currentTimeMillis();
        Long last = LAST_TRAY_ROLL.get(player.getUUID());
        if (last != null && now - last < TRAY_COOLDOWN_MS) return;
        LAST_TRAY_ROLL.put(player.getUUID(), now);
        roll(player, notation);
    }

    /**
     * Parse, roll, broadcast. Returns the total, or 0 when the line could not
     * be read (and the player has been told why).
     *
     * <p><b>The whole roll is shown, not just the total.</b> "Sable rolls
     * 2d6+3: [4, 2] +3 = 9" is a sentence a table can argue with; "9" is a
     * number they have to trust. Same reason advantage prints the die it threw
     * away — the near miss is most of the drama, and hiding it makes the
     * feature feel like it did nothing.</p>
     *
     * <p>A refusal names the input rather than the rule, because somebody who
     * typed {@code 2d6+} wants to see {@code 2d6+} back, not a grammar.</p>
     */
    public static int roll(ServerPlayer player, String input) {
        Object parsed = Dice.parse(input);
        if (parsed instanceof Dice.Failure failure) {
            Feedback.chat(player, Lang.fmt("msg.roll.unreadable", "input", failure.input()));
            return 0;
        }
        Dice.Spec spec = (Dice.Spec) parsed;

        // A stat roll adds that character's modifier, and says whose and what.
        String statLabel = "";
        String statKey = "";
        int statMod = 0;
        if (spec.stat().isPresent()) {
            Stat stat = spec.stat().get();
            statMod = CharacterService.statModifier(player, stat);
            statKey = stat.key();
            spec = spec.withBonus(spec.bonus() + statMod);
            statLabel = Lang.fmt("msg.roll.stat", "stat", Lang.get("stat." + stat.key()),
                    "mod", (statMod >= 0 ? "+" : "") + statMod);
        }

        Dice.Result result = Dice.roll(spec, player.getRandom()::nextInt);

        // Not when dropped > 0: a single d20 with advantage would otherwise read
        // "13 ([13]) [adv, dropped 9]", and the ([13]) restates the number two
        // characters to its left. The edge label already shows both dice.
        String detail = spec.count() > 1 || spec.bonus() != 0
                ? Lang.fmt("msg.roll.detail", "dice", result.dice().toString(),
                        "bonus", spec.bonus() == 0 ? ""
                                : (spec.bonus() > 0 ? "+" : "") + spec.bonus())
                : "";
        String edge = switch (spec.edge()) {
            case ADVANTAGE -> Lang.fmt("msg.roll.advantage", "dropped", result.dropped());
            case DISADVANTAGE -> Lang.fmt("msg.roll.disadvantage", "dropped", result.dropped());
            case NONE -> "";
        };

        player.level().getServer().getPlayerList().broadcastSystemMessage(
                Feedback.colored(Lang.fmt("msg.roll.result",
                        "player", player.getName().getString(),
                        "notation", spec.describe(),
                        "stat", statLabel,
                        "detail", detail,
                        "edge", edge,
                        "roll", result.total(),
                        "flair", result.naturalTwenty() ? Lang.fmt("msg.roll.nat20")
                                : result.naturalOne() ? Lang.fmt("msg.roll.nat1") : "")),
                false);

        // The roller's own tray lands on the same numbers the table just read.
        // Typed rolls too: one tray, whichever way the dice were asked for.
        Net.sendIfAble(player, new RollResultPayload(spec.sides(), statKey, statMod,
                spec.edge().ordinal(), result.dice(), result.dropped(), result.total(),
                result.naturalTwenty() ? RollResultPayload.NAT_20
                        : result.naturalOne() ? RollResultPayload.NAT_1 : RollResultPayload.PLAIN));
        return result.total();
    }

    private DiceRolls() {}
}
