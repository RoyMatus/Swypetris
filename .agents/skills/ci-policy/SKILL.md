---
name: ci-policy
description: Change or review Swypetris local verification policy, merge evidence and release gates.
---

# Verification policy

Read [publishing/CI.md](../../../publishing/CI.md), [publishing/POLICY.md](../../../publishing/POLICY.md), the affected local runner and `tools/ci/select_checks.py`; these own scope, commands and enforcement. Inspect actual GitHub rules before changing protection settings.

Run builds, JVM/Android/tool tests and signed release verification locally. Do not dispatch GitHub Actions, install a self-hosted Actions runner or use a cloud fallback. Missing local capacity means verification is blocked. SonarQube Cloud is the explicit service exception: run its scanner locally and await the Quality Gate. Never weaken lint, tests, report validation, dependency verification or Sonar failures.

Use the parent's diagnosed execution profile and reservation; one owner schedules heavy Gradle/emulator work. Preserve caches and failed-run diagnostics. Unknown/shared execution inputs retain conservative full regression. Validate selector, report and local evidence contracts for policy changes. Windows is the only mandatory verification platform; Linux/WSL is not required. Optional Unix tests do not gate merges or releases, and their omission is not proof of Unix behavior.

The final PR gate binds local evidence to a clean exact head and current main base. Manage-PullRequest.ps1 must reject stale, incomplete or failed evidence and use the expected head SHA when merging. GitHub PR/no-force-push/no-deletion protections remain, but UI merges bypass local test verification: this limitation must remain explicit. Archived workflows are historical fixtures, never execution entrypoints.

Release verification independently checks the exact main commit, complete regression, signatures/certificate fingerprints and signed APK runtime behavior. Verification does not publish; use the separate local publisher only with explicit release authorization. Preserve protected release tags, artifact hashes and provenance, including verification of historical cloud releases. Signing keys stay private. Do not change access or secrets without assignment.

Before a major/substantial release, follow [RELEASE-COMPLIANCE.md](../../../publishing/RELEASE-COMPLIANCE.md), review current RuStore and GitHub requirements and resolve mandatory violations or unverified owner items. Technical gates do not replace store/device acceptance.
