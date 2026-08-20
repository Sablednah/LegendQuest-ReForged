package com.sablednah.legendquest.character;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.legendquest.data.StatBlock;

import net.minecraft.resources.Identifier;

/**
 * Everything LegendQuest knows about one player, stored as a codec-serialized
 * attachment on the player entity (survives death via {@code copyOnDeath},
 * saved with the player by vanilla).
 *
 * <p>This replaces the 2,430-line {@code PC} god class and its SQL tables.
 * Skill <em>definitions</em> are immutable registry data; everything mutable
 * and per-player lives here — which is what makes the old shared-instance
 * bugs impossible by construction.</p>
 */
public final class PlayerCharacter {

    private Optional<Identifier> raceId = Optional.empty();
    private Optional<Identifier> mainClassId = Optional.empty();
    private Optional<Identifier> subClassId = Optional.empty();
    private boolean raceChanged = false;
    private long karma = 0;
    private double mana = 0;
    /** Rolled once on first login (mode per config); empty until then. */
    private Optional<StatBlock> baseStats = Optional.empty();
    /** Class id → accumulated XP. XP is banked per class, as in the original. */
    private final Map<String, Long> classXp = new HashMap<>();
    /** Skill ids bought with skill points. */
    private final Set<String> purchasedSkills = new HashSet<>();
    /** Feat ids bought with skill points. */
    private final Set<String> purchasedFeats = new HashSet<>();
    private int skillPointsSpent = 0;
    /** Skill id → last activation (epoch ms); drives the phase machine. */
    private final Map<String, Long> lastUse = new HashMap<>();
    /** Stat name → points bought with skill points ("expensive stat boosts"). */
    private final Map<String, Integer> statBoosts = new HashMap<>();
    /** Item id → skill id: right-clicking that item fires the skill. */
    private final Map<String, String> bindings = new HashMap<>();
    /** The loadout: an ordered skill list cycled on one "spellbook" item. */
    private final java.util.List<String> loadout = new java.util.ArrayList<>();
    private int loadoutIndex = 0;
    private Optional<Identifier> loadoutItem = Optional.empty();
    /** Per-player opt-out of the floating nameplate. Persisted: a toggle that
     *  forgets itself every relog is worse than no toggle. */
    private boolean nameplateHidden = false;

    public PlayerCharacter() {}

    /**
     * Everything skill points were spent on, grouped so the main codec stays
     * under RecordCodecBuilder's 16-field ceiling. A MapCodec reads sibling
     * keys, so the saved NBT keeps the exact same flat layout as before.
     */
    private record Purchases(List<String> skills, List<String> feats,
            int spent, Map<String, Integer> statBoosts) {

        static final MapCodec<Purchases> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("purchased_skills", List.of())
                        .forGetter(Purchases::skills),
                Codec.STRING.listOf().optionalFieldOf("purchased_feats", List.of())
                        .forGetter(Purchases::feats),
                Codec.INT.optionalFieldOf("skill_points_spent", 0).forGetter(Purchases::spent),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stat_boosts", Map.of())
                        .forGetter(Purchases::statBoosts))
                .apply(i, Purchases::new));
    }

    private PlayerCharacter(Optional<Identifier> raceId, Optional<Identifier> mainClassId,
            Optional<Identifier> subClassId, boolean raceChanged, long karma, double mana,
            Optional<StatBlock> baseStats, Map<String, Long> classXp, Purchases purchases,
            Map<String, Long> lastUse, Map<String, String> bindings,
            List<String> loadout, int loadoutIndex, Optional<Identifier> loadoutItem,
            boolean nameplateHidden) {
        this.raceId = raceId;
        this.mainClassId = mainClassId;
        this.subClassId = subClassId;
        this.raceChanged = raceChanged;
        this.karma = karma;
        this.mana = mana;
        this.baseStats = baseStats;
        this.classXp.putAll(classXp);
        this.purchasedSkills.addAll(purchases.skills());
        this.purchasedFeats.addAll(purchases.feats());
        this.skillPointsSpent = purchases.spent();
        this.statBoosts.putAll(purchases.statBoosts());
        this.lastUse.putAll(lastUse);
        this.bindings.putAll(bindings);
        this.loadout.addAll(loadout);
        this.loadoutIndex = loadoutIndex;
        this.loadoutItem = loadoutItem;
        this.nameplateHidden = nameplateHidden;
    }

    public static final MapCodec<PlayerCharacter> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.optionalFieldOf("race").forGetter(c -> c.raceId),
            Identifier.CODEC.optionalFieldOf("main_class").forGetter(c -> c.mainClassId),
            Identifier.CODEC.optionalFieldOf("sub_class").forGetter(c -> c.subClassId),
            Codec.BOOL.optionalFieldOf("race_changed", false).forGetter(c -> c.raceChanged),
            Codec.LONG.optionalFieldOf("karma", 0L).forGetter(c -> c.karma),
            Codec.DOUBLE.optionalFieldOf("mana", 0.0D).forGetter(c -> c.mana),
            StatBlock.CODEC.optionalFieldOf("base_stats").forGetter(c -> c.baseStats),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("class_xp", Map.of())
                    .forGetter(c -> Map.copyOf(c.classXp)),
            Purchases.MAP_CODEC.forGetter(c -> new Purchases(
                    List.copyOf(c.purchasedSkills), List.copyOf(c.purchasedFeats),
                    c.skillPointsSpent, Map.copyOf(c.statBoosts))),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("last_use", Map.of())
                    .forGetter(c -> Map.copyOf(c.lastUse)),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("bindings", Map.of())
                    .forGetter(c -> Map.copyOf(c.bindings)),
            Codec.STRING.listOf().optionalFieldOf("loadout", List.of())
                    .forGetter(c -> List.copyOf(c.loadout)),
            Codec.INT.optionalFieldOf("loadout_index", 0).forGetter(c -> c.loadoutIndex),
            Identifier.CODEC.optionalFieldOf("loadout_item").forGetter(c -> c.loadoutItem),
            Codec.BOOL.optionalFieldOf("nameplate_hidden", false).forGetter(c -> c.nameplateHidden))
            .apply(i, PlayerCharacter::new));

    /** True when this player has switched their own floating nameplate off. */
    public boolean nameplateHidden() { return nameplateHidden; }

    public void setNameplateHidden(boolean hidden) { this.nameplateHidden = hidden; }

    // --- race / classes ---
    public Optional<Identifier> raceId() { return raceId; }
    public Optional<Identifier> mainClassId() { return mainClassId; }
    public Optional<Identifier> subClassId() { return subClassId; }
    public boolean raceChanged() { return raceChanged; }

    public void setRace(Identifier id, boolean locked) {
        this.raceId = Optional.of(id);
        this.raceChanged = locked;
    }

    public void setMainClass(Identifier id) { this.mainClassId = Optional.of(id); }
    public void setSubClass(Optional<Identifier> id) { this.subClassId = id; }

    // --- karma / mana ---
    public long karma() { return karma; }
    public void addKarma(long delta) { this.karma += delta; }

    public double mana() { return mana; }
    public void setMana(double value) { this.mana = Math.max(0, value); }

    // --- stats ---
    public Optional<StatBlock> baseStats() { return baseStats; }
    public void setBaseStats(StatBlock stats) { this.baseStats = Optional.of(stats); }

    // --- xp ---
    public long xpFor(Identifier classId) {
        return classXp.getOrDefault(classId.toString(), 0L);
    }

    public void addXp(Identifier classId, long amount) {
        classXp.merge(classId.toString(), amount, Long::sum);
    }

    /** Respec's blunt instrument: rewrite a class's banked XP outright. */
    public void setXp(Identifier classId, long amount) {
        classXp.put(classId.toString(), Math.max(0, amount));
    }

    public Map<String, Long> allXp() { return Map.copyOf(classXp); }

    // --- skills ---
    public boolean hasPurchased(Identifier skillId) {
        return purchasedSkills.contains(skillId.toString());
    }

    public void purchase(Identifier skillId, int cost) {
        purchasedSkills.add(skillId.toString());
        skillPointsSpent += cost;
    }

    public int skillPointsSpent() { return skillPointsSpent; }

    // --- stat boosts (skill points made permanent) ---

    public int statBoost(String statName) {
        return statBoosts.getOrDefault(statName, 0);
    }

    public int totalStatBoosts() {
        return statBoosts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void buyStatBoost(String statName, int cost) {
        statBoosts.merge(statName, 1, Integer::sum);
        skillPointsSpent += cost;
    }

    /** Respec: hand back every point spent on skills, feats and stats. */
    public void refundPurchases() {
        purchasedSkills.clear();
        purchasedFeats.clear();
        statBoosts.clear();
        skillPointsSpent = 0;
    }

    // --- feats ---

    public boolean hasFeat(Identifier featId) {
        return purchasedFeats.contains(featId.toString());
    }

    public Set<String> featIds() {
        return Set.copyOf(purchasedFeats);
    }

    public void buyFeat(Identifier featId, int cost) {
        purchasedFeats.add(featId.toString());
        skillPointsSpent += cost;
    }

    public long lastUse(Identifier skillId) {
        return lastUse.getOrDefault(skillId.toString(), 0L);
    }

    public void stampUse(Identifier skillId, long epochMs) {
        lastUse.put(skillId.toString(), epochMs);
    }

    // --- item bindings (the old /link) ---

    public Optional<Identifier> bindingFor(Identifier itemId) {
        return Optional.ofNullable(bindings.get(itemId.toString())).map(Identifier::parse);
    }

    public void bind(Identifier itemId, Identifier skillId) {
        bindings.put(itemId.toString(), skillId.toString());
    }

    /** @return the skill that was bound, if any. */
    public Optional<Identifier> unbind(Identifier itemId) {
        return Optional.ofNullable(bindings.remove(itemId.toString())).map(Identifier::parse);
    }

    public Map<String, String> bindings() {
        return Map.copyOf(bindings);
    }

    // --- loadout (the old class loadouts: one item cycling many skills) ---

    public List<Identifier> loadout() {
        return loadout.stream().map(Identifier::parse).toList();
    }

    public Optional<Identifier> loadoutItem() {
        return loadoutItem;
    }

    public void setLoadoutItem(Optional<Identifier> itemId) {
        this.loadoutItem = itemId;
    }

    public boolean addToLoadout(Identifier skillId) {
        String key = skillId.toString();
        if (loadout.contains(key)) return false;
        loadout.add(key);
        return true;
    }

    public boolean removeFromLoadout(Identifier skillId) {
        boolean removed = loadout.remove(skillId.toString());
        if (loadoutIndex >= loadout.size()) loadoutIndex = 0;
        return removed;
    }

    public void clearLoadout() {
        loadout.clear();
        loadoutIndex = 0;
    }

    /** @return the newly selected skill, or empty if the loadout is empty. */
    public Optional<Identifier> cycleLoadout() {
        if (loadout.isEmpty()) return Optional.empty();
        loadoutIndex = (loadoutIndex + 1) % loadout.size();
        return selectedSkill();
    }

    public Optional<Identifier> selectedSkill() {
        if (loadout.isEmpty()) return Optional.empty();
        if (loadoutIndex >= loadout.size()) loadoutIndex = 0;
        return Optional.of(Identifier.parse(loadout.get(loadoutIndex)));
    }

    public int loadoutIndex() {
        return loadoutIndex;
    }

    /** Jump straight to a loadout slot (hotkeys); out-of-range is ignored. */
    public void selectLoadout(int index) {
        if (index >= 0 && index < loadout.size()) loadoutIndex = index;
    }

    /** Reorder (drag & drop): move {@code from} to {@code to}, keeping the
     *  currently selected <em>skill</em> selected whatever its new slot. */
    public void moveLoadout(int from, int to) {
        if (from < 0 || from >= loadout.size() || to < 0 || to >= loadout.size() || from == to) return;
        String selected = loadout.isEmpty() ? null : loadout.get(Math.min(loadoutIndex, loadout.size() - 1));
        loadout.add(to, loadout.remove(from));
        if (selected != null) loadoutIndex = Math.max(0, loadout.indexOf(selected));
    }
}
