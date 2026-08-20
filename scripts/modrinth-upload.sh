#!/usr/bin/env bash
# Publish one Modrinth version, with ALL of a release's artifacts attached to it.
#
#   MODRINTH_TOKEN=xxx MODRINTH_PROJECT_ID=abc12345 \
#     ./scripts/modrinth-upload.sh <version-number> <changelog-file> <file> [file...]
#
# Normally run for you by .github/workflows/modrinth.yml when a GitHub release is published.
#
# Unlike CurseForge, a Modrinth version holds MANY files of ANY type, so the mod jar, both genre
# pack ZIPs and the example skill-pack jar all live in one version. That is the whole reason the
# packs can be hosted here properly instead of being linked out to GitHub - a CurseForge mod project
# accepts jar/litemod only. See .github/workflows/curseforge.yml for that story.
#
# The first file given is the PRIMARY file: it is what the big download button offers. Pass the mod
# jar first.
#
# Uses python3 rather than jq: jq is not installed on the dev box, and python3 is, so this stays
# runnable locally as well as on a CI runner.
#
# API reference: https://docs.modrinth.com/api/operations/createversion/
set -euo pipefail

API="https://api.modrinth.com/v2"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

VERSION_NUMBER="${1:?usage: modrinth-upload.sh <version-number> <changelog-file> <file> [file...]}"
CHANGELOG_FILE="${2:?missing changelog file}"
shift 2
[ "$#" -gt 0 ] || { echo "!! No files to upload." >&2; exit 1; }

: "${MODRINTH_TOKEN:?set MODRINTH_TOKEN (create a PAT at https://modrinth.com/settings/pats)}"
: "${MODRINTH_PROJECT_ID:?set MODRINTH_PROJECT_ID (the ID on the project's settings page)}"

MC_VERSION="${MC_VERSION:-$(sed -n 's/^minecraft_version=\(.*\)$/\1/p' "$HERE/gradle.properties")}"
RELEASE_TYPE="${RELEASE_TYPE:-release}"
[ -n "$MC_VERSION" ] || { echo "!! Could not determine a Minecraft version." >&2; exit 1; }
[ -f "$CHANGELOG_FILE" ] || { echo "!! No such changelog: $CHANGELOG_FILE" >&2; exit 1; }

# Build the multipart file arguments and the matching file_parts list. The names are positional
# (file0, file1, ...) rather than the filenames: Modrinth matches file_parts entries to multipart
# field names, and a filename containing a space or an unusual character would otherwise have to be
# escaped identically in two places.
CURL_FILES=()
PARTS=()
i=0
for f in "$@"; do
    [ -f "$f" ] || { echo "!! No such file: $f" >&2; exit 1; }
    CURL_FILES+=(-F "file$i=@$f")
    PARTS+=("file$i")
    echo "   file$i = $(basename "$f") ($(stat -c%s "$f") bytes)$([ "$i" = 0 ] && echo '  [primary]')"
    i=$((i + 1))
done

DATA="$(CHANGELOG="$CHANGELOG_FILE" VER="$VERSION_NUMBER" PROJECT="$MODRINTH_PROJECT_ID" \
    MC="$MC_VERSION" RTYPE="$RELEASE_TYPE" PARTS="${PARTS[*]}" python3 -c '
import json, os
parts = os.environ["PARTS"].split()
print(json.dumps({
  "name": "LegendQuest ReForged " + os.environ["VER"],
  "version_number": os.environ["VER"],
  "changelog": open(os.environ["CHANGELOG"], encoding="utf-8").read(),
  "dependencies": [],
  "game_versions": [os.environ["MC"]],
  "version_type": os.environ["RTYPE"],
  "loaders": ["neoforge"],
  "featured": True,
  "project_id": os.environ["PROJECT"],
  "file_parts": parts,
  "primary_file": parts[0],
}))')"

if [ -n "${MODRINTH_DEBUG:-}" ]; then
    # Carries no credentials, so it is safe to print when diagnosing a rejection.
    echo ">> data:"; python3 -m json.tool <<<"$DATA" | sed 's/^/     /'
fi

echo ">> Creating Modrinth version $VERSION_NUMBER on project $MODRINTH_PROJECT_ID ($RELEASE_TYPE, MC $MC_VERSION)"
# --form-string for the JSON, matching the CurseForge script: curl treats ';', a leading '@' and a
# leading '<' as special inside -F, and a changelog containing any of them would silently mangle it.
RESPONSE="$(curl -sS --max-time 600 -w '\n%{http_code}' \
    -H "Authorization: $MODRINTH_TOKEN" \
    --form-string "data=$DATA" \
    "${CURL_FILES[@]}" \
    "$API/version")"

STATUS="$(tail -n1 <<<"$RESPONSE")"
BODY="$(sed '$d' <<<"$RESPONSE")"

if [ "$STATUS" = "200" ] || [ "$STATUS" = "201" ]; then
    VERSION_ID="$(python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("id",""))
except Exception: pass' <<<"$BODY" 2>/dev/null || true)"
    echo ">> Published${VERSION_ID:+ as version $VERSION_ID}"
    echo "   https://modrinth.com/mod/$MODRINTH_PROJECT_ID/version/$VERSION_NUMBER"
    exit 0
fi

echo "!! Modrinth rejected the version (HTTP $STATUS)" >&2
echo "$BODY" >&2
case "$STATUS" in
    401) echo "!! 401: the token is wrong or expired. A Modrinth PAT goes in the Authorization" >&2
         echo "!! header BARE - no 'Bearer ' prefix, unlike most APIs." >&2 ;;
    400) echo "!! 400: Modrinth names the bad field in the body above. Common causes: a" >&2
         echo "!! version_number that already exists on the project (they must be unique), or a" >&2
         echo "!! game_versions entry Modrinth does not list yet." >&2 ;;
esac
exit 1
