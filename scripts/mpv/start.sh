#!/usr/bin/env sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)
version=$(grep '^version = ' "$project_dir/build.gradle" | cut -d "'" -f 2)
player_host="$HOME/.mpv-ipc/mpvsocket"

mkdir -p "$(dirname -- "$player_host")"

exec java -jar "$project_dir/build/libs/syncnuke-desktop-$version-all.jar" \
  --player mpv \
  --player-host "$player_host" \
  --launch-player \
  "$@"
