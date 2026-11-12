#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ "$(uname -s)" = Darwin ]; then
    TASK_CACHE="$HOME/Library/Caches/period-android"
    if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
        export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    fi
else
    TASK_CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/period-android"
fi
export GRADLE_USER_HOME="${PERIOD_GRADLE_HOME:-$HOME/Library/Caches/period-gradle}"
cd "$PROJECT_DIR"
trap 'if command -v dot_clean >/dev/null 2>&1; then dot_clean "$PROJECT_DIR"; fi' EXIT
./gradlew --no-daemon --project-cache-dir "$TASK_CACHE/project" -Pperiod.buildRoot="$TASK_CACHE/build" "$@"
