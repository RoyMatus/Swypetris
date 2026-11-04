---
name: ci-policy
description: Change or review Swypetris verification selection, evidence validation and merge/release enforcement.
---

# Verification policy

Read the affected contract in [CI.md](../../../publishing/CI.md), its enforcing runner/validator and `tools/ci/select_checks.py`. Use [POLICY.md](../../../publishing/POLICY.md) for enforcement ownership and trust boundaries; inspect actual GitHub rules before protection maintenance and verify settings afterward.

Preserve these invariants when changing policy:

- All execution stays local under [execution profiles](../../../publishing/EXECUTION-PROFILES.md); the locally run Sonar scanner awaits the remote Quality Gate. No Actions or cloud fallback.
- Unknown/shared inputs retain conservative full regression. Validate affected selector, report and evidence contracts; never weaken lint, tests, dependency verification or Sonar failures. Windows is mandatory; optional Unix coverage is not implied by Windows results.
- Merge evidence binds a clean exact head/current main base. `Manage-PullRequest.ps1` rejects failed/incomplete/stale evidence and merges with expected head SHA. Preserve PR/main/tag protections and the explicit limitation that UI/other API merges bypass local evidence validation.
- Exact-main release verification independently checks full regression, original signing certificates and signed runtime behavior, artifact hashes and provenance (including historical cloud provenance). Verification never publishes; the separate publisher requires authorization and the [compliance review](../../../publishing/RELEASE-COMPLIANCE.md). Preserve private keys/access and separate device/store acceptance.

Archived workflows are historical fixtures, never execution entrypoints. Reuse existing check evidence and diagnostics; policy editing does not authorize publication or changes to secrets/access.
