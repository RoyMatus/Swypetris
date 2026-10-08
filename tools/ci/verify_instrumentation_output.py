"""Validate raw AndroidJUnitRunner output from an explicitly selected emulator."""

import re
import sys
from pathlib import Path


def verify(mode: str, classes: str, output: str) -> None:
    expected_count = None
    current_class = None
    passed_classes = set()
    passed = skipped = 0
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: numtests="):
            expected_count = int(line.partition("=")[2])
        elif line.startswith("INSTRUMENTATION_STATUS: class="):
            current_class = line.partition("=")[2]
        elif line == "INSTRUMENTATION_STATUS_CODE: 0":
            passed += 1
            if current_class:
                passed_classes.add(current_class)
        elif line == "INSTRUMENTATION_STATUS_CODE: -4":
            skipped += 1
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: ") and line not in {
            "INSTRUMENTATION_STATUS_CODE: 1",
        }:
            raise ValueError(f"Android test failed: {line}")

    if expected_count is None or passed == 0 or passed + skipped != expected_count:
        raise ValueError(f"Incomplete Android run: {passed} passed, {skipped} skipped, expected {expected_count}")
    if not re.search(rf"(?m)^OK \({expected_count} tests?\)$", output):
        raise ValueError("AndroidJUnitRunner did not report success")
    if not re.search(r"(?m)^INSTRUMENTATION_CODE: -1$", output):
        raise ValueError("Instrumentation did not finish successfully")
    if mode == "selected":
        missing = set(classes.split(",")) - passed_classes
        if missing:
            raise ValueError(f"Selected Android classes did not execute: {', '.join(sorted(missing))}")
    elif mode != "full":
        raise ValueError(f"Unknown Android check mode: {mode}")
    print(f"Android tests: {passed} passed, {skipped} skipped, 0 failures")


if __name__ == "__main__":
    try:
        verify(sys.argv[1], sys.argv[2], Path(sys.argv[3]).read_text(encoding="utf-8"))
    except (IndexError, OSError, ValueError) as error:
        raise SystemExit(str(error)) from error
