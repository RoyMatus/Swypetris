#!/usr/bin/env bash
set -euo pipefail

if [ "$CHECK_MODE" = full ]; then
  ANDROID_SERIAL=emulator-5554 bash ./gradlew :app:connectedDebugAndroidTest --console=plain
else
  ANDROID_SERIAL=emulator-5554 bash ./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$ANDROID_CLASSES" --console=plain
fi

python3 tools/ci/verify_android_results.py "$CHECK_MODE" "$ANDROID_CLASSES"
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell monkey -p ru.itoltec.swypetris -c android.intent.category.LAUNCHER 1
