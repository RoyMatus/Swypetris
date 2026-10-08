"""Reuse the same-commit main CI gate before releasing, without rescanning Sonar."""

import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request


def gate_result(runs: list[dict], sha: str) -> bool:
    matching = [run for run in runs if run.get("head_sha") == sha
                and run.get("event") == "push" and run.get("head_branch") == "main"]
    if not matching:
        return False
    latest = max(matching, key=lambda run: (run["id"], run.get("run_attempt", 1)))
    if latest["status"] != "completed":
        return False
    if latest.get("conclusion") != "success":
        raise ValueError(f"Android CI did not pass for this commit: {latest.get('conclusion')}")
    return True


def main() -> None:
    repository = os.environ["GITHUB_REPOSITORY"]
    sha = os.environ["GITHUB_SHA"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid repository or commit")
    query = urllib.parse.urlencode(dict(head_sha=sha, event="push", branch="main", per_page=100))
    url = f"https://api.github.com/repos/{repository}/actions/workflows/ci.yml/runs?{query}"
    request = urllib.request.Request(url, headers={
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    })
    deadline = time.monotonic() + 1800
    while time.monotonic() < deadline:
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)
        if result.get("total_count", 0) > 100:
            raise ValueError("Too many CI runs to assess completely")
        if gate_result(result["workflow_runs"], sha):
            print("Same-commit Android CI passed, including its Sonar Quality Gate")
            return
        time.sleep(30)
    raise ValueError("Timed out waiting for same-commit main Android CI")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, urllib.error.URLError) as error:
        raise SystemExit(str(error)) from error
