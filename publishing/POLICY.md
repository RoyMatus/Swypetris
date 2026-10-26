# Project policy and enforcement

Current verification runs locally. [CI.md](CI.md) and `tools/ci/select_checks.py` own check scope; [AGENTS.md](../AGENTS.md) owns agent behavior. Do not duplicate executable matrices in skills or roles.

| Rule | Enforcing owner | Required behavior |
| --- | --- | --- |
| Changes enter main through a PR | GitHub main ruleset | Use Manage-PullRequest.ps1 with reviewed code/body and valid local evidence |
| Exact head/current base and passing checks | Local gate and merge script | Reject dirty, stale, incomplete, failed or modified evidence; merge with expected head SHA |
| No force push/deletion/bypass of main | GitHub integrity rules | Preserve restrictions and empty bypass actors |
| App/tool checks and diagnostics | Local selector, runners and validators | Preserve quality thresholds, required platform checks and failure evidence |
| Sonar Quality Gate | Local scanner plus SonarQube Cloud | This is the sole remote analysis exception; credentials or gate failure block applicable checks |
| Signed release | Exact-main local full regression and signed verification | Preserve certificates, runtime smoke and artifact hashes; verification never publishes |
| Publication | Explicit local publisher | Require authorization, compliance and provenance; never overwrite a release/tag |
| Release tag integrity | GitHub protected v* tags | Preserve no-update/no-delete rules and no bypass actors |
| Issue closure | Closes #N on merged PR | Confirm closure and add one final evidence comment |
| Project membership/status | Local Sync-IssueProject.ps1 and observed native automation | Inspect/repair via API; never infer built-in automation is enabled |
| Product, device and credential decisions | Agent/user | Preserve design/gameplay, explicit emulator selection and private keys |

## Trust boundary and migration

There is no required GitHub Actions `android` check in the target local policy. GitHub still requires PRs and protects main/tags; local evidence is enforced by the repository merge script. GitHub UI and independent API clients can bypass that local test gate. Local reports are not independently authenticated GitHub results and cannot defend against an operator deliberately forging both reports and source. Use the approved script for delivery and report this limitation honestly.

Inspect effective branch rules and classic protection before maintenance, preserve PR/no-force/no-delete/bypass scope, and read settings back afterward. Remove the former hosted check requirement only after validating the local replacement; do not claim that a document edit changed the remote setting. A genuine classic 404 means no classic protection; other API errors fail closed. No review/access/secrets expansion is part of this migration.

GitHub Actions definitions are archived outside `.github/workflows`; cloud dispatch entrypoints reject execution. No Actions or Dependabot Actions schedule is the approved path. Without a suitable local executor, required verification remains blocked. Do not add a self-hosted Actions runner as a workaround.

The former `release-signing` Environment and stored secrets are historical configuration; preserve them unless separately assigned. Local signing uses the existing private keys and protected local credentials. Historical published-release provenance remains verifiable; it does not authorize new cloud execution. Existing dated reports describe their measured past configuration and are not current instructions.

## Delivery and compliance

Before a major/substantial release, complete [RELEASE-COMPLIANCE.md](RELEASE-COMPLIANCE.md) for exact intended artifacts using current official RuStore and GitHub requirements. Mandatory violations or unverified owner items block publication independently of technical checks.

Automatic remote branch deletion does not clean local worktrees. Inspect ownership, open PRs, unmerged work and artifacts before cleanup; never remove another process's work. Reconcile Projects locally with [tools/PROJECT-SYNC.md](../tools/PROJECT-SYNC.md); after ProjectAccessError stop Project operations until credentials change.

User-request/ticket scope is also subject to the [RuStore compliance gate](RUSTORE-COMPLIANCE.md). Confirmed conflicts must not be implemented; the store_compliance role reviews substantial or uncertain scope and final publication evidence.
