---
name: android-test-selection
description: Select proportionate local Android build, JVM, instrumentation, and release checks for a Swypetris code change.
---

# Swypetris Android test selection

Choose verification from the changed behavior and plausible failure modes. See [publishing/CI.md](../../../publishing/CI.md) for the canonical commands and [TESTING.md](../../../TESTING.md) for the test inventory.

## Choose the smallest check that covers the risk

- For a narrow resource, documentation, or agent-instruction edit, use review or a focused resource check as relevant; do not run Android suites when no Android behavior or build input changed.
- For a localized JVM gameplay rule or utility, run its focused unit tests when available. For broader production code changes, use the PR check set: `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:jacocoDebugUnitTestReport`.
- For variant-dependent logic, cross-cutting gameplay interactions, or a main-branch equivalent check, include `:app:test` to cover all JVM test variants.
- Run `:app:connectedDebugAndroidTest` on an explicitly selected isolated emulator when the change can affect Android/Compose UI, lifecycle, platform integration, or behavior covered only by instrumentation. A build/install result alone is not a completed instrumentation test.
- For a release candidate or release-workflow gate, run the full release verification scope: API 35 emulator instrumentation and launcher smoke test, signed release APK and AAB builds, `lintRelease`, and signature/archive checks. Release verification does not publish the release.

On Windows, use `tools/Verify-Tests.ps1` for its supported standard suites and pass `-JavaHome` or an explicit `-Serial` when required. For a narrower check, use direct Gradle/adb commands and always select the target device explicitly. Follow the repository device policy: use an isolated emulator first; use Pixel 7 only for necessary device-specific behavior and leave Swypetris installed afterward.

Report the command or task actually run, result, and any device/API level or environmental limitation. Distinguish compile/install success from a test that exercised the target behavior. Preserve complete failure output in ignored build output when a runner fails.
