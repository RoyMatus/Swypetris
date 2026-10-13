"""Enforce the disabled remote-check entrypoint and absence of active workflows."""

import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
@unittest.skipUnless(shutil.which('pwsh'), 'PowerShell is needed to exercise the retired entrypoint')
class RemoteChecksDisabledTests(unittest.TestCase):
    def test_remote_entrypoint_fails_without_loading_credentials_or_dispatching(self):
        with tempfile.TemporaryDirectory() as directory:
            script = Path(directory) / 'Invoke-RemoteChecks.ps1'
            shutil.copy(ROOT / 'tools/Invoke-RemoteChecks.ps1', script)
            for arguments in ([], ['-Head', 'feature/example', '-Suite', 'FocusedJvm',
                                  '-TestClass', 'GameEngineTest', '-Reason', 'No local JDK', '-WhatIf']):
                result = subprocess.run(['pwsh', '-NoProfile', '-File', str(script), *arguments],
                                        capture_output=True, text=True, timeout=30)
                self.assertNotEqual(0, result.returncode)
                self.assertIn('Remote checks are disabled', result.stdout + result.stderr)
        source = (ROOT / 'tools/Invoke-RemoteChecks.ps1').read_text(encoding='utf-8')
        self.assertNotIn('Import-Module', source)
        self.assertNotIn('Invoke-GitHubRest', source)

    def test_no_executable_workflows_or_dependabot_schedule(self):
        directory = ROOT / '.github/workflows'
        self.assertEqual([], list(directory.glob('*.yml')) + list(directory.glob('*.yaml')))
        self.assertFalse((ROOT / '.github/dependabot.yml').exists())


if __name__ == '__main__':
    unittest.main()
