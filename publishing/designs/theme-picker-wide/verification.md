# Package verification — 2026-10-13

## Scope and identity

Base: `7b8e1bfa5a4e6669750d53457156f565160802a8` in `RoyMatus/Swypetris`. The supplied component and tests were temporarily integrated in an isolated local worktree against this base. The production changes are distributed as `integration.patch`; they are not merged into main. `SHA256SUMS.txt` binds the exact distributed files.

The host probe confirmed a local Windows executor, JDK 21, Android SDK platform 36.1 and accelerated API 35 emulator support. The host was busy, so the compilation used two Gradle workers and the existing caches. No cloud checks were dispatched. Device availability is not a passed device test.

## Checks actually completed

| Check | Result | Evidence / limits |
| --- | --- | --- |
| Production Kotlin compilation | Passed | `:app:compileDebugKotlin` completed with the component connected to the existing `PalettePicker` entry point |
| Android test Kotlin compilation | Passed | `:app:compileDebugAndroidTestKotlin` compiled the new focused tests and the updated existing geometric assertion |
| Static Kotlin checks | Passed | `:app:detekt` completed after correcting three overlong source lines |
| Standalone mockup | Passed | At product widths 420px and 320px: 12 cards, no horizontal overflow, no overflowing title bounds |
| Reference assets and interaction | Passed | All 12 embedded material strips loaded at 560×80 pixels; selecting Nord updated the selected card; no browser runtime errors |
| Reference image review | Completed | Inspected the wide-card PNG; the images and palette names use the existing project artwork |
| Patch and archive integrity | Passed during packaging | Reverse-check against the compiled working tree; clean reapplication check at the pinned base; source-copy comparisons; ZIP contents verified against SHA-256 checksums |

`browser-check.json` contains the machine-readable mockup results. Mockup checks establish browser-reference behavior, not native Android rendering or accessibility acceptance.

## Observed failure and recheck

The first local invocation took **192.035 seconds** and failed detekt on three newly introduced lines exceeding the configured maximum line length. Production Kotlin compilation completed in that invocation; the failed invocation is not reported as a successful overall check. The three lines were wrapped without changing behavior.

The successful follow-up invocation took **128.497 seconds** and ran `:app:compileDebugAndroidTestKotlin :app:detekt` with the production compilation as a dependency. Gradle reported **29 actionable tasks: 5 executed, 24 up-to-date**. These durations are observed command wall times, not developer effort or a controlled cold/warm performance comparison. Full local logs remain in the owned worktree's ignored `app/build/verification/wide-picker-compile*.log` output; private host paths and device identities are not included in the public package.

## Remaining integration checks

Android instrumentation was **not executed** for this preparation-only handoff. The supplied test cases were compiled, not run. A separate active emulator/workload was observed on the host; its processes and device session were not used or stopped for this package. Runtime verification belongs to the implementation ticket on an owned, explicitly selected headless emulator.

Run the new `WidePalettePickerTest` cases and the relevant existing picker/material scenarios after integrating the patch. Check actual portrait sizes, font scales, selection persistence, randomization, screen-reader state and screenshots. The provided fallback already defines what to implement; no new design decision is required.

Strict Android lint, the full JVM suite, SonarQube Quality Gate, the final exact-head/current-main PR gate and any release checks were not run or claimed here. They remain subject to repository policy at actual integration/delivery. No APK installation, instrumentation pass, protected merge, issue completion or release is established by this handoff.

Recorded active development time: **Unknown** for package preparation; no issue-specific phase journal covered the entire preparation interval. The two measured command durations above must not be substituted for active development time.
