"""Check archived workflow boundaries and the disabled remote entrypoint.

Archived definitions are historical fixtures, not active verification.
"""

import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / 'tools/ci/legacy-workflows/developer-check.yml'


def step(text, name):
    return text.split(f'      - name: {name}\n', 1)[1].split('\n      - ', 1)[0]


class DeveloperWorkflowTests(unittest.TestCase):
    def test_optional_scope_and_diagnostics_preserve_required_gates(self):
        source = WORKFLOW.read_text(encoding='utf-8')
        triggers = source.split('on:\n', 1)[1].split('\npermissions:', 1)[0]
        self.assertEqual(['workflow_dispatch'], re.findall(r'^  (\w+):', triggers, re.M))
        self.assertEqual(['focused-checks'], re.findall(r'^  ([\w-]+):', source.split('jobs:\n')[1], re.M))
        self.assertIn('contents: read', source)
        self.assertNotIn('secrets.', source)
        self.assertNotRegex(source, r'contents: write|actions: write|sonar|release-check|full_regression_proof|continue-on-error')
        self.assertIn('group: ${{ github.workflow }}-${{ github.ref }}', source)
        self.assertIn('cancel-in-progress: false', source)
        self.assertLess(source.index('Validate focused request'), source.index('actions/setup-java'))
        self.assertIn('options: [FocusedJvm, FocusedAndroid]', source)
        self.assertIn('if: always()', step(source, 'Save focused results and diagnostics'))
        for path in ('app/build/verification/', 'app/build/test-results/', 'app/build/outputs/androidTest-results/'):
            self.assertIn(path, step(source, 'Save focused results and diagnostics'))
        self.assertNotRegex(source, r'(?m)^ +(?:run|script):.*\$\{\{ inputs\.')
        self.assertEqual(2, source.count('-MaxWorkers 2'))
        self.assertIn('-Suite FocusedJvm -TestClass $classes', source)
        self.assertIn('-Suite FocusedAndroid -TestClass', source)
        self.assertIn('-Serial emulator-5556', source)

    def test_emulator_backend_and_gradle_pin_match_required_ci(self):
        source = WORKFLOW.read_text(encoding='utf-8')
        required = (ROOT / 'tools/ci/legacy-workflows/ci.yml').read_text(encoding='utf-8')
        for expression in (
            r'gradle/actions/setup-gradle@\w+', r'reactivecircus/android-emulator-runner@\w+',
            r'api-level: \d+', r'arch: \w+', r'emulator-port: \d+',
            r"emulator-build: '[0-9]+'", r'emulator-options: [^\n]+',
        ):
            self.assertEqual(re.search(expression, required)[0], re.search(expression, source)[0])
        self.assertIn('-no-window', source)
        self.assertIn('java-version: \'21\'', source)


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
