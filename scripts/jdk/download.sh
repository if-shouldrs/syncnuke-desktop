#!/usr/bin/env sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)
jdk_dir="$project_dir/dist/jdk"

if [ -x "$jdk_dir/bin/java" ]; then
  exit 0
fi

archive=$(mktemp)
trap 'rm -f -- "$archive"' EXIT

mkdir -p "$jdk_dir"
curl --fail --location \
  "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
  --output "$archive"
tar -xzf "$archive" --strip-components=1 -C "$jdk_dir"
