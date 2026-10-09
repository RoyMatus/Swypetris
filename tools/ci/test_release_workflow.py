"""Exercise the release routing and publication conditions in the actual workflow.

actionlint validates YAML/expression syntax separately; these tests exercise the
small boolean expressions used here, not a replacement GitHub Actions engine.
"""

import re
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).resolve().parents[2] / ".github/workflows/release-check.yml"


def condition(expression, event, ref, results=None, cancelled=False, success=True):
    expression = expression.removeprefix("${{").removesuffix("}}").strip()
    for key, value in {
        'github.event_name': event, 'github.ref': ref,
        'needs.regression.result': (results or {}).get('regression', 'success'),
        'needs.instrumented-tests.result': (results or {}).get('instrumented-tests', 'success'),
        'needs.signed-release.result': (results or {}).get('signed-release', 'success'),
    }.items():
        expression = expression.replace(key, repr(value))
    expression = expression.replace('cancelled()', str(cancelled)).replace('always()', 'True')
    expression = expression.replace('success()', str(success)).replace('&&', ' and ').replace('||', ' or ')
    expression = re.sub(r'!(?!=)', ' not ', expression)
    # These workflow expressions consist only of booleans and string comparisons.
    return eval(expression.strip(), {'__builtins__': {}}, {})


class ReleaseWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.text = WORKFLOW.read_text(encoding='utf-8')
        self.instrumented = self.text.split('  instrumented-tests:\n', 1)[1].split('  signed-release:\n', 1)[0]
        self.publish = self.text.split('  publish:\n', 1)[1]

    def step_condition(self, name):
        block = self.instrumented.split(f'      - name: {name}\n', 1)[1]
        return re.search(r'^        if: (.+)$', block, re.M)[1]

    def test_main_gate_and_tag_manual_regression_are_exclusive(self):
        gate = self.step_condition('Verify same-commit full Windows regression')
        regression = self.step_condition('Android instrumentation and launch smoke test')
        for event, ref, main in (
            ('push', 'refs/heads/main', True), ('push', 'refs/tags/v1.2.3', False),
            ('workflow_dispatch', 'refs/heads/main', False),
            ('workflow_dispatch', 'refs/tags/v1.2.3', False),
        ):
            with self.subTest(event=event, ref=ref):
                self.assertEqual(main, condition(gate, event, ref))
                self.assertEqual(not main, condition(regression, event, ref))
        self.assertNotRegex(self.instrumented, r'^    if:', 'The prerequisite job must not be skipped',)
        self.assertIn('CHECK_MODE: full', self.instrumented)
        self.assertIn("tags: ['v*']", self.text)
        self.assertIn("paths: ['gradle.properties']", self.text)
        self.assertIn('workflow_dispatch:', self.text)

    def test_publish_requires_every_success_and_never_tag_manual_or_cancelled(self):
        expression = re.search(r'^    if: (.+)$', self.publish, re.M)[1]
        self.assertTrue(condition(expression, 'push', 'refs/heads/main'))
        for prerequisite in ('regression', 'instrumented-tests', 'signed-release'):
            for result in ('failure', 'cancelled', 'skipped'):
                with self.subTest(prerequisite=prerequisite, result=result):
                    self.assertFalse(condition(expression, 'push', 'refs/heads/main', {prerequisite: result}))
        self.assertFalse(condition(expression, 'push', 'refs/tags/v1.2.3'))
        self.assertFalse(condition(expression, 'workflow_dispatch', 'refs/heads/main'))
        self.assertFalse(condition(expression, 'push', 'refs/heads/main', cancelled=True))
        self.assertIn('needs: [regression, instrumented-tests, signed-release]', self.publish)
        self.assertIn('Recheck current full CI attempt before publication', self.publish)

    def test_main_report_upload_retains_failed_report_collection_only_for_tag_manual(self):
        expression = self.step_condition('Save instrumentation reports')
        self.assertFalse(condition(expression, 'push', 'refs/heads/main', success=False))
        self.assertTrue(condition(expression, 'workflow_dispatch', 'refs/heads/main', success=False))


if __name__ == '__main__':
    unittest.main()
