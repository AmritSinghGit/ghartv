#!/bin/bash
set -Eeuo pipefail
PROJECT="${GHARTV_PROJECT:-$HOME/Downloads/GharTV_Nova_v0.4.2}"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
fail(){ echo "ERROR: $*" >&2; exit 1; }
[ -d "$PROJECT/.git" ] || fail "Canonical GharTV checkout missing: $PROJECT"
[ "$(git -C "$PROJECT" symbolic-ref --short HEAD)" = main ] || fail "GharTV must remain on main"
[ -z "$(git -C "$PROJECT" status --porcelain)" ] || fail "GharTV checkout has local work; nothing was changed"
(cd "$PROJECT" && bash VALIDATE_SOURCE.command)
JAVA_HOME_SELECTED="/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home"
[ -x "$JAVA_HOME_SELECTED/bin/java" ] || JAVA_HOME_SELECTED="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
[ -x "$JAVA_HOME_SELECTED/bin/java" ] || fail "JDK 17 missing"
export JAVA_HOME="$JAVA_HOME_SELECTED" PATH="$JAVA_HOME_SELECTED/bin:$PATH" ANDROID_SDK_ROOT="$SDK" ANDROID_HOME="$SDK"
SIGNING_ENV="$HOME/Library/Application Support/GharTV/signing/signing.env"
[ -f "$SIGNING_ENV" ] || fail "Existing signing identity missing"
# shellcheck disable=SC1090
source "$SIGNING_ENV"
export GHARTV_SIGNING_STORE GHARTV_SIGNING_STORE_PASSWORD GHARTV_SIGNING_KEY_ALIAS GHARTV_SIGNING_KEY_PASSWORD
GRADLE="$PROJECT/android-tv/gradlew"; [ -x "$GRADLE" ] || GRADLE="$HOME/Library/Caches/GharTV-Nova/gradle-8.11.1/bin/gradle"
[ -x "$GRADLE" ] || fail "Gradle 8.11.1 missing"
export GRADLE_USER_HOME="$HOME/Library/Caches/GharTV-Nova/gradle-user-home-java17"
(cd "$PROJECT/android-tv" && "$GRADLE" --no-daemon --max-workers=2 -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleRelease)
APK="$PROJECT/android-tv/app/build/outputs/apk/release/app-release.apk"; [ -f "$APK" ] || fail "Review APK missing"
SERIAL=""
while IFS= read -r s; do
  name="$("$ADB" -s "$s" emu avd name 2>/dev/null | tr -d '\r' | head -1 || true)"
  case "$name" in GharTV_*) SERIAL="$s"; break;; esac
done < <("$ADB" devices | awk '$2=="device"&&$1~/^emulator-/{print $1}')
if [ -z "$SERIAL" ]; then
  nohup "$EMULATOR" -avd GharTV_Nova_Manual_google_tv_API36 -port 5580 -dns-server 1.1.1.1,8.8.8.8 -no-snapshot-load > /tmp/ghartv-rc5-emulator.log 2>&1 &
  SERIAL=emulator-5580; "$ADB" -s "$SERIAL" wait-for-device
  for _ in $(seq 1 180); do [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" = 1 ] && break; sleep 2; done
fi
"$ADB" -s "$SERIAL" install -r -d "$APK"
"$ADB" -s "$SERIAL" shell am force-stop in.ghartv.nova
"$ADB" -s "$SERIAL" shell am start -W -n in.ghartv.nova/.SplashActivity --es ghartv_theme_preview birthday --es ghartv_theme_person dad
echo "GharTV RC5 Dad birthday splash and automatic preview are open on $SERIAL"
