"""Check optional workflow boundaries and run its validation/dispatch code offline.

The fake API records every request, so negative tests also prove that no dispatch
occurred. These tests do not claim hosted Gradle/emulator execution passed.
"""

import json
import os
import re
import shlex
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/developer-check.yml'
SHA = 'a' * 40


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
        required = (ROOT / '.github/workflows/ci.yml').read_text(encoding='utf-8')
        for expression in (
            r'gradle/actions/setup-gradle@\w+', r'reactivecircus/android-emulator-runner@\w+',
            r'api-level: \d+', r'arch: \w+', r'emulator-port: \d+',
            r"emulator-build: '[0-9]+'", r'emulator-options: [^\n]+',
        ):
            self.assertEqual(re.search(expression, required)[0], re.search(expression, source)[0])
        self.assertIn('-no-window', source)
        self.assertIn('java-version: \'21\'', source)


@unittest.skipUnless(shutil.which('pwsh'), 'PowerShell is needed to exercise actual validation/dispatch')
class DeveloperRequestTests(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory(prefix='swypetris-focused-')
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name)
        tools = self.root / 'tools'
        tools.mkdir()
        shutil.copy(ROOT / 'tools/Verification.psm1', tools)
        shutil.copy(ROOT / 'tools/Invoke-RemoteChecks.ps1', tools)
        for source_set, names in (('test', ('GameEngineTest', 'BoardRulesTest')), ('androidTest', ('SmokeTest',))):
            directory = self.root / f'app/src/{source_set}/java/ru/itoltec/swypetris'
            directory.mkdir(parents=True)
            for name in names:
                (directory / f'{name}.kt').write_text(f'class {name} {{ @Test fun works() {{}} }}')
        self.environment = dict(os.environ, LOCAL_SHA=SHA, REMOTE_SHA=SHA,
                                EXPECTED_SHA=SHA, GITHUB_SHA=SHA,
                                GITHUB_REF='refs/heads/feature/focused',
                                CHECK_SUITE='FocusedJvm', TEST_CLASSES='GameEngineTest',
                                CHECK_REASON='No local JDK; verify collision rules',
                                MOCK_CASE='', API_LOG=str(self.root / 'api.jsonl'))
        self.git_mock = '''
function global:git {
    $global:LASTEXITCODE = 0
    if ($args -contains 'rev-parse') { return $env:LOCAL_SHA }
    if ($args -contains '--show-current') {
        if ($env:MOCK_CASE -eq 'wrong-branch') { return 'other' }
        return 'feature/focused'
    }
    if ($args -contains 'status') {
        if ($env:MOCK_CASE -eq 'dirty') { return ' M app/example.kt' }
        return
    }
    if ($args -contains 'check-ref-format') { return }
    throw 'Unexpected git invocation'
}
'''
        (tools / 'GitHub-Api.psm1').write_text('''
function New-GitHubClient { param($Repository) [pscustomobject]@{Repository=$Repository} }
function Invoke-GitHubRest {
    param($Client, $Method, $Path, $Body)
    @{ method=$Method; path=$Path; body=$Body } | ConvertTo-Json -Depth 8 -Compress |
        Add-Content -LiteralPath $env:API_LOG
    if ($Method -eq 'Post') {
        if ($env:MOCK_CASE -eq 'post-error') { throw 'Ambiguous network failure' }
        return
    }
    if ($Path.StartsWith('git/ref/heads/')) {
        return @{ object=@{type='commit'; sha=$env:REMOTE_SHA} }
    }
    if ($Path.StartsWith('pulls?')) {
        if ($env:MOCK_CASE -eq 'open-pr') { return @{number=42} }
        return
    }
    if ($Path -eq 'actions/workflows/developer-check.yml') {
        if ($env:MOCK_CASE -eq 'inactive') { return @{state='disabled_manually'} }
        return @{state='active'}
    }
    throw 'Unexpected API request'
}
Export-ModuleMember -Function New-GitHubClient, Invoke-GitHubRest
''', encoding='utf-8')

    def run_script(self, script, **environment):
        path = self.root / 'request.ps1'
        path.write_text(self.git_mock + script, encoding='utf-8')
        return subprocess.run(['pwsh', '-NoLogo', '-NoProfile', '-File', str(path)],
                              cwd=self.root, env=dict(self.environment, **environment),
                              capture_output=True, text=True, timeout=20, check=False)

    def test_workflow_validation_rejects_invalid_inputs_before_setup(self):
        block = step(WORKFLOW.read_text(encoding='utf-8'), 'Validate focused request')
        script = textwrap.dedent(block.split('        run: |\n', 1)[1])
        cases = (
            ({}, True),
            ({'CHECK_SUITE': 'FocusedAndroid', 'TEST_CLASSES': 'SmokeTest'}, True),
            ({'TEST_CLASSES': ' GameEngineTest , ru.itoltec.swypetris.BoardRulesTest '}, True),
            ({'CHECK_REASON': 'Untrusted text $(); "quotes"; `backtick`\nsecond line'}, True),
            ({'CHECK_SUITE': 'Fast'}, False),
            ({'TEST_CLASSES': ''}, False),
            ({'TEST_CLASSES': 'GameEngineTest,'}, False),
            ({'TEST_CLASSES': 'SmokeTest'}, False),
            ({'TEST_CLASSES': 'GameEngineTest#method'}, False),
            ({'TEST_CLASSES': '*'}, False),
            ({'TEST_CLASSES': 'GameEngineTest; throw "injection"'}, False),
            ({'CHECK_REASON': '   '}, False),
            ({'CHECK_REASON': 'x' * 1001}, False),
            ({'EXPECTED_SHA': 'a' * 7}, False),
            ({'EXPECTED_SHA': SHA + '\n'}, False),
            ({'EXPECTED_SHA': 'b' * 40}, False),
            ({'GITHUB_SHA': 'b' * 40}, False),
            ({'GITHUB_REF': 'refs/tags/v1.0.0'}, False),
        )
        for inputs, valid in cases:
            with self.subTest(inputs=inputs):
                result = self.run_script(script, **inputs)
                self.assertEqual(valid, result.returncode == 0, result.stdout + result.stderr)
                if valid:
                    self.assertIn('Validated', result.stdout)

    def invoke_wrapper(self, what_if=False, **environment):
        return self.run_script('''
$ErrorActionPreference = 'Stop'
$request = @{
    Head='feature/focused'; Suite=$env:CHECK_SUITE
    TestClass=@($env:TEST_CLASSES -split ','); Reason=$env:CHECK_REASON
}
''' + ("$request.WhatIf = $true\n" if what_if else '') + '''
$result = & ./tools/Invoke-RemoteChecks.ps1 @request
$result | ConvertTo-Json -Depth 5 -Compress
''', **environment)

    def test_workflow_invocations_preserve_class_arrays_and_untrusted_reason(self):
        # Exercise the exact PowerShell commands, substituting only the test runner.
        (self.root / 'tools/Verify-Tests.ps1').write_text('''
param([string]$Suite, [string[]]$TestClass, [string]$Serial, [int]$MaxWorkers, [string]$Reason)
$PSBoundParameters | ConvertTo-Json -Compress
''', encoding='utf-8')
        source = WORKFLOW.read_text(encoding='utf-8')
        jvm = textwrap.dedent(step(source, 'Run focused JVM checks').split('        run: |\n', 1)[1])
        # The action runs this scalar through a shell. shlex verifies its argument
        # boundary; executing the resulting -Command checks PowerShell binding.
        android = shlex.split(step(source, 'Run focused Android checks').split('          script: >-\n', 1)[1].strip())
        self.assertEqual(['pwsh', '-NoLogo', '-NoProfile', '-Command'], android[:-1])
        reason = 'Limited host; quotes "x"; $([int]1 + 2); `text`\nnext line'
        for suite, command in (('FocusedJvm', jvm), ('FocusedAndroid', android[-1])):
            with self.subTest(suite=suite):
                result = self.run_script(command, TEST_CLASSES=' GameEngineTest , BoardRulesTest ', CHECK_REASON=reason)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                arguments = json.loads(result.stdout.strip())
                self.assertEqual(suite, arguments['Suite'])
                self.assertEqual(['GameEngineTest', 'BoardRulesTest'], arguments['TestClass'])
                self.assertEqual(2, arguments['MaxWorkers'])
                self.assertEqual(reason, arguments['Reason'])
                if suite == 'FocusedAndroid':
                    self.assertEqual('emulator-5556', arguments['Serial'])

    def api_requests(self):
        log = self.root / 'api.jsonl'
        return [json.loads(line) for line in log.read_text(encoding='utf-8-sig').splitlines()] if log.exists() else []

    def test_what_if_validates_remote_but_never_dispatches(self):
        result = self.invoke_wrapper(what_if=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        calls = self.api_requests()
        self.assertTrue(calls)
        self.assertTrue(all(call['method'] == 'Get' for call in calls))
        self.assertIn('not-dispatched', result.stdout)
        self.assertIn(SHA, result.stdout)

    def test_dispatch_binds_sha_classes_and_reason_without_claiming_test_success(self):
        reason = 'No emulator; validate "collision" $() `text`\nsecond line'
        result = self.invoke_wrapper(CHECK_REASON=reason, TEST_CLASSES='GameEngineTest,BoardRulesTest')
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        calls = self.api_requests()
        posts = [call for call in calls if call['method'] == 'Post']
        self.assertEqual(1, len(posts))
        body = posts[0]['body']
        self.assertEqual('feature/focused', body['ref'])
        self.assertEqual(SHA, body['inputs']['expected_sha'])
        self.assertEqual(reason, body['inputs']['reason'])
        self.assertEqual('FocusedJvm', body['inputs']['suite'])
        self.assertEqual('ru.itoltec.swypetris.BoardRulesTest,ru.itoltec.swypetris.GameEngineTest', body['inputs']['test_classes'])
        self.assertEqual('git/ref/heads/feature%2Ffocused', calls[0]['path'])
        self.assertIn('state=open&head=RoyMatus%3Afeature%2Ffocused', calls[1]['path'])
        summary = json.loads(result.stdout.strip().splitlines()[-1])
        self.assertEqual('accepted-not-completed', summary['Status'])
        self.assertEqual(SHA, summary['Sha'])

    def test_wrapper_refuses_duplicate_or_unpushed_or_invalid_requests(self):
        for inputs in (
            {'MOCK_CASE': 'open-pr'}, {'REMOTE_SHA': 'b' * 40},
            {'MOCK_CASE': 'wrong-branch'}, {'MOCK_CASE': 'dirty'},
            {'MOCK_CASE': 'inactive'}, {'TEST_CLASSES': '*'},
            {'TEST_CLASSES': ''}, {'CHECK_REASON': '  '}, {'CHECK_SUITE': 'Android'},
        ):
            with self.subTest(inputs=inputs):
                (self.root / 'api.jsonl').unlink(missing_ok=True)
                result = self.invoke_wrapper(**inputs)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertFalse(any(call['method'] == 'Post' for call in self.api_requests()))

    def test_dispatch_error_is_not_retried_or_reported_as_accepted(self):
        result = self.invoke_wrapper(MOCK_CASE='post-error')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(1, len([call for call in self.api_requests() if call['method'] == 'Post']))
        self.assertNotIn('accepted-not-completed', result.stdout)


if __name__ == '__main__':
    unittest.main()
