package com.sablednah.legendquest.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.sablednah.legendquest.LQConfig;
import com.sablednah.legendquest.LQRegistries;
import com.sablednah.legendquest.LegendQuest;
import com.sablednah.legendquest.character.PlayerCharacter;
import com.sablednah.legendquest.core.Leveling;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Advancements, granted by criterion NAME, with no trigger type of our own.
 *
 * <p><b>Lifted from ZombieMod's {@code neoforge/Feats}</b>, which arrived at this
 * shape first; the mechanism is identical and the vocabulary is this mod's. The
 * shipped advancements are plain data under {@code data/legendquest/advancement/},
 * every criterion is vanilla's {@code minecraft:impossible}, and this class awards
 * any criterion whose name starts {@code legendquest:}.</p>
 *
 * <p><b>Do not "improve" this into a registered trigger type.</b> A trigger type
 * is a registry entry, and the whole point is that a vanilla client is never
 * asked about anything of ours — these are ordinary advancements, so they work
 * on an unmodded client and in Better Advancements without either knowing this
 * mod exists.</p>
 *
 * <p><b>The criterion names are public API.</b> A pack author can ship their own
 * advancement using any name below and it will be granted; renaming one breaks
 * other people's datapacks. The README carries the table.</p>
 *
 * <p>The index is rebuilt whenever {@code server.getAdvancements().tree()}
 * changes identity, which is what {@code /reload} does to it — so there is no
 * reload listener here to be renamed by the next Minecraft version.</p>
 */
public final class Achievements {

    private static final String PREFIX = LegendQuest.MODID + ":";

    /**
     * Families written as {@code name/<whole number>}, so a pack can add a step
     * this mod never shipped — {@code legendquest:level/35} — and get it granted
     * with no code. Every family here is "the highest you have reached", so all
     * steps at or below the current figure are offered.
     */
    private static final String LEVEL = PREFIX + "level/";
    private static final String KARMA_BRIGHT = PREFIX + "karma_bright/";
    private static final String KARMA_DARK = PREFIX + "karma_dark/";

    /** What this session granted, and how many names the loaded datapacks listen for. */
    public static final class Counters {
        public int granted;
        public int listening;

        @Override
        public String toString() {
            return granted + " criteria granted this session, " + listening + " criterion names in use";
        }
    }

    public static final Counters COUNTERS = new Counters();

    private static Object indexedTree;
    private static Map<String, List<AdvancementHolder>> index = Map.of();
    private static TreeSet<Integer> levelSteps = new TreeSet<>();
    private static TreeSet<Integer> brightSteps = new TreeSet<>();
    private static TreeSet<Integer> darkSteps = new TreeSet<>();

    // --- what the rest of the mod calls ------------------------------------

    /** Something happened. {@code subject} may be null for an event that has none. */
    public static void fire(ServerPlayer player, String event, String subject) {
        if (!listening(player)) return;
        award(player, PREFIX + event);
        if (subject != null) award(player, PREFIX + event + "/" + subject);
    }

    /** A race was chosen. Also settles "have they now played them all". */
    public static void raceChosen(ServerPlayer player, Identifier raceId) {
        fire(player, "race_chosen", raceId.toString());
        everyRace(player);
    }

    /** A class was taken, as main or sub. */
    public static void classChosen(ServerPlayer player, Identifier classId) {
        fire(player, "class_chosen", classId.toString());
    }

    /**
     * The level moved. Offers every step at or below it, so a character who was
     * already past a milestone when its advancement arrived still gets it.
     */
    public static void levelled(ServerPlayer player, int level) {
        if (!listening(player)) return;
        for (int step : levelSteps.headSet(level, true)) {
            award(player, LEVEL + step);
        }
        if (level >= LQConfig.MAX_LEVEL.get()) award(player, PREFIX + "max_level");
        mastery(player);
    }

    /**
     * Karma, as distance from neutral in each direction.
     *
     * <p>Two families rather than one signed number because the two are
     * different achievements: a saint and a monster have both gone a long way,
     * and neither is the other's progress.</p>
     */
    public static void karma(ServerPlayer player) {
        if (!listening(player)) return;
        long karma = CharacterService.data(player).karma();
        if (karma > 0) {
            for (int step : brightSteps.headSet((int) Math.min(karma, Integer.MAX_VALUE), true)) {
                award(player, KARMA_BRIGHT + step);
            }
        } else if (karma < 0) {
            for (int step : darkSteps.headSet((int) Math.min(-karma, Integer.MAX_VALUE), true)) {
                award(player, KARMA_DARK + step);
            }
        }
    }

    /**
     * Classes taken to the cap.
     *
     * <p>Derived from the XP already kept per class rather than from a new
     * record of its own: {@code classXp} is what decides a class's level
     * anyway, so asking it cannot disagree with the character sheet. "All" is
     * measured against the classes this player may actually choose — a class a
     * server has restricted is not held against anybody, the same rule
     * ZombieMod applies to a concealed genus.</p>
     */
    public static void mastery(ServerPlayer player) {
        if (!listening(player)) return;
        PlayerCharacter pc = CharacterService.data(player);
        int cap = LQConfig.MAX_LEVEL.get();
        int open = 0;
        int mastered = 0;
        for (var holder : player.level().registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS).listElements().toList()) {
            Identifier id = holder.key().identifier();
            if (!LQPermissions.canSelectClass(player, id)) continue;
            open++;
            if (Leveling.levelForXp(pc.xpFor(id), LQConfig.XP_LEVEL_BASE.get(), cap) >= cap) {
                mastered++;
                award(player, PREFIX + "class_mastered");
                award(player, PREFIX + "class_mastered/" + id);
            }
        }
        if (open > 0 && mastered >= open) award(player, PREFIX + "classes_all_mastered");
    }

    /** Every race this player is allowed to pick, played at least once. */
    private static void everyRace(ServerPlayer player) {
        PlayerCharacter pc = CharacterService.data(player);
        int open = 0;
        int played = 0;
        for (var holder : player.level().registryAccess().lookupOrThrow(LQRegistries.RACE).listElements().toList()) {
            Identifier id = holder.key().identifier();
            if (!LQPermissions.canSelectRace(player, id)) continue;
            open++;
            if (pc.hasPlayedRace(id)) played++;
        }
        if (open > 0 && played >= open) award(player, PREFIX + "races_all");
    }

    /**
     * A party did something together. {@code legends} is the whole party at the
     * cap, which is checked here rather than on levelling because it is a fact
     * about other people: everybody in it earns it at the moment the last of
     * them arrives.
     */
    public static void party(ServerPlayer player, String event) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        var party = Parties.get(server).partyOf(player.getUUID());
        if (party.isEmpty()) return;
        int cap = LQConfig.MAX_LEVEL.get();
        boolean all = party.get().members().size() > 1;
        List<ServerPlayer> online = new ArrayList<>();
        for (var memberId : party.get().members()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member == null) {
                all = false;   // somebody we cannot see is somebody we cannot vouch for
                continue;
            }
            online.add(member);
            if (CharacterService.level(member) < cap) all = false;
        }
        for (ServerPlayer member : online) {
            fire(member, event, null);
            if (all) fire(member, "party_legends", null);
        }
    }

    /**
     * Catch a character up with themselves at login.
     *
     * <p>Everything here is re-derived from saved state, so a player who was
     * level 30 before any of this existed is not asked to start again — and an
     * advancement a datapack adds next month is granted to the people who had
     * already earned it. This is the half that makes the criterion names worth
     * publishing.</p>
     */
    public static void login(ServerPlayer player) {
        if (!listening(player)) return;
        PlayerCharacter pc = CharacterService.data(player);
        // An older save has a race but no history of having played it; this is
        // where that is put right, once, for free.
        pc.raceId().ifPresent(pc::recordRacePlayed);
        pc.raceId().ifPresent(id -> fire(player, "race_chosen", id.toString()));
        pc.mainClassId().ifPresent(id -> fire(player, "class_chosen", id.toString()));
        pc.subClassId().ifPresent(id -> fire(player, "class_chosen", id.toString()));
        for (String skill : pc.skillIds()) fire(player, "skill_learned", skill);
        for (String feat : pc.featIds()) fire(player, "feat_bought", feat);
        levelled(player, CharacterService.level(player));
        karma(player);
        everyRace(player);
    }

    // --- the index ----------------------------------------------------------

    /**
     * Whether to bother at all.
     *
     * <p>A fake player is turned away here only to save the work: NeoForge
     * patches {@code PlayerAdvancements.award} to refuse one outright — the
     * check is on the class, so overriding {@code isFakePlayer()} does not get
     * round it — and another mod's grinder could never have earned anything
     * whatever this method said.</p>
     */
    private static boolean listening(ServerPlayer player) {
        if (!LQConfig.ADVANCEMENTS.get() || player.isFakePlayer()) return false;
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        Object tree = server.getAdvancements().tree();
        if (tree != indexedTree) {
            rebuild(server);
            indexedTree = tree;
        }
        return !index.isEmpty();
    }

    private static void rebuild(MinecraftServer server) {
        Map<String, List<AdvancementHolder>> built = new HashMap<>();
        TreeSet<Integer> levels = new TreeSet<>();
        TreeSet<Integer> bright = new TreeSet<>();
        TreeSet<Integer> dark = new TreeSet<>();
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            for (String name : holder.value().criteria().keySet()) {
                if (!name.startsWith(PREFIX)) continue;
                built.computeIfAbsent(name, k -> new ArrayList<>()).add(holder);
                step(name, LEVEL, levels, holder);
                step(name, KARMA_BRIGHT, bright, holder);
                step(name, KARMA_DARK, dark, holder);
            }
        }
        index = built;
        levelSteps = levels;
        brightSteps = bright;
        darkSteps = dark;
        COUNTERS.listening = built.size();
    }

    private static void step(String name, String prefix, TreeSet<Integer> into, AdvancementHolder holder) {
        if (!name.startsWith(prefix)) return;
        try {
            into.add(Integer.parseInt(name.substring(prefix.length())));
        } catch (NumberFormatException e) {
            LegendQuest.LOGGER.warn("Advancement {} has criterion '{}', which needs a whole number after"
                    + " the slash -- it will never be granted", holder.id(), name);
        }
    }

    private static void award(ServerPlayer player, String criterion) {
        for (AdvancementHolder holder : index.getOrDefault(criterion, List.of())) {
            if (player.getAdvancements().award(holder, criterion)) {
                COUNTERS.granted++;
            }
        }
    }

    private Achievements() {}
}
