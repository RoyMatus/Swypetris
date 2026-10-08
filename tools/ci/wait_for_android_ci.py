"""Fail-closed release gate for the latest same-SHA full Windows CI attempt."""

import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from full_regression_proof import validate_archive


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


class ArtifactRedirect(urllib.request.HTTPRedirectHandler):
    """The signed artifact download URL must not receive the GitHub token."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if urllib.parse.urlparse(newurl).scheme != "https":
            raise ValueError("Insecure artifact redirect")
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        redirected.remove_header("Authorization")
        return redirected


class GitHub:
    def __init__(self, repository: str, token: str):
        self.base = f"https://api.github.com/repos/{repository}/"
        self.headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
                        "X-GitHub-Api-Version": "2022-11-28"}
        self.opener = urllib.request.build_opener(ArtifactRedirect())

    def read(self, path: str, binary: bool = False):
        request = urllib.request.Request(self.base + path, headers=self.headers)
        with self.opener.open(request, timeout=60) as response:
            if binary:
                data = response.read(256 * 1024 * 1024 + 1)
                if len(data) > 256 * 1024 * 1024:
                    raise ValueError("Artifact exceeds size limit")
                return data
            return json.load(response)

    def collection(self, path: str, key: str) -> list[dict]:
        result = self.read(path + ("&" if "?" in path else "?") + "per_page=100")
        if result.get("total_count", 0) > 100:
            raise ValueError(f"Incomplete API collection: {key}")
        return result[key]


def verify_latest(api: GitHub, repository: str, sha: str, root: Path) -> dict | None:
    query = urllib.parse.urlencode(dict(head_sha=sha, event="push", branch="main"))
    path = f"actions/workflows/ci.yml/runs?{query}"
    runs = api.collection(path, "workflow_runs")
    if not gate_result(runs, sha):
        return None
    run = max((run for run in runs if run.get("head_sha") == sha
               and run.get("event") == "push" and run.get("head_branch") == "main"),
              key=lambda run: (run["id"], run.get("run_attempt", 1)))
    run_id, attempt = run["id"], run["run_attempt"]
    workflow = api.read("actions/workflows/ci.yml")
    if workflow.get("path") != ".github/workflows/ci.yml" or workflow.get("state") != "active":
        raise ValueError("Android CI workflow identity/state mismatch")
    current = api.read(f"actions/runs/{run_id}/attempts/{attempt}")
    if current.get("id") != run_id or current.get("run_attempt") != attempt:
        raise ValueError("CI attempt identity mismatch")
    artifacts = api.collection(f"actions/runs/{run_id}/artifacts", "artifacts")
    selected = [item for item in artifacts if item.get("name") == f"full-regression-{run_id}-{attempt}"]
    if len(selected) != 1:
        raise ValueError("Missing or ambiguous full regression artifact for the current attempt")
    artifact = selected[0]
    jobs = api.collection(f"actions/runs/{run_id}/attempts/{attempt}/jobs", "jobs")
    data = api.read(f"actions/artifacts/{artifact['id']}/zip", binary=True)
    validate_archive(data, artifact, current, jobs, repository, sha, workflow["id"], root)
    # Do not authorize an older success if a new run/attempt started during download/validation.
    refreshed = api.collection(path, "workflow_runs")
    if not gate_result(refreshed, sha):
        raise ValueError("CI changed while verifying evidence")
    latest = max(refreshed, key=lambda run: (run["id"], run.get("run_attempt", 1)))
    if (latest["id"], latest["run_attempt"]) != (run_id, attempt):
        raise ValueError("CI run/attempt changed while verifying evidence")
    return dict(repository=repository, sha=sha, workflow_id=workflow["id"], run_id=run_id,
                attempt=attempt, artifact_id=artifact["id"], artifact_digest=artifact["digest"],
                mode="full", instrumentation="success", smoke="success")


def main() -> None:
    repository = os.environ["GITHUB_REPOSITORY"]
    sha = os.environ["GITHUB_SHA"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid repository or commit")
    api = GitHub(repository, os.environ["GITHUB_TOKEN"])
    deadline = time.monotonic() + 1800
    while time.monotonic() < deadline:
        result = verify_latest(api, repository, sha, Path.cwd())
        if result is not None:
            destination = Path("app/build/ci/verified-main-regression.json")
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
            print(f"Verified full Windows regression and Quality Gate: run {result['run_id']}, attempt {result['attempt']}")
            return
        time.sleep(30)
    raise ValueError("Timed out waiting for same-commit main Android CI")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, urllib.error.URLError) as error:
        raise SystemExit(str(error)) from error
