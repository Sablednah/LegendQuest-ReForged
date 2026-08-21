package com.sablednah.legendquest.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/** Player-facing messages. '&' colour codes translate to '§'. */
public final class Feedback {

    public static void actionBar(ServerPlayer player, String text) {
        player.displayClientMessage(colored(text), true);
    }

    public static void chat(ServerPlayer player, String text) {
        player.displayClientMessage(colored(text), false);
    }

    /**
     * A notice that must survive an open GUI: modded clients draw it over
     * the screen (chat is blurred behind inventories); vanilla clients get
     * ordinary chat.
     */
    public static void notify(ServerPlayer player, String text) {
        if (player.connection != null
                && player.connection.hasChannel(com.sablednah.legendquest.network.NoticePayload.TYPE)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.sablednah.legendquest.network.NoticePayload(text));
        } else {
            chat(player, text);
        }
    }

    /**
     * A level-up, made an occasion of: the chat line, a title card over the
     * world, and the toast chime. All three are vanilla packets, so a vanilla
     * client gets the whole show — and the sound goes to the one player rather
     * than to everyone standing near them.
     */
    public static void levelUp(ServerPlayer player, int level, String className) {
        chat(player, Lang.fmt("msg.levelup", "level", level));
        if (!com.sablednah.legendquest.LQConfig.LEVEL_UP_FANFARE.get()) return;

        player.connection.send(new net.minecraft.network.protocol.game
                .ClientboundSetTitlesAnimationPacket(5, 40, 10));
        player.connection.send(new net.minecraft.network.protocol.game
                .ClientboundSetTitleTextPacket(
                        colored(Lang.fmt("msg.levelup.title", "level", level))));
        player.connection.send(new net.minecraft.network.protocol.game
                .ClientboundSetSubtitleTextPacket(
                        colored(Lang.fmt("msg.levelup.subtitle", "class", className))));
        // Sent straight down this player's connection rather than played into the
        // world: their level-up is not the business of everyone standing nearby.
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                        net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE),
                net.minecraft.sounds.SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(), 1.0F, 1.0F, 0L));
    }

    /**
     * Turn '&amp;' colour codes into a styled component.
     *
     * <p><b>Real styles, not section signs in the text.</b> The obvious
     * implementation puts {@code §} characters in a {@code Component.literal}
     * and lets the client interpret them. That renders correctly in game and is
     * wrong everywhere else, because {@code getString()} hands the codes back
     * verbatim — so the console, the log, and anything driving the server over
     * RCON get {@code §7CHR: §f14 §8(+2)} instead of a sentence. An owner
     * reading their own server's output should not have to decode it.</p>
     *
     * <p><b>Only where a real code follows.</b> Before that, this was a blind
     * {@code replace('&', '§')} — fine while every string came from a lang
     * template we wrote and correct by construction, and wrong the moment party
     * chat put <em>player</em> text through it: "Tom &amp; Jerry" became
     * "Tom § Jerry", the space swallowed as though it were a code.</p>
     *
     * <p>Both halves were found from the other side of a seam: the mangling
     * here, the section signs by reading Standards' output over RCON. Neither is
     * visible from inside, because the client renders both correctly and so can
     * never tell you the stored form was wrong. Standards carries the same fix
     * (38cb7a0); the rules below are the legacy ones either way.</p>
     *
     * <p>Whether players may use codes deliberately is a separate question,
     * answered where their text enters — see {@link PartyChat}.</p>
     */
    public static Component colored(String text) {
        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        Style style = Style.EMPTY;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // '§' counts as a marker too: some strings are assembled with the
            // sign already in them, and they should style rather than leak.
            ChatFormatting code = (c == '&' || c == '§') && i + 1 < text.length()
                    ? ChatFormatting.getByCode(text.charAt(i + 1))
                    : null;
            if (code == null) {
                run.append(c);
                continue;
            }
            if (!run.isEmpty()) {
                out.append(Component.literal(run.toString()).withStyle(style));
                run.setLength(0);
            }
            style = advance(style, code);
            i++; // consume the code letter
        }
        if (!run.isEmpty()) {
            out.append(Component.literal(run.toString()).withStyle(style));
        }
        return out;
    }

    /**
     * Legacy rule: a colour clears any formatting before it, {@code &r} clears
     * everything, and bold/italic/underline/strike/obfuscated accumulate.
     */
    private static Style advance(Style style, ChatFormatting code) {
        if (code == ChatFormatting.RESET) {
            return Style.EMPTY;
        }
        return code.isFormat() ? style.applyFormat(code) : Style.EMPTY.withColor(code);
    }

    private Feedback() {}
}
