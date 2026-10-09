"""Exercise the actual PowerShell launcher gate without an emulator or Gradle."""

import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


class WindowsLauncherSmokeTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("pwsh"), "PowerShell is needed to exercise its native script")
    def test_launcher_success_failure_and_stale_evidence(self):
        script = Path(__file__).with_suffix(".ps1").resolve()
        with tempfile.TemporaryDirectory(prefix="swypetris-smoke-gate-") as workspace:
            result = subprocess.run(
                ["pwsh", "-NoProfile", "-File", str(script), "-Workspace", workspace],
                capture_output=True, text=True, timeout=30, check=False,
            )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Smoke exit 0: continuation=True; stale evidence removed", result.stdout)
        self.assertIn("Smoke exit 1: continuation=False; stale evidence removed", result.stdout)


if __name__ == "__main__":
    unittest.main()
