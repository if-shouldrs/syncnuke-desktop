#!/usr/bin/env sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
java_command=java

if ! command -v java >/dev/null 2>&1; then
  "$script_dir/jdk/download.sh"
  java_command="$project_dir/dist/jdk/bin/java"
fi

"$java_command" \
  -classpath "$project_dir/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain \
  --project-dir "$project_dir" \
  build
