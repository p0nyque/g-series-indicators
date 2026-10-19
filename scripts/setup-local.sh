#!/bin/sh
# Prepares this checkout for local builds on macOS. Safe to re-run.
# - points local.properties at the Android SDK (the tracked copy has upstream's Linux path)
# - writes a placeholder app/google-services.json (gitignored) so the Firebase plugin is satisfied.
#   Its API key only has to look valid: Firebase checks the key's format at app start and crashes
#   on a malformed one; uploads with the fake key then fail quietly.
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

# Also replaces the earlier placeholder, whose key "dummy" crashed the app on start.
if [ ! -f app/google-services.json ] || grep -q '"current_key":"dummy"' app/google-services.json; then
  client() {
    printf '{"client_info":{"mobilesdk_app_id":"1:0:android:%s","android_client_info":{"package_name":"%s"}},"api_key":[{"current_key":"AIza00000000000000000000000000000000000"}]}' "$1" "$2"
  }
  printf '{"project_info":{"project_number":"0","project_id":"dummy"},"client":[%s,%s,%s,%s]}\n' \
    "$(client 0 org.obd.graphs.app)" \
    "$(client 1 org.obd.graphs.my.giulia.aa)" \
    "$(client 2 org.obd.graphs.my.giulia.performance_monitor)" \
    "$(client 3 org.obd.graphs.my.giulia)" > app/google-services.json
  echo "app/google-services.json -> placeholder (Firebase uploads fail quietly)"
fi

echo "Done. Build with: ./gradlew :app:assembleGiuliaAADebug"
