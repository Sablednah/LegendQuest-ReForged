#!/usr/bin/env python3
"""Write the shipped advancements to src/main/resources/data/legendquest/advancement/.

Generated rather than hand-written for the same reason ZombieMod generates its own: the list is
data, and a tree of parents is much easier to keep honest in one table than in thirty files that
have to agree about each other. This script asserts every parent exists, which is the check that
stops a silently orphaned branch.

Titles and descriptions are literal text on purpose. A vanilla client has no LegendQuest language
file, so a translation key would reach it as the key.

Every criterion is vanilla's minecraft:impossible and is granted from code by NAME -- see
neoforge/Achievements. The names are public API for pack authors; renaming one breaks their
datapacks.

    python3 scripts/make-advancements.py
"""
import json
import pathlib
import shutil

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/data/legendquest/advancement"

LQ = "legendquest:"

A = []


def item(name):
    return {"id": f"minecraft:{name}"}


def add(path, parent, icon, title, description, criteria, frame="task", hidden=False, xp=0):
    A.append((path, parent, icon, frame, title, description, criteria, hidden, xp))


# ---- root
add("root", None, item("book"), "LegendQuest",
    "Every legend starts as somebody deciding what they are. Choose a race.",
    ["race_chosen"])

# ---- the character line
add("character/class", "root", item("iron_sword"), "Take Up a Calling",
    "Choose a class. What you are is decided; what you do is up to you.", ["class_chosen"])
add("character/level_5", "character/class", item("experience_bottle"), "Blooded",
    "Reach level 5.", ["level/5"])
add("character/level_10", "character/level_5", item("golden_apple"), "Known by Name",
    "Reach level 10.", ["level/10"])
add("character/level_20", "character/level_10", item("diamond"), "Veteran",
    "Reach level 20.", ["level/20"], frame="goal")
add("character/max_level", "character/level_20", item("nether_star"), "The Cap",
    "Reach the highest level this server allows.", ["max_level"], frame="challenge", xp=100)

add("character/skill", "character/class", item("enchanted_book"), "First Trick",
    "Learn a skill. Spend a point, gain an ability.", ["skill_learned"])
add("character/feat", "character/skill", item("totem_of_undying"), "Set in Your Ways",
    "Buy a feat. Some doors open only for the soul that fits them.", ["feat_bought"])
add("character/respec", "character/skill", item("grindstone"), "Second Thoughts",
    "Respec, and buy your character back differently. Levels are the price.", ["respec"])

add("character/mastered", "character/max_level", item("netherite_ingot"), "Master of the Craft",
    "Take a class all the way to the cap.", ["class_mastered"], frame="challenge", xp=100)
add("character/all_classes", "character/mastered", item("beacon"), "Jack of All Trades",
    "Reach the cap in every class open to you.",
    ["classes_all_mastered"], frame="challenge", hidden=True, xp=500)
add("character/all_races", "root", item("player_head"), "Many Lives",
    "Play every race open to you. Not in one lifetime, obviously.",
    ["races_all"], frame="challenge", hidden=True, xp=250)

# ---- karma, both ways
add("karma/bright", "root", item("glowstone_dust"), "Well Thought Of",
    "Do enough good to be spoken well of.", ["karma_bright/100"])
add("karma/saint", "karma/bright", item("beacon"), "Saintly",
    "Do a great deal of good, consistently, where anyone could see.",
    ["karma_bright/1000"], frame="goal")
add("karma/dark", "root", item("wither_rose"), "Ill Spoken Of",
    "Do enough harm that people have started to mention it.", ["karma_dark/100"])
add("karma/villain", "karma/dark", item("dragon_head"), "The Villain of the Piece",
    "Go a very long way down. Somebody has to be the reason for the quest.",
    ["karma_dark/1000"], frame="goal")

# ---- parties
add("party/joined", "root", item("lead"), "Better Together",
    "Be in a party with somebody else.", ["party_joined"])
add("party/gathered", "party/joined", item("ender_pearl"), "Gather the Party",
    "Pull your party to you with /party tp.", ["party_gathered"])
add("party/legends", "party/gathered", item("enchanted_golden_apple"), "A Party of Legends",
    "Be in a party where every member online has reached the cap.",
    ["party_legends"], frame="challenge", hidden=True, xp=500)


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    paths = {a[0] for a in A}
    for path, parent, icon, frame, title, description, criteria, hidden, xp in A:
        assert parent is None or parent in paths, f"{path}: no such parent {parent}"
        display = {
            "icon": icon,
            "title": {"text": title},
            "description": {"text": description},
            "frame": frame,
            "show_toast": True,
            # Chat announcements are the server's to decide, through the vanilla gamerule.
            "announce_to_chat": path != "root",
            "hidden": hidden,
        }
        body = {}
        if parent is None:
            display["background"] = "minecraft:gui/advancements/backgrounds/adventure"
        else:
            body["parent"] = LQ + parent
        body["display"] = display
        # Every criterion is granted by the mod, by name. See neoforge/Achievements.
        body["criteria"] = {LQ + c: {"trigger": "minecraft:impossible"} for c in criteria}
        body["requirements"] = [[LQ + c] for c in criteria]
        if xp:
            body["rewards"] = {"experience": xp}
        body["sends_telemetry_event"] = False
        file = OUT / f"{path}.json"
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(json.dumps(body, indent=2) + "\n")
    print(f"{len(A)} advancements -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
