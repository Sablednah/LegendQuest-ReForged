package com.sablednah.legendquest.neoforge;

import com.sablednah.crawlspace.api.CrawlSpaceApi;
import com.sablednah.crawlspace.api.Perception;
import com.sablednah.legendquest.core.Stat;

import net.minecraft.server.level.ServerPlayer;

/**
 * CrawlSpace's dungeons, played with LegendQuest's dice: noticing a trap or a
 * secret door is a WIS check, disarming a trap a DEX check, and dwarves are
 * good with traps. The only class that imports CrawlSpace. LegendQuest loads
 * it by name, and only when CrawlSpace is installed, so it builds and runs
 * without CrawlSpace too (the build leaves this file out when CrawlSpace's
 * jar is not beside it).
 *
 * <p>A noticed thing shows its roll to the player who noticed it; a failure to
 * notice shows nothing, because a visible failed roll would announce that
 * something was there. A disarm, which the player chose to try, always shows
 * its roll.</p>
 */
public final class CrawlSpaceSupport {

    /** How much better a dwarf is at disarming: the stone remembers its own. */
    private static final int DWARF_DISARM = 4;

    private CrawlSpaceSupport() {
    }

    /** Called by name from LegendQuest's constructor when CrawlSpace is loaded. */
    public static void register() {
        CrawlSpaceApi.setPerception(new Check());
    }

    private static final class Check implements Perception {

        @Override
        public boolean notices(ServerPlayer player, Hidden what, int depth) {
            int mod = CharacterService.statModifier(player, Stat.WIS);
            int dc = (what == Hidden.TRAP ? 10 : 12) + depth;
            int roll = player.getRandom().nextInt(20) + 1;
            boolean ok = roll == 20 || (roll != 1 && roll + mod >= dc);
            if (ok) {
                show(player, "msg.crawl.perceive", roll, mod, dc);
            }
            return ok;
        }

        @Override
        public boolean disarms(ServerPlayer player, int depth) {
            int mod = CharacterService.statModifier(player, Stat.DEX) + (isDwarf(player) ? DWARF_DISARM : 0);
            int dc = 10 + depth;
            int roll = player.getRandom().nextInt(20) + 1;
            boolean ok = roll == 20 || (roll != 1 && roll + mod >= dc);
            show(player, ok ? "msg.crawl.disarm.ok" : "msg.crawl.disarm.fail", roll, mod, dc);
            return ok;
        }

        /**
         * Stepping off a puzzle room's maze: an Athletics (STR) check to catch
         * yourself on the last block you stood on. The roll always shows: the
         * player knows they slipped. No {@code @Override}, deliberately: the
         * method arrived in CrawlSpace 0.2.0, and without the annotation this
         * still compiles against 0.1.0, where it is simply never called.
         */
        public boolean recovers(ServerPlayer player, int depth) {
            int mod = CharacterService.statModifier(player, Stat.STR);
            int dc = 10 + depth;
            int roll = player.getRandom().nextInt(20) + 1;
            boolean ok = roll == 20 || (roll != 1 && roll + mod >= dc);
            show(player, ok ? "msg.crawl.athletics.ok" : "msg.crawl.athletics.fail", roll, mod, dc);
            return ok;
        }

        // The five below arrived in CrawlSpace 0.3.0. No @Override, as with recovers: against an older
        // CrawlSpace this still compiles, and the methods are simply never called.

        /** A mimic, before it bites: Investigation (INT). Only a success shows, like noticing a trap. */
        public boolean spotsMimic(ServerPlayer player, int depth) {
            return check(player, Stat.INT, 0, 12 + depth, "msg.crawl.mimic", null);
        }

        /** A trapped chest's needle, as it opens: Sleight of Hand (DEX), and dwarves know a lock. */
        public boolean disarmsChest(ServerPlayer player, int depth) {
            return check(player, Stat.DEX, isDwarf(player) ? DWARF_DISARM : 0, 12 + depth, "msg.crawl.needle.ok", "msg.crawl.needle.fail");
        }

        /** A trap's tell, as you come near: Perception (WIS). Only a success shows. */
        public boolean readsTell(ServerPlayer player, int depth) {
            return check(player, Stat.WIS, 0, 10 + depth, "msg.crawl.tell", null);
        }

        /** A shrine's omen, as you come near: Insight (WIS). Only a success shows. */
        public boolean readsOmen(ServerPlayer player, int depth) {
            return check(player, Stat.WIS, 0, 12 + depth, "msg.crawl.omen", null);
        }

        /** A sentry's eye: Stealth (DEX). It shows either way: seen or not, the player learns it now. */
        public boolean evades(ServerPlayer player, int depth) {
            return check(player, Stat.DEX, 0, 12 + depth, "msg.crawl.stealth.ok", "msg.crawl.stealth.fail");
        }
    }

    /** A d20 check: a natural 20 always passes and a 1 always fails. Shows {@code fail} only when it is given. */
    private static boolean check(ServerPlayer player, Stat stat, int bonus, int dc, String pass, String fail) {
        int mod = CharacterService.statModifier(player, stat) + bonus;
        int roll = player.getRandom().nextInt(20) + 1;
        boolean ok = roll == 20 || (roll != 1 && roll + mod >= dc);
        if (ok || fail != null) {
            show(player, ok ? pass : fail, roll, mod, dc);
        }
        return ok;
    }

    private static boolean isDwarf(ServerPlayer player) {
        return CharacterService.data(player).raceId().map(id -> id.getPath().equals("dwarf")).orElse(false);
    }

    private static void show(ServerPlayer player, String key, int roll, int mod, int dc) {
        Feedback.chat(player, Lang.fmt(key,
                "roll", roll,
                "mod", mod == 0 ? "" : (mod > 0 ? " +" : " ") + mod,
                "total", roll + mod,
                "dc", dc));
    }
}
