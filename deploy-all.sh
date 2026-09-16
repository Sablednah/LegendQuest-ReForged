#!/usr/bin/env bash
# Put the right jar into EVERY CurseForge instance that already has one.
#
# deploy.sh handles one instance from one build, which is what you want mid-loop.
# This is the other job: after a release, bring the whole estate up to it. You
# cannot build all three from one checkout, so this takes a directory that
# already holds the tagged jars -- typically the release artifacts:
#
#   ./deploy-all.sh /path/to/dir/with/legendquest-2.3.1+mc*.jar
#
# Instances are chosen by "already has a legendquest jar", so this never
# installs the mod somewhere new -- it only updates what is already there.
set -euo pipefail

INSTANCES="${LQ_INSTANCES:-/mnt/c/Users/darre/curseforge/minecraft/Instances}"
BUILT="${1:-}"
[ -n "$BUILT" ] && [ -d "$BUILT" ] || { echo "usage: $0 <dir containing legendquest-*+mc*.jar>" >&2; exit 1; }

# ---------------------------------------------------------------------------
# Route on the instance's OWN gameVersion, not its folder name.
#
# Two of the seven instances carrying LegendQuest are named after a mod
# ("MobHealth - Forge", "Standards") rather than a Minecraft version, and
# guessing from the name gets both wrong. minecraftinstance.json knows.
#
# That file is written UTF-8 WITH BOM, so it must be read as utf-8-sig; plain
# utf-8 fails on the very first character with a json.JSONDecodeError that
# looks like a corrupt file.
# ---------------------------------------------------------------------------
mc_version_of() {
    python3 -c "
import json,sys
try:
    d = json.load(open(sys.argv[1], encoding='utf-8-sig'))
    print(d.get('gameVersion') or d.get('baseModLoader', {}).get('minecraftVersion') or '')
except Exception:
    print('')
" "$1/minecraftinstance.json" 2>/dev/null || true
}

# One process query for the whole sweep rather than one per instance. See
# CLAUDE.md: a warning here is not a guard, this has to stop the deploy.
# The trailing backslash is not decoration. A running instance's own command
# line carries "--gameDir C:\...\Instances\26.2 --assetsDir C:\..." with NO
# separator after the folder, so a pattern that stops at the next backslash
# swallows the rest of the argument and yields "26.2 --assetsDir C:". That never
# equals the folder name, the guard misses, and the script tries to replace a jar
# under a live game (which fails with "Permission denied" on the rm, mid-run --
# seen 2026-09-16). Requiring the trailing backslash matches the deeper paths in
# the same command line instead (natives, libraries), which DO have one, and
# keeps names with spaces whole.
RUNNING_CMDS="$(powershell.exe -NoProfile -Command \
  "Get-CimInstance Win32_Process | Where-Object { \$_.Name -like 'java*' } | ForEach-Object { \$_.CommandLine }" \
  2>/dev/null | tr -d '\r' || true)"

# Ask the question the right way round: we KNOW the folder name, so look for it
# in the running command lines rather than parsing a name out of them. Every
# attempt to READ the name failed the same way -- the launcher's own argument is
#   --gameDir C:\...\Instances\26.2 --assetsDir C:\...
# with no separator after the folder, so the pattern ran on into the next
# argument and produced "26.2 --assetsDir C:", which matches no instance. The
# guard then missed and a deploy started under a live game (2026-09-16): the rm
# failed with "Permission denied" halfway through the estate.
#
# The boundary matters as much as the name: without it "26.2" also matches
# "26.2.test". Names with spaces ("MobHealth - Forge") stay whole either way,
# because nothing here splits on whitespace.
instance_running() {
    local name="$1" padded
    # Fixed strings, no regex: an instance name can hold dots ("26.2") and
    # spaces ("MobHealth - Forge"), and escaping them for grep -E is how the
    # previous attempt died -- its bracket expression opened with "[." , which
    # POSIX reads as a collating symbol, so sed failed and EVERY instance came
    # back "not running". A guard that errors must never read as "safe".
    #
    # Three boundaries are all the launcher can put after the folder name: a
    # deeper path, a closing quote, or the end of the argument. The padding
    # gives that last one something to match.
    padded="$(printf '%s' "$RUNNING_CMDS" | sed 's/$/ /')"
    printf '%s' "$padded" | grep -qF -- "Instances\\$name\\" && return 0
    printf '%s' "$padded" | grep -qF -- "Instances\\$name\"" && return 0
    printf '%s' "$padded" | grep -qF -- "Instances\\$name " && return 0
    return 1
}
echo

fail=0
for dir in "$INSTANCES"/*/; do
    name="$(basename "$dir")"
    mods="$dir/mods"
    ls "$mods"/legendquest-*.jar >/dev/null 2>&1 || continue

    mc="$(mc_version_of "$dir")"
    jar="$(ls "$BUILT"/legendquest-*+mc"$mc".jar 2>/dev/null | head -1 || true)"
    if [ -z "$mc" ]; then
        echo "?? $name: could not read its Minecraft version -- SKIPPED"; fail=1; continue
    fi
    if [ -z "$jar" ]; then
        echo "?? $name: no jar for Minecraft $mc in $BUILT -- SKIPPED"; fail=1; continue
    fi
    if instance_running "$name"; then
        echo "!! $name is RUNNING -- refusing to overwrite underneath a live game"; fail=1; continue
    fi

    was="$(ls "$mods" | grep -i '^legendquest.*\.jar$' | tr '\n' ' ')"
    rm -f "$mods"/legendquest-*.jar
    cp "$jar" "$mods/"
    base="$(basename "$jar")"

    # A half-written copy looks identical to a good one in a directory listing.
    cmp -s "$jar" "$mods/$base"            || { echo "!! $name: copy does not match the source"; fail=1; continue; }
    unzip -t "$mods/$base" >/dev/null 2>&1 || { echo "!! $name: deployed jar is not a valid zip"; fail=1; continue; }
    printf ">> %-30s mc %-8s %s-> %s\n" "$name" "$mc" "$was" "$base"
done

echo
if [ "$fail" -eq 0 ]; then echo "All instances deployed and verified."
else echo "Finished WITH PROBLEMS -- see above."; fi
exit "$fail"
