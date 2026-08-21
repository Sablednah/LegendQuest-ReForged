package com.sablednah.legendquest.neoforge;

import net.minecraft.network.chat.Component;
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

    /** The format codes Minecraft actually knows: colours, styles, and reset. */
    private static final String CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    /**
     * Translate '&amp;' colour codes, but only where one really is a code.
     *
     * <p>This used to be a blind {@code replace('&', '§')}, which was fine while
     * every string came from a lang template written by us and correct by
     * construction. Party chat put <em>player</em> text through the same path
     * and the assumption broke: "Tom &amp; Jerry" arrived as "Tom § Jerry", the
     * space swallowed as though it were a code. Requiring a valid code character
     * after the ampersand costs our own templates nothing and stops that.</p>
     *
     * <p>This only rescues stray ampersands. Whether players may use codes
     * deliberately is a different question, answered where their text enters —
     * see {@link PartyChat}.</p>
     */
    public static Component colored(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean isCode = c == '&' && i + 1 < text.length()
                    && CODES.indexOf(text.charAt(i + 1)) >= 0;
            out.append(isCode ? '§' : c);
        }
        return Component.literal(out.toString());
    }

    private Feedback() {}
}
