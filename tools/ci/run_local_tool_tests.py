"""Run the complete tool suite, rejecting unexpected platform skips."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))


def main():
    # Retired Linux cloud-shell coverage is optional, never a Windows merge gate.
    loader = unittest.defaultTestLoader
    suite = unittest.TestSuite(loader.loadTestsFromName(path.stem)
                               for path in sorted(Path('tools/ci').glob('test_*.py'))
                               if path.name != 'test_cloud_android_checks.py')
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    unexpected = [(test.id(), reason) for test, reason in result.skipped]
    if unexpected:
        print('Unexpected skipped tool tests:', unexpected, file=sys.stderr)
    return 0 if result.wasSuccessful() and result.testsRun > len(result.skipped) and not unexpected else 1


if __name__ == '__main__':
    raise SystemExit(main())
