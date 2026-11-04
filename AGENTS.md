# Swypetris — Agent Instructions

Load documentation and skills for the affected task, not as a routine checklist. Each procedure has one owner:

| Task | Primary source |
| --- | --- |
| Enforcement and merge/release trust boundaries | [publishing/POLICY.md](publishing/POLICY.md) |
| Check scope, commands, exact-head PR and exact-main release evidence | [publishing/CI.md](publishing/CI.md), `tools/ci/select_checks.py` |
| Host diagnosis, resource budget and evidence reuse | [publishing/EXECUTION-PROFILES.md](publishing/EXECUTION-PROFILES.md) |
| TDD, test inventory and supplemental release scenarios | [TESTING.md](TESTING.md) |
| Issue/epic metadata, sequential delivery, completion comments and reviews | [publishing/DEVELOPMENT-WORKFLOW.md](publishing/DEVELOPMENT-WORKFLOW.md) |
| Project reconciliation | [tools/PROJECT-SYNC.md](tools/PROJECT-SYNC.md) |
| Request compliance and publication review | [publishing/RUSTORE-COMPLIANCE.md](publishing/RUSTORE-COMPLIANCE.md), [publishing/RELEASE-COMPLIANCE.md](publishing/RELEASE-COMPLIANCE.md) |

Keep task guidance in `.agents/skills/` and automation in `tools/`. Use built-in agents for ordinary work; `.codex/agents/store-compliance.toml` retains a specialized read-only review boundary.

## Safety and evidence

- Inspect affected code; make the smallest complete change. Preserve architecture, language, naming and dependencies. Do not edit generated files or perform unrelated cleanup/upgrades.
- Use a separate worktree. Preserve others' changes, processes, branches, worktrees and workflow runs. After confirmed merge, automatically remove only your completed, unused local/remote topic branches and worktrees: first check open PRs, unmerged commits, local changes and needed artifacts, preserving required evidence. Never delete protected branches or active/unmerged work.
- Prefer repository API scripts and CLI over UI. Never print/commit credentials or signing keys, upload code/secrets to an unassigned service, or expand access without authorization.
- For a new, changed or uncertain affected API/framework contract, consult authenticated Context7 and check version/API scope; if unavailable, use official documentation. Reuse research until the contract/version changes; do not repeat exhausted-quota attempts.
- Separate confirmed defects, observed source behavior, runtime hypotheses and unknowns. Check scanner findings against their actual rule, location and reachable failure path. Never claim a build, test, analysis, setting, merge or publication without its result; installation is not a passed test.

## Scope and gameplay

- Existing design is protected: change only requested visuals, preserving adjacent placement, proportions, spacing, colors, typography, icons, textures and animations. Material product/design decisions outside authorization belong to the user.
- Preserve the portrait Activity lock; never enable/force landscape in app code or tests. Derive geometry from available space/insets with dp/sp bounds and accessibility minimums, not device-specific pixels. Check relevant portrait sizes, aspect ratios and font scales.
- Keep rules/tuning and state in their existing single owners. Preserve sessions through navigation, backgrounding and configuration changes. Check affected scoring, spawning, collision, merging, progression, victory and loss without changing unrelated mechanics.
- Difficulty stays Easy / Medium / Hard with centralized parameters and balanced early pacing. Pause returns to the main menu with Continue, never a pause screen. Game indicators remain near the upper-left with a small consistent inset-aware margin.
- Apply the [RuStore request gate](publishing/RUSTORE-COMPLIANCE.md) before implementation. Explain and refuse only confirmed mandatory-rule conflicts, cite provisions/evidence, offer a compliant alternative and continue independent compliant work. Investigate uncertainty; recommendations are not prohibitions. For substantial/uncertain scope and before publication, use `store_compliance`; if unavailable, give its exact instructions to an available agent without claiming the custom role is active.

## Execution and verification

- Diagnose the execution host with `tools/Get-ExecutionProfile.ps1 -Context '<epic/ticket>'` before an epic, a continuation on another machine or standalone implementation. Follow [execution profiles](publishing/EXECUTION-PROFILES.md); recheck after resource/toolchain changes, not every command. A phone is a client, not proof of backend capacity.
- All builds, tests and release verification run locally. Missing local capacity blocks required verification; no GitHub Actions dispatch, cloud fallback or self-hosted Actions runner. SonarQube Cloud is the sole analysis-service exception: scan locally and await its Quality Gate. Preserve mandatory [PR/release gates](publishing/CI.md).
- Keep one risk-based check plan and one owner of heavy Gradle/device work. Use `android-test-selection` for changed behavior; the selector does not replace risk assessment. Instruction-only edits need review, not Android builds; local tool changes need tool tests. Full regression covers cross-system changes, unresolved failures and release gates.
- Reuse applicable research, source findings and passing evidence across agents/checkpoints. Keep compatible caches through sequential tickets; no habitual clean/forced rerun. Broaden/rerun for changed inputs or unresolved risk. Preserve failure logs/device diagnostics and diagnose before retrying; stop only owned processes. Profile measured slow tests before changing synchronization.
- Use [TDD](TESTING.md#tdd-for-behavior-changes) for suitable behavior changes and reproducible bugs: observe the intended RED before implementation, then GREEN and affected post-refactor checks. Record exceptions and alternatives in the check plan. Build/environment failures are not behavior RED; intermediate RED never replaces the final gate.
- Delegate only useful independent work with explicit ownership and the current profile, budget and evidence. Do not require a procession of roles or duplicate discovery/checks. Simple delegated work uses `gpt-6-luna` with low effort; the parent owns integration and final verification.

## Devices

- Before device tests, inspect `adb devices -l` and identify serial/model. Start with an isolated headless emulator (`-no-window`), always selecting an explicit serial. Open a window only for required visual/manual verification.
- Use the connected Pixel 7 for real-device behavior, update compatibility or release acceptance; state the risk covered. Documentation/local-tool-only merges do not require reinstalling the game. Authorized Swypetris uninstall may remove its data; never uninstall unrelated apps. Leave Swypetris installed afterward.

## Delivery

- Prepare code, selected development checks, diff review, English GitHub text, release notes and applicable measurements locally before the final PR push. Use `tools/Update-Issue.ps1` and `tools/Manage-PullRequest.ps1`; preview supported writes with `-WhatIf` and read back results. A prepared draft PR may supply the number for local Sonar; complete final verification before ready/merge. Prepare completion text during checks without changing the tested commit.
- Follow the [development workflow](publishing/DEVELOPMENT-WORKFLOW.md) for metadata and completion: state the epic queue, deliver one linked branch/PR per child, and confirm merge/closure before starting the next. Keep statuses factual, unstarted work Backlog and explicit On Hold until resumed. After `ProjectAccessError`, report once and stop Project operations until credentials change.
- Merge only through `Manage-PullRequest.ps1` with passing evidence for the clean exact PR head/current main base. Never bypass missing/failed checks or main/tag protections; UI/other API merges bypass the local gate. Use `Closes #N` for completion PRs, verify closure and post one consolidated evidence comment.
- Retain actual check counts/durations, failures/rework and logs. After epic implementation merges, publish and link `publishing/reports/epic-<number>-development-review.md`. Use the workflow's optional journal/draft helpers only when useful; stop active intervals when leaving/waiting, leave missing time Unknown and never infer speedups from unlike cold/warm runs.
- Publication requires separate authorization, compliance and technical gates. Complete [supplemental release scenarios](TESTING.md#required-release-scenarios) at their pre/postpublication stages; regression skips are not acceptance. Keep `EnergyScenarioTest` excluded unless separately requested. After publication run `tools/release/Verify-PublishedRelease.ps1`; artifact verification does not replace instrumentation, physical-device acceptance or the release gate.
