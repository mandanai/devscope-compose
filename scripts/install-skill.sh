#!/bin/sh
# Installs the journeyscope agent skill for Claude Code, Codex CLI and/or Gemini CLI.
set -eu

repo="mandanai/devscope-compose"
raw="https://raw.githubusercontent.com/$repo/main"
api="https://api.github.com/repos/$repo/contents"

agent=all
scope=project
dest=
link=0
force=0
gemini_native=0

usage() {
  cat <<EOF
Usage: install-skill.sh [--agent claude|codex|gemini|all] [--scope project|user]
                        [--dest DIR] [--gemini-native] [--link] [--force]

  --agent          Target agent (default: all).
  --scope          project = ./<dir>, user = \$HOME/<dir> (default: project).
  --dest DIR       Use DIR instead of the current directory / \$HOME as the base.
  --gemini-native  Install Gemini to .gemini/skills instead of the shared .agents/skills.
  --link           Symlink instead of copy (needs a checkout).
  --force          Overwrite an existing install.
EOF
}

while [ $# -gt 0 ]; do
  case $1 in
    --agent) agent=${2:?}; shift 2 ;;
    --scope) scope=${2:?}; shift 2 ;;
    --dest) dest=${2:?}; shift 2 ;;
    --gemini-native) gemini_native=1; shift ;;
    --link) link=1; shift ;;
    --force) force=1; shift ;;
    -h | --help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

case $agent in claude | codex | gemini | all) ;; *) echo "invalid --agent: $agent" >&2; exit 2 ;; esac
case $scope in project | user) ;; *) echo "invalid --scope: $scope" >&2; exit 2 ;; esac

if [ -n "$dest" ]; then
  base=$dest
elif [ "$scope" = user ]; then
  base=$HOME
else
  base=$(pwd)
fi

dirs=
add_dir() {
  case " $dirs " in *" $1 "*) ;; *) dirs="$dirs $1" ;; esac
}
case $agent in claude | all) add_dir "$base/.claude/skills" ;; esac
case $agent in codex | all) add_dir "$base/.agents/skills" ;; esac
case $agent in
  gemini | all)
    if [ "$gemini_native" = 1 ]; then add_dir "$base/.gemini/skills"; else add_dir "$base/.agents/skills"; fi
    ;;
esac

script_dir=$(cd "$(dirname "$0")" 2>/dev/null && pwd || echo "")
local_skill=
if [ -n "$script_dir" ] && [ -f "$script_dir/../skills/journeyscope/SKILL.md" ]; then
  local_skill=$(cd "$script_dir/../skills/journeyscope" && pwd)
fi

staging=
cleanup() { [ -n "$staging" ] && rm -rf "$staging"; }
trap cleanup EXIT

fetch_remote() {
  command -v curl >/dev/null 2>&1 || { echo "curl is required to download the skill" >&2; exit 1; }
  staging=$(mktemp -d)
  mkdir -p "$staging/journeyscope"
  fetch_tree "skills/journeyscope" "$staging/journeyscope"
  local_skill="$staging/journeyscope"
}

# Lists a GitHub contents directory and downloads files recursively (no jq dependency).
fetch_tree() {
  remote=$1
  target=$2
  mkdir -p "$target"
  listing=$(curl -fsSL "$api/$remote")
  printf '%s\n' "$listing" | sed -n 's/^ *"path": *"\([^"]*\)".*/\1/p' | while read -r path; do
    name=$(basename "$path")
    case $(printf '%s\n' "$listing" | grep -A6 "\"path\": *\"$path\"" | sed -n 's/^ *"type": *"\([^"]*\)".*/\1/p' | head -n1) in
      dir) fetch_tree "$path" "$target/$name" ;;
      *) curl -fsSL "$raw/$path" -o "$target/$name" ;;
    esac
  done
}

if [ -z "$local_skill" ]; then
  if [ "$link" = 1 ]; then echo "--link needs a local checkout" >&2; exit 1; fi
  fetch_remote
fi

for d in $dirs; do
  target="$d/journeyscope"
  if [ -e "$target" ] || [ -L "$target" ]; then
    if [ "$force" = 1 ]; then rm -rf "$target"; else echo "exists, skipping (use --force): $target" >&2; continue; fi
  fi
  mkdir -p "$d"
  if [ "$link" = 1 ]; then ln -s "$local_skill" "$target"; else cp -R "$local_skill" "$target"; fi
  echo "installed: $target"
done

echo "Reload skills in your agent (Gemini: /skills reload), then ask it to set up JourneyScope."
