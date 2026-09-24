# Swypetris — Agent Instructions

## General

- Inspect the relevant existing code before editing.
- Make the smallest change that solves the requested task.
- Preserve existing architecture, naming, style, and behavior unless the task explicitly requires otherwise.
- Do not perform unrelated refactoring, cleanup, or dependency/version upgrades.
- Fix root causes; do not hide errors with arbitrary retries, delays, or broad exception handling.
- Do not invent product or game-design decisions. Ask when ambiguity materially affects behavior.

## Android / Code

- Follow the project's existing language, architecture, and configuration.
- If existing application code is Java, keep new application code in Java unless explicitly requested otherwise.
- Reuse existing APIs and dependencies before adding new ones.
- Do not edit generated files.
- Avoid hardcoded device-specific dimensions and arbitrary positioning.
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

## Final response

Briefly report:

- what changed;
- important files changed;
- checks actually run and their results;
- remaining issues or uncertainty.