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
