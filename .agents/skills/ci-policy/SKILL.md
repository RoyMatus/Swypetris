---
name: ci-policy
description: Change or review Swypetris GitHub Actions CI policy, required checks, and release gates.
---

# CI policy

Before major/substantial release preparation, follow [RELEASE-COMPLIANCE.md](../../../publishing/RELEASE-COMPLIANCE.md): open and read the complete current RuStore requirements and review current GitHub sources, audit both platforms together, and complete the candidate-specific report. Resolve mandatory violations and unverified external/owner items before publication; CI success alone is insufficient.

Read [publishing/CI.md](../../../publishing/CI.md), [publishing/POLICY.md](../../../publishing/POLICY.md), the affected workflow and `tools/ci/select_checks.py`; these own the matrix, commands and enforcement. Inspect actual GitHub rules before changing job names, triggers or conditions.

Preserve the stable required `android` check from GitHub Actions and conservative full-regression fallback. Skip expensive steps inside the stable job where the selector permits it. Do not weaken real lint, test, report or Sonar Quality Gate failures. Preserve report upload after failures and distinguish each check's outcome. Validate focused selector/report tests for changed policy.

Use the diagnosed host profile for development work. Local-only tooling uses explicit path mappings and tool behavior tests; unknown inputs and actual CI execution/gate changes remain conservative. Optional remote focused checks are for constrained/no-local-execution work before an open PR, with explicit classes, SHA and reason; they never replace final required CI or authorize publication. Avoid duplicate optional/PR runs and full release workflows for ordinary development feedback.

Keep concurrency scoped to the same PR/ref; do not cancel another process's runs. Verify required CI on the current PR commit after any main update. Ordinary CI and release verification are separate: `release-check.yml` publishes only after trusted successful main-push Android CI completion (`workflow_run`) for a version configuration change, with exact-SHA schema 3 evidence and independent signed release checks. Manual dispatch is main-only, optionally verifies an existing version tag on main history, and never publishes; tag pushes do not trigger signing. Keep control scripts pinned to the workflow SHA and candidate source checked out separately after context validation. Signing uses the main-only `release-signing` Environment; retain protected release tags, provenance validation and the selected Windows tool prerequisite of the stable `android` check. Signing secrets stay private and public certificate fingerprints must match. Do not publish or change access without task authorization.
