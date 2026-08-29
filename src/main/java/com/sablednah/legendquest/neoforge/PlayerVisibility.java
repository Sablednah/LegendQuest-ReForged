package com.sablednah.legendquest.neoforge;

import net.minecraft.server.level.ServerPlayer;

/**
 * Whether a player is meant to be seen at all.
 *
 * <p>Standards owns vanish, because it owns the command that does it and the
 * permission that sees through it. LegendQuest owns the nameplate drawn on the
 * player, and only LegendQuest knows that exists — so Standards answers the
 * question and this side acts on it. The same division as chat, claims and
 * combat.</p>
 *
 * <p><b>This class never mentions Standards</b>, so every call is safe on a
 * server that has never heard of it. {@link VanishSupport} installs the real
 * answer when it is present; until then everybody is visible, which is the
 * correct answer for a server with no vanish command.</p>
 */
public final class PlayerVisibility {

    @FunctionalInterface
    public interface Check {
        boolean vanished(ServerPlayer player);
    }

    private static Check check = player -> false;
    private static java.util.function.BooleanSupplier any = () -> false;

    static void setCheck(Check installed, java.util.function.BooleanSupplier anyVanished) {
        check = installed;
        any = anyVanished;
    }

    /**
     * True when this player is hidden and anything drawn on them should not be.
     *
     * <p>A vanished player is unpaired from every viewer's tracker — but a
     * display entity following them is its own entity and is not, so the
     * nameplate hangs in the air over nobody, marking the spot. Identical in
     * shape to the spectator leak, and reported the same way: Sable vanished
     * and his own nameplate stayed put.</p>
     */
    public static boolean vanished(ServerPlayer player) {
        // Cheap gate first: on almost every server nobody is vanished, and this
        // is consulted for every player every tick by the nameplate follower.
        return any.getAsBoolean() && check.vanished(player);
    }

    private PlayerVisibility() {}
}
