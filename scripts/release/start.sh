#!/usr/bin/env sh

set -e

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
"$script_dir/scripts/jdk/download.sh"
exec "$script_dir/jdk/bin/java" -jar "$script_dir/syncnuke-desktop.jar" "$@"

read -p "Press enter to continue..."
