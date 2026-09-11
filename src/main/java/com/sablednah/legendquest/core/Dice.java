package com.sablednah.legendquest.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * Tabletop dice notation, parsed the way a person at a table would say it.
 *
 * <p>Deliberately free of Minecraft imports, like {@link Mechanics}: it takes a
 * bounded-random function and returns a plain result, so it can be exercised
 * without a server. That is not tidiness for its own sake — dice are the one
 * thing in this mod where being wrong is both easy and invisible, and a parser
 * you can run on its own is a parser you can actually check.</p>
 *
 * <p><b>What it understands</b>, in the order somebody is likely to try it:</p>
 * <ul>
 *   <li>nothing at all → {@code d20}, exactly as {@code /roll} always did</li>
 *   <li>{@code 6} → a d6. A bare number is the number of <i>sides</i>, because
 *       "roll a 6" at a table means a d6 and never "roll six dice"</li>
 *   <li>{@code d6}, {@code 2d6}, {@code 2d6+3}, {@code 4d8-2}, {@code d%}</li>
 *   <li>{@code adv} / {@code advantage} / {@code dis} / {@code disadvantage},
 *       anywhere in the line, alone or beside a die</li>
 *   <li>a stat, long or short: {@code str}, {@code strength}, {@code dex},
 *       {@code cha} (an alias for CHR, because half the world spells it that
 *       way and being pedantic at somebody rolling dice is no way to behave)</li>
 * </ul>
 *
 * <p><b>Odd dice are allowed on purpose.</b> A d7 is not a real object, and
 * people ask for one anyway; there is no reason for software to refuse. The
 * only bounds are that a die has at least two sides and that the pool stays
 * small enough to print.</p>
 *
 * <p><b>Advantage is two dice, both shown.</b> Taking the better of two and
 * reporting one number tells the table nothing about how close it was, and the
 * near-miss is most of the drama. It applies to the first die group only, which
 * is what advantage means — you do not roll 2d6 with advantage, you roll a d20
 * with advantage and add things to it.</p>
 */
public final class Dice {

    /** Sane ceilings. A d1000 is fine; ten thousand of them is a denial of chat. */
    public static final int MAX_COUNT = 100;
    public static final int MAX_SIDES = 1000;

    public enum Edge { NONE, ADVANTAGE, DISADVANTAGE }

    /**
     * A parsed request, before any dice are thrown.
     *
     * @param stat the stat whose modifier feeds {@code bonus}, if the roll was
     *             asked for by name ("/roll str"). Kept so the caller can look
     *             the modifier up — {@link Dice} never touches a player.
     */
    public record Spec(int count, int sides, int bonus, Edge edge, Optional<Stat> stat) {

        public static Spec d20() {
            return new Spec(1, 20, 0, Edge.NONE, Optional.empty());
        }

        public Spec withBonus(int b) {
            return new Spec(count, sides, b, edge, stat);
        }

        /** "2d6+3", "d20 with advantage" — how the roll will be announced. */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (count != 1) sb.append(count);
            sb.append('d').append(sides);
            if (bonus > 0) sb.append('+').append(bonus);
            if (bonus < 0) sb.append(bonus);
            return sb.toString();
        }
    }

    /** What actually happened, with the individual dice kept. */
    public record Result(Spec spec, List<Integer> dice, int dropped, int total) {
        /** True only for a single unmodified d20, so "natural 20" keeps meaning
         *  something. A 20 on 3d20+5 is not a natural 20 and saying so is noise. */
        public boolean naturalTwenty() {
            return spec.sides() == 20 && spec.count() == 1 && total - spec.bonus() == 20;
        }

        public boolean naturalOne() {
            return spec.sides() == 20 && spec.count() == 1 && total - spec.bonus() == 1;
        }
    }

    /** Why a line could not be read, for a message that helps. */
    public record Failure(String input, String reason) {}

    /**
     * Parse a spoken-ish dice line. Empty or blank gives a plain d20.
     *
     * <p>Tokens are taken in any order and accumulate, so "adv str" and
     * "str adv" are the same roll, and "2d6 +3" survives the stray space.</p>
     */
    public static Object parse(String input) {
        if (input == null || input.isBlank()) return Spec.d20();
        String cleaned = input.toLowerCase(Locale.ROOT).trim()
                .replace("with", " ")
                .replace(",", " ");

        int count = 0, sides = 0, bonus = 0;
        Edge edge = Edge.NONE;
        Optional<Stat> stat = Optional.empty();
        boolean sawDie = false;

        for (String token : cleaned.split("[\\s]+")) {
            if (token.isBlank()) continue;

            switch (token) {
                case "adv", "advantage", "a" -> { edge = Edge.ADVANTAGE; continue; }
                case "dis", "disadv", "disadvantage", "d" -> { edge = Edge.DISADVANTAGE; continue; }
                default -> { }
            }

            Optional<Stat> asStat = stat(token);
            if (asStat.isPresent()) {
                stat = asStat;
                continue;
            }

            // A leading sign is a bonus: "+3", "-2".
            if ((token.startsWith("+") || token.startsWith("-")) && token.length() > 1) {
                try {
                    bonus += Integer.parseInt(token);
                    continue;
                } catch (NumberFormatException nan) {
                    return new Failure(input, "notanumber");
                }
            }

            // Dice notation, with or without a count, with or without a bonus
            // welded on: d6, 2d6, 2d6+3, d%.
            java.util.regex.Matcher m = DIE.matcher(token);
            if (m.matches()) {
                count = m.group(1) == null || m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
                sides = "%".equals(m.group(2)) ? 100 : Integer.parseInt(m.group(2));
                if (m.group(3) != null && !m.group(3).isEmpty()) bonus += Integer.parseInt(m.group(3));
                sawDie = true;
                continue;
            }

            // A bare number is SIDES, not a count: "/roll 6" is a d6.
            if (token.matches("\\d+")) {
                sides = Integer.parseInt(token);
                count = 1;
                sawDie = true;
                continue;
            }

            return new Failure(input, "unknown");
        }

        if (!sawDie) {
            count = 1;
            sides = 20;
        }
        if (sides < 2) return new Failure(input, "sides");
        if (sides > MAX_SIDES) return new Failure(input, "toobig");
        if (count < 1 || count > MAX_COUNT) return new Failure(input, "toomany");

        return new Spec(count, sides, bonus, edge, stat);
    }

    private static final java.util.regex.Pattern DIE =
            java.util.regex.Pattern.compile("(\\d*)d(\\d+|%)([+-]\\d+)?");

    /** Stat by short or long name, or the common misspelling of CHR. */
    public static Optional<Stat> stat(String token) {
        return switch (token) {
            case "str", "strength" -> Optional.of(Stat.STR);
            case "dex", "dexterity" -> Optional.of(Stat.DEX);
            case "con", "constitution" -> Optional.of(Stat.CON);
            case "int", "intelligence" -> Optional.of(Stat.INT);
            case "wis", "wisdom" -> Optional.of(Stat.WIS);
            case "chr", "cha", "charisma" -> Optional.of(Stat.CHR);
            default -> Optional.empty();
        };
    }

    /** Throw the dice. {@code nextInt} is bounded-exclusive, as {@code Random}'s is. */
    public static Result roll(Spec spec, IntUnaryOperator nextInt) {
        List<Integer> dice = new ArrayList<>(spec.count());
        for (int i = 0; i < spec.count(); i++) {
            dice.add(nextInt.applyAsInt(spec.sides()) + 1);
        }

        int dropped = 0;
        if (spec.edge() != Edge.NONE) {
            // The second die of the pair, kept for display: the table wants to
            // see what advantage saved them from.
            int other = nextInt.applyAsInt(spec.sides()) + 1;
            int first = dice.get(0);
            boolean keepFirst = spec.edge() == Edge.ADVANTAGE ? first >= other : first <= other;
            dice.set(0, keepFirst ? first : other);
            dropped = keepFirst ? other : first;
        }

        int total = spec.bonus();
        for (int d : dice) total += d;
        return new Result(spec, List.copyOf(dice), dropped, total);
    }

    private Dice() {}
}
