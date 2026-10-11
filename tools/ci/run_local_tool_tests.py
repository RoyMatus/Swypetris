"""Run the complete tool suite, rejecting unexpected platform skips."""
import os
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))


def main():
    suite = unittest.defaultTestLoader.discover('tools/ci', pattern='test_*.py')
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    unexpected = [(test.id(), reason) for test, reason in result.skipped
                  if not (os.name == 'nt' and test.id().startswith('test_cloud_android_checks.CloudAndroidChecksTests.'))]
    if unexpected:
        print('Unexpected skipped tool tests:', unexpected, file=sys.stderr)
    return 0 if result.wasSuccessful() and result.testsRun > len(result.skipped) and not unexpected else 1


if __name__ == '__main__':
    raise SystemExit(main())
