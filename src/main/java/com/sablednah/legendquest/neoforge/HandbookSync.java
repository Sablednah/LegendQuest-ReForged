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

        List<Entry> skills = new ArrayList<>();
        skillLookup.listElements()
                .sorted(Comparator.comparing(ref -> ref.value().name()))
                .forEach(ref -> skills.add(skillPage(ref.key().identifier(), ref.value(),
                        raceLookup, classLookup)));

        var featLookup = access.lookupOrThrow(LQRegistries.FEAT);
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
        lines.add(Line.text("§7Cost: §f" + feat.cost() + " skill points"
                + (feat.level() > 0 ? " §7· needs level §f" + feat.level() : "")));
        if (feat.hasKarmaBand()) {
            lines.add(Line.text("§7Soul: §f" + karmaBand(feat.karmaMin(), feat.karmaMax())
                    + " §8(drifting out of band suspends the feat)"));
        }
        if (!feat.requires().isEmpty()) {
            lines.add(Line.text("§6Requires the feat" + (feat.requires().size() > 1 ? "s" : "") + ":"));
            for (Identifier req : feat.requires()) {
                String name = featLookup.get(net.minecraft.resources.ResourceKey
                        .create(LQRegistries.FEAT, req))
                        .map(r -> r.value().name()).orElse(prettify(req.getPath()));
                lines.add(Line.link("  ▸ " + name, "feat", req.toString()));
            }
        }
        if (!feat.allowedRaces().isEmpty() || !feat.allowedGroups().isEmpty()) {
            lines.add(Line.text("§6Only for:"));
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
            lines.add(Line.text("§6Only for the calling" + (feat.allowedClasses().size() > 1 ? "s" : "") + ":"));
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
        if (race.isDefault()) lines.add(Line.text("§8The starting race, until you choose."));
        lines.add(Line.text(""));
        lines.add(Line.text("§7Health §f" + trim(race.baseHealth())
                + " §7· Mana §f" + trim(race.baseMana())
                + " §7(+" + trim(race.manaPerSecond()) + "/s) §7· Size §f" + trim(race.size())));
        statLine(lines, race.statmods());
        if (!race.groups().isEmpty()) {
            lines.add(Line.text("§7Lineage: §f" + String.join(", ", race.groups())));
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
            lines.add(Line.text("§6Open classes:"));
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
        if (charClass.isDefault()) lines.add(Line.text("§8The starting class, until you choose."));
        lines.add(Line.text(""));

        var growth = charClass.growth();
        StringBuilder vitals = new StringBuilder("§7");
        if (growth.healthMod() != 0) vitals.append("Health ").append(signed(growth.healthMod())).append(" ");
        if (growth.healthPerLevel() != 0) vitals.append("(+").append(trim(growth.healthPerLevel())).append("/lvl) ");
        if (growth.manaBonus() != 0) vitals.append("· Mana ").append(signed(growth.manaBonus())).append(" ");
        if (growth.manaPerLevel() != 0) vitals.append("(+").append(trim(growth.manaPerLevel())).append("/lvl) ");
        if (growth.manaPerSecond() != 0) vitals.append("· Regen ").append(signed(growth.manaPerSecond())).append("/s ");
        if (growth.speedMod() != 0) vitals.append("· Speed ").append(signed(growth.speedMod()));
        if (vitals.length() > 2) lines.add(Line.text(vitals.toString().strip()));
        statLine(lines, charClass.statmods());

        var el = charClass.eligibility();
        if (el.mainOnly()) lines.add(Line.text("§7Main class only."));
        if (el.subOnly()) lines.add(Line.text("§7Sub class only."));

        // "Open to" resolves groups to the actual races — clickable, no jargon.
        if (!el.allowedRaces().isEmpty() || !el.allowedGroups().isEmpty()) {
            lines.add(Line.text("§6Open to:"));
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
                lines.add(Line.text("  §8— the " + String.join(", ", el.allowedGroups())
                        + " lineage" + (el.allowedGroups().size() > 1 ? "s" : "")));
            }
        } else {
            lines.add(Line.text("§7Open to every race."));
        }
        if (!el.requires().isEmpty() || !el.requiresOne().isEmpty()) {
            lines.add(Line.text("§6Requires mastering:"));
            for (Identifier req : el.requires()) {
                lines.add(Line.link("  ▸ " + prettify(req.getPath()), "class", req.toString()));
            }
            if (!el.requiresOne().isEmpty()) {
                lines.add(Line.text("  §7one of:"));
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

    // --- skill pages ---

    private static Entry skillPage(Identifier id, com.sablednah.legendquest.data.SkillDefinition def,
            HolderLookup.RegistryLookup<Race> raceLookup,
            HolderLookup.RegistryLookup<CharClass> classLookup) {
        List<Line> lines = new ArrayList<>();
        description(lines, def.description(), Optional.empty());
        lines.add(Line.text(""));
        lines.add(Line.text("§7Type: §f" + def.type().name().toLowerCase(Locale.ROOT)));

        var costs = def.costs();
        StringBuilder cost = new StringBuilder("§7");
        if (costs.manaCost() > 0) cost.append("Mana ").append(costs.manaCost()).append(" ");
        if (def.timing().cooldownMs() > 0) cost.append("· Cooldown ").append(def.timing().cooldownMs() / 1000).append("s ");
        if (def.timing().buildupMs() > 0) cost.append("· Buildup ").append(def.timing().buildupMs() / 1000.0).append("s ");
        if (def.timing().delayMs() > 0) cost.append("· Delay ").append(def.timing().delayMs() / 1000.0).append("s ");
        if (def.timing().durationMs() > 0) cost.append("· Lasts ").append(def.timing().durationMs() / 1000.0).append("s");
        if (cost.length() > 2) lines.add(Line.text(cost.toString().strip()));
        costs.consumes().ifPresent(item -> lines.add(Line.text("§7Consumes: §f"
                + costs.consumesQty() + "× " + itemName(item))));
        if (costs.karmaRequired() != 0) {
            lines.add(Line.text("§7Karma: needs " + (costs.karmaRequired() > 0 ? "at least " : "at most ")
                    + costs.karmaRequired()));
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
            lines.add(Line.text("§6Taught by:"));
            lines.addAll(sources);
        }
        return new Entry(id.toString(), def.name(), def.icon(), 0, lines);
    }

    // --- shared formatting ---

    private static void description(List<Line> lines, Optional<String> desc, Optional<String> longDesc) {
        desc.ifPresent(d -> lines.add(Line.text("§f" + d)));
        longDesc.ifPresent(d -> lines.add(Line.text("§7" + d)));
        if (desc.isEmpty() && longDesc.isEmpty()) {
            lines.add(Line.text("§8(No description — add one in the YAML!)"));
        }
    }

    private static void statLine(List<Line> lines, StatBlock mods) {
        if (mods.equals(StatBlock.ZERO)) return;
        StringBuilder sb = new StringBuilder("§7Stats: §f");
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
        if (!rules.crafting()) barred.add("crafting");
        if (!rules.smelting()) barred.add("smelting");
        if (!rules.brewing()) barred.add("brewing");
        if (!rules.enchanting()) barred.add("enchanting");
        if (!rules.repairing()) barred.add("repair");
        if (!rules.taming()) barred.add("taming");
        if (!barred.isEmpty()) {
            lines.add(Line.text("§7Stations §cbarred§7: " + String.join(", ", barred)));
        }
    }

    private static void boonLines(List<Line> lines, com.sablednah.legendquest.data.Boons boons) {
        boons.attributes().forEach((id, bonus) -> {
            Identifier attrId = Identifier.tryParse(id);
            String name = attrId == null ? id : prettify(attrId.getPath());
            String ramp = bonus.perLevel() != 0
                    ? " §8(+" + trim(bonus.perLevel()) + "/level)" : "";
            lines.add(Line.text("§dBoon: §f" + signed(bonus.base()) + " " + name + ramp));
        });
        if (boons.enchantRebate() > 0) {
            lines.add(Line.text("§dBoon: §fenchanting rebates " + boons.enchantRebate()
                    + " XP level" + (boons.enchantRebate() == 1 ? "" : "s")));
        }
        if (boons.smithRefund() > 0) {
            lines.add(Line.text("§dBoon: §f" + Math.round(boons.smithRefund() * 100)
                    + "% chance to recover a material when crafting gear"));
        }
        if (boons.goldToolMana() > 0) {
            lines.add(Line.text("§dBoon: §fgolden tools harvest like netherite, burning "
                    + trim(boons.goldToolMana()) + " mana per block"));
        }
    }

    private static void grantLines(List<Line> lines, Map<Identifier, SkillGrant> skills) {
        if (skills.isEmpty()) return;
        lines.add(Line.text(""));
        lines.add(Line.text("§6Skills:"));
        skills.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingInt(SkillGrant::level)))
                .forEach(entry -> lines.add(Line.link(
                        "  ▸ " + prettify(entry.getKey().getPath()) + grantSuffix(entry.getValue()),
                        "skill", entry.getKey().toString())));
    }

    private static String grantSuffix(SkillGrant grant) {
        if (grant.level() <= 0 && grant.cost() <= 0 && !grant.hasKarmaBand()) {
            return " §8(from the start)";
        }
        StringBuilder sb = new StringBuilder(" §8(");
        boolean first = true;
        if (grant.level() > 0) { sb.append("level ").append(grant.level()); first = false; }
        if (grant.cost() > 0) {
            sb.append(first ? "" : ", ").append(grant.cost()).append(" sp");
            first = false;
        }
        if (grant.hasKarmaBand()) sb.append(first ? "" : ", ").append(karmaBand(grant.karmaMin(), grant.karmaMax()));
        return sb.append(")").toString();
    }

    /** "karma ≥ 50" / "karma ≤ -50" / "karma 0..100" — soul requirements. */
    private static String karmaBand(long min, long max) {
        if (min != Long.MIN_VALUE && max != Long.MAX_VALUE) return "karma " + min + "…" + max;
        if (min != Long.MIN_VALUE) return "karma ≥ " + min;
        return "karma ≤ " + max;
    }

    // --- item rules + gear pages ---

    private static void itemRuleLines(List<Line> lines, ItemRules rules, Map<String, Entry> gear) {
        ruleLine(lines, "Weapons", rules.allowedWeapons(), false, gear);
        ruleLine(lines, "Weapons", rules.disallowedWeapons(), true, gear);
        ruleLine(lines, "Armour", rules.allowedArmour(), false, gear);
        ruleLine(lines, "Armour", rules.disallowedArmour(), true, gear);
        ruleLine(lines, "Tools", rules.allowedTools(), false, gear);
        ruleLine(lines, "Tools", rules.disallowedTools(), true, gear);
    }

    private static void ruleLine(List<Line> lines, String label,
            Optional<HolderSet<Item>> maybeSet, boolean barred, Map<String, Entry> gear) {
        if (maybeSet.isEmpty()) return;
        HolderSet<Item> set = maybeSet.get();
        String verb = barred ? " §cbarred§7: " : ": ";
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
            lines.add(Line.text("§7Everything the tag §f" + tagKey.location() + "§7 covers:"));
            lines.add(Line.text(""));
            String iconId = "";
            for (Holder<Item> holder : set) {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(holder.value());
                lines.add(Line.icon("§f" + itemName(holder.value()), itemId.toString()));
                if (iconId.isEmpty()) iconId = itemId.toString();
            }
            if (lines.size() == 2) lines.add(Line.text("§8(empty tag)"));
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

    private HandbookSync() {}
}
