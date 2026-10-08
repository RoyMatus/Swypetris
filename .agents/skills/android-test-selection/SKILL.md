---
name: android-test-selection
description: Select proportionate local Android build, JVM, instrumentation, and release checks for a Swypetris code change.
---

# Android test selection

Use [publishing/CI.md](../../../publishing/CI.md) for commands and CI scope, `tools/ci/select_checks.py` for path mappings, and [TESTING.md](../../../TESTING.md) for the inventory. Do not copy their matrices here.

Choose local checks from changed behavior and plausible failure modes: focused JVM tests for logic; explicit-device instrumentation for UI, lifecycle or platform integration; relevant size/font-scale inspection for visuals; broader regression only for cross-system risk or unresolved failures. Instruction-only edits need review, not Android builds. A release candidate needs the documented release scope and signing checks.

Use `tools/Verify-Tests.ps1` for supported standard suites (JavaHome and explicit Serial as needed); direct Gradle/adb is appropriate for narrower checks. Follow AGENTS.md device policy. Retain the runner's completed result and reports, and distinguish build/install success from exercised behavior. Report actual checks and limitations.

For a failed run, follow the focused repair loop and its attempt/time budget in [AGENTS.md](../../../AGENTS.md#verification-and-reporting) before repeating broad verification.
