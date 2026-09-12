#!/bin/bash
set -Eeuo pipefail
PERSON="${1:-dad}"
case "$PERSON" in mom|amrit|harjas|wifey|sis|dad|simrat) ;; *) echo "Use: $0 [mom|amrit|harjas|wifey|sis|dad|simrat]" >&2; exit 2;; esac
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"; ADB="$SDK/platform-tools/adb"
[ -x "$ADB" ] || { echo "adb not found: $ADB" >&2; exit 1; }
SERIAL="${GHARTV_EMULATOR_SERIAL:-}"
if [ -z "$SERIAL" ]; then
  while IFS= read -r s; do
    [ -n "$s" ] || continue
    name="$("$ADB" -s "$s" emu avd name 2>/dev/null | tr -d '\r' | head -1 || true)"
    case "$name" in GharTV_*) SERIAL="$s"; break;; esac
  done < <("$ADB" devices | awk '$2=="device"&&$1~/^emulator-/{print $1}')
fi
[ -n "$SERIAL" ] || { echo "No running GharTV emulator found." >&2; exit 1; }
"$ADB" -s "$SERIAL" shell am force-stop in.ghartv.nova
"$ADB" -s "$SERIAL" shell am start -W -n in.ghartv.nova/.SplashActivity \
  --es ghartv_theme_preview birthday --es ghartv_theme_person "$PERSON" --ez ghartv_splash_hold true
echo "Opened birthday preview for $PERSON on $SERIAL"
