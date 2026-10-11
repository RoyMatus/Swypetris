"""Release must depend on a successful main CI run for exactly its own commit."""

import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import wait_for_android_ci as gate
from wait_for_android_ci import gate_result


class ReleaseGateTests(unittest.TestCase):
    def run_data(self, **changes):
        run = dict(id=1, head_sha="abc", event="push", head_branch="main",
                   status="completed", conclusion="success")
        return dict(run, **changes)

    def test_exact_success(self):
        self.assertTrue(gate_result([self.run_data()], "abc"))

    def test_other_commits_prs_branches_and_missing_runs_do_not_pass(self):
        for changes in ({"head_sha": "def"}, {"event": "pull_request"}, {"head_branch": "other"}):
            self.assertFalse(gate_result([self.run_data(**changes)], "abc"))
        self.assertFalse(gate_result([], "abc"))

    def test_pending_newer_run_is_not_hidden_by_old_success(self):
        self.assertFalse(gate_result([self.run_data(), self.run_data(id=2, status="in_progress")], "abc"))

    def test_failed_skipped_cancelled_or_neutral_run_rejects_release(self):
        for conclusion in ("failure", "skipped", "cancelled", "neutral", "timed_out"):
            with self.subTest(conclusion=conclusion), self.assertRaises(ValueError):
                gate_result([self.run_data(conclusion=conclusion)], "abc")


class ReleaseGateCliTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.previous = Path.cwd()
        os.chdir(self.directory.name)
        self.addCleanup(os.chdir, self.previous)
        self.sha = 'a' * 40
        self.result = dict(repository='example/repo', sha=self.sha, workflow_id=9,
                           run_id=7, attempt=2, artifact_id=11, artifact_digest='sha256:abc',
                           mode='full', instrumentation='success', smoke='success')
        self.output = Path('app/build/ci/verified-main-regression.json')
        environment = patch.dict(os.environ, GITHUB_REPOSITORY='example/repo',
                                 GITHUB_SHA='b' * 40, GITHUB_TOKEN='test-placeholder')
        environment.start()
        self.addCleanup(environment.stop)

    def invoke(self, args, result):
        with patch('sys.argv', ['wait_for_android_ci.py', *args]), \
                patch.object(gate, 'GitHub') as api, \
                patch.object(gate, 'verify_latest', return_value=result) as verify, \
                patch.object(gate.time, 'sleep') as sleep:
            gate.main()
            return api, verify, sleep

    def test_explicit_sha_overrides_control_checkout_and_writes_exact_source(self):
        api, verify, sleep = self.invoke(
            ['--sha', self.sha, '--run-id', '7', '--attempt', '2', '--no-wait'], self.result)
        verify.assert_called_once_with(api.return_value, 'example/repo', self.sha, Path.cwd())
        sleep.assert_not_called()
        self.assertEqual(self.result, json.loads(self.output.read_text(encoding='utf-8')))

    def test_default_sha_uses_environment(self):
        api, verify, _ = self.invoke(['--no-wait'], dict(self.result, sha='b' * 40))
        verify.assert_called_once_with(api.return_value, 'example/repo', 'b' * 40, Path.cwd())

    def test_wrong_run_or_attempt_never_writes_evidence(self):
        for flags in (['--run-id', '8'], ['--attempt', '3'], ['--run-id', '8', '--attempt', '3']):
            with self.subTest(flags=flags), self.assertRaisesRegex(ValueError, 'triggering run/attempt'):
                self.invoke(['--sha', self.sha, '--no-wait', *flags], self.result)
            self.assertFalse(self.output.exists())

    def test_no_wait_missing_evidence_fails_once_without_sleep(self):
        with patch('sys.argv', ['wait_for_android_ci.py', '--sha', self.sha, '--no-wait']), \
                patch.object(gate, 'GitHub'), \
                patch.object(gate, 'verify_latest', return_value=None) as verify, \
                patch.object(gate.time, 'sleep') as sleep:
            with self.assertRaisesRegex(ValueError, 'without waiting'):
                gate.main()
            verify.assert_called_once()
            sleep.assert_not_called()
        self.assertFalse(self.output.exists())
