---
name: android-implementation
description: Implement an assigned Swypetris Android gameplay or Compose UI change while preserving state and related game behavior.
---

# Android implementation

Locate the existing owner of the affected rule, UI state, or persisted session before editing. Follow the current Kotlin/Compose architecture and reuse the project's APIs and dependencies. For a new, changed or uncertain framework/API contract, consult Context7 as required by `AGENTS.md` and reuse that research until the contract changes; if unavailable, state that and use official documentation.

Use the current task's execution profile and check plan; diagnose once at the required AGENTS.md boundary, not once per edit or delegated task. Reuse the parent's source findings and successful test evidence. Reserve heavy Gradle/emulator execution with the verification owner before starting it.

Apply the [TDD procedure and exceptions](../../../TESTING.md#tdd-for-behavior-changes) to new or changed testable behavior and reproducible bugs. Derive one scenario from acceptance criteria, observe the focused test's expected RED before implementing that behavior, make the smallest change for GREEN, then refactor and rerun affected tests. Inspect the failure cause; do not weaken the scenario to fit the implementation. Return the actual RED/GREEN evidence or the recorded exception with alternative checks.

For UI changes, derive placement from available space and insets, and check the affected screen at relevant sizes and font scales. For state changes, preserve the existing source of truth across recomposition, navigation, configuration changes, and saved sessions as applicable. Keep gameplay rules out of the UI when the current architecture permits it.

Before replacing graphics or pixel assertions, inspect the source asset's alpha and coverage and the production scaling/filtering path. Compare filtered output with a like-for-like oracle; retain independent source, geometry, layering, clipping, migration and state assertions. At an early checkpoint, run only the affected photo, field, HUD and Next rendering cases and relevant neighbors, then keep the mandatory final gate. Preserve portrait layout and existing visual design.

Check only gameplay interactions touched by the change, such as difficulty, scoring, spawning, collision, progression, victory, or loss. Use `android-test-selection` yourself for proportional verification; do not spawn `tester` or another agent by default. Further delegation must meet the necessity rule in `AGENTS.md`. Return the changed behavior, files, check results, and any unresolved risk to the parent agent.
