#!/usr/bin/env bash
# Build LegendQuest ReForged and copy the jar into a CurseForge instance.
#
# The target instance is chosen from the jar's Minecraft version, so it follows
# whichever version branch is checked out:
#
#   legendquest-2.2.0.jar            -> a 1.21.11 instance  (default: fantasy)
#   legendquest-2.2.0+mc26.1.2.jar   -> the "26.1.2" instance
#   legendquest-2.2.0+mc26.2.jar     -> the "26.2" instance
#
# Usage:  ./deploy.sh
#         LQ_INSTANCE="/path/to/instance" ./deploy.sh    # override the target
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
INSTANCES="/mnt/c/Users/darre/curseforge/minecraft/Instances"

# 26.x needs Java 25 and 1.21.11 needs 21, but the gradle toolchain provisions
# whatever the build asks for, so any JDK that can launch gradle will do.
for candidate in "$ROOT/tools/jdk21" \
                 "/home/sable/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
                 "$ROOT/../MobHealth-Forge/tools/jdk21"; do
    if [ -x "$candidate/bin/java" ]; then export JAVA_HOME="$candidate"; break; fi
done
if [ -z "${JAVA_HOME:-}" ]; then
    echo "!! No JDK found. There is no system Java on this machine." >&2
    exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

echo ">> Building (JAVA_HOME=$JAVA_HOME)..."
"$ROOT/gradlew" build --console=plain -q

JAR="$(ls -t "$ROOT"/build/libs/legendquest-*.jar 2>/dev/null | grep -v -- '-sources' | head -1 || true)"
[ -n "$JAR" ] || { echo "!! No built jar in build/libs" >&2; exit 1; }
JARNAME="$(basename "$JAR")"

# Pick the instance from the jar's +mcX.Y tag. That tag exists precisely so
# three same-named jars cannot be confused with each other, so it may as well
# do the routing too.
#
# The 26.x instances are named after their version, so they map directly. The
# 1.21.11 line has three instances (fantasy, wasteland, sci-fi) named after
# their CONTENT rather than their version, so it needs a mapping; the fantasy
# one is the default and LQ_INSTANCE picks either of the others.
MC_TAG=""
[[ "$JARNAME" =~ \+mc([0-9.]+)\.jar$ ]] && MC_TAG="${BASH_REMATCH[1]}"
case "$MC_TAG" in
    ""|1.21.11) TARGET="$INSTANCES/MobHealth - Forge" ;;  # stock fantasy
    *)          TARGET="$INSTANCES/$MC_TAG" ;;
esac
INSTANCE="${LQ_INSTANCE:-$TARGET}"
MODS="$INSTANCE/mods"
NAME="$(basename "$INSTANCE")"

[ -d "$MODS" ] || { echo "!! Instance mods folder not found: $MODS" >&2; exit 1; }

# ---------------------------------------------------------------------------
# REFUSE if that instance is running.
#
# Windows does NOT lock the jar, so the copy silently succeeds and the running
# JVM then dies the moment it lazily loads a class it had not already touched:
# NoClassDefFoundError under a ZipException about an invalid LOC header, with a
# perfectly good jar sitting on disk. It reads as a mod bug and is not one.
#
# So this has to be an active check that stops the deploy. A guard that only
# prints a warning is not a guard -- that exact mistake has been made here
# before, by an earlier version of this script whose comment claimed Windows
# would refuse the write for us.
# ---------------------------------------------------------------------------
# The character class must NOT exclude whitespace. Seven instances carry this
# mod and two are named with spaces ("MobHealth - Forge", "Standards"), so a
# \s in there truncates the name at the first space, compares "MobHealth"
# against the folder "MobHealth - Forge", never matches, and the guard silently
# passes while the game is running. That was the state of this script until
# 2026-09-07; what stood in for the guard was `set -e` aborting on the rm below
# failing with "Permission denied" -- luck, not a check. Let the name run to
# the next backslash or quote.
RUNNING="$(powershell.exe -NoProfile -Command \
  "Get-CimInstance Win32_Process | Where-Object { \$_.Name -like 'java*' } | ForEach-Object { \
   \$m=[regex]::Match(\$_.CommandLine,'Instances\\\\([^\\\\\"]+)'); if (\$m.Success) { \$m.Groups[1].Value } }" \
  2>/dev/null | tr -d '\r' | sort -u || true)"

if echo "$RUNNING" | grep -qxF "$NAME"; then
    echo "!! '$NAME' is RUNNING. Refusing to overwrite a jar underneath a live game." >&2
    echo "!! Close Minecraft and run this again." >&2
    exit 1
fi

# Say which build is being replaced, and by which. Two jars can carry the same
# filename AND the same version and still differ -- that has happened here, and
# the version string is no help at all when it does. The script itself cannot
# get this wrong (it removes and copies unconditionally, with no comparison to
# fumble); what it guards against is a PERSON, or an agent, deciding to skip a
# deploy because "it already says 2.5.0". The stamp makes that judgement
# checkable afterwards -- printing it here makes it visible before.
#
# Both helpers below are written around `set -euo pipefail`, which is hostile to
# the two cases that matter most here. A pipeline whose FIRST element fails
# fails the whole pipeline under pipefail, and a failed command substitution in
# an assignment then exits the script under -e, with nothing printed. So:
#   ls glob | head   dies when there is no jar yet -- a FIRST deploy into an
#                    instance, the one run where this code has something to say.
#   unzip -p | sed   dies when the jar has no build.properties -- a pre-stamp
#                    jar, which is most of the instances right now.
# Both are silent, and both pass every test that uses a populated instance and a
# current jar. Caught by MobHealth's session hitting the first one; the second
# was sitting beside it. A glob loop has no pipeline to fail, and `|| true`
# keeps a missing entry from being fatal.
stampof() {
    local out
    out=$(unzip -p "$1" legendquest/build.properties 2>/dev/null || true)
    printf '%s' "$out" | sed -n 's/^commit=//p' | head -1 || true
}
OLDJAR=""
for f in "$MODS"/legendquest-*.jar; do
    if [ -f "$f" ]; then OLDJAR="$f"; break; fi
done
if [ -n "$OLDJAR" ]; then
    OLDSTAMP=$(stampof "$OLDJAR")
    echo ">> Replacing $(basename "$OLDJAR") [build ${OLDSTAMP:-none, predates stamps}]"
else
    echo ">> No existing LegendQuest jar in '$NAME' (first deploy here)"
fi

echo ">> Removing previous LegendQuest jars from '$NAME'..."
rm -f "$MODS"/legendquest-*.jar

cp "$JAR" "$MODS/"

# A half-written copy looks identical to a good one in a directory listing.
cmp -s "$JAR" "$MODS/$JARNAME" || { echo "!! Deployed jar does not match the build." >&2; exit 1; }
unzip -t "$MODS/$JARNAME" >/dev/null 2>&1 || { echo "!! Deployed jar is not a valid zip." >&2; exit 1; }

echo ">> Deployed $JARNAME ($(stat -c%s "$JAR") bytes) [build $(stampof "$JAR")] to '$NAME'"
echo ">> Launch that instance in CurseForge to test."
