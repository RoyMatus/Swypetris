---
name: ci-policy
description: Change or review Swypetris GitHub Actions CI policy, required checks, and release gates.
---

# CI policy

Read [publishing/CI.md](../../../publishing/CI.md), [publishing/POLICY.md](../../../publishing/POLICY.md), the affected workflow and `tools/ci/select_checks.py`; these own the matrix, commands and enforcement. Inspect actual GitHub rules before changing job names, triggers or conditions.

Preserve the stable required `android` check from GitHub Actions and conservative full-regression fallback. Skip expensive steps inside the stable job where the selector permits it. Do not weaken real lint, test, report or Sonar Quality Gate failures. Preserve report upload after failures and distinguish each check's outcome. Validate focused selector/report tests for changed policy.

Keep concurrency scoped to the same PR/ref; do not cancel another process's runs. Verify required CI on the current PR commit after any main update. Ordinary CI and release verification are separate: `release-check.yml` publishes only on its main push path after its gates; tag/manual runs verify without publication. Signing secrets stay private and public certificate fingerprints must match. Do not publish or change access without task authorization.
