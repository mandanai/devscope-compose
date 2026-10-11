#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
props="$root/local.properties"

read_prop() { grep -E "^$1=" "$props" | head -1 | cut -d= -f2-; }

for key in mavenCentralUsername mavenCentralPassword signingInMemoryKey signingInMemoryKeyPassword; do
  value="$(read_prop "$key")"
  if [[ -z "$value" || "$value" == "CHANGEME" ]]; then
    echo "local.properties: $key is not set" >&2
    exit 1
  fi
  export "ORG_GRADLE_PROJECT_$key=$value"
done

cd "$root"
exec ./gradlew --no-configuration-cache "${@:-publishToMavenCentral}"
