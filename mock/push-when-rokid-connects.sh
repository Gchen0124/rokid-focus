#!/bin/zsh
# When Rokid Glasses show up on USB, copy laptop tasks.json onto the glasses app.
# Do not restart Focus on every edit — the app polls files/tasks.json every second.
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

ROOT="$(cd "$(dirname "$0")" && pwd)"
LIST="$ROOT/tasks.json"
TMP="/data/local/tmp/focus_tasks.json"
PKG="com.chenniuniu.rokidfocus.glass"

last_serial=""
last_hash=""

push_list() {
  local serial="$1"
  echo "[rokid] $serial — pushing list"
  if ! adb -s "$serial" push "$LIST" "$TMP" >/dev/null; then
    echo "[rokid] adb push failed"
    return 1
  fi
  if ! adb -s "$serial" shell run-as "$PKG" cp "$TMP" files/tasks.json >/dev/null; then
    echo "[rokid] run-as copy failed"
    return 1
  fi
  if ! adb -s "$serial" shell pidof "$PKG" >/dev/null 2>&1; then
    adb -s "$serial" shell am start -n "$PKG/.MainActivity" >/dev/null || true
  else
    adb -s "$serial" shell am start -n "$PKG/.MainActivity" \
      -a android.intent.action.MAIN --activity-single-top >/dev/null || true
  fi
  echo "[rokid] refreshed $(date '+%H:%M:%S')"
}

while true; do
  serial="$(adb devices -l 2>/dev/null | awk '/model:RG_glasses|product:glasses/ {print $1; exit}')"
  if [[ -z "$serial" ]]; then
    last_serial=""
    sleep 1
    continue
  fi
  hash="$(shasum -a 1 "$LIST" 2>/dev/null | awk '{print $1}')"
  if [[ "$serial" == "$last_serial" && "$hash" == "$last_hash" ]]; then
    sleep 1
    continue
  fi
  if push_list "$serial"; then
    last_serial="$serial"
    last_hash="$hash"
  fi
  sleep 1
done
