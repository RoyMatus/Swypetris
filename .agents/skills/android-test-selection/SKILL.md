---
name: android-test-selection
description: Select proportionate local Android build, JVM, instrumentation, and release checks for a Swypetris code change.
---

# Android test selection

Use [publishing/CI.md](../../../publishing/CI.md) for commands and CI scope, `tools/ci/select_checks.py` for path mappings, and [TESTING.md](../../../TESTING.md) for the inventory. Do not copy their matrices here.

Choose local checks from changed behavior and plausible failure modes: focused JVM tests for logic; explicit-device instrumentation for UI, lifecycle or platform integration; relevant size/font-scale inspection for visuals; broader regression only for cross-system risk or unresolved failures. Instruction-only edits need review, not Android builds. A release candidate needs the documented release scope and signing checks.

Use the task's diagnosed execution profile from AGENTS.md and `publishing/EXECUTION-PROFILES.md`; child agents reuse the parent's profile. Choose scope independently of location: a weak host does not justify a broader suite. Run one verification owner per worktree/device, reuse successful unchanged evidence, and expand only when changed behavior or a failure requires it. The final mandatory PR check is not a reason to run an identical full local suite first. Use `-MaxWorkers` from the profile rather than editing shared Gradle settings for one computer.

Use `tools/Verify-Tests.ps1` for supported standard suites (JavaHome and explicit Serial as needed); direct Gradle/adb is appropriate for narrower checks. Follow AGENTS.md device policy. Retain the runner's completed result and reports, and distinguish build/install success from exercised behavior. Report actual checks and limitations.
