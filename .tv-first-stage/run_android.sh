#!/bin/bash
# Disposable GitHub runner only. Never installed on the owner's Mac.
set -euo pipefail
export ANDROID_USER_HOME="$RUNNER_TEMP/nova-preview-android"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
mkdir -p "$ANDROID_AVD_HOME"
sudo chmod 666 /dev/kvm
printf 'no\n' | avdmanager create avd -n NovaPreviewCI -p "$ANDROID_AVD_HOME/NovaPreviewCI.avd" -k 'system-images;android-30;google_apis;x86_64' --force
# Explicitly align the index with the directory just created. Some SDK-manager
# revisions write the index in a different user home than the new emulator.
test -f "$ANDROID_AVD_HOME/NovaPreviewCI.avd/config.ini"
printf 'avd.ini.encoding=UTF-8\npath=%s\ntarget=android-30\n' "$ANDROID_AVD_HOME/NovaPreviewCI.avd" > "$ANDROID_AVD_HOME/NovaPreviewCI.ini"
"$ANDROID_HOME/emulator/emulator" -list-avds | tee /tmp/tv-preview-avds.txt
grep -Fx NovaPreviewCI /tmp/tv-preview-avds.txt
"$ANDROID_HOME/emulator/emulator" -avd NovaPreviewCI -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -accel on > /tmp/tv-preview-emulator.log 2>&1 &
export NOVA_PREVIEW_CI_PID=$!
trap 'adb -s emulator-5556 emu kill >/dev/null 2>&1 || true' EXIT
python - <<'PY'
import subprocess,time,os
from pathlib import Path
end=time.monotonic()+150
while time.monotonic()<end:
    try:os.kill(int(os.environ['NOVA_PREVIEW_CI_PID']),0)
    except ProcessLookupError:
        print(Path('/tmp/tv-preview-emulator.log').read_text()[-10000:])
        raise RuntimeError('CI_EMULATOR_EXITED_BEFORE_BOOT') from None
    try:
        p=subprocess.run(['adb','-s','emulator-5556','shell','getprop','sys.boot_completed'],capture_output=True,text=True,timeout=4)
        if p.stdout.strip()=='1':break
    except subprocess.TimeoutExpired:pass
    time.sleep(2)
else:
    print(Path('/tmp/tv-preview-emulator.log').read_text()[-10000:])
    raise RuntimeError('CI_EMULATOR_BOOT_TIMEOUT')
PY
adb -s emulator-5556 shell input keyevent 82
adb -s emulator-5556 install -r -g android-tv/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r -g android-tv/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
timeout 150 adb -s emulator-5556 shell am instrument -w -r in.ghartv.nova.test/androidx.test.runner.AndroidJUnitRunner | tee /tmp/tv-preview-instrumentation.txt
python - <<'PY'
from pathlib import Path
s=Path('/tmp/tv-preview-instrumentation.txt').read_text()
assert 'OK (7 tests)' in s and 'FAILURES' not in s and 'INSTRUMENTATION_FAILED' not in s,s
print('ACTUAL_ANDROID_PREVIEW_CONTROLLER=7_PASS_LOCAL_VIDEO_NOT_PROVIDER_PLAYBACK')
PY
