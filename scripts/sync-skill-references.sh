#!/bin/sh
# Regenerates skills/journeyscope/references from ai/. Edit ai/, never the generated copy.
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
src="$root/ai"
dest="$root/skills/journeyscope/references"
base="https://github.com/mandanai/devscope-compose/blob/main"

rm -rf "$dest"
mkdir -p "$dest/scenarios"

copy() {
  rel=$1
  {
    printf '<!-- Generated from ai/%s by scripts/sync-skill-references.sh; edit there. -->\n' "$rel"
    sed -e "s#](\.\./\.\./docs/#]($base/docs/#g" -e "s#](\.\./docs/#]($base/docs/#g" "$src/$rel"
  } >"$dest/$rel"
}

for f in "$src"/*.md; do
  name=$(basename "$f")
  [ "$name" = "README.md" ] && continue
  copy "$name"
done

for f in "$src"/scenarios/*.md; do
  copy "scenarios/$(basename "$f")"
done
