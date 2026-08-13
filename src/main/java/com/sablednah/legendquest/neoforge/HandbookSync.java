package com.sablednah.legendquest.neoforge;

import java.util.ArrayList;
import java.util.Comparator;
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
 */
public final class HandbookSync {

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, build(player));
    }

    private static HandbookPayload build(ServerPlayer player) {
        var access = player.level().registryAccess();
        var raceLookup = access.lookupOrThrow(LQRegistries.RACE);
        var classLookup = access.lookupOrThrow(LQRegistries.CHAR_CLASS);
        var skillLookup = access.lookupOrThrow(LQRegistries.SKILL);

        List<Entry> races = new ArrayList<>();
        raceLookup.listElements()
                .sorted(Comparator.comparing(ref -> ref.value().name()))
                .forEach(ref -> races.add(racePage(ref.key().identifier(), ref.value(), classLookup)));

        List<Entry> classes = new ArrayList<>();
        classLookup.listElements()
                .sorted(Comparator.comparing(ref -> ref.value().name()))
                .forEach(ref -> classes.add(classPage(ref.key().identifier(), ref.value(), raceLookup)));

        List<Entry> skills = new ArrayList<>();
        skillLookup.listElements()
                .sorted(Comparator.comparing(ref -> ref.value().name()))
                .forEach(ref -> skills.add(skillPage(ref.key().identifier(), ref.value(),
                        raceLookup, classLookup)));

        return new HandbookPayload(races, classes, skills);
    }

    // --- race pages ---

    private static Entry racePage(Identifier id, Race race,
            net.minecraft.core.HolderLookup.RegistryLookup<CharClass> classLookup) {
        List<Line> lines = new ArrayList<>();
        description(lines, race.identity().description(), race.identity().longDescription());
        if (race.isDefault()) lines.add(Line.text("§8The starting race, until you choose."));
        lines.add(Line.text(""));
        lines.add(Line.text("§7Health §f" + trim(race.baseHealth())
                + " §7· Mana §f" + trim(race.baseMana())
                + " §7(+" + trim(race.manaPerSecond()) + "/s) §7· Size §f" + trim(race.size())));
        statLine(lines, race.statmods());
        if (!race.groups().isEmpty()) {
            lines.add(Line.text("§7Groups: §f" + String.join(", ", race.groups())));
        }
        itemRuleLines(lines, race.itemRules());
        boonLines(lines, race.boons());
        grantLines(lines, race.skills());

        // Which classes will have this race?
        List<Line> classLines = new ArrayList<>();
        classLookup.listElements().forEach(ref -> {
            CharClass c = ref.value();
            if (c.isDefault()) return;
            var el = c.eligibility();
            boolean open = (el.allowedRaces().isEmpty() && el.allowedGroups().isEmpty())
                    || el.allowedRaces().contains(id)
                    || race.groups().stream().anyMatch(el.allowedGroups()::contains);
            if (open) {
                classLines.add(new Line("  ▸ " + c.name(), "class", ref.key().identifier().toString()));
            }
        });
        if (!classLines.isEmpty()) {
            lines.add(Line.text(""));
            lines.add(Line.text("§6Open classes:"));
            lines.addAll(classLines);
        }
        return new Entry(id.toString(), race.name(), "", lines);
    }

    // --- class pages ---

    private static Entry classPage(Identifier id, CharClass charClass,
            net.minecraft.core.HolderLookup.RegistryLookup<Race> raceLookup) {
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
        if (!el.allowedRaces().isEmpty() || !el.allowedGroups().isEmpty()) {
            lines.add(Line.text("§6Open to:"));
            for (Identifier raceId : el.allowedRaces()) {
                raceLookup.get(net.minecraft.resources.ResourceKey.create(LQRegistries.RACE, raceId))
                        .ifPresent(r -> lines.add(new Line("  ▸ " + r.value().name(),
                                "race", raceId.toString())));
            }
            if (!el.allowedGroups().isEmpty()) {
                lines.add(Line.text("  §7…and any " + String.join(", ", el.allowedGroups())));
            }
        } else {
            lines.add(Line.text("§7Open to every race."));
        }
        if (!el.requires().isEmpty() || !el.requiresOne().isEmpty()) {
            lines.add(Line.text("§6Requires mastering:"));
            for (Identifier req : el.requires()) {
                lines.add(new Line("  ▸ " + prettify(req.getPath()), "class", req.toString()));
            }
            if (!el.requiresOne().isEmpty()) {
                lines.add(Line.text("  §7one of:"));
                for (Identifier req : el.requiresOne()) {
                    lines.add(new Line("  ▸ " + prettify(req.getPath()), "class", req.toString()));
                }
            }
        }
        itemRuleLines(lines, charClass.itemRules());
        boonLines(lines, charClass.boons());
        grantLines(lines, charClass.skills());
        return new Entry(id.toString(), charClass.name(), "", lines);
    }

    // --- skill pages ---

    private static Entry skillPage(Identifier id, com.sablednah.legendquest.data.SkillDefinition def,
            net.minecraft.core.HolderLookup.RegistryLookup<Race> raceLookup,
            net.minecraft.core.HolderLookup.RegistryLookup<CharClass> classLookup) {
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
                sources.add(new Line("  ▸ " + ref.value().name() + grantSuffix(grant),
                        "race", ref.key().identifier().toString()));
            }
        });
        classLookup.listElements().forEach(ref -> {
            SkillGrant grant = ref.value().skills().get(id);
            if (grant != null) {
                sources.add(new Line("  ▸ " + ref.value().name() + grantSuffix(grant),
                        "class", ref.key().identifier().toString()));
            }
        });
        if (!sources.isEmpty()) {
            lines.add(Line.text(""));
            lines.add(Line.text("§6Taught by:"));
            lines.addAll(sources);
        }
        return new Entry(id.toString(), def.name(), def.icon(), lines);
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

    private static void grantLines(List<Line> lines, Map<Identifier, SkillGrant> skills) {
        if (skills.isEmpty()) return;
        lines.add(Line.text(""));
        lines.add(Line.text("§6Skills:"));
        skills.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingInt(SkillGrant::level)))
                .forEach(entry -> lines.add(new Line(
                        "  ▸ " + prettify(entry.getKey().getPath()) + grantSuffix(entry.getValue()),
                        "skill", entry.getKey().toString())));
    }

    private static String grantSuffix(SkillGrant grant) {
        if (grant.level() <= 0 && grant.cost() <= 0) return " §8(from the start)";
        StringBuilder sb = new StringBuilder(" §8(");
        if (grant.level() > 0) sb.append("level ").append(grant.level());
        if (grant.cost() > 0) sb.append(grant.level() > 0 ? ", " : "").append(grant.cost()).append(" sp");
        return sb.append(")").toString();
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
    }

    private static void itemRuleLines(List<Line> lines, ItemRules rules) {
        ruleLine(lines, "Weapons", rules.allowedWeapons(), rules.disallowedWeapons());
        ruleLine(lines, "Armour", rules.allowedArmour(), rules.disallowedArmour());
        ruleLine(lines, "Tools", rules.allowedTools(), rules.disallowedTools());
    }

    private static void ruleLine(List<Line> lines, String label,
            Optional<HolderSet<Item>> allowed, Optional<HolderSet<Item>> disallowed) {
        allowed.ifPresent(set ->
                lines.add(Line.text("§7" + label + ": §a" + describeSet(set))));
        disallowed.ifPresent(set ->
                lines.add(Line.text("§7" + label + " §cbarred§7: " + describeSet(set))));
    }

    /** A tag prints as its prettified name; a direct list prints item names. */
    private static String describeSet(HolderSet<Item> set) {
        var tagKey = set.unwrapKey();
        if (tagKey.isPresent()) {
            return prettify(tagKey.get().location().getPath());
        }
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
