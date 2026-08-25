#!/usr/bin/env sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)
version=$(grep '^version = ' "$project_dir/build.gradle" | cut -d "'" -f 2)
player_host="$HOME/.mpv-ipc/mpvsocket"
mpv_path_file="$project_dir/dist/mpv/path"
java_command=java

if ! command -v java >/dev/null 2>&1; then
  "$script_dir/../jdk/download.sh"
  java_command="$project_dir/dist/jdk/bin/java"
fi

if ! command -v mpv >/dev/null 2>&1; then
  mpv_command=""
  if [ -f "$mpv_path_file" ]; then
    IFS= read -r mpv_command < "$mpv_path_file" || true
  fi

  if [ ! -x "$mpv_command" ]; then
    printf "MPV was not found on PATH. Enter the path to the MPV executable: "
    if ! IFS= read -r mpv_command; then
      exit 1
    fi
    case "$mpv_command" in
      ~/*) mpv_command="$HOME/${mpv_command#~/}" ;;
    esac
    if [ ! -x "$mpv_command" ]; then
      echo "MPV executable not found: $mpv_command" >&2
      exit 1
    fi

    mpv_dir=$(CDPATH= cd -- "$(dirname -- "$mpv_command")" && pwd)
    mpv_command="$mpv_dir/$(basename -- "$mpv_command")"
    mkdir -p "$(dirname -- "$mpv_path_file")"
    printf '%s\n' "$mpv_command" > "$mpv_path_file"
  fi

  PATH="$(dirname -- "$mpv_command"):$PATH"
  export PATH
fi

mkdir -p "$(dirname -- "$player_host")"

exec "$java_command" -jar "$project_dir/build/libs/syncnuke-desktop-$version-all.jar" \
  --player mpv \
  --player-host "$player_host" \
  --launch-player \
  "$@"
