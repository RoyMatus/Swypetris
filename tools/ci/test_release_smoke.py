"""Keep original release failures and independently attempt all diagnostics."""

from contextlib import redirect_stderr
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

from release_smoke import smoke_diagnostics


class ReleaseSmokeDiagnosticsTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.output = Path(self.directory.name)
        self.adb = Mock(return_value='normal log')
        self.observations = ['clicked settings']
        self.stderr = io.StringIO()

    def test_success_retains_observations_log_and_cleanup(self):
        with smoke_diagnostics(self.adb, self.output, self.observations):
            pass
        self.assertEqual(self.observations, json.loads((self.output / 'observations.json').read_text()))
        self.assertEqual('normal log', (self.output / 'logcat.txt').read_text())
        self.adb.assert_any_call('shell', 'rm', '/sdcard/swypetris-release-smoke.xml')

    def test_ui_error_survives_log_and_cleanup_failures(self):
        self.adb.side_effect = subprocess.CalledProcessError(1, 'adb')
        original = RuntimeError('UI did not expose Continue')
        with redirect_stderr(self.stderr), self.assertRaises(RuntimeError) as caught:
            with smoke_diagnostics(self.adb, self.output, self.observations):
                raise original
        self.assertIs(caught.exception, original)
        self.assertEqual(2, self.adb.call_count)
        self.assertTrue((self.output / 'observations.json').exists())
        self.assertIn('diagnostics failed', self.stderr.getvalue())

    def test_passing_ui_with_missing_log_still_fails(self):
        self.adb.side_effect = [subprocess.TimeoutExpired('adb', 40), '']
        with redirect_stderr(self.stderr), self.assertRaisesRegex(RuntimeError, 'diagnostics are incomplete'):
            with smoke_diagnostics(self.adb, self.output, self.observations):
                pass
        self.assertTrue((self.output / 'observations.json').exists())
        self.assertEqual(2, self.adb.call_count)

    def test_observation_write_failure_does_not_prevent_other_diagnostics(self):
        original_write = Path.write_text

        def write(path, *args, **kwargs):
            if path.name == 'observations.json':
                raise OSError('disk unavailable')
            return original_write(path, *args, **kwargs)

        with patch.object(Path, 'write_text', write), redirect_stderr(self.stderr):
            with self.assertRaisesRegex(RuntimeError, 'diagnostics are incomplete'):
                with smoke_diagnostics(self.adb, self.output, self.observations):
                    pass
        self.assertTrue((self.output / 'logcat.txt').exists())
        self.assertEqual(2, self.adb.call_count)

    def test_android_crash_is_still_rejected(self):
        self.adb.return_value = 'FATAL EXCEPTION\nProcess: ru.itoltec.swypetris'
        with self.assertRaisesRegex(RuntimeError, 'runtime crash'):
            with smoke_diagnostics(self.adb, self.output, self.observations):
                pass

    def test_cleanup_failure_after_passing_ui_is_not_silenced(self):
        self.adb.side_effect = ['normal log', subprocess.CalledProcessError(1, 'adb')]
        with redirect_stderr(self.stderr), self.assertRaisesRegex(RuntimeError, 'diagnostics are incomplete'):
            with smoke_diagnostics(self.adb, self.output, self.observations):
                pass
        self.assertTrue((self.output / 'logcat.txt').exists())


if __name__ == '__main__':
    unittest.main()
