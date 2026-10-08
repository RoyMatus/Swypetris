"""Allow legacy debt to shrink, never silently expand it in a later PR."""

import argparse
import hashlib
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

BASELINE = "config/detekt/baseline.xml"
INITIAL_SHA256 = "1834ca40cc3c9c6b2a85e464678b79cc0e2ac1bc845ec2e5be5a6c7a4a6591a2"


def issue_ids(xml: str) -> set[str]:
    root = ET.fromstring(xml)
    if root.tag != "SmellBaseline" or root.find("CurrentIssues") is None:
        raise ValueError("Invalid detekt baseline")
    manual = root.find("ManuallySuppressedIssues")
    if manual is None:
        raise ValueError("Missing manual-suppression section")
    if len(manual):
        raise ValueError("Manual detekt suppressions are not allowed")
    ids = [item.text for item in root.findall("CurrentIssues/ID")]
    if any(not item for item in ids) or len(ids) != len(set(ids)):
        raise ValueError("Empty or duplicate detekt baseline IDs")
    return set(ids)


def verify(current: str, previous: str) -> None:
    added = issue_ids(current) - issue_ids(previous)
    if added:
        raise ValueError(f"Detekt baseline cannot grow: {len(added)} new IDs")


def verify_initial(data: bytes) -> None:
    if hashlib.sha256(data).hexdigest() != INITIAL_SHA256:
        raise ValueError("Initial baseline differs from the reviewed legacy snapshot")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    args = parser.parse_args()
    subprocess.run(["git", "rev-parse", "--verify", f"{args.base}^{{commit}}"], check=True,
                   capture_output=True)
    tree = subprocess.check_output(["git", "ls-tree", "--name-only", args.base, BASELINE],
                                   text=True, encoding="utf-8")
    data = Path(BASELINE).read_bytes()
    current = data.decode("utf-8")
    issue_ids(current)
    if tree.strip():
        previous = subprocess.check_output(["git", "show", f"{args.base}:{BASELINE}"],
                                           text=True, encoding="utf-8")
        verify(current, previous)
        print("Detekt baseline did not grow")
    else:
        verify_initial(data)
        print("Initial detekt baseline adoption; inspect legacy debt in this PR")


if __name__ == "__main__":
    main()
