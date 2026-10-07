# Swypetris — Agent Instructions

Project agents live in `.codex/agents/`, reusable workflows in `.agents/skills/`, and repository automation in `tools/`. These are separate Codex conventions; keep each file with its owner.

## General

- Inspect the relevant existing code before editing.
- Make the smallest change that solves the requested task.
- Preserve existing architecture, naming, style, and behavior unless the task explicitly requires otherwise.
- Do not perform unrelated refactoring, cleanup, or dependency/version upgrades.
- Fix root causes; do not hide errors with arbitrary retries, delays, or broad exception handling.
- Do not invent product or game-design decisions. Ask when ambiguity materially affects behavior.
- If a user's premise conflicts with verified evidence, explain the conflict and recommend the correction. Do not agree merely to be agreeable; separate observed facts from inferences and uncertainty.
- Before each code change, check current documentation for the affected framework, library, or API through Context7. If Context7 is unavailable or has no relevant documentation, say so and consult the official documentation; do not present that fallback as a Context7 check.

## Tool economy

- Prefer APIs, purpose-built tools, scripts, CLI commands, and shell commands when they can complete the task. Use browser or desktop UI control only when these lighter options cannot provide the required result.
- Limit searches and build output to the evidence needed for the decision. Preserve complete failure logs in ignored build output, and report the relevant error plus its log path.
- When a browser tab or URL is known, target it directly instead of listing every open tab. Repeat checks only when a change or unresolved risk requires them.

## Delegation

- Default to no subagents. Never spawn an agent solely because its role matches the topic or because parallel work is possible. Before each spawn, identify a concrete, bounded subtask that is necessary for the current request, the distinct output it must produce, and why the main agent should not do it directly. If any of these is missing, do not spawn. Use the fewest agents that can complete the task. A direct user request for a named agent authorizes that agent only, not additional agents.
- Do not spawn agents for duplicate work, trivial searches, sequential edits, or a role checklist. Do not ask a subagent to spawn another agent unless that additional delegation independently meets this rule and the parent explicitly assigns it.
- For simple, self-contained tasks worth delegating, explicitly choose `gpt-6-luna` with low reasoning effort. Use a stronger model only when the assigned task needs deeper reasoning. Keep the scope and requested output short; do not pin models in the project agent files so the parent can choose per task.
- If a bounded, independent codebase search or analysis meets this rule, select the project-scoped `scout` agent in `.codex/agents/scout.toml` when available. Pass only the task context it needs. The main agent verifies findings before acting.
- When delegation is necessary, select the matching role: `security` for a bounded vulnerability investigation or assigned security fix, `code_quality` for a concrete quality defect, `tester` for focused test work, `devops` for CI/CD or release automation, and `feature_manager` for feature-issue triage. Consult `game_designer` only when a new gameplay or player-experience feature needs distinct design analysis; the user makes product decisions.
- Select `developer` for a necessary, assigned Android application implementation subtask. Keep GitHub integration and final verification with the main agent.
- Give any editing agent explicit file ownership; keep final integration and verification with the main agent and avoid simultaneous edits to shared files.
- Treat a narrow SonarQube Cloud finding as input to one necessary scout assignment, not a reason to spawn a standing Sonar-specific agent. Do not claim an analysis ran without its report.

## GitHub issue workflow

- Complete code edits, review, verification, and commit/PR content locally before pushing or writing implementation updates to GitHub. Do not post intermediate local-work progress or repeatedly query unchanged GitHub statuses; batch necessary writes at delivery checkpoints and verify their results.
- Before creating or changing tickets, epics, or related GitHub objects, prepare the complete bodies, acceptance criteria, metadata, dependencies, and planned updates locally. Review the prepared result before sending it through the API; do not use GitHub as a drafting workspace.
- Inspect CI at meaningful, spaced checkpoints rather than in a tight polling loop. Recheck only when completion is expected or a changed state requires a decision.
- Make issue, pull request, and GitHub Project updates through APIs or API-backed tools, not browser UI automation.
- Use English for all GitHub-facing content, including issue and pull request titles, descriptions, comments, labels, milestones, project fields, commit messages, branch names, and release notes.
- Use `tools/Update-Issue.ps1` for issue metadata, relationships, comments, linked branches, and the project's Status, Work Type, and Priority; preview changes with `-WhatIf`. Project field updates require a GitHub token with project read/write access.
- For a specific issue, use `tools/Update-Issue.ps1 -Inspect -IncludeContent` to read its metadata, body, and comments together; request `-ListOptions` only when choosing labels or milestones. Prefer the project scripts to an unverified `gh` installation.
- After `ProjectAccessError`, report the missing scope once and skip further Project reads until the credentials change.
- Use `tools/Manage-PullRequest.ps1` to create a PR from a pushed branch, inspect its CI checks, and merge it after checks pass. Pass `-IssueNumber` only for issue-linked work; standalone maintenance PRs need no issue. Run creation, inspection, and merging as separate decisions; do not auto-merge after polling.
- For each issue, prepare and verify the implementation locally first. Then update its metadata and link the implementation branch, push the verified branch, create the PR, inspect CI, merge, and close the issue with the PR URL. Check the actual result after every API write.
- Record confirmed reusable API commands and workflow findings in the relevant script or project documentation as they are discovered. Never save access tokens or other secrets.
- When feature-issue preparation requires a distinct delegated subtask under the Delegation rule, assign it to `feature_manager`; otherwise prepare the scope, dependencies, order, and metadata directly. Explain the planned order and post a concrete plan in chat before starting each issue.
- Implement issues sequentially, one at a time. Use a separate branch and pull request for each issue, verify it, merge it into `main`, and close the issue before starting the next one.
- Keep GitHub Project Status aligned with the actual branch, pull request, and merge state. Create a separate linked branch for each issue.
- When closing an issue, include the pull request URL in the closing issue comment.
- Record the completed plan, actual verification, and pull request in one consolidated issue comment when the work is ready for GitHub delivery.

## Android / Code

- Follow the project's existing language, architecture, and configuration.
- If existing application code is Java, keep new application code in Java unless explicitly requested otherwise.
- Reuse existing APIs and dependencies before adding new ones.
- Do not edit generated files.
- Avoid hardcoded device-specific dimensions and arbitrary positioning.
- Keep the game's composition, visual hierarchy, and controls consistent across device sizes, aspect ratios, resolutions, orientations, and font scales. Derive layout sizes and positions from available space; use `dp`/`sp` for accessibility minimums and sensible bounds, never fixed pixels tied to one device.
- Keep gameplay logic separate from UI where the existing architecture allows it.

## Scope and safety

- Treat the existing layout and graphic design as protected requirements. Change only visual elements directly required by the current ticket or explicitly requested task; preserve all other positioning, spacing, sizes, proportions, colors, typography, icons, textures, animations, and visual hierarchy.
- Do not redesign, restyle, or "improve" adjacent elements while implementing a ticket. If completing the ticket requires a visual change outside its scope, explain the dependency and obtain explicit user approval before making that change.
- Keep diffs focused and reviewable.
- Do not rename unrelated symbols or reorganize packages without need.
- Do not touch unrelated gameplay values, screens, or UI.
- Do not broaden a fix into unrelated cleanup or style-only changes.
- When changing state/lifecycle logic, preserve game state across navigation, backgrounding, and configuration changes; avoid duplicate sources of truth.

## Gameplay

- Preserve unrelated mechanics when changing gameplay.
- Keep tunable gameplay values centralized where practical.
- When relevant, check interactions between affected gameplay systems such as difficulty, scoring, spawning, collisions, merging, progression, victory, and loss.
- Avoid duplicating gameplay configuration or rules across multiple locations.
- Difficulty should support Easy / Medium / Hard and must not become disproportionately aggressive early in the game.
- Difficulty parameters should have a single source of truth.

## Product rules

- Do not create a separate pause screen. Pausing should return to the main menu with a Continue action.
- Game indicators should remain near the upper-left with a small consistent margin and adapt to different screen sizes/insets.

## Verification

After changes:

1. Review the diff for accidental or unrelated edits.
2. Run the smallest relevant available build/test/lint checks.
3. Fix regressions caused by the change.
4. Never claim a check was run if it was not.

- Match verification to changed behavior; delegate focused test work to `tester` only when the Delegation rule is met. Do not run the full suites by default for small changes.

- On Windows, use `tools/Verify-Tests.ps1` for its standard test suites; pass `-JavaHome` when needed and an explicit `-Serial` for the Android suite. Use direct Gradle or adb commands for narrower checks.

### Android test devices

- Before Android device verification, check `adb devices -l` for physical phones connected through Wi-Fi debugging or USB. Identify each connected target by its serial and model; connection availability alone is not a reason to use a physical phone.
- Run instrumentation tests on an isolated emulator first. Use the connected Pixel 7 only when device-specific behavior makes a real-device check necessary.
- Use an isolated emulator by default. Use a connected physical phone when the required verification needs real-device behavior, and explain that need before testing on it.
- Start emulators with `-no-window` by default. Open an emulator window only when visual observation or manual interaction is necessary for the required verification; use adb, instrumentation, screenshots, and recordings without a visible window otherwise.
- The user authorizes uninstalling the old Swypetris application, including its local data, from the Pixel 7 for necessary tests. Do not uninstall unrelated applications.
- Always select the target device explicitly by its adb serial; never run against an unspecified connected device.
- Leave the tested application installed on the Pixel 7 when verification is complete.

## Final response

Briefly report:

- what changed;
- important files changed;
- checks actually run and their results;
- remaining issues or uncertainty.
