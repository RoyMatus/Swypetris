"""Produce and verify attempt-bound evidence of the cloud full debug regression."""

import hashlib
import io
import json
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

# Embedded Python on the local Windows runner omits the script directory.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from verify_instrumentation_output import verify
from verify_quality_results import verify_steps

WORKFLOW = ".github/workflows/ci.yml"
FILES = {
    "app-debug.apk": "app/build/outputs/apk/debug/app-debug.apk",
    "app-debug-androidTest.apk": "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
    "instrumentation.txt": "app/build/outputs/androidTest-results/windows/instrumentation.txt",
    "smoke.txt": "app/build/outputs/androidTest-results/windows/smoke.txt",
    "environment.json": "app/build/outputs/androidTest-results/windows/environment.json",
}
REQUIRED_STEPS = {
    "Select checks for changed files", "Analyze Kotlin", "Build and lint app",
    "Run JVM tests and coverage", "Validate quality reports", "SonarQube Cloud analysis",
    "Run affected Android tests and smoke", "Build full regression evidence",
    "Save full regression evidence", "Verify required checks executed",
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def configuration(root: Path) -> dict:
    """Hash tracked app/build/CI inputs with git blob IDs (independent of CRLF checkout)."""
    output = subprocess.check_output([
        "git", "ls-tree", "-r", "HEAD", "app/src", "app/build.gradle.kts",
        "app/proguard-rules.pro", "gradle", "gradlew", "gradlew.bat",
        "gradle.properties", "build.gradle.kts", "settings.gradle.kts",
        "config", "tools/ci", ".github/workflows",
    ], cwd=root, text=True)
    return {line.split("\t", 1)[1]: line.split("\t", 1)[0].split()[2]
            for line in output.splitlines()}


def test_inventory(root: Path) -> set[str]:
    tests = set()
    for source in (root / "app/src/androidTest").rglob("*.kt"):
        text = source.read_text(encoding="utf-8-sig")
        if not re.search(r"@Test\b", text):
            continue
        package = re.search(r"(?m)^package ([\w.]+)", text)
        if not package or not re.search(rf"(?m)^class {re.escape(source.stem)}\b", text):
            raise ValueError(f"Cannot determine instrumentation class: {source}")
        methods = re.findall(r"@Test\s+fun\s+(\w+)", text)
        if len(methods) != len(re.findall(r"@Test\b", text)):
            raise ValueError(f"Unsupported instrumentation declaration: {source}")
        tests.update(f"{package[1]}.{source.stem}#{method}" for method in methods)
    if not tests:
        raise ValueError("No instrumentation inventory")
    return tests


def verify_inventory(report: str, root: Path) -> None:
    reported = []
    current_class = current_test = None
    for line in report.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: class="):
            current_class = line.partition("=")[2]
        elif line.startswith("INSTRUMENTATION_STATUS: test="):
            current_test = line.partition("=")[2]
        elif line in {"INSTRUMENTATION_STATUS_CODE: 0", "INSTRUMENTATION_STATUS_CODE: -4"}:
            reported.append(f"{current_class}#{current_test}")
    if len(reported) != len(set(reported)) or set(reported) != test_inventory(root):
        raise ValueError("Incomplete or duplicate instrumentation test inventory")


def validate_payload(proof: dict, payload: dict[str, bytes], root: Path) -> None:
    if proof.get("schema") != 2 or proof.get("mode") != "full" or proof.get("android_classes") != "":
        raise ValueError("Evidence is not an unfiltered full regression")
    if proof.get("instrumentation") != "success" or proof.get("smoke") != "success":
        raise ValueError("Instrumentation/smoke did not succeed")
    if set(payload) != set(FILES) | {"configuration.json"}:
        raise ValueError("Missing or unexpected evidence files")
    if proof.get("sha256") != {name: digest(data) for name, data in payload.items()}:
        raise ValueError("Evidence file hash mismatch")
    if json.loads(payload["configuration.json"]) != configuration(root):
        raise ValueError("Checked configuration differs from the release commit")
    # Keep/hash original bytes, but validate text with the same universal newline
    # handling as Path.read_text in the existing Windows report checker.
    report = "\n".join(payload["instrumentation.txt"].decode("utf-8-sig").splitlines())
    verify("full", "", report)
    verify_inventory(report, root)
    # SmokeTest must actually pass; opt-in test skips remain allowed by the existing policy.
    verify("selected", "ru.itoltec.swypetris.SmokeTest", report)
    if not re.search(r"(?m)^Events injected: 1\s*$", payload["smoke.txt"].decode("utf-8-sig")):
        raise ValueError("Launcher smoke report is incomplete")
    env = json.loads(payload["environment.json"])
    expected = dict(os="Linux", api="35", abi="x86_64", serial="emulator-5556",
                    avd="SwypetrisCI35", mode="full", android_classes="",
                    instrumentation="success", smoke="success")
    if any(env.get(key) != value for key, value in expected.items()):
        raise ValueError("Unexpected cloud regression environment")
    for key in ("java", "emulator", "fingerprint", "instrumentation_command", "emulator_options"):
        if not env.get(key):
            raise ValueError(f"Missing environment detail: {key}")
    if env["instrumentation_command"] != "adb -s emulator-5556 shell am instrument -w -r ru.itoltec.swypetris.test/androidx.test.runner.AndroidJUnitRunner":
        raise ValueError("Full instrumentation command was filtered or changed")
    for name in ("app-debug.apk", "app-debug-androidTest.apk"):
        with zipfile.ZipFile(io.BytesIO(payload[name])) as apk:
            if "AndroidManifest.xml" not in apk.namelist() or apk.testzip() is not None:
                raise ValueError(f"Invalid tested APK: {name}")


def create(root: Path, destination: Path) -> None:
    outputs = json.loads(os.environ["CHECK_OUTPUTS"])
    verify_steps(outputs, json.loads(os.environ["CHECK_STEPS"]))
    if outputs["mode"] != "full" or outputs["android"]:
        raise ValueError("Only full mode can produce release evidence")
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    if sha != os.environ["GITHUB_SHA"]:
        raise ValueError("Checkout differs from the workflow SHA")
    payload = {name: (root / path).read_bytes() for name, path in FILES.items()}
    payload["configuration.json"] = json.dumps(configuration(root), sort_keys=True).encode()
    proof = dict(schema=2, repository=os.environ["GITHUB_REPOSITORY"], workflow=WORKFLOW,
                 workflow_ref=os.environ["GITHUB_WORKFLOW_REF"], sha=sha,
                 event=os.environ["GITHUB_EVENT_NAME"], ref=os.environ["GITHUB_REF"],
                 run_id=int(os.environ["GITHUB_RUN_ID"]), attempt=int(os.environ["GITHUB_RUN_ATTEMPT"]),
                 mode="full", android_classes="", instrumentation="success", smoke="success",
                 sha256={name: digest(data) for name, data in payload.items()})
    validate_payload(proof, payload, root)
    destination.mkdir(parents=True, exist_ok=False)
    for name, data in payload.items():
        (destination / name).write_bytes(data)
    (destination / "evidence.json").write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")


def validate_archive(data: bytes, artifact: dict, run: dict, jobs: list[dict],
                     repository: str, sha: str, workflow_id: int, root: Path) -> dict:
    if (run.get("repository", {}).get("full_name") != repository
            or run.get("workflow_id") != workflow_id or run.get("path") != WORKFLOW
            or run.get("head_sha") != sha or run.get("event") != "push"
            or run.get("head_branch") != "main" or run.get("status") != "completed"
            or run.get("conclusion") != "success"):
        raise ValueError("Untrusted or unsuccessful CI run")
    identity = dict(run_id=run["id"], attempt=run["run_attempt"])
    if (artifact.get("name") != f"full-regression-{run['id']}-{run['run_attempt']}"
            or artifact.get("expired") is not False
            or artifact.get("workflow_run", {}).get("id") != run["id"]
            or artifact.get("workflow_run", {}).get("head_sha") != sha
            or artifact.get("digest") != "sha256:" + digest(data)):
        raise ValueError("Artifact identity/digest mismatch or expired evidence")
    android = [job for job in jobs if job.get("name") == "android"]
    if len(android) != 1:
        raise ValueError("Missing or ambiguous android job")
    job = android[0]
    if (job.get("run_id") != run["id"] or job.get("run_attempt") != run["run_attempt"]
            or job.get("status") != "completed" or job.get("conclusion") != "success"
            or set(job.get("labels", [])) != {"ubuntu-24.04"}):
        raise ValueError("Wrong attempt or unsuccessful Ubuntu job")
    for name in REQUIRED_STEPS:
        steps = [step for step in job.get("steps", []) if step.get("name") == name]
        if len(steps) != 1 or steps[0].get("status") != "completed" or steps[0].get("conclusion") != "success":
            raise ValueError(f"Required evidence step did not succeed: {name}")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or set(names) != set(FILES) | {"configuration.json", "evidence.json"}:
            raise ValueError("Missing, duplicate or unexpected archive entries")
        if sum(item.file_size for item in archive.infolist()) > 256 * 1024 * 1024:
            raise ValueError("Evidence archive exceeds size limit")
        proof = json.loads(archive.read("evidence.json"))
        payload = {name: archive.read(name) for name in names if name != "evidence.json"}
    expected = dict(repository=repository, workflow=WORKFLOW, sha=sha, event="push",
                    ref="refs/heads/main", workflow_ref=f"{repository}/{WORKFLOW}@refs/heads/main", **identity)
    if any(proof.get(key) != value for key, value in expected.items()):
        raise ValueError("Evidence run/SHA/workflow/attempt mismatch")
    validate_payload(proof, payload, root)
    return proof


if __name__ == "__main__":
    create(Path.cwd(), Path("app/build/ci") / f"full-regression-{os.environ['GITHUB_RUN_ID']}-{os.environ['GITHUB_RUN_ATTEMPT']}")
