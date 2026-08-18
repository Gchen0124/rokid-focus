#!/bin/zsh
# When Rokid Glasses show up on USB, copy laptop tasks.json onto the glasses app.
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

ROOT="$(cd "$(dirname "$0")" && pwd)"
LIST="$ROOT/tasks.json"
TMP="/data/local/tmp/focus_tasks.json"
PKG="com.chenniuniu.rokidfocus.glass"

last_serial=""
last_hash=""

while true; do
  serial="$(adb devices -l 2>/dev/null | awk '/model:RG_glasses|product:glasses/ {print $1; exit}')"
  if [[ -z "$serial" ]]; then
    last_serial=""
    sleep 2
    continue
  fi
  hash="$(shasum -a 1 "$LIST" 2>/dev/null | awk '{print $1}')"
  if [[ "$serial" == "$last_serial" && "$hash" == "$last_hash" ]]; then
    sleep 2
    continue
  fi
  echo "[rokid] $serial — pushing list"
  adb -s "$serial" push "$LIST" "$TMP" >/dev/null
  adb -s "$serial" shell run-as "$PKG" cp "$TMP" files/tasks.json >/dev/null
  adb -s "$serial" shell am start -n "$PKG/.MainActivity" >/dev/null
  last_serial="$serial"
  last_hash="$hash"
  echo "[rokid] refreshed $(date '+%H:%M:%S')"
  sleep 2
done
