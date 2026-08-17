#!/usr/bin/env bash
# Zip the genre packs in packs/ into release-ready datapack archives.
#
# Usage: ./build-packs.sh [outdir]     (default outdir: build/packs)
#
# The one rule that matters: pack.mcmeta and data/ must sit at the ZIP ROOT.
# Zip the *containing folder* instead and Minecraft silently ignores the pack --
# no error, no log line, the content just never appears. Every archive is
# verified for that before this script will call it a success.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$ROOT/build/packs}"

command -v zip >/dev/null || { echo "!! 'zip' is not installed." >&2; exit 1; }

mkdir -p "$OUT"

built=0
for dir in "$ROOT"/packs/*/; do
    name="$(basename "$dir")"
    [ -f "$dir/pack.mcmeta" ] || { echo ">> skipping $name (no pack.mcmeta)"; continue; }

    zipfile="$OUT/legendquest-$name.zip"
    rm -f "$zipfile"

    # cd into the pack so paths are stored relative to it -> content at the root.
    # -x excludes the authoring files: the README and the vocabulary snippet are
    # for humans, and messages-snippet.yml is pasted into config, never loaded.
    ( cd "$dir" && zip -qr "$zipfile" . -x "README.md" "messages-snippet.yml" ".*" )

    # Verify the layout rather than trusting it. Listed once and held in a
    # variable: piping into `grep -q` under `pipefail` makes grep exit on the
    # first match, unzip take SIGPIPE, and the whole check report a false
    # failure.
    listing="$(unzip -l "$zipfile")"
    if ! printf '%s\n' "$listing" | grep -E '^ *[0-9]+ +\S+ +\S+ +pack\.mcmeta$' > /dev/null; then
        echo "!! $name: pack.mcmeta is NOT at the zip root -- Minecraft would ignore this pack." >&2
        printf '%s\n' "$listing" | head -20 >&2
        exit 1
    fi
    if ! printf '%s\n' "$listing" | grep -E ' data/' > /dev/null; then
        echo "!! $name: no data/ directory at the zip root." >&2
        exit 1
    fi

    entries=$(printf '%s\n' "$listing" | tail -1 | awk '{print $2}')
    size=$(stat -c%s "$zipfile")
    echo ">> $(basename "$zipfile")  ($size bytes, $entries entries, pack.mcmeta at root OK)"
    built=$((built + 1))
done

[ "$built" -gt 0 ] || { echo "!! No packs built." >&2; exit 1; }
echo ">> $built pack(s) written to $OUT"
