# Swypetris — Agent Instructions

## General

- Inspect the relevant existing code before editing.
- Make the smallest change that solves the requested task.
- Preserve existing architecture, naming, style, and behavior unless the task explicitly requires otherwise.
- Do not perform unrelated refactoring, cleanup, or dependency/version upgrades.
- Fix root causes; do not hide errors with arbitrary retries, delays, or broad exception handling.
- Do not invent product or game-design decisions. Ask when ambiguity materially affects behavior.
- If a user's premise conflicts with verified evidence, explain the conflict and recommend the correction. Do not agree merely to be agreeable; separate observed facts from inferences and uncertainty.

## Tool economy

- Prefer APIs, purpose-built tools, scripts, CLI commands, and shell commands when they can complete the task. Use browser or desktop UI control only when these lighter options cannot provide the required result.
- For GitHub issue triage, collect compact status, labels, dependencies, and comment summaries first. Read full bodies only for candidates that need them; do not dump raw connector responses.
- Limit searches and build output to the evidence needed for the decision. Preserve complete failure logs in ignored build output, and report the relevant error plus its log path.
- When a browser tab or URL is known, target it directly instead of listing every open tab. Repeat checks only when a change or unresolved risk requires them.

## GitHub issue workflow

- Make issue, pull request, and GitHub Project updates through APIs or API-backed tools, not browser UI automation.
- Use English for all GitHub-facing content, including issue and pull request titles, descriptions, comments, labels, milestones, project fields, commit messages, branch names, and release notes.
- Use `tools/Update-Issue.ps1` for issue metadata, relationships, comments, linked branches, and the project's Status, Work Type, and Priority; preview changes with `-WhatIf`. Project field updates require a GitHub token with project read/write access.
- When asked to work through issues, review the candidate open issues, their comments, attachments, and dependencies first. Consult closed issues only when relevant to those candidates. Choose an order that minimizes conflicts and rework; do not assume issue-number order is best.
- Explain the planned order and post a concrete plan in chat before starting each issue.
- Implement issues sequentially, one at a time. Use a separate branch and pull request for each issue, verify it, merge it into `main`, and close the issue before starting the next one.
- Move each issue through the available GitHub Project Status states as work progresses. Keep Status aligned with the actual branch, pull request, and merge state.
- Review and fill every issue metadata field: project Work Type and Priority, labels, assignee, milestone, relationships, and linked branch or pull request. Use actual scope, urgency, release plans, and dependencies; record a field as not applicable when no truthful value exists rather than inventing one. Create a separate branch for each issue and link it to the issue.
- When closing an issue, include the pull request URL in the closing issue comment.
- Record the plan, verification, and pull request in issue comments. Add other comments only for important findings or decisions that affect the issue; avoid routine progress commentary.
- Leave issues marked On Hold out of the implementation queue until the user explicitly resumes them.

## Android / Code

- Follow the project's existing language, architecture, and configuration.
- If existing application code is Java, keep new application code in Java unless explicitly requested otherwise.
- Reuse existing APIs and dependencies before adding new ones.
- Do not edit generated files.
- Avoid hardcoded device-specific dimensions and arbitrary positioning.
- Keep the game's composition, visual hierarchy, and controls consistent across device sizes, aspect ratios, resolutions, orientations, and font scales. Derive layout sizes and positions from available space; use `dp`/`sp` for accessibility minimums and sensible bounds, never fixed pixels tied to one device.
- Keep gameplay logic separate from UI where the existing architecture allows it.

## Scope and safety

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

### Android test devices

- Run instrumentation tests on an isolated emulator first. Use the connected Pixel 7 only when device-specific behavior makes a real-device check necessary.
- If the Pixel 7 is unavailable, locked, or its display turns off during a required check, tell the user so they can unlock it. Continue independent work while waiting.
- The user authorizes uninstalling the old Swypetris application, including its local data, from the Pixel 7 for necessary tests. Do not uninstall unrelated applications.
- Always select the target device explicitly by its adb serial; never run against an unspecified connected device.
- Leave the tested application installed on the Pixel 7 when verification is complete.

## Final response

Briefly report:

- what changed;
- important files changed;
- checks actually run and their results;
- remaining issues or uncertainty.
