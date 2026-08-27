package com.sablednah.legendquest.neoforge;

import com.sablednah.legendquest.LegendQuest;
import com.sablednah.standards.api.combat.Combat;
import com.sablednah.standards.api.combat.CombatKind;
import com.sablednah.standards.api.combat.Harm;
import com.sablednah.standards.api.groups.Claims;

/**
 * Reports LegendQuest's acts of combat to Standards.
 *
 * <p>Standards tags the fights it can see — a damage event with a player
 * behind it. It cannot see the ones LegendQuest resolves privately: a swing
 * that misses is a cancelled damage event, and a curse or a summon is no
 * damage event at all. Those are the cases this exists for.</p>
 *
 * <p><b>One of two classes importing {@code com.sablednah.standards}</b>, the
 * other being {@link ChatSupport}, and loaded only through the
 * {@code ModList.isLoaded} guard in {@code LegendQuest}. Everything else calls
 * {@link CombatTagging}, which does nothing at all without this.</p>
 *
 * <p>Durations are left to the server: {@code Combat.tag} without a seconds
 * argument uses the configured value for the kind, and a kind set to 0 seconds
 * is disabled entirely. A co-operative server turning PvP tagging off should
 * not have to argue with us about it.</p>
 */
public final class CombatSupport {

    public static void register() {
        CombatTagging.setSink((player, kind, source, seconds) -> {
            CombatKind mapped = switch (kind) {
                case PVP -> CombatKind.PVP;
                case PVE -> CombatKind.PVE;
                case SKILL -> CombatKind.SKILL;
            };
            if (seconds > 0) {
                Combat.tag(player, mapped, source, seconds);
            } else {
                Combat.tag(player, mapped, source);
            }
        });
        // Two seams, deliberately separate, because they are two different
        // facts: Harm is about the PAIR (a peaceful faction, an ally, a truce)
        // and Claims is about the PLACE (a safe zone, a spawn area). Neither
        // implies the other, and a mod that only deals damage needs neither --
        // Standards gates player-on-player damage itself. LegendQuest needs
        // both because a curse is not a damage event, so nothing else on the
        // server gets the chance to refuse it.
        CombatTagging.setGuard(Harm::forbidden);
        CombatTagging.setPlaceGuard(Claims::pvpAllowed);
        LegendQuest.LOGGER.info("Reporting combat to Standards, and asking it before doing harm");
    }

    private CombatSupport() {}
}
