"""Reject empty or incorrectly filtered connected Android test runs."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def verify(mode: str, classes: str, directory: Path) -> None:
    reports = list(directory.rglob("TEST-*.xml"))
    if not reports:
        raise ValueError(f"No Android test XML reports found in {directory}")
    executed: set[str] = set()
    failures = 0
    for report in reports:
        root = ET.parse(report).getroot()
        for case in root.iter("testcase"):
            failures += len(case.findall("failure")) + len(case.findall("error"))
            if case.find("skipped") is None:
                executed.add(case.attrib.get("classname", ""))
    if failures or not executed:
        raise ValueError(f"Android tests: {len(executed)} classes executed, {failures} failures")
    if mode == "selected":
        expected = set(classes.split(","))
        missing = expected - executed
        if missing:
            raise ValueError(f"Selected Android classes did not execute: {', '.join(sorted(missing))}")
    print(f"Android tests: {len(executed)} classes executed, 0 failures")


if __name__ == "__main__":
    try:
        verify(sys.argv[1], sys.argv[2], Path("app/build/outputs/androidTest-results/connected/debug"))
    except (IndexError, ValueError, ET.ParseError) as error:
        raise SystemExit(str(error)) from error
