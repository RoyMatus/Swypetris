"""Resolve trusted release candidates without executing candidate-controlled code."""

import argparse
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from wait_for_android_ci import GitHub, verify_latest

WORKFLOW = ".github/workflows/ci.yml"
VERSION = r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"


def commit_sha(value: str) -> str:
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{40}", value):
        raise ValueError("Invalid candidate commit SHA")
    return value


def optional(api: GitHub, path: str):
    try:
        return api.read(path)
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        return None


def tag_commit(api: GitHub, tag: str, missing_ok: bool = False) -> str | None:
    if not re.fullmatch("v" + VERSION, tag):
        raise ValueError("Invalid release tag")
    ref = optional(api, f"git/ref/tags/{tag}")
    if ref is None:
        if missing_ok:
            return None
        raise ValueError("Release tag does not exist")
    if ref.get("ref") != f"refs/tags/{tag}":
        raise ValueError("Unexpected tag ref")
    obj = ref["object"]
    for _ in range(5):
        if obj.get("type") != "tag":
            break
        annotated = api.read(f"git/tags/{commit_sha(obj.get('sha'))}")
        if annotated.get("sha") != obj["sha"]:
            raise ValueError("Annotated tag identity mismatch")
        obj = annotated["object"]
    if obj.get("type") != "commit":
        raise ValueError("Tag does not resolve to a commit within five levels")
    return commit_sha(obj.get("sha"))


def validate_tag(api: GitHub, sha: str, version: str) -> None:
    commit_sha(sha)
    if not re.fullmatch(VERSION, version):
        raise ValueError("Invalid release version")
    tag = "v" + version
    target = tag_commit(api, tag, missing_ok=True)
    if target is not None and target != sha:
        raise ValueError("Existing release tag points to a different commit")
    if optional(api, f"releases/tags/{tag}") is not None:
        raise ValueError("Release already exists; publication is never overwritten")


def create_tag_ref(api: GitHub, tag: str, sha: str) -> None:
    request = urllib.request.Request(
        api.base + "git/refs", method="POST",
        headers=dict(api.headers, **{"Content-Type": "application/json"}),
        data=json.dumps(dict(ref=f"refs/tags/{tag}", sha=sha)).encode("utf-8"))
    with api.opener.open(request, timeout=60) as response:
        if response.status != 201:
            raise ValueError("Unexpected tag creation response")


def establish_tag(api: GitHub, sha: str, version: str) -> None:
    """Atomically establish an immutable tag before gh release create --verify-tag."""
    validate_tag(api, sha, version)
    tag = "v" + version
    if tag_commit(api, tag, missing_ok=True) is None:
        try:
            create_tag_ref(api, tag, sha)
        except urllib.error.HTTPError as error:
            if error.code != 422:
                raise
            # A concurrent creator can win; only our exact commit is acceptable.
            if tag_commit(api, tag) != sha:
                raise ValueError("Concurrent release tag points to a different commit") from error
    if tag_commit(api, tag) != sha:
        raise ValueError("Established release tag points to a different commit")
    validate_tag(api, sha, version)


def main_ancestor(api: GitHub, sha: str) -> None:
    comparison = api.read(f"compare/{commit_sha(sha)}...main")
    if (comparison.get("status") not in {"ahead", "identical"}
            or comparison.get("base_commit", {}).get("sha") != sha
            or comparison.get("merge_base_commit", {}).get("sha") != sha):
        raise ValueError("Candidate is not an ancestor of protected main")


def version_at(api: GitHub, sha: str) -> str:
    content = api.read(f"contents/gradle.properties?ref={commit_sha(sha)}")
    if content.get("type") != "file" or content.get("encoding") != "base64":
        raise ValueError("Missing Gradle release configuration")
    text = base64.b64decode(content["content"], validate=False).decode("utf-8-sig")
    values = re.findall(r"(?m)^swypetrisVersion\s*=\s*(.*?)\s*$", text)
    if len(values) != 1 or not re.fullmatch(VERSION, values[0]):
        raise ValueError("Missing, duplicate or invalid release version")
    return values[0]


def trusted_trigger(api: GitHub, repository: str, event: dict) -> dict:
    if event.get("action") != "completed" or event.get("repository", {}).get("full_name") != repository:
        raise ValueError("Untrusted CI completion event")
    trigger = event["workflow_run"]
    run_id, attempt = trigger["id"], trigger["run_attempt"]
    if type(run_id) is not int or type(attempt) is not int or min(run_id, attempt) < 1:
        raise ValueError("Invalid triggering run identity")
    workflow = api.read("actions/workflows/ci.yml")
    if (workflow.get("path") != WORKFLOW or workflow.get("state") != "active"
            or type(workflow.get("id")) is not int or workflow["id"] < 1):
        raise ValueError("CI workflow identity/state mismatch")
    run = api.read(f"actions/runs/{run_id}/attempts/{attempt}")
    for candidate in (trigger, run):
        if (candidate.get("id") != run_id or candidate.get("run_attempt") != attempt
                or candidate.get("repository", {}).get("full_name") != repository
                or candidate.get("head_repository", {}).get("full_name") != repository
                or candidate.get("workflow_id") != workflow.get("id")
                or candidate.get("path") != WORKFLOW or candidate.get("event") != "push"
                or candidate.get("head_branch") != "main" or candidate.get("status") != "completed"
                or candidate.get("conclusion") != "success"):
            raise ValueError("Untrusted or unsuccessful triggering CI run")
    sha = commit_sha(run.get("head_sha"))
    if trigger.get("head_sha") != sha:
        raise ValueError("Trigger SHA differs from API run")
    query = urllib.parse.urlencode(dict(head_sha=sha, event="push", branch="main"))
    runs = api.collection(f"actions/workflows/ci.yml/runs?{query}", "workflow_runs")
    if not runs:
        raise ValueError("Missing source CI run")
    latest = max(runs, key=lambda item: (item["id"], item["run_attempt"]))
    if ((latest["id"], latest["run_attempt"]) != (run_id, attempt)
            or latest.get("head_sha") != sha or latest.get("status") != "completed"
            or latest.get("conclusion") != "success"):
        raise ValueError("Trigger is not the latest successful CI attempt")
    return run


def resolve(api: GitHub, repository: str, event_name: str, event: dict, ref: str, workflow_sha: str) -> dict:
    if event.get("repository", {}).get("full_name") != repository:
        raise ValueError("Event repository mismatch")
    automatic = event_name == "workflow_run"
    run = None
    if automatic:
        if ref != "refs/heads/main":
            raise ValueError("Automatic release control workflow must run from main")
        if event.get("action") != "completed":
            raise ValueError("CI event is not completed")
        run = trusted_trigger(api, repository, event)
        sha = run["head_sha"]
        commit = api.read(f"git/commits/{sha}")
        if commit.get("sha") != sha or not commit.get("parents"):
            raise ValueError("Candidate has no verified first parent")
        parent = commit_sha(commit["parents"][0]["sha"])
        before = api.read(f"contents/gradle.properties?ref={parent}")
        after = api.read(f"contents/gradle.properties?ref={sha}")
        if not before.get("sha") or not after.get("sha") or before["sha"] == after["sha"]:
            raise ValueError("Release configuration did not change against first parent")
    elif event_name == "workflow_dispatch" and ref == "refs/heads/main":
        tag = (event.get("inputs") or {}).get("tag", "")
        sha = tag_commit(api, tag) if tag else commit_sha(workflow_sha)
    else:
        raise ValueError("Release verification requires main manual dispatch or trusted CI completion")
    main_ancestor(api, sha)
    version = version_at(api, sha)
    if not automatic and tag and tag != "v" + version:
        raise ValueError("Requested tag differs from candidate version")
    if automatic:
        validate_tag(api, sha, version)
    return dict(sha=sha, version=version, publish="true" if automatic else "false",
                source_run_id=run["id"] if run else 0, source_attempt=run["run_attempt"] if run else 0)


def provenance(api: GitHub, repository: str, sha: str, event: dict, root: Path) -> dict:
    run = trusted_trigger(api, repository, event)
    if run["head_sha"] != commit_sha(sha):
        raise ValueError("Publication SHA differs from triggering CI")
    source = verify_latest(api, repository, sha, root)
    if source is None or (source["run_id"], source["attempt"]) != (run["id"], run["run_attempt"]):
        raise ValueError("Publication source CI changed")
    return dict(schema=1, repository=repository, sha=sha,
                release_run_id=int(os.environ["GITHUB_RUN_ID"]),
                release_run_attempt=int(os.environ["GITHUB_RUN_ATTEMPT"]),
                source_run_id=source["run_id"], source_run_attempt=source["attempt"],
                source_artifact_id=source["artifact_id"], source_artifact_digest=source["artifact_digest"])


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("resolve")
    tag = sub.add_parser("tag")
    tag.add_argument("--sha", required=True)
    tag.add_argument("--version", required=True)
    tag.add_argument("--create", action="store_true")
    proof = sub.add_parser("provenance")
    proof.add_argument("--sha", required=True)
    proof.add_argument("--output", default="publication-provenance.json")
    args = parser.parse_args()
    repository = os.environ["GITHUB_REPOSITORY"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid repository")
    api = GitHub(repository, os.environ["GITHUB_TOKEN"])
    if args.command == "tag":
        if args.create and (os.environ["GITHUB_EVENT_NAME"] != "workflow_run"
                            or os.environ["GITHUB_REF"] != "refs/heads/main"):
            raise ValueError("Tag creation requires the automatic main publication path")
        (establish_tag if args.create else validate_tag)(api, args.sha, args.version)
        return
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    if args.command == "resolve":
        result = resolve(api, repository, os.environ["GITHUB_EVENT_NAME"], event,
                         os.environ["GITHUB_REF"], os.environ["GITHUB_SHA"])
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            for key, value in result.items():
                output.write(f"{key}={value}\n")
    else:
        if os.environ["GITHUB_EVENT_NAME"] != "workflow_run":
            raise ValueError("Manual verification cannot authorize publication")
        result = provenance(api, repository, args.sha, event, Path.cwd())
        Path(args.output).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
