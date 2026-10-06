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
