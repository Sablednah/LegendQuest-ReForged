package com.sablednah.legendquest.skills.effects;

import com.sablednah.legendquest.character.LQAttachments;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;

/**
 * Marked quarry: a creature that will not vanish while somebody is hunting it.
 *
 * <p><b>The problem this exists for.</b> ZombieMod stopped forcing persistence
 * on its genera, so a rare one can now despawn while you are tracking it across
 * a valley. That is the right call for the mob cap and the wrong outcome for a
 * scout, and it is not a ZombieMod problem to solve — any creature worth
 * hunting has it.</p>
 *
 * <p><b>Why not {@code setPersistenceRequired()}.</b> It is public, it works on
 * a live mob, and it is the obvious answer. It is also wrong twice over. It is
 * one-way — the field is private, it is saved with the mob, and nothing clears
 * it, so a tracked creature would stay tracked forever. Worse,
 * {@code NaturalSpawner} deliberately skips persistent mobs when counting
 * toward the mob cap, so every use would permanently shrink the world's own
 * budget and vanilla would spawn a replacement for a mob that never left.
 * ZombieMod measured that ratchet on a dev server: 24 persistent genera became
 * 121 in four and a half minutes and kept climbing, while the count the cap
 * could see sat at 70 throughout. A skill a scout uses every fight would be the
 * same ratchet in miniature.</p>
 *
 * <p><b>{@link MobDespawnEvent} instead.</b> It fires from
 * {@code Mob.checkDespawn} for every mob, persistent or not, and answering
 * {@code DENY} refuses that despawn without touching the mob's own persistence
 * flag. So the creature stays, the mob cap still counts it, and when the mark
 * expires it despawns as it always would have.</p>
 *
 * <p><b>This handler runs for every mob, every despawn check</b>, so the first
 * thing it does has to be cheap. {@code hasData} is
 * {@code attachments != null && containsKey(type)} — no allocation, no codec,
 * nothing created for the overwhelming majority of mobs that were never
 * tracked. That is why the expiry is an attachment rather than a key in
 * {@code getPersistentData()}: reading that lazily <em>creates</em> a
 * CompoundTag on first touch, which is a per-mob allocation to answer "no".</p>
 *
 * <p>The mark is an absolute game time rather than a countdown, so it needs no
 * ticking of its own and survives a save: a creature marked for five minutes is
 * still marked five minutes later whether or not anybody was watching.</p>
 */
public final class Tracking {

    /** Mark a creature as quarry until {@code gameTime}. */
    public static void mark(LivingEntity quarry, long untilGameTime) {
        quarry.setData(LQAttachments.TRACKED, untilGameTime);
    }

    /** Let it go early — {@code revoke} on the effect, or a dispel. */
    public static void clear(LivingEntity quarry) {
        if (quarry.hasData(LQAttachments.TRACKED)) {
            quarry.removeData(LQAttachments.TRACKED);
        }
    }

    /** Whether this creature is being hunted right now. */
    public static boolean tracked(Entity entity) {
        if (!entity.hasData(LQAttachments.TRACKED)) return false;
        long until = entity.getData(LQAttachments.TRACKED);
        if (entity.level().getGameTime() < until) return true;
        // Expired. Drop it here rather than leaving a stale mark behind: this is
        // the only code that ever looks, so this is the only place that can.
        entity.removeData(LQAttachments.TRACKED);
        return false;
    }

    @SubscribeEvent
    static void onDespawn(MobDespawnEvent event) {
        if (tracked(event.getEntity())) {
            event.setResult(MobDespawnEvent.Result.DENY);
        }
    }

    private Tracking() {}
}
