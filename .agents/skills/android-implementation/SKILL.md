---
name: android-implementation
description: Implement an assigned Swypetris Android gameplay or Compose UI change while preserving state and related game behavior.
---

# Android implementation

Locate the existing owner of the affected rule, UI state, or persisted session before editing. Follow the current Kotlin/Compose architecture and reuse the project's APIs and dependencies. Consult Context7 for the affected framework or API as required by `AGENTS.md`; if unavailable, state that and use official documentation.

For UI changes, derive placement from available space and insets, and check the affected screen at relevant sizes and font scales. For state changes, preserve the existing source of truth across recomposition, navigation, configuration changes, and saved sessions as applicable. Keep gameplay rules out of the UI when the current architecture permits it.

Check only gameplay interactions touched by the change, such as difficulty, scoring, spawning, collision, progression, victory, or loss. Use `android-test-selection` yourself for proportional verification; do not spawn `tester` or another agent by default. Further delegation must meet the necessity rule in `AGENTS.md`. Return the changed behavior, files, check results, and any unresolved risk to the parent agent.
