"""Exercise routing from the actual workflow, not a replacement Actions engine."""

import re
import unittest
from pathlib import Path

WORKFLOW = Path(__file__).resolve().parents[2] / 'tools/ci/legacy-workflows/release-check.yml'


def job(text, name):
    return re.search(rf'^  {name}:\n(.*?)(?=^  [\w-]+:|\Z)', text, re.M | re.S)[1]


def condition(block, values, cancelled=False):
    expression = re.search(r'^    if: >-\n((?:      .+\n)+)', block, re.M)[1]
    for key, value in sorted(values.items(), key=lambda item: -len(item[0])):
        expression = expression.replace(key, repr(value))
    expression = expression.replace('cancelled()', str(cancelled)).replace('&&', ' and ').replace('||', ' or ')
    expression = re.sub(r'!(?!=)', ' not ', expression)
    return eval(' '.join(expression.split()), {'__builtins__': {}}, {})


class ReleaseWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.text = WORKFLOW.read_text(encoding='utf-8')

    def test_privileged_context_excludes_fork_pr_failure_and_non_main_dispatch(self):
        values = {'github.ref': 'refs/heads/main', 'github.event_name': 'workflow_run',
                  'github.event.workflow_run.conclusion': 'success',
                  'github.event.workflow_run.event': 'push',
                  'github.event.workflow_run.head_branch': 'main',
                  'github.event.workflow_run.head_repository.full_name': 'owner/repo',
                  'github.repository': 'owner/repo'}
        block = job(self.text, 'context')
        self.assertTrue(condition(block, values))
        for key, invalid in (
            ('github.ref', 'refs/heads/unreviewed'),
            ('github.event.workflow_run.conclusion', 'failure'),
            ('github.event.workflow_run.event', 'pull_request'),
            ('github.event.workflow_run.head_branch', 'candidate'),
            ('github.event.workflow_run.head_repository.full_name', 'fork/repo'),
        ):
            with self.subTest(key=key):
                self.assertFalse(condition(block, dict(values, **{key: invalid})))
        self.assertTrue(condition(block, dict(values, **{'github.event_name': 'workflow_dispatch'})))
        self.assertFalse(condition(block, dict(values, **{'github.event_name': 'workflow_dispatch', 'github.ref': 'refs/tags/v1.2.3'})))
        for name in ('regression', 'signed-release'):
            guarded = job(self.text, name)
            self.assertTrue(condition(guarded, values))
            self.assertFalse(condition(guarded, dict(values, **{'github.event.workflow_run.head_repository.full_name': 'fork/repo'})))
            self.assertFalse(condition(guarded, dict(values, **{'github.event.workflow_run.event': 'pull_request'})))
            self.assertFalse(condition(guarded, dict(values, **{'github.ref': 'refs/heads/unreviewed'})))

    def test_publication_requires_every_gate_and_auto_route(self):
        block = job(self.text, 'publish')
        values = {'github.event_name': 'workflow_run', 'needs.context.outputs.publish': 'true',
                  'github.event.workflow_run.head_repository.full_name': 'owner/repo', 'github.repository': 'owner/repo',
                  'needs.regression.result': 'success', 'needs.signed-release.result': 'success'}
        self.assertTrue(condition(block, values))
        for dependency in ('regression', 'signed-release'):
            for invalid in ('failure', 'skipped', 'cancelled'):
                self.assertFalse(condition(block, dict(values, **{f'needs.{dependency}.result': invalid})))
        self.assertFalse(condition(block, values, cancelled=True))
        self.assertFalse(condition(block, dict(values, **{'github.event_name': 'workflow_dispatch'})))
        self.assertFalse(condition(block, dict(values, **{'needs.context.outputs.publish': 'false'})))
        self.assertFalse(condition(block, dict(values, **{'github.event.workflow_run.head_repository.full_name': 'fork/repo'})))
        self.assertIn('needs: [context, regression, signed-release]', block)

    def test_candidate_control_and_evidence_are_explicit(self):
        context = job(self.text, 'context')
        self.assertIn('release_context.py resolve', context)
        self.assertNotIn('secrets.', context)
        for name in ('regression', 'signed-release', 'publish'):
            block = job(self.text, name)
            self.assertIn('ref: ${{ github.sha }}\n          path: control', block)
            self.assertIn('ref: ${{ needs.context.outputs.sha }}\n          path: candidate', block)
            self.assertIn('working-directory: candidate', block)
        signed = job(self.text, 'signed-release')
        self.assertIn('environment: release-signing', signed)
        self.assertIn('needs: [context, regression]', signed)
        self.assertNotIn('--rerun-tasks', signed)
        gate = job(self.text, 'regression')
        self.assertIn('--run-id "$SOURCE_RUN" --attempt "$SOURCE_ATTEMPT" --no-wait', gate)
        publish = job(self.text, 'publish')
        self.assertIn('release_context.py provenance --sha "$RELEASE_SHA"', publish)
        self.assertIn('--version "$version" --create', publish)
        self.assertEqual(2, publish.count('gh release create "v$version" --verify-tag'))
        self.assertNotIn('--target "$GITHUB_SHA"', publish)

    def test_manual_runtime_and_pins_match_main_contract(self):
        self.assertIn('workflow_run:', self.text)
        self.assertNotIn("tags: ['v*']", self.text)
        regression = job(self.text, 'regression')
        self.assertIn('if: needs.context.outputs.publish != \'true\'', regression)
        self.assertIn('script: cd candidate && bash ../control/tools/ci/run_cloud_android_checks.sh', regression)
        for name in ('regression', 'signed-release'):
            block = job(self.text, name)
            self.assertIn("emulator-build: '15917651'", block)
            self.assertIn('-no-window -gpu swangle -feature -Vulkan', block)
        for action in re.findall(r'uses: ([^\s]+)', self.text):
            self.assertRegex(action, r'@[0-9a-f]{40}$')
        self.assertEqual(self.text.count('uses: actions/checkout@'), self.text.count('persist-credentials: false'))


if __name__ == '__main__':
    unittest.main()
