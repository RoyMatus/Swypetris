# Behavior-preserving audit implementation — 2026-10-11

The owner approved sequential PRs for tool reliability, redundant calculations,
dependency verification, menu-audio preparation and updater disk writes. Gameplay,
layout, graphics, audio content, storage formats and application versions remain
protected. Existing Android runners and broad R8 rules are outside these changes.
The final verification is a manual signed run on main without publication.

Execution profile: Local, four Gradle workers, one Gradle process and one headless
emulator at a time. Focused local checks address each change's risk; required PR
CI remains mandatory. Successful unchanged checks are reused.

## Tool reliability

- Explicitly selected test classes must each contain a passing case. Unfiltered
  optional skips remain supported. Focused Pester: 16 passed, one platform skip,
  3.317 seconds; fixtures include a passing class alongside an all-skipped class.
- Signing backups restore into a protected sibling staging directory, then move
  only a complete key/credential set into an absent destination. Synthetic-key
  tests cover malformed input, partial failure, retry and a concurrent destination.
  Focused Pester: five passed, one platform skip, 4.03 seconds. No production signing credentials are used by these tests.
- Release smoke records observations independently of logcat availability and
  preserves the original UI failure when diagnostics fail. Incomplete diagnostics
  still fail an otherwise passing run. Six focused Python tests passed.
- Complete Python contract suite: 104 discovered, one platform skip, no failures,
  20.050 seconds. No local Android rebuild was needed for these tool changes;
  required CI covers the affected execution scripts on the final PR commit.

Context7 supplied current PowerShell cleanup/CLIXML documentation and Python
exception/context-manager semantics. These changes use existing supported APIs;
no framework or library upgrade is part of the implementation.

Merged as [PR #245](https://github.com/RoyMatus/Swypetris/pull/245), main commit
`d2c1e56ea33e18922a7a313c7398e28f9ad3184c`. Required CI run `38142944769`
passed: selection 7 seconds, Windows release tools 38 seconds (45 passed, one
skip), Android job 544 seconds. The Android job included 104 Python tests,
79 passing Linux Pester cases (12 platform skips), 149 JVM tests, 156 passing
Android tests (two opt-in skips), Sonar quality gate and execution-proof checks.

## Redundant calculations

History is loaded once during model initialization and reused for legacy-record
calculation. Derived record history is cached until its snapshot-state input
changes; sorting, tie order, duplicate IDs and persistence formats are preserved.
Directions for 216 firework sparks are computed once with the original numeric
expression; draw order, geometry, color and animation timing are preserved.

Risk-based local checks:

- Seven Android cases passed in 16.062 seconds, covering history initialization,
  cache invalidation, migration, new records, naming and statistics reset.
- Before the renderer change, the opt-in export test passed in 17.736 seconds
  and saved 75 frames: round/absolute celebrations at two portrait sizes and
  postcard rendering, each at 15 launch/burst/fade boundary timestamps.
  The exporter hashes canonical ARGB pixels before PNG encoding.
- After the change, the exporter passed in 17.010 seconds. All 75 canonical
  ARGB frame hashes matched the baseline exactly.
- Six focused JVM cases passed, including all 432 cached Float component bits
  against the original expression. Debug/test APK assembly, lint (including
  test sources) and detekt passed together in 1 minute 50 seconds.
- Thirteen victory/postcard UI cases passed in 47.414 seconds, covering animated
  backgrounds with fixed controls, lifecycle pause, reduced motion, portrait
  viewport/font scales, theme restoration and native sharing.

Rendering measurements use the same headless API 35 emulator, software drawing
with bitmap clearing, 30 warm-up frames and five samples of 60 frames per fixture.
They do not measure GPU presentation, device smoothness or audible audio output.

| Fixture | Before median ms/frame | After median ms/frame |
| --- | ---: | ---: |
| Round, small | 2.392 | 2.215 |
| Round, large | 3.325 | 3.320 |
| Absolute, small | 3.763 | 4.093 |
| Absolute, large | 4.244 | 4.826 |
| Postcard | 4.446 | 4.174 |

These separate runs show mixed timing changes and do not establish a consistent
rendering speedup. The supported result is removal of repeated trigonometric
calculations with identical tested pixels, not a measured frame-rate improvement.
Logs and baseline/export artifacts are retained in ignored
`app/build/verification/fireworks/`. The artifact exporter is opt-in through
`fireworkPhase`; ordinary CI retains the deterministic direction-bit test.

Merged as [PR #246](https://github.com/RoyMatus/Swypetris/pull/246), main commit
`290b19b5331bae506bae17c1a94896da1b616d8f`. Required CI run `38144447756`
passed; the Android job took 801 seconds. Its full instrumentation suite passed
158 cases with three opt-in skips in 415.379 seconds, following successful
one-case picker and fourteen-case UI preflights. Build, lint, detekt, full JVM
coverage, Sonar quality gate and execution-proof checks passed. The longer run
was inspected while active; GitHub did not expose unfinished job logs through
the CLI/API. It completed without cancellation or retry.

## Dependency verification

Added SHA-256 verification for 1,177 resolved artifacts and metadata files, with
metadata verification enabled and no broad trust exceptions. Dependency/plugin
versions and repository declarations are unchanged. Bootstrap covered debug,
instrumentation APKs, JVM/coverage, detekt, debug/release lint, Dokka and both
Windows/Linux AAPT2. Candidate generation tasks passed in 132 seconds.

Every entry matched a separately retrieved repository SHA-256 sidecar or the
SHA-256 of an HTTPS download from a configured repository:

| Source | SHA-256 sidecar | Direct artifact download |
| --- | ---: | ---: |
| Google Maven | 382 | 10 |
| Maven Central | 240 | 529 |
| RuStore Maven | 12 | 0 |
| Gradle Plugin Portal | 0 | 4 |

There were no local-cache or remote hash mismatches. Twenty-six AndroidX aliases
required their verified Gradle module metadata to map cached filenames to the
published artifact URLs. Source URLs are recorded in the verification entries.
Repository checksums/downloads share repository trust; no independent publisher
signature verification is claimed. Sonar engine/analyzer downloads outside
Gradle's resolver are not covered. See [maintenance and trust limits](../DEPENDENCY-VERIFICATION.md).

The graph export records for all 75 configurations were byte-identical before
and after verification, including 553 distinct component versions. One existing
`debugImplementationDependenciesMetadata` configuration reports versionless
Compose tooling/manifest requests; its diagnostic was preserved on both sides,
not counted as a resolved graph. The other 74 configurations resolved.

Strict local validation passed in 10 seconds, reusing 99 unchanged tasks and
executing three; the same debug/test/lint/detekt/coverage/Dokka scope was covered.
The unmodified `Build-Release.ps1` also passed in 177.692 seconds with signed
APK/AAB certificate checks and all 152 JVM results reused. Its local artifacts
are not a publication and do not replace final exact-main release verification.

An isolated Gradle 9.3.1 fixture accepted a benign JAR, then rejected modified
bytes with the exact artifact name and expected/actual SHA-256 in its report.
Baseline and rejection each took nine seconds; separate Gradle user homes kept
shared caches untouched. Evidence, graph exports and provenance checks are in
ignored `app/build/verification/dependency-audit/`.
