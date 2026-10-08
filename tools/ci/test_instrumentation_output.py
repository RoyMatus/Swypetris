"""Focused checks for the Windows runner's raw instrumentation gate."""

import unittest

from verify_instrumentation_output import verify


GOOD = """INSTRUMENTATION_STATUS: numtests=2
INSTRUMENTATION_STATUS: class=example.OneTest
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=example.OneTest
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=example.TwoTest
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=example.TwoTest
INSTRUMENTATION_STATUS_CODE: -4
INSTRUMENTATION_RESULT: stream=

Time: 1.0

OK (2 tests)

INSTRUMENTATION_CODE: -1
"""


class InstrumentationOutputTest(unittest.TestCase):
    def test_full_accepts_pass_and_skip(self):
        verify("full", "", GOOD)

    def test_selected_rejects_skipped_class(self):
        with self.assertRaisesRegex(ValueError, "example.TwoTest"):
            verify("selected", "example.OneTest,example.TwoTest", GOOD)

    def test_rejects_missing_completion(self):
        with self.assertRaisesRegex(ValueError, "finish successfully"):
            verify("full", "", GOOD.replace("INSTRUMENTATION_CODE: -1", ""))

    def test_rejects_failed_or_empty_run(self):
        with self.assertRaisesRegex(ValueError, "failed"):
            verify("full", "", GOOD.replace("INSTRUMENTATION_STATUS_CODE: 0", "INSTRUMENTATION_STATUS_CODE: -2"))
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            verify("full", "", GOOD.replace("INSTRUMENTATION_STATUS_CODE: 0", "INSTRUMENTATION_STATUS_CODE: -4"))


if __name__ == "__main__":
    unittest.main()
