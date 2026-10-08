# Project policy and enforcement

This document assigns rules to their enforcing owner. CI scope/commands live in [CI.md](CI.md) and `tools/ci/select_checks.py`; agent behavior lives in [AGENTS.md](../AGENTS.md). Do not duplicate executable path/test matrices in skills or role profiles.

| Rule | GitHub mechanism / owner | Instruction retained for the agent |
| --- | --- | --- |
| Changes enter main through a PR | Native active `Protect main` ruleset; zero required approvals, no CODEOWNERS approval | Prepare focused code/body locally; use Manage-PullRequest.ps1; verify the merge |
| Required CI is current and trusted | Native required `android`, GitHub Actions integration 15368, strict up-to-date policy | Update only your branch from current main, preserving local work; wait for the new commit's CI |
| No force push, deletion or bypass of main | Native non_fast_forward/deletion rules, empty bypass actors | Never weaken protection to merge |
| App/tool verification and reports | Custom Android CI, selector and result validators | Select proportional local checks; diagnose actual failures; retain evidence |
| Sonar Quality Gate | Custom scanner step inside android for app changes | Review findings; do not infer analyzer results or require an absent documentation-only Sonar check |
| Release publication | Custom release-check main version-change path, gated signing/verification | Authorize a version release explicitly; inspect actual published artifacts and hashes |
| Close an implemented issue | Native `Closes #N` when its PR merges into the default branch | Confirm closure; add one final comment with behavior, checks and PR URL |
| Delete merged remote branches | Native delete_branch_on_merge; GitHub preserves branches still needed by open PRs | Check ownership/open PRs before manual cleanup; local worktrees and branches still need inspection |
| Project closed/merged item -> Done | Native Projects built-in automation where enabled; API does not expose its workflow configuration | Confirm observed Status; repair only a mismatch, never infer automation is enabled |
| Open issue membership, archive recovery, stale/missing Status | Custom project-sync reconciliation; see tools/PROJECT-SYNC.md | Preserve reconciliation until an equivalent replacement is verified |
| Priority, Work Type, Testing, dependencies | Agent/user judgment; Update-Issue.ps1 applies chosen metadata | Choose from evidence and actual progress; do not invent automatic selection rules |
| Scope, visual design, gameplay decisions | Agent/user judgment; tests cover only specified behavior | Preserve AGENTS.md product constraints; resolve material decisions within task authorization |
| Device selection, secrets, truthful reporting | Agent responsibility; explicit runner targets and evidence | Use headless isolated emulator first, phone only for device-specific needs; never expose secrets or claim unrun checks |

## API delivery and protection maintenance

Use project API scripts, not browser UI. Inspect effective `rules/branches/main` and classic `branches/main/protection` before changes. Preserve enforcement/bypass scope, check source and strict policy. Remove classic protection only after confirming the active ruleset covers all its restrictions. `Manage-PullRequest.ps1` reads both protection mechanisms; only a genuine classic 404 means no classic rule, other errors fail closed.

Automatic branch deletion does not clean local checkouts. Never remove another process's branch/worktree or unmerged work. Repository auto-merge, reviewer approval, access grants and secrets are outside this policy migration.

## Current quality audit boundary

At the initial 2026-10-08 inspection, main was 19e42bb; audit PR #200 and feature PR #201 were open, so the detekt/lint audit was not yet on main. The separate quality_reports diagnostic step and focused negative tests already exist in PR #202 (036b57e), inherited by #201. Do not duplicate that pending work here. Its actual application findings remain failing; this policy change adds no baseline, rule disablement, reduced threshold or application refactor. After that PR merges, CI.md must describe its final executable scope.

References: [rulesets API](https://docs.github.com/en/rest/repos/rules), [branch protection API](https://docs.github.com/en/rest/branches/branch-protection), [Projects built-in automation](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/using-the-built-in-automations), [automatic branch deletion](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-the-automatic-deletion-of-branches).

## Confirmed repository configuration (2026-10-08)

Ruleset `Protect main` #24704305 is active for refs/heads/main: required PR with zero approvals and no CODEOWNERS/last-push/extra-unattributed approval, required android from Actions integration 15368, strict=true, deletion/non-fast-forward prohibited, no bypass actors. Classic protection was removed only after comparing its restrictions and reading effective rules back. Classic strict was already true; ruleset strict was false and was aligned. Repository delete_branch_on_merge changed false -> true; allow_auto_merge remains false. Existing open PR branches #200/#201/#202 were preserved. Full API snapshots are retained outside tracked source.

Project membership/status inspection found no missing or stale open items. Built-in workflow configuration is not exposed by the documented Projects API and was not changed or claimed enabled. Keep project-sync's recovery and reconciliation; verify Done after issue closure and repair a mismatch through Update-Issue.ps1 only if necessary.
