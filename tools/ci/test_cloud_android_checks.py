"""Exercise cloud runner failure handling with isolated fake SDK commands."""

import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


@unittest.skipUnless(os.name == "posix" and shutil.which("bash"), "Cloud shell requires Linux")
class CloudAndroidChecksTests(unittest.TestCase):
    def test_complete_incomplete_and_stalled_instrumentation(self):
        source = Path(__file__).with_name("run_cloud_android_checks.sh").read_text()
        complete = "\n".join((
            "INSTRUMENTATION_STATUS: numtests=1",
            "INSTRUMENTATION_STATUS: class=ru.itoltec.swypetris.SmokeTest",
            "INSTRUMENTATION_STATUS: test=launch",
            "INSTRUMENTATION_STATUS_CODE: 0",
            "OK (1 test)", "INSTRUMENTATION_CODE: -1", "",
        ))
        for case in ("complete", "incomplete", "stall"):
            with self.subTest(case=case), tempfile.TemporaryDirectory() as workspace:
                root = Path(workspace)
                commands = root / "bin"
                commands.mkdir()
                sdk = root / "sdk/emulator"
                sdk.mkdir(parents=True)
                (sdk / "source.properties").write_text("Pkg.Revision=37.2.12\n")
                # Any attempt to launch a second emulator must fail this fixture.
                (sdk / "emulator").write_text("#!/bin/sh\nexit 99\n")
                (sdk / "emulator").chmod(0o755)
                (root / "complete.txt").write_text(complete)
                (commands / "adb").write_text('''#!/usr/bin/env bash
set -eu
case "$*" in
  *'shell am instrument'*)
    if [ "$MOCK_CASE" = complete ]; then cat complete.txt
    elif [ "$MOCK_CASE" = stall ]; then echo 'INSTRUMENTATION_STATUS: current=1'; exec sleep 30
    else echo 'INSTRUMENTATION_STATUS: current=1'; fi ;;
  *'shell monkey'*) echo 'Events injected: 1' ;;
  *'getprop ro.build.version.sdk'*) echo 35 ;;
  *'getprop ro.product.cpu.abi'*) echo x86_64 ;;
  *'getprop ro.build.fingerprint'*) echo test/cloud ;;
  *) echo 'fixture diagnostic' ;;
esac
''')
                (commands / "adb").chmod(0o755)
                (commands / "java").write_text('#!/bin/sh\necho "fixture Java21"\n')
                (commands / "java").chmod(0o755)
                tools = root / "tools/ci"
                tools.mkdir(parents=True)
                shutil.copy(Path(__file__).with_name("verify_instrumentation_output.py"), tools)
                script = root / "checks.sh"
                script.write_text(source.replace("8m", "0.2s"))
                report = root / "app/build/outputs/androidTest-results/windows"
                report.mkdir(parents=True)
                (report / "environment.json").write_text('{"stale":true}')
                result = subprocess.run(["bash", str(script)], cwd=root, capture_output=True,
                                        text=True, timeout=5, env=dict(os.environ,
                                        PATH=f"{commands}:{os.environ['PATH']}", ANDROID_HOME=str(root / "sdk"),
                                        CHECK_MODE="selected", ANDROID_CLASSES="ru.itoltec.swypetris.SmokeTest",
                                        MOCK_CASE=case))
                self.assertTrue((report / "logcat-stream.txt").exists())
                if case == "complete":
                    self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                    environment = json.loads((report / "environment.json").read_text())
                    self.assertEqual("Pkg.Revision=37.2.12", environment["emulator"])
                    self.assertEqual("success", environment["instrumentation"])
                else:
                    self.assertNotEqual(0, result.returncode)
                    self.assertFalse((report / "environment.json").exists())
                    self.assertTrue((report / "app-stacks.txt").exists())
                    self.assertTrue((report / "logcat.txt").exists())
                if case == "stall":
                    self.assertEqual(124, result.returncode)
