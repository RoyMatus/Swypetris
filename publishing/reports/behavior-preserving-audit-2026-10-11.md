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

Added SHA-256 verification for 1,188 resolved artifacts and metadata files, with
metadata verification enabled and no broad trust exceptions. Dependency/plugin
versions and repository declarations are unchanged. Bootstrap covered debug,
instrumentation APKs, JVM/coverage, detekt, debug/release lint, Dokka and both
Windows/Linux AAPT2. Candidate generation tasks passed in 132 seconds.

Every entry matched a separately retrieved repository SHA-256 sidecar or the
SHA-256 of an HTTPS download from a configured repository:

| Source | SHA-256 sidecar | Direct artifact download |
| --- | ---: | ---: |
| Google Maven | 382 | 10 |
| Maven Central | 248 | 532 |
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

Initial Linux CI run `38146267638` failed on eleven missing parent POM/BOM
metadata hashes, before instrumentation. This exposed a warmed-cache bootstrap
gap, consistent with Gradle issue #19228. Refreshing dependency resolution added
exactly those eleven files in 96 seconds; no existing artifact hash changed or
was removed. All eleven additions matched Maven Central. Maintenance guidance
now requires refreshed generation and strict validation; metadata verification
remains enabled. The failed CI logs were preserved before correction.
Strict validation with refreshed resolution then passed detekt, debug lint and
JVM task configuration in 67 seconds (35 unchanged tasks reused). A new graph
export remained byte-identical to the baseline across all 75 configurations.

Merged as [PR #247](https://github.com/RoyMatus/Swypetris/pull/247), main commit
`2fc95ab399f3956cedc8cbd58ded503cd930e2b9`. Corrected required CI run `38146828578`
passed in 792 seconds for the Android job, including 158 passing Android cases
and three opt-in skips (413.119 seconds), quality checks and Sonar. This supplies
Linux execution evidence for the reviewed verification metadata.

## Remove manual-inspection exports

The owner requested removal of tests whose verdict could only come from human
eyes or ears. Review found no such subjective verdicts in the inspected test
suite: pixel/geometry, PCM, decoder and playback-state assertions are automated.
Fifteen ordinary test files did contain successful-run PNG exports used solely
for later manual inspection. Removed that export work while retaining test cases,
product assertions and in-memory bitmaps used in numeric comparisons. Keep
failure diagnostics, publication assets and explicit measurement exports.

Source comparison confirmed all 55 affected test methods remain. Three removed
assertions checked only PNG compression for those deleted inspection exports;
product assertions were retained. Removed the unused icon-preview montage
generation as well. Local test-APK compilation, lint including test sources and
detekt passed in 89 seconds (nine tasks executed, 53 reused). These checks cover
removed helper/import references and static defects; required PR CI executes the
affected Android cases. No additional local full device regression was repeated
for this test-output-only change.

Merged as [PR #249](https://github.com/RoyMatus/Swypetris/pull/249), main commit
`83664c8d8e433463342bb56df55b18f654001b39`. Required CI run `38148400320`
passed all 56 selected Android cases without skips, plus JVM and quality checks.
The successful-run export cleanup did not remove subjective tests: the reviewed
suite had no human-only verdicts to remove.

## Asynchronous menu audio preparation

The first menu request previously read and decoded the bundled WAV and populated
its static AudioTrack buffer on the caller/main thread. Preparation now runs on
Dispatchers.IO. Main owns adoption and playback decisions, using the current
mode, selection, focus and headphone gates when the worker finishes. Only one
preparation may be in flight. Cancellation retains ownership of a late-created
player until cleanup; release does not wait for the worker on main. Prepared
players remain reusable across navigation. PCM decoding, buffer format, gain,
loop points and audio assets are unchanged.

Expected preparation failures now log and remain silent until an explicit new
menu visit instead of propagating a synchronous initialization exception. This
is a deliberate failure-path change; no automatic retry loop is introduced.

Five first-call observations per version used separate instrumentation processes
on the same API 35 headless emulator (`emulator-5560`, software graphics,
`-noaudio`). The temporary measurement fixture was removed from shipped test
sources after measurement. Values below are milliseconds in observation order:

| Measurement | Before | After |
| --- | --- | --- |
| First menu call, wall | 217.690, 245.055, 106.527, 68.720, 456.749 | 12.788, 19.497, 22.037, 3.760, 9.806 |
| Median wall | 217.690 | 12.788 |
| Median main-thread CPU | 203.902 | 6.709 |
| Median observed playback-ready upper bound | 219.249 | 111.460 |

Readiness includes polling and dispatch and is not audible onset. These noisy
emulator observations support moving preparation work off main, not a physical
device performance or perceived audio-quality claim. All ten measurement runs
reported one passing case; traces/logs remain in ignored
`app/build/verification/audio-audit/`.

Five separate eight-second whole-app startup traces per version used force-stop
without clearing data or OS caches. `am start` TotalTime values were
4466/3372/2622/2904/3054 ms before and 3080/3065/2898/3126/3236 ms after;
medians were 3054 and 3080 ms. No whole-app startup improvement is established.
Per-run median frame durations were 129.32/124.85/128.39/134.00/123.52 ms before
and 117.93/135.23/116.11/125.61/124.50 ms after. Software-GPU histogram anomalies
are excluded; these broad captures do not isolate audio or establish causality.

Local validation: four PlaylistClock JVM cases passed; app/test assembly, debug
lint and detekt passed in 107 seconds. Removing the temporary measurement fixture
then rebuilding the test APK and rerunning affected lint/detekt took 59 seconds.
All 13 selected Android cases passed in 232.922 seconds, including six new
controlled-factory cases and the seven real-media/integration cases. The real
loop advanced 4,331,328 frames over 88,485 ms through navigation, exceeding three
1,417,069-frame cycles without resetting. PCM/hash/level checks passed; no
listening or physical-speaker assessment was performed. Nineteen CI selector
tests passed, including future selection of the new preparation class for either
menu-audio source file. Required PR CI covers the mapping change with full
regression; no additional duplicate local full regression was scheduled.
