# Swypetris — Agent Instructions

Project policy: [publishing/POLICY.md](publishing/POLICY.md). CI selection and commands: [publishing/CI.md](publishing/CI.md), `tools/ci/select_checks.py`, and `tools/Verify-Tests.ps1`. Test inventory: [TESTING.md](TESTING.md). Project reconciliation: [tools/PROJECT-SYNC.md](tools/PROJECT-SYNC.md). Keep project skills in `.agents/skills/`, role profiles in `.codex/agents/`, and automation in `tools/`.

## Working safely

- Inspect affected code first; make the smallest complete change. Preserve architecture, language, naming and dependencies; do not edit generated files or perform unrelated cleanup/upgrades.
- Use a separate worktree. Preserve pre-existing changes and other processes' branches, worktrees and workflow runs. Clean up only your merged, unused branches/worktrees after checking open PRs, unmerged work, local changes and needed artifacts.
- Before code changes, consult current affected API/framework documentation through Context7. If unavailable, state that and consult official documentation.
- Prefer repository API scripts and CLI over UI automation. Never print or commit credentials, signing keys or other secrets; do not expand access without authorization.
- Default to no subagents. Delegate only a necessary, bounded, distinct task that justifies not doing it directly; assign file ownership, preserve others' edits, and retain final integration/verification. Do not delegate duplicate searches or spawn role checklists. Use the relevant project role/skill; simple delegated work uses `gpt-6-luna` with low effort.

## Scope, design and gameplay

- Existing layout/design is protected. Change only requested visual elements; preserve adjacent placement, proportions, spacing, colors, typography, icons, textures and animations. Material product/design decisions outside authorization belong to the user.
- Derive UI geometry from available space/insets; use dp/sp bounds and accessibility minimums. Verify relevant screen sizes, aspect ratios, orientations and font scales; avoid device-specific pixels.
- Keep gameplay logic and tunable rules in their existing single owners. Preserve state across navigation, backgrounding and configuration changes; avoid duplicate sources of truth.
- Check affected interactions among scoring, spawning, collision, merging, progression, victory and loss. Preserve unrelated mechanics. Difficulty remains Easy / Medium / Hard with centralized parameters and balanced early pacing.
- Pause returns to the main menu with Continue; do not add a pause screen. Game indicators stay near the upper-left with a small consistent inset-aware margin.

## Delivery

- Prepare and verify code and complete GitHub-facing text locally before writes; use English. Use `tools/Update-Issue.ps1` and `tools/Manage-PullRequest.ps1`, preview supported writes with `-WhatIf`, and verify API results.
- Implement epic issues sequentially: one linked branch/PR per issue, merge and confirm closure before the next. State the concrete issue plan in chat. Project Priority, Work Type and Testing need evidence-based agent judgment; keep Status aligned with actual progress. After ProjectAccessError, report once and stop Project operations until credentials change.
- Put `Closes #N` in the PR body for merge-driven issue closure. Confirm closure after merge and add one consolidated issue comment containing completed behavior, actual checks and PR URL; do not redundantly close an already closed issue.
- GitHub enforces PRs and required checks. Keep your PR current with main, preserve local work when updating it, and wait for CI on the new commit. Inspect at meaningful intervals; never bypass failures or weaken protection to merge. If your CI run greatly exceeds a recent comparable duration or shows no test progress for several minutes, inspect its current step and available logs; cancel only your stalled run, preserve diagnostics, find the cause, and fix it before retrying. See POLICY.md for enforcement ownership.

## Verification and reporting

- Review the diff and choose the smallest checks covering changed behavior using `android-test-selection`; CI selection is not a substitute for local risk assessment. Do not build Android for instruction-only edits. Fix regressions introduced by the task; preserve full failure logs in ignored build output.
- Before device tests, inspect `adb devices -l` and identify serial/model. Use an isolated headless emulator (`-no-window`) first and always select an explicit serial. Open a window only when required for visual/manual verification.
- Use the connected Pixel 7 only when real-device behavior is necessary and explain why. Authorized Swypetris uninstall may remove its data; never uninstall unrelated apps. Leave Swypetris installed afterward.
- Separate facts, inferences and uncertainty. Never claim a build, test, scanner, setting change or merge without its result; install success is not a passed instrumentation test. Report changed behavior/files, actual checks, relevant logs and remaining limitations concisely.
