#!/bin/bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT="$ROOT/android-tv"
PACKAGE="in.ghartv.nova"
AVD_NAME="${GHARTV_AVD_NAME:-GharTV_Nova_Manual_google_tv_API36}"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
RESULT="$HOME/Desktop/GharTV-Test-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RESULT"

fail(){ printf 'ERROR: %s\nEvidence: %s\n' "$1" "$RESULT" >&2; exit 1; }
[[ -x "$ADB" && -x "$EMULATOR" ]] || fail "Android SDK tools were not found at $SDK"

if [[ -x "$PROJECT/gradlew" ]]; then
  (cd "$PROJECT" && ./gradlew --no-daemon :app:assembleDebug)
else
  GRADLE="$HOME/Library/Caches/GharTV-Nova/gradle-8.11.1/bin/gradle"
  [[ -x "$GRADLE" ]] || fail "Gradle wrapper/cached Gradle 8.11.1 is unavailable"
  (cd "$PROJECT" && "$GRADLE" --no-daemon :app:assembleDebug)
fi
APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || fail "Debug APK was not produced"

"$ADB" start-server >/dev/null 2>&1 || true
SERIAL=""
while read -r device _; do
  [[ "$device" == emulator-* ]] || continue
  name="$("$ADB" -s "$device" emu avd name 2>/dev/null | head -1 | tr -d '\r' || true)"
  if [[ "$name" == "$AVD_NAME" ]]; then SERIAL="$device"; break; fi
done < <("$ADB" devices | tail -n +2)

if [[ -z "$SERIAL" ]]; then
  "$EMULATOR" -avd "$AVD_NAME" -no-snapshot-save >"$RESULT/emulator.log" 2>&1 &
  for _ in $(seq 1 180); do
    while read -r device _; do
      [[ "$device" == emulator-* ]] || continue
      name="$("$ADB" -s "$device" emu avd name 2>/dev/null | head -1 | tr -d '\r' || true)"
      [[ "$name" == "$AVD_NAME" ]] && SERIAL="$device" && break
    done < <("$ADB" devices | tail -n +2)
    [[ -n "$SERIAL" ]] && break
    sleep 2
  done
fi
[[ -n "$SERIAL" ]] || fail "Existing GharTV TV AVD did not appear in adb"

for _ in $(seq 1 300); do
  state="$("$ADB" -s "$SERIAL" get-state 2>/dev/null || true)"
  boot="$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [[ "$state" == device && "$boot" == 1 ]] && break
  sleep 2
done
"$ADB" -s "$SERIAL" shell pm list features | grep -q android.software.leanback || fail "Target is not Android TV"

output="$("$ADB" -s "$SERIAL" install -r -d "$APK" 2>&1)" || {
  if grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE <<<"$output"; then
    "$ADB" -s "$SERIAL" uninstall "$PACKAGE" >/dev/null 2>&1 || true
    "$ADB" -s "$SERIAL" install -d "$APK"
  else
    printf '%s\n' "$output" >&2; fail "APK install failed"
  fi
}
"$ADB" -s "$SERIAL" shell am start -W -n "$PACKAGE/.MainActivity"
sleep 4
"$ADB" -s "$SERIAL" exec-out screencap -p > "$RESULT/ghartv.png" || true
"$ADB" -s "$SERIAL" logcat -d -v threadtime > "$RESULT/logcat.txt" || true
printf 'GharTV opened on %s. Screenshot: %s\n' "$SERIAL" "$RESULT/ghartv.png"
