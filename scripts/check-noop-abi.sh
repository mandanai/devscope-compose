#!/usr/bin/env bash
# A noop twin replaces its real module at runtime without recompiling the consumer, so:
#  1. adapter twins declare no member the real module lacks;
#  2. adapter twins cover the whole real ABI (core-noop is intentionally partial);
#  3. every journeyscope member the compiled sample calls must exist in the noop ABIs.
# Run after `./gradlew :samples:composeApp:compileAndroidMain` (or any build of the sample).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
sample_classes="${1:-$root/samples/composeApp/build/classes/kotlin/android/main}"

members() {
  awk '/^public/ { cls = $0; sub(/^public .*class /, "", cls); sub(/ .*/, "", cls); next }
       /^\t/ {
         line = $0; sub(/^\t/, "", line)
         static = (line ~ /static/) ? "static " : ""
         kind = (line ~ / field /) ? "field" : "fun"
         sub(/^.*(fun|field) /, "", line)
         print cls "\t" static kind " " line
       }' "$1" | sort -u
}

status=0
all_noop="$(mktemp)"
for noop in "$root"/devscope-*-noop; do
  name="$(basename "$noop")"
  real="${name%-noop}"
  noop_api="$noop/api/android/$name.api"
  real_api="$root/$real/api/android/$real.api"
  members "$noop_api" >> "$all_noop"

  if [[ "$name" != "devscope-core-noop" ]]; then
    extra="$(comm -23 <(members "$noop_api") <(members "$real_api"))"
    if [[ -n "$extra" ]]; then
      echo "$name declares members the real module lacks:" >&2
      echo "$extra" >&2
      status=1
    fi
    missing="$(comm -13 <(members "$noop_api") <(members "$real_api"))"
    if [[ -n "$missing" ]]; then
      echo "$name misses real members:" >&2
      echo "$missing" >&2
      status=1
    fi
  fi
done
sort -u "$all_noop" -o "$all_noop"

if [[ -d "$sample_classes" ]]; then
  used="$(mktemp)"
  find "$sample_classes" -name '*.class' -print0 | xargs -0 javap -c -p 2>/dev/null |
    sed -nE 's#.*// (Method|InterfaceMethod|Field) (dev/chayaphon/journeyscope/[^ ]+)\.([^.: ]+):(.+)$#\1\t\2\t\3\t\4#p' |
    grep -v $'\tdev/chayaphon/journeyscope/sample/' |
    awk -F"\t" '{ gsub(/"/, "", $3); kind = ($1 == "Field") ? "field" : "fun"; print $2 "\t" kind " " $3 " " $4 }' | sort -u > "$used"
  # javap prints statics and instance members alike; compare on the part after any `static `.
  sed -E 's/\tstatic /\t/' "$all_noop" | sort -u > "$all_noop.plain"
  unresolved="$(comm -23 "$used" "$all_noop.plain")"
  if [[ -n "$unresolved" ]]; then
    echo "sample calls members no noop module provides:" >&2
    echo "$unresolved" >&2
    status=1
  fi
else
  echo "skipping sample check: $sample_classes not built" >&2
fi
exit $status
