package com.sablednah.legendquest.neoforge;

import com.sablednah.legendquest.LegendQuest;
import com.sablednah.standards.api.vanish.Vanish;
import com.sablednah.standards.api.vanish.VanishEvent;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Takes the nameplate down when Standards hides a player, and puts it back.
 *
 * <p>One of three classes importing {@code com.sablednah.standards}, alongside
 * {@link ChatSupport} and {@link CombatSupport}, and loaded only through the
 * guard in {@code LegendQuest}. Everything else consults
 * {@link PlayerVisibility}, which answers "visible" without it.</p>
 *
 * <h2>Two mechanisms, because one is not enough</h2>
 *
 * <p><b>Ask</b>, so a player who logs in already vanished never gets a plate:
 * that state is restored during login, before anything of ours is attached, so
 * there is no event to hear. {@link Nameplate} does this by consulting
 * {@link PlayerVisibility} in the same gate that handles death and spectator.</p>
 *
 * <p><b>Listen</b>, for the change mid-session — the case actually reported.
 * The per-tick follower would catch it anyway, and that was nearly the reason
 * not to subscribe. But a tick of a name hovering over empty space is precisely
 * the giveaway the whole thing exists to prevent, and the event fires in the
 * same call that unpairs the player, so the plate goes at the same instant they
 * do rather than a frame later. (Standards' point, and a fair one.)</p>
 *
 * <p>The plate is removed outright rather than hidden per viewer. Standards
 * offers {@code hiddenFrom(subject, viewer)} so staff holding
 * {@code standards.vanish.see} could keep seeing the name — but that means
 * gating our own entity's visibility per viewer, which is real work for a
 * refinement. Removing it fixes the giveaway, which is the bug.</p>
 */
public final class VanishSupport {

    public static void register() {
        PlayerVisibility.setCheck(Vanish::isVanished, Vanish::anyVanished);
        NeoForge.EVENT_BUS.addListener(VanishSupport::onVanish);
        LegendQuest.LOGGER.info("Nameplates will follow Standards' vanish state");
    }

    @SubscribeEvent
    private static void onVanish(VanishEvent event) {
        ServerPlayer player = event.getPlayer();
        if (event.isVanished()) {
            Nameplate.clear(player);
        } else {
            Nameplate.refresh(player);
        }
    }

    private VanishSupport() {}
}
