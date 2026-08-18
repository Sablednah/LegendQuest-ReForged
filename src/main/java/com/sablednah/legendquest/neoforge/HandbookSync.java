package com.sablednah.legendquest.neoforge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.sablednah.legendquest.LQRegistries;
import com.sablednah.legendquest.core.Stat;
import com.sablednah.legendquest.data.CharClass;
import com.sablednah.legendquest.data.ItemRules;
import com.sablednah.legendquest.data.Race;
import com.sablednah.legendquest.data.SkillGrant;
import com.sablednah.legendquest.data.StatBlock;
import com.sablednah.legendquest.network.HandbookPayload;
import com.sablednah.legendquest.network.HandbookPayload.Entry;
import com.sablednah.legendquest.network.HandbookPayload.Line;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Builds the Players Handbook from the live registries and sends it once on
 * login. Pages are plain lines plus typed links, so adding a race in a YAML
 * file updates the book on the next login with zero client knowledge.
 *
 * <p>Every tag referenced by an item rule also becomes a "gear" page listing
 * its actual items (icons included), and the rule line links to it — "what
 * exactly ARE fighter weapons?" is one click, never a wiki visit.</p>
 */
public final class HandbookSync {

    public static void send(ServerPlayer player) {
        if (player.connection == null
                || !player.connection.hasChannel(HandbookPayload.TYPE)) return; // vanilla/fake
        Net.sendIfAble(player, build(player));
    }

    private static HandbookPayload build(ServerPlayer player) {
        var access = player.level().registryAccess();
        HolderLookup.RegistryLookup<Race> raceLookup = access.lookupOrThrow(LQRegistries.RACE);
        HolderLookup.RegistryLookup<CharClass> classLookup = access.lookupOrThrow(LQRegistries.CHAR_CLASS);
        var skillLookup = access.lookupOrThrow(LQRegistries.SKILL);

        // Gear pages are collected while rendering rule lines.
        Map<String, Entry> gear = new LinkedHashMap<>();

        List<Entry> races = new ArrayList<>();
        raceLookup.listElements()
                .sorted(Comparator.comparing(ref -> ref.value().name()))
                .forEach(ref -> races.add(racePage(ref.key().identifier(), ref.value(),
                        classLookup, gear)));

        List<Entry> classes = new ArrayList<>();
        classLookup.listElements()
                .sorted(Comparator.comparing(ref -> ref.value().name()))
                .forEach(ref -> classes.add(classPage(ref.key().identifier(), ref.value(),
                        raceLookup, gear)));

        var featLookup = access.lookupOrThrow(LQRegistries.FEAT);

        // Only skills something can actually teach you. The built-in D&D skills stay
        // registered under a genre pack (a pack filter hides races and classes, not
        // skills), and listing spells no Role grants is just a longer book to search.
        java.util.Set<Identifier> obtainable = new java.util.HashSet<>();
        raceLookup.listElements().forEach(ref -> obtainable.addAll(ref.value().skills().keySet()));
        classLookup.listElements().forEach(ref -> obtainable.addAll(ref.value().skills().keySet()));
        featLookup.listElements().forEach(ref -> obtainable.addAll(ref.value().skills().keySet()));

        List<Entry> skills = new ArrayList<>();
        skillLookup.listElements()
                .filter(ref -> obtainable.contains(ref.key().identifier()))
                .sorted(Comparator.comparing(ref -> ref.value().name()))
                .forEach(ref -> skills.add(skillPage(ref.key().identifier(), ref.value(),
                        raceLookup, classLookup)));

        List<Entry> feats = new ArrayList<>();
        featLookup.listElements()
                .sorted(Comparator.comparing(ref -> ref.value().name()))
                .forEach(ref -> feats.add(featPage(ref.key().identifier(), ref.value(),
                        featLookup, raceLookup, classLookup, gear)));

        List<Entry> gearPages = new ArrayList<>(gear.values());
        gearPages.sort(Comparator.comparing(Entry::name));
        return new HandbookPayload(races, classes, skills, gearPages, feats);
    }

    // --- feat pages ---

    private static Entry featPage(Identifier id, com.sablednah.legendquest.data.Feat feat,
            HolderLookup.RegistryLookup<com.sablednah.legendquest.data.Feat> featLookup,
            HolderLookup.RegistryLookup<Race> raceLookup,
            HolderLookup.RegistryLookup<CharClass> classLookup, Map<String, Entry> gear) {
        List<Line> lines = new ArrayList<>();
        description(lines, feat.description(), Optional.empty());
        lines.add(Line.text(""));
        lines.add(Line.text("§7" + hbf("hb.cost_line", "cost", feat.cost())
                + (feat.level() > 0 ? " §7· " + hbf("hb.needs_level", "level", feat.level()) : "")));
        if (feat.hasKarmaBand()) {
            lines.add(Line.text("§7" + hbf("hb.soul_line", "band", karmaBand(feat.karmaMin(), feat.karmaMax()))));
        }
        if (!feat.requires().isEmpty()) {
            lines.add(Line.text("§6" + hb("hb.requires_feats")));
            for (Identifier req : feat.requires()) {
                String name = featLookup.get(net.minecraft.resources.ResourceKey
                        .create(LQRegistries.FEAT, req))
                        .map(r -> r.value().name()).orElse(prettify(req.getPath()));
                lines.add(Line.link("  ▸ " + name, "feat", req.toString()));
            }
        }
        if (!feat.allowedRaces().isEmpty() || !feat.allowedGroups().isEmpty()) {
            lines.add(Line.text("§6" + hb("hb.only_for")));
            raceLookup.listElements()
                    .sorted(Comparator.comparing(ref -> ref.value().name()))
                    .forEach(ref -> {
                        Race race = ref.value();
                        if (race.isDefault()) return;
                        boolean open = feat.allowedRaces().contains(ref.key().identifier())
                                || race.groups().stream().anyMatch(feat.allowedGroups()::contains);
                        if (open) {
                            lines.add(Line.link("  ▸ " + race.name(), "race",
                                    ref.key().identifier().toString()));
                        }
                    });
        }
        if (!feat.allowedClasses().isEmpty()) {
            lines.add(Line.text("§6" + hb("hb.only_for_calling")));
            for (Identifier classId : feat.allowedClasses()) {
                String name = classLookup.get(net.minecraft.resources.ResourceKey
                        .create(LQRegistries.CHAR_CLASS, classId))
                        .map(r -> r.value().name()).orElse(prettify(classId.getPath()));
                lines.add(Line.link("  ▸ " + name, "class", classId.toString()));
            }
        }
        itemRuleLines(lines, feat.itemRules(), gear);
        boonLines(lines, feat.boons());
        grantLines(lines, feat.skills());
        return new Entry(id.toString(), feat.name(), feat.icon(), feat.cost(), lines);
    }

    // --- race pages ---

    private static Entry racePage(Identifier id, Race race,
            HolderLookup.RegistryLookup<CharClass> classLookup, Map<String, Entry> gear) {
        List<Line> lines = new ArrayList<>();
        description(lines, race.identity().description(), race.identity().longDescription());
        if (race.isDefault()) lines.add(Line.text("§8" + hb("hb.starting_race")));
        lines.add(Line.text(""));
        lines.add(Line.text("§7" + hb("hb.health") + " §f" + trim(race.baseHealth())
                + " §7· " + Lang.term("mana") + " §f" + trim(race.baseMana())
                + " §7(+" + trim(race.manaPerSecond()) + "/s) §7· " + hb("hb.size") + " §f" + trim(race.size())));
        statLine(lines, race.statmods());
        if (!race.groups().isEmpty()) {
            lines.add(Line.text("§7" + hb("hb.lineage") + ": §f" + String.join(", ", race.groups())));
        }
        itemRuleLines(lines, race.itemRules(), gear);
        craftRuleLines(lines, race.craftRules());
        boonLines(lines, race.boons());
        grantLines(lines, race.skills());

        // Which classes will have this race?
        List<Line> classLines = new ArrayList<>();
        classLookup.listElements().forEach(ref -> {
            CharClass c = ref.value();
            if (c.isDefault()) return;
            if (classOpenToRace(c, id, race)) {
                classLines.add(Line.link("  ▸ " + c.name(), "class",
                        ref.key().identifier().toString()));
            }
        });
        if (!classLines.isEmpty()) {
            lines.add(Line.text(""));
            lines.add(Line.text("§6" + hb("hb.open_classes")));
            lines.addAll(classLines);
        }
        return new Entry(id.toString(), race.name(), "", 0, lines);
    }

    private static boolean classOpenToRace(CharClass charClass, Identifier raceId, Race race) {
        var el = charClass.eligibility();
        return (el.allowedRaces().isEmpty() && el.allowedGroups().isEmpty())
                || el.allowedRaces().contains(raceId)
                || race.groups().stream().anyMatch(el.allowedGroups()::contains);
    }

    // --- class pages ---

    private static Entry classPage(Identifier id, CharClass charClass,
            HolderLookup.RegistryLookup<Race> raceLookup, Map<String, Entry> gear) {
        List<Line> lines = new ArrayList<>();
        description(lines, charClass.identity().description(), charClass.identity().longDescription());
        if (charClass.isDefault()) lines.add(Line.text("§8" + hb("hb.starting_class")));
        lines.add(Line.text(""));

        var growth = charClass.growth();
        // Every label goes through Lang: a pack that renames mana to "Charge"
        // must see "Charge" here too, and this line used to be the one place
        // that said "Mana" regardless. Collected as parts and joined rather
        // than appended with leading separators, so a class with no health
        // modifier no longer opens the line with a stray "·".
        List<String> vitals = new ArrayList<>();
        if (growth.healthMod() != 0 || growth.healthPerLevel() != 0) {
            vitals.add(hb("hb.health") + " " + perLevel(growth.healthMod(), growth.healthPerLevel()));
        }
        if (growth.manaBonus() != 0 || growth.manaPerLevel() != 0) {
            vitals.add(Lang.term("mana") + " " + perLevel(growth.manaBonus(), growth.manaPerLevel()));
        }
        if (growth.manaPerSecond() != 0) {
            vitals.add(hb("hb.regen") + " " + signed(growth.manaPerSecond()) + "/s");
        }
        if (growth.speedMod() != 0) {
            vitals.add(hb("hb.speed") + " " + signed(growth.speedMod()));
        }
        if (!vitals.isEmpty()) lines.add(Line.text("§7" + String.join(" · ", vitals)));
        statLine(lines, charClass.statmods());

        var el = charClass.eligibility();
        if (el.mainOnly()) lines.add(Line.text("§7" + hb("hb.main_only")));
        if (el.subOnly()) lines.add(Line.text("§7" + hb("hb.sub_only")));

        // "Open to" resolves groups to the actual races — clickable, no jargon.
        if (!el.allowedRaces().isEmpty() || !el.allowedGroups().isEmpty()) {
            lines.add(Line.text("§6" + hb("hb.open_to")));
            raceLookup.listElements()
                    .sorted(Comparator.comparing(ref -> ref.value().name()))
                    .forEach(ref -> {
                        Race race = ref.value();
                        if (race.isDefault()) return;
                        if (classOpenToRace(charClass, ref.key().identifier(), race)) {
                            lines.add(Line.link("  ▸ " + race.name(), "race",
                                    ref.key().identifier().toString()));
                        }
                    });
            if (!el.allowedGroups().isEmpty()) {
                lines.add(Line.text("  §8" + hbf("hb.lineage_line", "list", String.join(", ", el.allowedGroups()))));
            }
        } else {
            lines.add(Line.text("§7" + hb("hb.open_to_everyone")));
        }
        if (!el.requires().isEmpty() || !el.requiresOne().isEmpty()) {
            lines.add(Line.text("§6" + hb("hb.requires_mastering")));
            for (Identifier req : el.requires()) {
                lines.add(Line.link("  ▸ " + prettify(req.getPath()), "class", req.toString()));
            }
            if (!el.requiresOne().isEmpty()) {
                lines.add(Line.text("  §7" + hb("hb.one_of")));
                for (Identifier req : el.requiresOne()) {
                    lines.add(Line.link("  ▸ " + prettify(req.getPath()), "class", req.toString()));
                }
            }
        }
        itemRuleLines(lines, charClass.itemRules(), gear);
        craftRuleLines(lines, charClass.craftRules());
        boonLines(lines, charClass.boons());
        grantLines(lines, charClass.skills());
        return new Entry(id.toString(), charClass.name(), "", 0, lines);
    }

    /**
     * Built-ins that stay silent on purpose — sound, particles and the caster's
     * own flourish message are things you experience, not things you need told.
     * Silence from anything else means an author forgot, so it gets named.
     */
    private static final java.util.Set<String> DECORATIVE = java.util.Set.of(
            "legendquest:sound", "legendquest:particle_line", "legendquest:message");

    // --- skill pages ---

    private static Entry skillPage(Identifier id, com.sablednah.legendquest.data.SkillDefinition def,
            HolderLookup.RegistryLookup<Race> raceLookup,
            HolderLookup.RegistryLookup<CharClass> classLookup) {
        List<Line> lines = new ArrayList<>();
        description(lines, def.description(), Optional.empty());
        lines.add(Line.text(""));
        lines.add(Line.text("§7" + hb("hb.type") + ": §f" + def.type().name().toLowerCase(Locale.ROOT)));

        var costs = def.costs();
        StringBuilder cost = new StringBuilder("§7");
        if (costs.manaCost() > 0) cost.append(Lang.term("mana")).append(" ").append(costs.manaCost()).append(" ");
        if (def.timing().cooldownMs() > 0) cost.append("· ").append(hb("hb.cooldown")).append(" ").append(def.timing().cooldownMs() / 1000).append("s ");
        if (def.timing().buildupMs() > 0) cost.append("· ").append(hb("hb.buildup")).append(" ").append(def.timing().buildupMs() / 1000.0).append("s ");
        if (def.timing().delayMs() > 0) cost.append("· ").append(hb("hb.delay")).append(" ").append(def.timing().delayMs() / 1000.0).append("s ");
        if (def.timing().durationMs() > 0) cost.append("· ").append(hb("hb.lasts")).append(" ").append(def.timing().durationMs() / 1000.0).append("s");
        if (cost.length() > 2) lines.add(Line.text(cost.toString().strip()));
        costs.consumes().ifPresent(item -> lines.add(Line.text("§7" + hb("hb.consumes") + ": §f"
                + costs.consumesQty() + "× " + itemName(item))));
        if (costs.karmaRequired() != 0) {
            lines.add(Line.text("§7" + hbf(costs.karmaRequired() > 0
                    ? "hb.karma_needs_at_least" : "hb.karma_needs_at_most",
                    "value", costs.karmaRequired())));
        }

        // What it actually does, straight from the effects. Flavour text is the
        // author's job; the numbers are ours, and generating them means the book
        // cannot drift from the data the way a hand-written line would.
        List<String> doings = new ArrayList<>();
        for (var effect : def.effects()) {
            String said = effect.describe();
            if (said.isBlank() && !DECORATIVE.contains(effect.type().toString())) {
                // A third-party effect that has not overridden describe(): name it
                // rather than let the skill read as though it does nothing.
                said = hbf("hb.fx.unknown", "type", effect.type());
            }
            if (!said.isBlank()) doings.add(said);
        }
        if (!doings.isEmpty()) {
            lines.add(Line.text(""));
            lines.add(Line.text("§6" + hb("hb.effects")));
            for (String said : doings) lines.add(Line.text("  §f" + said));
        }

        // Who teaches it, and when?
        List<Line> sources = new ArrayList<>();
        raceLookup.listElements().forEach(ref -> {
            SkillGrant grant = ref.value().skills().get(id);
            if (grant != null) {
                sources.add(Line.link("  ▸ " + ref.value().name() + grantSuffix(grant),
                        "race", ref.key().identifier().toString()));
            }
        });
        classLookup.listElements().forEach(ref -> {
            SkillGrant grant = ref.value().skills().get(id);
            if (grant != null) {
                sources.add(Line.link("  ▸ " + ref.value().name() + grantSuffix(grant),
                        "class", ref.key().identifier().toString()));
            }
        });
        if (!sources.isEmpty()) {
            lines.add(Line.text(""));
            lines.add(Line.text("§6" + hb("hb.taught_by")));
            lines.addAll(sources);
        }
        return new Entry(id.toString(), def.name(), def.icon(), 0, lines);
    }

    // --- shared formatting ---

    private static void description(List<Line> lines, Optional<String> desc, Optional<String> longDesc) {
        desc.ifPresent(d -> lines.add(Line.text("§f" + d)));
        longDesc.ifPresent(d -> lines.add(Line.text("§7" + d)));
        if (desc.isEmpty() && longDesc.isEmpty()) {
            lines.add(Line.text("§8" + hb("hb.no_description")));
        }
    }

    private static void statLine(List<Line> lines, StatBlock mods) {
        if (mods.equals(StatBlock.ZERO)) return;
        StringBuilder sb = new StringBuilder("§7" + hb("hb.stats") + ": §f");
        boolean first = true;
        for (Stat stat : Stat.values()) {
            int v = mods.get(stat);
            if (v == 0) continue;
            if (!first) sb.append(", ");
            sb.append(stat.name()).append(v > 0 ? " +" : " ").append(v);
            first = false;
        }
        lines.add(Line.text(sb.toString()));
    }

    private static void craftRuleLines(List<Line> lines, com.sablednah.legendquest.data.CraftRules rules) {
        List<String> barred = new ArrayList<>();
        if (!rules.crafting()) barred.add(hb("hb.station_crafting"));
        if (!rules.smelting()) barred.add(hb("hb.station_smelting"));
        if (!rules.brewing()) barred.add(hb("hb.station_brewing"));
        if (!rules.enchanting()) barred.add(hb("hb.station_enchanting"));
        if (!rules.repairing()) barred.add(hb("hb.station_repair"));
        if (!rules.taming()) barred.add(hb("hb.station_taming"));
        if (!barred.isEmpty()) {
            lines.add(Line.text("§7" + hbf("hb.stations_barred", "list", String.join(", ", barred))));
        }
    }

    private static void boonLines(List<Line> lines, com.sablednah.legendquest.data.Boons boons) {
        boons.attributes().forEach((id, bonus) -> {
            Identifier attrId = Identifier.tryParse(id);
            String name = attrId == null ? id : prettify(attrId.getPath());
            String ramp = bonus.perLevel() != 0
                    ? " §8(+" + trim(bonus.perLevel()) + "/level)" : "";
            lines.add(Line.text("§d" + hb("hb.boon") + ": §f" + signed(bonus.base()) + " " + name + ramp));
        });
        if (boons.enchantRebate() > 0) {
            lines.add(Line.text("§d" + hb("hb.boon") + ": §f"
                    + hbf("hb.boon_enchant", "levels", boons.enchantRebate())));
        }
        if (boons.smithRefund() > 0) {
            lines.add(Line.text("§d" + hb("hb.boon") + ": §f"
                    + hbf("hb.boon_smith", "pct", Math.round(boons.smithRefund() * 100))));
        }
        if (boons.goldToolMana() > 0) {
            lines.add(Line.text("§d" + hb("hb.boon") + ": §f"
                    + hbf("hb.boon_goldtool", "mana", trim(boons.goldToolMana()))));
        }
    }

    private static void grantLines(List<Line> lines, Map<Identifier, SkillGrant> skills) {
        if (skills.isEmpty()) return;
        lines.add(Line.text(""));
        lines.add(Line.text("§6" + hb("hb.skills_header")));
        skills.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingInt(SkillGrant::level)))
                .forEach(entry -> lines.add(Line.link(
                        "  ▸ " + prettify(entry.getKey().getPath()) + grantSuffix(entry.getValue()),
                        "skill", entry.getKey().toString())));
    }

    private static String grantSuffix(SkillGrant grant) {
        if (grant.level() <= 0 && grant.cost() <= 0 && !grant.hasKarmaBand()) {
            return " §8(" + hb("hb.from_start") + ")";
        }
        StringBuilder sb = new StringBuilder(" §8(");
        boolean first = true;
        if (grant.level() > 0) { sb.append(hbf("hb.grant_level", "level", grant.level())); first = false; }
        if (grant.cost() > 0) {
            sb.append(first ? "" : ", ").append(hbf("hb.grant_sp", "cost", grant.cost()));
            first = false;
        }
        if (grant.hasKarmaBand()) sb.append(first ? "" : ", ").append(karmaBand(grant.karmaMin(), grant.karmaMax()));
        return sb.append(")").toString();
    }

    /** "karma ≥ 50" / "karma ≤ -50" / "karma 0..100" — soul requirements. */
    private static String karmaBand(long min, long max) {
        if (min != Long.MIN_VALUE && max != Long.MAX_VALUE) return hbf("hb.karma_between", "min", min, "max", max);
        if (min != Long.MIN_VALUE) return hbf("hb.karma_at_least", "value", min);
        return hbf("hb.karma_at_most", "value", max);
    }

    // --- item rules + gear pages ---

    private static void itemRuleLines(List<Line> lines, ItemRules rules, Map<String, Entry> gear) {
        ruleLine(lines, hb("hb.weapons"), rules.allowedWeapons(), false, gear);
        ruleLine(lines, hb("hb.weapons"), rules.disallowedWeapons(), true, gear);
        ruleLine(lines, hb("hb.armour"), rules.allowedArmour(), false, gear);
        ruleLine(lines, hb("hb.armour"), rules.disallowedArmour(), true, gear);
        ruleLine(lines, hb("hb.tools"), rules.allowedTools(), false, gear);
        ruleLine(lines, hb("hb.tools"), rules.disallowedTools(), true, gear);
    }

    private static void ruleLine(List<Line> lines, String label,
            Optional<HolderSet<Item>> maybeSet, boolean barred, Map<String, Entry> gear) {
        if (maybeSet.isEmpty()) return;
        HolderSet<Item> set = maybeSet.get();
        String verb = barred ? " §c" + hb("hb.barred") + "§7: " : ": ";
        var tagKey = set.unwrapKey();
        if (tagKey.isPresent()) {
            String gearId = gearPage(gear, set);
            String pretty = prettify(tagKey.get().location().getPath());
            lines.add(new Line("§7" + label + verb + (barred ? "§c" : "§a") + pretty + " §8→",
                    "", "gear", gearId));
        } else {
            lines.add(Line.text("§7" + label + verb + (barred ? "§c" : "§a") + describeList(set)));
        }
    }

    /** Register (once) a gear page listing a tag's actual items, icons and all. */
    private static String gearPage(Map<String, Entry> gear, HolderSet<Item> set) {
        var tagKey = set.unwrapKey().orElseThrow();
        String id = "#" + tagKey.location();
        gear.computeIfAbsent(id, key -> {
            List<Line> lines = new ArrayList<>();
            lines.add(Line.text("§7" + hbf("hb.tag_covers", "tag", tagKey.location())));
            lines.add(Line.text(""));
            String iconId = "";
            for (Holder<Item> holder : set) {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(holder.value());
                lines.add(Line.icon("§f" + itemName(holder.value()), itemId.toString()));
                if (iconId.isEmpty()) iconId = itemId.toString();
            }
            if (lines.size() == 2) lines.add(Line.text("§8" + hb("hb.empty_tag")));
            return new Entry(key, prettify(tagKey.location().getPath()), iconId, 0, lines);
        });
        return id;
    }

    private static String describeList(HolderSet<Item> set) {
        List<String> names = new ArrayList<>();
        for (Holder<Item> holder : set) {
            names.add(itemName(holder.value()));
            if (names.size() >= 8) {
                names.add("…");
                break;
            }
        }
        return String.join(", ", names);
    }

    private static String itemName(Item item) {
        return new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
    }

    private static String prettify(String path) {
        return path.replace('_', ' ');
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static String signed(double value) {
        return (value > 0 ? "+" : "") + trim(value);
    }

    /** "+6 (+0.3/lvl)", or whichever half is non-zero. */
    private static String perLevel(double base, double perLevel) {
        StringBuilder sb = new StringBuilder();
        if (base != 0) sb.append(signed(base));
        if (perLevel != 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("(+").append(trim(perLevel)).append("/lvl)");
        }
        return sb.toString();
    }

    /** hb.* strings render client-side without Feedback, so convert '&' here. */
    private static String hb(String key) {
        return Lang.get(key).replace('&', '\u00a7');
    }

    private static String hbf(String key, Object... kv) {
        return Lang.fmt(key, kv).replace('&', '\u00a7');
    }

    private HandbookSync() {}
}
