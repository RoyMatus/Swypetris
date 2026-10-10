#!/usr/bin/env bash
set -euo pipefail

# APKs are built before the action boots its isolated API 35 emulator.
serial=emulator-5556
report_dir=app/build/outputs/androidTest-results/windows
mkdir -p "$report_dir"
# sys.boot_completed can precede an adbd reconnect and package-service readiness.
# Require three consecutive successful probes before installing, without retrying
# any APK installation or test failure.
deadline=$((SECONDS + 120))
ready=0
while ((SECONDS < deadline && ready < 3)); do
  if timeout 5s adb -s "$serial" shell pm path android > "$report_dir/boot-readiness.txt" 2>&1 &&
      grep -q '^package:' "$report_dir/boot-readiness.txt"; then
    ready=$((ready + 1))
  else
    ready=0
  fi
  sleep 2
done
if ((ready < 3)); then
  cat "$report_dir/boot-readiness.txt"
  echo 'Package Manager did not become ready within 120 seconds.' >&2
  exit 1
fi
# Keep device logs already received even if the emulator disconnects mid-test.
adb -s "$serial" logcat -v threadtime > "$report_dir/logcat-stream.txt" 2>&1 &
logcat_pid=$!
capture_failure() {
  result=$?
  kill "$logcat_pid" 2>/dev/null || true
  wait "$logcat_pid" 2>/dev/null || true
  if [ "$result" -ne 0 ]; then
    timeout 20s adb -s "$serial" shell 'pid=$(pidof ru.itoltec.swypetris); if [ -n "$pid" ]; then run-as ru.itoltec.swypetris debuggerd -b "$pid"; fi' > "$report_dir/app-stacks.txt" 2>&1 || true
    timeout 15s adb -s "$serial" logcat -d > "$report_dir/logcat.txt" 2>&1 || true
    timeout 15s adb -s "$serial" shell dumpsys window > "$report_dir/window.txt" 2>&1 || true
    timeout 30s adb -s "$serial" pull /sdcard/Android/data/ru.itoltec.swypetris/files "$report_dir/device-files" >/dev/null 2>&1 || true
    for server_log in /tmp/adb.*.log; do
      if [ -f "$server_log" ] && [ "$(wc -c < "$server_log")" -le 10485760 ]; then
        cp "$server_log" "$report_dir/adb-server.log"
      fi
    done
  fi
  exit "$result"
}
trap capture_failure EXIT
# Read installed SDK metadata without launching another emulator process while
# the test AVD is rendering. Missing metadata is still a real setup failure.
export SWYPETRIS_CI_EMULATOR_VERSION
SWYPETRIS_CI_EMULATOR_VERSION="$(grep '^Pkg.Revision=' "$ANDROID_HOME/emulator/source.properties")"
test -n "$SWYPETRIS_CI_EMULATOR_VERSION"
rm -f "$report_dir/environment.json" "$report_dir/smoke.txt" "$report_dir/instrumentation.txt"
install_cloud_apk() {
  local apk="$1" name="$2" remote="/data/local/tmp/swypetris-ci-$2.apk" expected actual
  expected=$(sha256sum "$apk" | cut -d ' ' -f 1)
  # Avoid negotiated sync compression, and verify the complete transferred bytes
  # before Package Manager sees them. Failed transfers/installations are not retried.
  timeout 120s adb -s "$serial" push -Z "$apk" "$remote" | tee "$report_dir/transfer-$name.txt"
  actual=$(timeout 15s adb -s "$serial" shell sha256sum "$remote" | tr -d '\r' | cut -d ' ' -f 1)
  if [ "$actual" != "$expected" ]; then
    echo "Transferred $name APK checksum mismatch." >&2
    return 1
  fi
  timeout 120s adb -s "$serial" shell pm install -r "$remote" | tee "$report_dir/install-$name.txt"
  grep -Eq '^Success[[:space:]]*$' "$report_dir/install-$name.txt"
  timeout 15s adb -s "$serial" shell rm "$remote"
}
install_cloud_apk app/build/outputs/apk/debug/app-debug.apk app
install_cloud_apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk test
adb -s "$serial" shell monkey -p ru.itoltec.swypetris -c android.intent.category.LAUNCHER 1 | tee "$report_dir/smoke.txt"
grep -Eq '^Events injected: 1[[:space:]]*$' "$report_dir/smoke.txt"
# Exercise a fresh picker's drawer before full regression exercises its remembered
# image directory. Both executions select a unique fixture and verify the crop result.
if [ "$CHECK_MODE" = full ]; then
  timeout --signal=INT --kill-after=15s 8m adb -s "$serial" shell am instrument -w -r -e class ru.itoltec.swypetris.BackgroundPickerTest \
    ru.itoltec.swypetris.test/androidx.test.runner.AndroidJUnitRunner | tee "$report_dir/picker-preflight.txt"
  python3 tools/ci/verify_instrumentation_output.py selected ru.itoltec.swypetris.BackgroundPickerTest "$report_dir/picker-preflight.txt"
  focused=ru.itoltec.swypetris.AbsoluteVictoryUiTest,ru.itoltec.swypetris.VictoryThemeIntegrationTest,ru.itoltec.swypetris.SmokeTest
  timeout --signal=INT --kill-after=15s 8m adb -s "$serial" shell am instrument -w -r -e class "$focused" \
    ru.itoltec.swypetris.test/androidx.test.runner.AndroidJUnitRunner | tee "$report_dir/ui-preflight.txt"
  python3 tools/ci/verify_instrumentation_output.py selected "$focused" "$report_dir/ui-preflight.txt"
  timeout 30s adb -s "$serial" pull /sdcard/Android/data/ru.itoltec.swypetris/files "$report_dir/device-files-preflight"
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
timeout --signal=INT --kill-after=15s 8m adb "${args[@]}" | tee "$report_dir/instrumentation.txt"
python3 tools/ci/verify_instrumentation_output.py "$CHECK_MODE" "$ANDROID_CLASSES" "$report_dir/instrumentation.txt"
if [ "$CHECK_MODE" = full ]; then
  timeout 30s adb -s "$serial" pull /sdcard/Android/data/ru.itoltec.swypetris/files "$report_dir/device-files"
fi
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
    emulator=os.environ['SWYPETRIS_CI_EMULATOR_VERSION'],
    emulator_options='@SwypetrisCI35 -port 5556 -no-window -gpu swangle -feature -Vulkan -no-snapshot -noaudio -no-boot-anim -wipe-data',
    instrumentation_command='adb -s emulator-5556 shell am instrument -w -r '
        + (f"-e class {os.environ['ANDROID_CLASSES']} " if os.environ['CHECK_MODE'] == 'selected' else '')
        + 'ru.itoltec.swypetris.test/androidx.test.runner.AndroidJUnitRunner',
)
Path('app/build/outputs/androidTest-results/windows/environment.json').write_text(
    json.dumps(environment, indent=2) + '\n', encoding='utf-8')
PY
