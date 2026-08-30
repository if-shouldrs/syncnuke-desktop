#!/usr/bin/env sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)

exit_code=0
SYNCNUKE_NO_PAUSE=1 "$script_dir/../build/build.sh" || exit_code=$?

if [ "$exit_code" -eq 0 ]; then
  version=$(grep '^version = ' "$project_dir/build.gradle" | cut -d "'" -f 2)
  release_dir="$project_dir/dist/syncnuke-desktop-$version"
  jar="$project_dir/build/libs/syncnuke-desktop-$version-all.jar"

  mkdir -p "$release_dir/scripts/jdk"
  cp "$jar" "$release_dir/syncnuke-desktop.jar"
  cp "$project_dir/scripts/jdk/download.sh" "$project_dir/scripts/jdk/download.bat" "$release_dir/scripts/jdk/"
  cp "$project_dir/scripts/release/start.sh" "$project_dir/scripts/release/start.bat" "$release_dir/"
  cp "$project_dir/README.md" "$project_dir/LICENSE.md" "$release_dir/"
  chmod 755 "$release_dir/start.sh" "$release_dir/scripts/jdk/download.sh"
  chmod 644 "$release_dir/LICENSE.md"
  printf '%s\n' "$version" > "$release_dir/VERSION"

  echo "Release created at $release_dir"
fi

printf '\nPress Enter to continue... '
IFS= read -r _ || true
exit "$exit_code"
