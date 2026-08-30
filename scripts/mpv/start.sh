#!/usr/bin/env sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)
player_host="$HOME/.mpv-ipc/mpvsocket"
java_command=java
if [ -f "$project_dir/VERSION" ]; then
  version=$(sed -n '1p' "$project_dir/VERSION")
  jar="$project_dir/syncnuke-desktop-$version-all.jar"
  runtime_dir="$project_dir"
else
  version=$(grep '^version = ' "$project_dir/build.gradle" | cut -d "'" -f 2)
  jar="$project_dir/build/libs/syncnuke-desktop-$version-all.jar"
  runtime_dir="$project_dir/dist"
fi
mpv_path_file="$runtime_dir/mpv/path"

if ! command -v java >/dev/null 2>&1; then
  "$script_dir/../jdk/download.sh"
  java_command="$runtime_dir/jdk/bin/java"
fi

if ! command -v mpv >/dev/null 2>&1; then
  mpv_command=""
  if [ -f "$mpv_path_file" ]; then
    IFS= read -r mpv_command < "$mpv_path_file" || true
  fi

  if [ -d "$mpv_command" ]; then
    mpv_command="${mpv_command%/}/mpv"
  fi

  if [ ! -x "$mpv_command" ]; then
    printf "MPV was not found on PATH. Enter the path to the MPV executable: "
    if ! IFS= read -r mpv_command; then
      exit 1
    fi
    case "$mpv_command" in
      ~/*) mpv_command="$HOME/${mpv_command#~/}" ;;
    esac
    if [ -d "$mpv_command" ]; then
      mpv_command="${mpv_command%/}/mpv"
    fi

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
has_user=false
has_room=false
for argument in "$@"; do
  case "$argument" in
    --user) has_user=true ;;
    --room) has_room=true ;;
  esac
done

if [ "$has_user" = false ] || [ "$has_room" = false ]; then
  echo "Usage: $0 --user <name> --room <name> [options]" >&2
  exit 1
fi

mkdir -p "$(dirname -- "$player_host")"

exit_code=0
"$java_command" -jar "$jar" \
  --player mpv \
  --player-host "$player_host" \
  --launch-player \
  "$@" || exit_code=$?

printf '\nPress Enter to continue... '
IFS= read -r _ || true
exit "$exit_code"
