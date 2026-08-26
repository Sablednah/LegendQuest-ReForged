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
if [[ "$JARNAME" =~ \+mc([0-9.]+)\.jar$ ]]; then
    TARGET="$INSTANCES/${BASH_REMATCH[1]}"
else
    TARGET="$INSTANCES/MobHealth - Forge"   # the stock-fantasy 1.21.11 instance
fi
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
RUNNING="$(powershell.exe -NoProfile -Command \
  "Get-CimInstance Win32_Process | Where-Object { \$_.Name -like 'java*' } | ForEach-Object { \
   \$m=[regex]::Match(\$_.CommandLine,'Instances\\\\([^\\\\\"]+)'); if (\$m.Success) { \$m.Groups[1].Value } }" \
  2>/dev/null | tr -d '\r' | sort -u || true)"

if echo "$RUNNING" | grep -qxF "$NAME"; then
    echo "!! '$NAME' is RUNNING. Refusing to overwrite a jar underneath a live game." >&2
    echo "!! Close Minecraft and run this again." >&2
    exit 1
fi

echo ">> Removing previous LegendQuest jars from '$NAME'..."
rm -f "$MODS"/legendquest-*.jar

cp "$JAR" "$MODS/"

# A half-written copy looks identical to a good one in a directory listing.
cmp -s "$JAR" "$MODS/$JARNAME" || { echo "!! Deployed jar does not match the build." >&2; exit 1; }
unzip -t "$MODS/$JARNAME" >/dev/null 2>&1 || { echo "!! Deployed jar is not a valid zip." >&2; exit 1; }

echo ">> Deployed $JARNAME ($(stat -c%s "$JAR") bytes) to '$NAME'"
echo ">> Launch that instance in CurseForge to test."
