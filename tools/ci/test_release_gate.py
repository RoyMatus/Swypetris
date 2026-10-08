"""Release must depend on a successful main CI run for exactly its own commit."""

import unittest

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
