#!/bin/sh
# Prepares this checkout for local builds on macOS. Safe to re-run.
# - points local.properties at the Android SDK (the tracked copy has upstream's Linux path)
set -e
cd "$(dirname "$0")/.."

SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ ! -d "$SDK_DIR" ]; then
  echo "Android SDK not found at $SDK_DIR. Install Android Studio and run its setup wizard first." >&2
  exit 1
fi

# local.properties is tracked upstream; keep our edit out of commits.
git update-index --skip-worktree local.properties
printf 'sdk.dir=%s\n' "$SDK_DIR" > local.properties
echo "local.properties -> $SDK_DIR"


echo "Done. Build with: ./gradlew :app:assembleGiuliaAADebug"
