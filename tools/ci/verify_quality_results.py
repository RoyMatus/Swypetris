"""Fail closed when required quality steps or their report evidence are absent."""

import json
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def verify_steps(outputs: dict, steps: dict) -> None:
    mode = outputs.get("mode")
    if mode not in {"none", "selected", "full"}:
        raise ValueError("CI selector did not produce a valid mode")
    required = {"changes"}
    for key in ("github_scripts", "release_scripts", "ci_scripts"):
        if outputs.get(key) not in {"true", "false"}:
            raise ValueError(f"CI selector did not produce a valid {key} flag")
        if outputs[key] == "true":
            required.add(key)
    if mode != "none":
        required.update({"detekt", "build", "jvm", "quality_reports", "device", "sonar"})
    missing = sorted(key for key in required if steps.get(key, {}).get("outcome") != "success")
    if missing:
        raise ValueError(f"Required CI steps did not succeed: {', '.join(missing)}")
    print(f"Required CI steps succeeded ({mode})")


def verify_reports(build: Path) -> None:
    reports = list((build / "test-results/testDebugUnitTest").glob("TEST-*.xml"))
    executed = 0
    if not reports:
        raise ValueError("No debug JVM test reports")
    for report in reports:
        for case in ET.parse(report).getroot().iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                raise ValueError(f"Failed JVM test in {report}")
            if case.find("skipped") is None:
                executed += 1
    if not executed:
        raise ValueError("No JVM tests executed")
    coverage = build / "reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"
    counters = ET.parse(coverage).getroot().findall("counter")
    if not any(c.get("type") == "LINE" and int(c.get("covered", "0")) > 0 for c in counters):
        raise ValueError("JaCoCo XML contains no covered production lines")
    lint = ET.parse(build / "reports/lint-results-debug.xml").getroot()
    if any(issue.get("severity") in {"Error", "Fatal"} for issue in lint.iter("issue")):
        raise ValueError("Lint XML contains errors")
    for name in ("detekt.html", "detekt.sarif"):
        if not (build / "reports/detekt" / name).is_file():
            raise ValueError(f"Missing detekt report: {name}")
    print(f"Quality reports verified: {executed} JVM tests, nonempty JaCoCo, lint, detekt")


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["--steps"]:
            verify_steps(json.loads(os.environ["CHECK_OUTPUTS"]), json.loads(os.environ["CHECK_STEPS"]))
        else:
            verify_reports(Path("app/build"))
    except (ValueError, KeyError, OSError, ET.ParseError) as error:
        raise SystemExit(str(error)) from error
