#!/usr/bin/env bash
set -euo pipefail

# APKs are built before the action boots its isolated API 35 emulator.
serial=emulator-5556
report_dir=app/build/outputs/androidTest-results/windows
mkdir -p "$report_dir"
capture_failure() {
  result=$?
  if [ "$result" -ne 0 ]; then
    adb -s "$serial" logcat -d > "$report_dir/logcat.txt" 2>&1 || true
    adb -s "$serial" shell dumpsys window > "$report_dir/window.txt" 2>&1 || true
    adb -s "$serial" pull /sdcard/Android/data/ru.itoltec.swypetris/files "$report_dir/device-files" >/dev/null 2>&1 || true
  fi
  exit "$result"
}
trap capture_failure EXIT
rm -f "$report_dir/environment.json" "$report_dir/smoke.txt" "$report_dir/instrumentation.txt"
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$serial" shell monkey -p ru.itoltec.swypetris -c android.intent.category.LAUNCHER 1 | tee "$report_dir/smoke.txt"
# Exercise a fresh picker's drawer before full regression exercises its remembered
# image directory. Both executions select a unique fixture and verify the crop result.
if [ "$CHECK_MODE" = full ]; then
  adb -s "$serial" shell am instrument -w -r -e class ru.itoltec.swypetris.BackgroundPickerTest \
    ru.itoltec.swypetris.test/androidx.test.runner.AndroidJUnitRunner | tee "$report_dir/picker-preflight.txt"
  python3 tools/ci/verify_instrumentation_output.py selected ru.itoltec.swypetris.BackgroundPickerTest "$report_dir/picker-preflight.txt"
fi
args=(-s "$serial" shell am instrument -w -r)
if [ "$CHECK_MODE" = selected ]; then
  [ -n "$ANDROID_CLASSES" ] || { echo 'Selected checks require test classes.' >&2; exit 1; }
  args+=(-e class "$ANDROID_CLASSES")
elif [ "$CHECK_MODE" != full ]; then
  echo 'Unsupported check mode.' >&2
  exit 1
fi
args+=(ru.itoltec.swypetris.test/androidx.test.runner.AndroidJUnitRunner)
adb "${args[@]}" | tee "$report_dir/instrumentation.txt"
python3 tools/ci/verify_instrumentation_output.py "$CHECK_MODE" "$ANDROID_CLASSES" "$report_dir/instrumentation.txt"
python3 - <<'PY'
import json
import os
import subprocess
from pathlib import Path

def command(*args):
    return subprocess.check_output(args, stderr=subprocess.STDOUT, text=True).strip()

serial = 'emulator-5556'
environment = dict(
    os='Linux', mode=os.environ['CHECK_MODE'], android_classes=os.environ['ANDROID_CLASSES'],
    serial=serial, avd='SwypetrisCI35', instrumentation='success', smoke='success',
    api=command('adb', '-s', serial, 'shell', 'getprop', 'ro.build.version.sdk'),
    abi=command('adb', '-s', serial, 'shell', 'getprop', 'ro.product.cpu.abi'),
    fingerprint=command('adb', '-s', serial, 'shell', 'getprop', 'ro.build.fingerprint'),
    java=command('java', '-version'),
    emulator=command(str(Path(os.environ['ANDROID_HOME']) / 'emulator/emulator'), '-version'),
    emulator_options='@SwypetrisCI35 -port 5556 -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim -wipe-data',
    instrumentation_command='adb -s emulator-5556 shell am instrument -w -r '
        + (f"-e class {os.environ['ANDROID_CLASSES']} " if os.environ['CHECK_MODE'] == 'selected' else '')
        + 'ru.itoltec.swypetris.test/androidx.test.runner.AndroidJUnitRunner',
)
Path('app/build/outputs/androidTest-results/windows/environment.json').write_text(
    json.dumps(environment, indent=2) + '\n', encoding='utf-8')
PY
