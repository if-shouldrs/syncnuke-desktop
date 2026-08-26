#!/usr/bin/env sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

printf "Select a video player:\n1) MPV\nSelection: "
IFS= read -r player

case "$player" in
  1)
    exec "$script_dir/scripts/mpv/start.sh" "$@"
    ;;
  *)
    echo "Invalid selection: $player" >&2
    exit 1
    ;;
esac
