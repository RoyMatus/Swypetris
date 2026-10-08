"""Negative cases for the required-check and report evidence gates."""

import tempfile
import unittest
from pathlib import Path

from verify_quality_results import verify_reports, verify_steps


class RequiredStepTests(unittest.TestCase):
    def outputs(self, mode):
        return dict(mode=mode, github_scripts="false", release_scripts="false", ci_scripts="false")

    def test_documentation_needs_only_successful_selection(self):
        verify_steps(self.outputs("none"), {"changes": {"outcome": "success"}})

    def test_missing_mode_or_flags_fails(self):
        for outputs in ({}, {"mode": "none"}):
            with self.assertRaises(ValueError):
                verify_steps(outputs, {"changes": {"outcome": "success"}})

    def test_skipped_missing_failed_or_masked_step_fails(self):
        keys = ("changes", "detekt", "build", "jvm", "quality_reports", "device", "sonar")
        for mode in ("selected", "full"):
            for key in keys:
                for outcome in ("skipped", "failure", "cancelled", None):
                    steps = {k: {"outcome": "success"} for k in keys}
                    steps[key] = {"outcome": outcome, "conclusion": "success"}
                    with self.subTest(mode=mode, key=key, outcome=outcome):
                        with self.assertRaises(ValueError):
                            verify_steps(self.outputs(mode), steps)
            verify_steps(self.outputs(mode), {k: {"outcome": "success"} for k in keys})

    def test_report_step_must_be_present_for_app_checks(self):
        steps = {key: {"outcome": "success"}
                 for key in ("changes", "detekt", "build", "jvm", "device", "sonar")}
        for mode in ("selected", "full"):
            with self.subTest(mode=mode), self.assertRaisesRegex(ValueError, "quality_reports"):
                verify_steps(self.outputs(mode), steps)

    def test_report_failure_preserves_successful_jvm_outcome(self):
        steps = {key: {"outcome": "success"}
                 for key in ("changes", "detekt", "build", "jvm", "device", "sonar")}
        steps["quality_reports"] = {"outcome": "failure"}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            QualityReportTests().populate(root)
            (root / "reports/lint-results-debug.xml").write_text(
                '<issues><issue severity="Error"/></issues>')
            with self.assertRaisesRegex(ValueError, "Lint XML contains errors"):
                verify_reports(root)
        self.assertEqual("success", steps["jvm"]["outcome"])
        for mode in ("selected", "full"):
            with self.subTest(mode=mode), self.assertRaisesRegex(ValueError, "quality_reports"):
                verify_steps(self.outputs(mode), steps)

    def test_workflow_assigns_report_validation_to_its_own_step(self):
        workflow = Path(".github/workflows/ci.yml").read_text(encoding="utf-8")
        jvm = workflow.split("      - name: Run JVM tests and coverage\n", 1)[1].split("      - name:", 1)[0]
        reports = workflow.split("      - name: Validate quality reports\n", 1)[1].split("      - name:", 1)[0]
        self.assertNotIn("verify_quality_results.py", jvm)
        self.assertIn("jacocoDebugUnitTestReport", jvm)
        self.assertIn("id: quality_reports", reports)
        self.assertIn("!cancelled()", reports)
        self.assertIn("verify_quality_results.py", reports)

    def test_selected_script_check_must_execute(self):
        outputs = self.outputs("none")
        outputs["github_scripts"] = "true"
        with self.assertRaises(ValueError):
            verify_steps(outputs, {"changes": {"outcome": "success"}})


class QualityReportTests(unittest.TestCase):
    def populate(self, root):
        files = {
            "test-results/testDebugUnitTest/TEST-demo.xml": '<testsuite><testcase name="passed"/></testsuite>',
            "reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml": '<report><counter type="LINE" covered="1" missed="2"/></report>',
            "reports/lint-results-debug.xml": '<issues/>',
            "reports/detekt/detekt.html": '<html/>',
            "reports/detekt/detekt.sarif": '{}',
        }
        for name, content in files.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)

    def test_valid_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.populate(root)
            verify_reports(root)

    def test_empty_coverage_skipped_tests_and_lint_errors_fail(self):
        invalid = {
            "test-results/testDebugUnitTest/TEST-demo.xml": '<testsuite><testcase><skipped/></testcase></testsuite>',
            "reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml": '<report><counter type="LINE" covered="0"/></report>',
            "reports/lint-results-debug.xml": '<issues><issue severity="Error"/></issues>',
        }
        for name, content in invalid.items():
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.populate(root)
                (root / name).write_text(content)
                with self.assertRaises(ValueError):
                    verify_reports(root)

    def test_missing_report_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.populate(root)
            (root / "reports/detekt/detekt.sarif").unlink()
            with self.assertRaises(ValueError):
                verify_reports(root)
