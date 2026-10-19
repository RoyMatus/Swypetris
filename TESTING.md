# Test suite audit (#62)

## How to run the suites

Before a new epic, a continuation on another machine or standalone ticket, diagnose the execution host and select the budget/place using [publishing/EXECUTION-PROFILES.md](publishing/EXECUTION-PROFILES.md). Tooling-only changes use focused tool tests; documentation-only changes need no Android build.

Use JDK 21 and the Android SDK. With PowerShell 7 on Windows/Linux, `./tools/Verify-Tests.ps1 -Suite Fast` runs debug build, lint, JVM tests, and JaCoCo checks, then prints a short XML-backed summary. For Android tests, start an isolated emulator and run `./tools/Verify-Tests.ps1 -Suite Android -Serial emulator-5554`, replacing the serial with the connected emulator's actual serial. Pass `-JavaHome <JDK21-directory>` if Java is not on `PATH` and `JAVA_HOME` is unset, and `-MaxWorkers <profile-budget>` to bound concurrency. Complete Gradle logs are saved under ignored `app/build/verification/`. Unix hosts can also use `./gradlew` and `ANDROID_SERIAL=<serial>` directly.

Before a direct `adb -s <serial> shell am instrument` capture outside the Gradle runner, use `tools/ci/Prepare-DirectInstrumentation.ps1 -Serial <serial>` on the owned headless emulator. It checks the installed app/test packages against the already-built APK bytes and signing metadata, installs missing or different artifacts and retains failure diagnostics. The Windows emulator launcher retains startup stdout/stderr, PID, serial and options in ignored verification output before it runs the same package preparation. Preserve that output when the emulator disappears.

JVM tests use JUnit 4 for concise rule checks and Kotest BehaviorSpec for multi-step state behavior. Gradle runs both through JUnit Platform and Vintage. Android framework, activity lifecycle, media decoding, and Compose interactions remain in `androidTest` with AndroidX JUnit 4 and Compose test APIs. New tests should use controlled clocks and seeded or injected random sources; a sleep or an actual device belongs only in an explicitly opt-in measurement scenario.

Graphics and audio tests assert measurable properties: pixels, bounds, clipping,
PCM data, decoder/player state and playback position. Passing them is not an
aesthetic judgment or a listening assessment. Ordinary tests keep bitmap captures
in memory when assertions consume them; successful-run PNG exports solely for
manual inspection are omitted. Failure diagnostics, publication-media generation
and explicit numeric measurement exports retain their separate purposes.

For an audio task requiring perceived loudness, softness or fatigue acceptance,
establish an available and authorized listening or emulator-output capture path
before building recording tooling. Record the listener, playback path and actual
audition result separately from asset hashes, PCM bytes and native playback tests.
If listening access is unavailable, retain the objective evidence and report the
subjective result as unknown. Do not expand the emulator gRPC allowlist or capture
host microphone audio without authorization.

`FireworkRenderingTest.exportsDeterministicBoundaryFrames` is an opt-in export:
pass `fireworkPhase=<artifact-label>` to retain deterministic renderer frames,
`fireworkBenchmark=true` for software-draw timing and approximate process-wide
ART allocation/GC counters, and `victoryCapture=true` for recordings of both
actual victory Activities with their production animation clock. The recording
exports portrait video frames, observed phase timestamps, graphics statistics
and a static postcard. These are review/measurement artifacts, not automated
judgments of beauty. Recording and emulator overhead must be reported alongside
any comparison; software drawing is not device GPU presentation performance.
The normal regression skips this export and does not record video or wait for
real-time celebration cycles.

`MaterialRenderingTest` checks the twelve themes and Settings cards at two portrait
sizes/font scales, all seven connected figures, spawn clipping and theme-switched
textured explosions. `ConnectedMaterialRenderingTest` compares actual atlas pixels
for all canonical cells/rotations, O's effective zero rotation, square rotation before
non-square scaling and four-quadrant reconstruction. It also checks shared material
ownership. `ConnectedSessionTest` covers format-four migration, format-five round
trips, metadata-only cache invalidation and malformed artwork rejection;
`BlockAppearanceTest` covers lock, hidden lock-out, row removal and shard identity.
Pass `materialCapture=true` for source-comparison PNG exports
or `materialBenchmark=true` for cached software-draw timings. The separate
`MaterialFrameEvidenceTest` requires `materialFrames=true`: it measures the real
Activity with its production clock, a dense board and alternating movement input,
and exports screenshots/gfxinfo. It has no ComposeTestRule and is skipped in
ordinary regression. Headless emulator and shared-host overhead are part of the
measurement; no physical-device frame rate or baseline speedup is implied.

`MenuFrameEvidenceTest` requires `menuFrames=true`. It records the real menu
Activity while switching the twelve approved themes twice, exporting observed
frame timings, process memory and screenshots. It is skipped in ordinary
regression; measurements are specific to the selected device and observation
interval, with no frame-rate threshold or memory-leak verdict.

For development checkpoints, use `./tools/Verify-Tests.ps1 -Suite FocusedJvm -TestClass BackgroundCropTest,GameEngineTest` or `-Suite FocusedAndroid -TestClass SmokeTest -Serial emulator-5554`. Short and fully qualified class names are accepted; methods, wildcards, missing classes and empty selections are rejected. Focused JVM runs do not generate whole-app coverage. The original `Fast` and `Android` modes remain available; Fast builds debug, runs debug lint/JVM/JaCoCo, but does not run detekt, Sonar or device tests and does not replace the mandatory final local gate.

Every completed Gradle invocation writes a timestamped log and JSON summary in ignored `app/build/verification/`: elapsed seconds, source commit/dirty flag, selected classes, Java/PowerShell/OS and explicit emulator identity when applicable. XML reports must be complete, successful and match a focused selection. Fresh execution rejects stale reports. JVM `UP-TO-DATE` and `FROM-CACHE` results are explicitly labelled reused rather than freshly executed; device tests must execute. Failed invocations retain their log and failed summary. No environment dump or signing credentials are recorded.

Agree acceptance criteria and material visual/audio references before implementation. Run focused checks at meaningful checkpoints; after a failure, diagnose and exercise the failed scenario before broader checks. Prepare final code, tests, documentation and English PR text before its required local gate; API-only description edits need no new code commit. Do not repeat successful unchanged full suites after each small edit. Use full local regression for cross-system risk or unresolved failures. Build the final signed candidate after the feature set is complete; earlier signed candidates are warranted for signing, self-update, release shrinking or packaging changes. Preserve per-issue PR checks and same-commit local main release gates.

Final local PR checks use `tools/ci/select_checks.py` for affected Android classes plus SmokeTest on an explicit API 35 emulator. App checks retain complete debug JVM coverage even with narrow Android selection. Shared/unmapped inputs select full regression. `Invoke-LocalChecks.ps1` records exact head/base and required results; merge uses that evidence, not a hosted status. The exact-main local release gate adds full tool/JVM/Android/quality checks and independent signed artifact/runtime verification. No GitHub Actions or self-hosted runner is used. SonarQube Cloud is the explicit exception: its scanner runs locally and awaits the remote Quality Gate. Missing local capacity blocks completion. Physical-device use remains limited to device-specific behavior.

## TDD for behavior changes

Use TDD by default for new or changed testable logic and reproducible bug fixes. Apply it as work touches behavior; existing code and tests do not need a retrospective rewrite. The ticket's risk-based check plan identifies the scenario, test level and any exception before implementation. Tests express acceptance behavior and boundaries rather than copying implementation details.

| Change | Development approach |
| --- | --- |
| Game rules, scoring, collision, progression, timing and state transitions | Small deterministic JVM RED/GREEN cycles; control clocks and random sources. |
| Reproducible bug | First run a regression test exposing the bug against unchanged production behavior. An existing failing regression can supply RED. |
| Persistence, migrations, lifecycle or Compose interaction | Test the contract first at the lowest faithful level; use focused instrumentation when real Android behavior is needed. Keep separately testable rules on the JVM. |
| Behavior-preserving refactoring | Establish a green baseline from valid existing tests; add passing characterization tests for coverage gaps before refactoring. Do not manufacture RED for unchanged behavior. |
| Subjective visuals or audio | Agree references and acceptance criteria first. Use TDD for changed measurable behavior, and visual/listening acceptance for subjective qualities; record unavailable acceptance as unknown. |
| Documentation or instructions | Review content, links and consistency; no artificial tests or Android build. |

### Short development cycle

1. **RED:** Write one small test for the next acceptance scenario and run its focused scope before implementing the behavior. For a bug, keep production behavior unchanged until the test reproduces it. Confirm the observed failure is the missing behavior or bug. Missing SDK/device access, unrelated compilation errors, unexplained timeouts and broken fixtures are not behavior RED. For a new API, minimal declarations or stubs may enable compilation; observe the behavioral failure before adding the implementation.
2. **GREEN:** Implement only enough to satisfy that scenario, then run the same test scope. Keep assertions tied to requirements; document a genuine requirement/test correction instead of weakening checks to fit the code. Add the next scenario through another small cycle rather than implementing a whole feature after one test.
3. **REFACTOR:** Improve structure where needed while preserving behavior, and rerun affected tests after changes. If no refactor is needed, say so. Broaden checks for coupled behavior or unresolved risk, then complete the existing final PR/release gates at their normal delivery boundaries.

Use the existing `FocusedJvm` or `FocusedAndroid` runners described above; they select classes, not individual methods. `-Reason` can identify the ticket, scenario and RED/GREEN phase without adding a new runner mode. Direct narrower checks remain available under the normal verification policy. Keep compatible caches; a relevant implementation/test change justifies rerunning the affected scope, while an unchanged successful unrelated suite remains reusable. Do not clean, force reruns or run full regression, Sonar and signed builds at every cycle by habit.

### Evidence and exceptions

Retain the actual test identity, expected/observed RED failure, GREEN result and post-refactor result when applicable, with commands and log/report paths. Preserve relevant failing XML under ignored verification output before another run overwrites it. Existing failed runner summaries remain failed; a nonzero exit alone does not identify a valid RED. Label cached/up-to-date results as reused and only claim observed execution when it occurred. A green final report or coverage percentage alone does not demonstrate that tests preceded implementation.

Record exceptions in the ticket's check plan with the reason and alternative verification. The table covers routine exceptions; a reproducible scenario that cannot reasonably be automated needs the concrete limitation and an alternative reproduction/acceptance check. Missing capacity does not waive a required check. Do not claim TDD if RED was not observed or was written after the behavior; report the actual verification performed. Intermediate RED results never authorize merge, and mandatory final evidence must still pass for the exact PR head/current base.

For the first three suitable, independently authorized logic/bug tickets after [#303](https://github.com/RoyMatus/Swypetris/issues/303), link the preceding pilot records in each ticket's check plan and record its position (1/3, 2/3 or 3/3) to avoid restarting the trial in a new chat. Use the normal completion records to retain actual focused-check durations, substantive rework and acceptance scenarios missed during implementation; unknown active-work time stays unknown. On the third completion, summarize all three outcomes and any observed process cost. This is a small process trial, not proof of a causal speedup; it does not authorize starting future tickets or replacing the existing release gates.

## Current verified inventory

The latest completed broad local snapshot is [PR #255](https://github.com/RoyMatus/Swypetris/pull/255), head `251af6c66a92da7c9cac7f76fba6ca2ac06fedbb` on 2026-10-12: **154 JVM cases** and **175 unique Android cases**, 172 passed and three opt-in skips. These are case counts, not assertions, portrait/font fixtures or repeated executions. The default Android skips are `EnergyScenarioTest` (measurement), `SignedUpdateScenarioTest` (explicit signed-install fixture) and `FireworkRenderingTest` (explicit frame/clip export); a normal full regression does not exercise them. Both #248 motion exports passed separately, without being counted as additional unique regression cases. Subsequent changes still need their own applicable exact-head evidence.

Historical full PR CI additionally ran the one-case picker preflight and a 14-case UI preflight before the complete inventory. Those 15 cases were repeated in the full run, not 15 extra unique tests. The current full local gate runs one complete instrumentation suite; targeted preflights remain available as development checks. The signed APK has a separate menu/settings/new-game/pause/Continue runtime smoke; full instrumentation exercises the debug target. JVM coverage measures debug production code executed by JVM tests and does not include Android/Compose coverage.

The original #62 counts and coverage figures below are historical snapshots, not the current inventory. Use actual XML and invocation outcome for each new run; Gradle cached/up-to-date results do not represent fresh test execution. Settings selection includes `StartingLevelTest` and `VibrationSettingsTest` alongside the existing game/music/reset flows and `SmokeTest`.

## Test class inventory (#205)

The original #62 baseline on 2026-09-28 had 12 JVM test classes with 65 JUnit methods and 21 Android test classes with 71 methods, plus the `IsolatedStorageRule` fixture. These are historical counts. The following current review records each class's distinct regression purpose and keep/update decision; no class is removed merely to reduce the count.

| Test class | Level and behavior checked | Decision |
| --- | --- | --- |
| `BackgroundCropTest` | JVM: aspect-preserving cover, pinch anchor, clamping and portrait viewport dimensions | Keep; catches numeric crop bounds without an emulator. |
| `BackgroundStorageTest` | Android: bounded image import, private copy, persisted crop/toggle and invalid/missing content | Keep; exercises real bitmap and storage APIs. |
| `BackgroundUiTest` | Android UI: pinch/drag, crop save/cancel, recreation and restored image | Keep; protects retained settings and game state. |
| `BackgroundPickerTest` | Android UI: real DocumentsUI selection and result callback | Keep; scoped MediaStore fixture requires API 29+, picker supports app minSdk. |
| `BackgroundRenderingTest` | Compose: portrait sizes, font scale, light/dark backgrounds and unchanged geometry/piece pixels | Keep; saves renders for inspection. |
| `DifficultyTest` | JVM: Easy/Medium/Hard curves, late-game bounds, selected difficulty across rounds | Keep; covers current three-mode progression. |
| `GameEngineTest` | JVM: movement, collision, rotation, drops, line clears, game over, bag, gravity | Add stacked-board ghost case; the original ghost check used an empty board. |
| `GameFeedbackTest` | JVM: command-to-feedback rules, global intensity, zero suppression and fixed-strength fallback | Keep; integration feedback test checks playback effects separately. |
| `ThemeSoundGainTest` | JVM: per-event gain routing, Classic CLEAR outlier attenuation, finite positive attenuation bounds and fallback | Keep; numerical safety cannot establish perceived loudness or replace Pixel speaker/headphone audition. |
| `GameLayoutTest` | JVM: fruit placement across sizes/shapes and random round-theme selection | Keep; Android UI tests separately check rendered bounds. |
| `PaletteCycleTest` | JVM: stable ordered theme selection, forward/backward wrap, unknown-ID fallback and unchanged authored list | Keep; independent of pointer routing and persistence checks. |
| `GameRulesTest` | JVM: score and level boundaries, mixed descent, feedback priority | Keep; assertions match current score rules. |
| `GestureControllerTest` | JVM: taps, rotation, reversal, diagonal protection, hard drop, piece change, clear | Keep; each boundary or state transition protects a distinct input behavior. |
| `LaunchIntroMotionTest` | JVM: deterministic animation progress and haptic values | Keep; device tests check rendered intro and lifecycle. |
| `LineClearAnimationTest` | JVM: column order, timing boundary, completed-clear count | Keep; UI clear test checks pause and rendering. |
| `VictoryMotionTest` / `FireworkParticlesTest` | JVM: periodic timing, elapsed-time partitioning across wrap, spark rise/fall, outward deceleration, varied finite lifetimes and historical trail ordering | Keep; verifies motion contracts without pinning the previous renderer's ray coordinates or judging appearance. |
| `FireworkRenderingTest` | Android opt-in: deterministic boundary frames, matched software/ART samples and real Activity motion clips/postcard | Keep explicit export/measurement; default skipped, no aesthetic oracle or extra animation clock. |
| `PlaylistClockTest` | JVM: seeded shuffle, exact gaps, pause, legacy selection | Keep; injected clock avoids wall time. |
| `RecordHistoryTest` | JVM: strict records per difficulty and legacy group | Keep; UI results test checks persistence and entry. |
| `RewardsTest` | JVM: fruit thresholds, fixed line reward, cell-based gesture steps | Keep; all three are current rules, though the gesture case spans two areas. |
| `VictoryRulesTest` | JVM: threshold, overshoot, second round, victory over loss | Keep; state transitions are independent of the victory UI test. |
| `AbsoluteVictoryRulesTest` | JVM: retired score boundary, normal spawning/loss and overflow-safe larger scores | Updated for endless play; no terminal score is introduced. |
| `EndlessProgressionTest` | JVM: movement/scoring across 999,999 and subsequent ordinary rounds with unchanged gravity | Protects continuation beyond the retired cap. |
| `AbsoluteVictoryIntegrationTest` | Android: recovery from retired terminal saves, exact numeric persistence, same-ID record continuation and actual postcard PNG/read-only URI | Preserves old saves/history and tests image sharing independently of layout. |
| `AbsoluteVictoryUiTest` | Compose: round-record share arrow and Continue across portrait/font sizes, native chooser cancellation/return | Replaces the retired terminal name-entry flow. |
| `RoundRecordSharingTest` | Android: strict record-at-start eligibility, ties, repeated qualifying rounds, lifecycle and restoration | Sharing never writes a result or ends the run. |
| `RecordShareFailureTest` | Compose: dismissible missing-receiver error and continued play without a history write | A recoverable share error must preserve the round. |
| `EndlessScoreUiTest` | Compose: full Long record score at narrow portrait/large font | Oversized scores wrap between digit groups without clipping. |
| `BrandNavigationTest` | Android UI: menu order, compact layout, contacts and intent failure | Keep; needs Compose and platform intents. |
| `EnergyScenarioTest` | Android measurement: repeatable gameplay workload | Keep opt-in; its real-time sleep is for measurement, never a normal correctness assertion. |
| `ForegroundUiTest` | Android lifecycle: locked portrait board bounds, notification shade, task reuse, insets | Keep; requires system UI and Activity state. |
| `GameFeedbackIntegrationTest` | Android integration: sound/vibration settings and single-event behavior | Keep; complements pure feedback rules. |
| `VibrationSettingsTest` | Compose UI: 0/50/100% slider input, persisted intensity, narrow/tablet portrait screens and large fonts | Update fixture/name; retain every input, persistence and reachability assertion. |
| `GameTimerTest` | Android model: injected scheduler, pause remainder, clear and terminal deadlines | Keep; deterministic despite Android model construction. |
| `GameUiTest` | Compose UI: settings, controls, recreation, pause, record and layout | Keep; exercises user actions beyond model-only tests. |
| `GestureDensityTest` | Compose input: pointer across spawn and pixel-to-dp conversion | Keep; device density behavior cannot be established by JVM gestures alone. |
| `HelpHudTest` | Compose UI: help, HUD bounds and score pulse | Keep; visual semantics and font scale. |
| `LaunchIntroRecreationTest` | Android lifecycle: intro across recreation and background | Keep; distinct from intro frame tests. |
| `LaunchIntroTest` | Compose UI: skip, Back, animation stages and icon assets | Keep; covers observable launch behavior. |
| `LegalScreenTest` | Compose UI: offline notices and bundled license | Keep; verifies packaged content. |
| `LineClearUiTest` | Compose/model: clearing pause, controlled time and cancellation | Keep; complements pure line-clear rules. |
| `MigrationTest` | Android storage: one-time balance reset across separate stores, retained settings/new saves, durable-write failure retries | Keep; requires persisted Android data. |
| `MusicIntegrationTest` | Android media: fanfare mode and packaged audio decoding | Keep; JVM playlist tests cannot decode Android media. |
| `MenuPreparationTest` | Android audio ownership: asynchronous preparation, cancellation and playback gates | Keep; controlled factory completion checks late resource adoption and release without listening. |
| `UpdatePersistenceTest` | Android update policy: durable asynchronous writes, manual coalescing and cancellation | Keep; controlled commit outcomes check ordering and main-thread responsiveness without networking. |
| `MusicSettingsFruitTest` | Compose UI: picker, persistence, fruit visuals and gesture safety | Keep; verifies current UI and interaction rules. |
| `ContactsAboutTest` | Compose UI/intent: approved attribution, full repository URL, portrait/font fit and legal/privacy navigation | Keep; protects exact copy and neighboring actions. |
| `PublicationTest` | Android UI/assets: offline privacy and store-media export | Keep; validates packaged privacy and generated media. |
| `ResultsIntegrationTest` | Compose/storage: record name, game-over result and history | Keep; user flow and persistence. |
| `SessionLifecycleTest` | Android model/storage: preferences, resume, gesture, clear, corruption, victory | Keep; covers current lifecycle rules with controlled clock. |
| `SessionWriteTest` | Android storage: snapshot writes and board cache | Keep; protects persistence integrity. |
| `SmokeTest` | Android UI: launch, new game, pause to menu, continue | Keep; runs on every app change and in full release regression. |
| `StatisticsResetTest` | Android UI/storage: confirm versus cancel | Keep; protects destructive settings action. |
| `VictoryThemeIntegrationTest` | Compose/model: victory continuation, random round themes, manual override, persistence, palettes, hints and large fonts | Keep; covers state and presentation together. |
| `UpdateDownloadTest` | JVM: complete downloads, truncation, oversize, corruption, cancellation and untrusted URLs | Keep; verifies that partial or foreign downloads cannot become installable APKs. |
| `AppUpdatesTest` | Android: release metadata and update entry points | Keep; validates JSON/platform parsing and manual check visibility. |
| `UpdateApkTest` | Android: package/version and malformed APK rejection | Keep; uses the real package parser. |
| `AutomaticUpdatesTest` | Android UI: explicit consent, manual-only choice, preference persistence and disabling | Keep; exercises the real Activity and Settings switch across recreation. |
| `GameplayGeometryTest` | JVM: safe-top spawn alignment, visible bottom and insufficient available height | Keep; protects inset-derived board geometry independently of painting. |
| `GestureHoldTest` | JVM: 300 ms readiness, one exchange per touch, early rotation, cancellation and jitter | Keep; protects gesture timing and replacement-piece isolation. |
| `HiddenBufferTest` | JVM: hidden spawn/collision/ghost rows, partial lock, blocked spawn and collapse | Keep; preserves logical board boundaries beyond visible geometry. |
| `HoldTest` | JVM: empty/occupied Hold, bag consumption, lock re-enabling, blocked swap and reset | Keep; separate from gesture/platform presentation. |
| `LockDelayTest` | JVM: 500 ms delay, successful-action reset budget, airborne freeze and pause | Keep; distinct timing boundaries. |
| `MarathonTimingTest` | JVM: speed/20G bound, callback partitioning and clear/lock/death deadlines | Keep; checks preserved remainders without wall-clock sleeps. |
| `MenuSkyMotionTest` | JVM: independent cloud recycling and twinkle periods | Keep; protects motion continuity independently of artwork pixels. |
| `PlacementTest` | JVM: last-action spin classification, kick promotion, chains and hidden-row perfect clears | Keep; placement classification precedes award calculation. |
| `ProgressionTest` | JVM: pre-clear level, cumulative higher-start thresholds, round retention, overflow and fruits | Keep; covers coupled line/level/fruit boundaries. |
| `ScoringTest` | JVM: scaled awards, combo/back-to-back/perfect-clear composition, drops and single award | Keep; prevents duplicate or overflowed scoring. |
| `SrsTest` | JVM: eight transitions, wall/floor kicks and hidden rows | Keep; distinct I/JLSTZ tables and rejected-rotation state. |
| `UpdateCheckPolicyTest` | JVM: polling, rate limits, HTTP dates and malformed timing | Keep; separates generic failures from rate-limit evidence. |
| `UpdateFailureTest` | JVM: safe error classification, cleanup, cancellation identity and programming failures | Keep; never masks unexpected defects as recoverable network notices. |
| `BorderlessHudTest` | Compose pixels/bounds: score/fruit lanes, full-opacity Hold in all twelve themes/seven shapes and unchanged Next contrast | Keep; compare available/used pixels and the active material at equal size/background, preserving semantics/layout. `holdCapture=true` explicitly exports comparisons and representative portrait fixtures. |
| `GameplaySurfaceTest` | Compose pixels: grid under status area and clipped foreground | Keep; protects edge-to-edge surface behavior. |
| `HistoryCacheTest` | Android storage: initialization read, malformed/legacy rows and cache invalidation | Keep; validates the cached record owner's lifecycle. |
| `HoldIntegrationTest` | Compose/storage: gesture, persisted Hold, replacement isolation and feedback suppression | Keep; platform interaction complements JVM Hold rules. |
| `HudLayeringTest` | Compose pixels: piece/fruit intersection and score segment visibility | Keep; preserves drawing order and theme contrast. |
| `MaterialRenderingTest` | Compose pixels/state: twelve materials/fields, seven pigments, light score without backdrop, two portrait sizes/fonts and every Settings card | Keep; optional source-comparison exports/software timings supplement assertions. |
| `MaterialFrameEvidenceTest` | Opt-in native Activity: production clock, dense board, movement, gfxinfo and screenshots | Skip ordinarily; no Compose test clock, and no timing threshold on a shared software emulator. |
| `ThemeSoundIntegrationTest` | Android resources/model/native audio: all 24 approved hashes, manual/random/restored selection, timed/command events, load/cache/stop/release | Keep; `themeSoundCapture=true` additionally runs a real-time repeated gameplay audio workload with and without music. Markers alone do not establish subjective listening acceptance. |
| `MenuSkyTest` | Compose pixels/lifecycle: anchored artwork, background/disabled clocks and isolated wide crop | Keep; its 640×360 artwork crop is not a landscape application viewport. |
| `MenuThemeSwipeTest` | Compose/model: silent logo/free-area theme cycling, every control's exclusion/actions, canceled/multiple/short/vertical input, saved game/palette, intro and portrait/font text/geometry | Keep; gestures use the real menu surface and shared manual-selection owner. |
| `NextQueueTest` | Compose bounds: spawn-position preview and suppression when obstructed | Keep; separate from saved seven-bag ordering. |
| `ShareAppTest` | Android QR/intent: canonical store/latest-release links and sharing | Keep; decoded content and platform payloads. |
| `ShareDialogInteractionTest` | Compose touch/intent: Share without background dismissal | Keep; event propagation and chooser payload. |
| `StartingLevelTest` | Compose/storage: slider, unclipped explanation, cumulative higher starts and unchanged Continue | Update selection; existing scenarios exercise the setting's contracts. |
| `UpdateCheckTest` | Android policy/UI: playable fresh launch with equal/older/offline metadata, dismissible newer offers without consent/download, duplicate manual checks, persisted deadlines and safe messages | Keep; model/platform integration. |
| `UpdateDeliveryRecoveryTest` | Android asynchronous delivery: retry and no installable partial file | Keep; cleanup and transition completion without a real download. |
| `VictorySessionIntegrationTest` | Android model: round continuation, retained score/rules/time and one record | Keep; session/music transitions independent of visual geometry. |

## Tool test inventory

These groups account for the existing coherent modules. Archived launcher/workflow
contract tests do not authorize hosted execution.

| Modules | Regression purpose and decision |
| --- | --- |
| `tools/tests/GitHub-Api.Tests.ps1` | Keep: API payloads, branch/PR/project operations and readiness failure boundaries. |
| `tools/tests/Verification.Tests.ps1`, `LocalVerification.Tests.ps1`, `ExecutionProfile.Tests.ps1` | Keep: selections, fresh/cached XML, exact head/base and hashes, local host budgets and diagnostics. |
| `tools/tests/Release-Verification.Tests.ps1`, `Release-SigningRestore.Tests.ps1`, `Release-Transaction.Tests.ps1`, `Release-Local.Tests.ps1` | Keep: signatures/certificates, private-key restoration, transaction safety, local provenance and immutable publication. |
| `tools/tests/Development-Workflow.Tests.ps1` | Keep: observed intervals, unknown/unfinished time and reports. |
| `tools/ci/test_select_checks.py`, `test_quality_results.py`, `test_instrumentation_output.py`, `test_full_regression_proof.py`, `test_local_verification.py` | Update existing settings assertion; keep conservative routing, complete source/XML inventory, parser failures, thresholds and hash-bound evidence. |
| `tools/ci/test_release_context.py`, `test_release_gate.py`, `test_release_smoke.py` | Keep: release context, exact-main gates and runtime observation parsing, including historical provenance. Retired workflow-routing assertions were removed. |
| `tools/ci/test_windows_android_smoke.py`, `test_windows_android_smoke.ps1` | Keep: actual Windows PowerShell launcher/smoke adapter contract; not another app regression suite. |
| `tools/ci/test_cloud_android_checks.py` | Keep archived/static contracts and optional Unix fixtures; never dispatch cloud checks. |
| `tools/ci/test_developer_workflow.py` | Keep: disabled remote entrypoint and absence of executable workflows or Dependabot scheduling. Retired developer-workflow routing assertions were removed. |

## Finite audit and behavior map (#205)

The inventory accounts for 31 JVM and 49 Android test classes; fixture helpers
are not additional tests. Review starts from merged #248
(`3edb6c280c6ecc43e4309d0390413bd5d83bf575`), without inherited uncommitted app
changes. Run summaries identify subsequent tested heads.

| Supported behavior | Owning regression groups |
| --- | --- |
| Spawn, collision, rotation, locking, hidden rows and loss | Engine, SRS, hidden-buffer, placement, lock-delay and geometry; GameplaySurface/Foreground UI. |
| Scoring, progression, fruits, rounds and record sharing | Scoring/Progression/Rewards/Victory/Endless rules; session/theme/record-share UI/integration and retired-terminal migration; motion/particles and opt-in captures. |
| Gestures, Hold, Ghost and Next | GestureController/GestureHold/Hold and queue rules; GestureDensity/HoldIntegration/NextQueue/GameUi. |
| Pause/Continue, navigation, recreation and durable saves | Foreground/SessionLifecycle/SessionWrite/GameTimer/LaunchIntroRecreation, navigation and saved queue. |
| Settings, results and historical data | StartingLevel/Vibration/MusicSettings/StatisticsReset; ResultsIntegration/RecordHistory/HistoryCache, archive/current ordering and long labels. |
| Updates, migrations, sharing and release safeguards | Migration, update policy/download/failure/platform/recovery/consent; QR/share; tool inventory and independent signed gate. |

Confirmed repairs: settings routing omitted `StartingLevelTest` (the strengthened
existing assertion failed before the mapping fix); vibration and palette settings
forced 600×400 full-UI fixtures. They retain narrow/tall fixtures and now use
600×960 portrait at font scale 2. The vibration method is renamed to describe
portrait coverage. Palette reachability/persistence and every slider 0/50/100%
assertion remain. The isolated wide MenuSky artwork crop remains meaningful.

No test is removed or added by #205; its routing assertion is extended. #248
replaced obsolete implementation-pinning `FireworkDirectionsTest` with three
particle behavior cases, preserving timing and foreground assertions. Default
runs retain three explicit opt-in skips. Both README languages now describe the
already-delivered 999,999 terminal victory/postcard, firework stages, record labels,
saved celebration restoration and vibration strength.

No additional material missing behavior case was established in this finite
review. This does not establish exhaustive state/device coverage or zero bugs.
Physical appearance/listening, broad API compatibility and hardware frame pacing
remain separate limitations. Deterministic pixel/PCM/state checks do not replace
human acceptance. Exact-head and exact-main release gates remain required.

The original #62 audit found no proven obsolete or interchangeable test group to remove. Android classes need framework services or actual Compose behavior; moving them to JVM would require a simulator or duplicate UI mechanism. That audit added `SessionQueueBehaviorSpec` for ordered seven-bag restoration and corrupt-queue rejection. Its `Given/When/Then` structure describes the saved-session transition.

## Historical coverage and limits (#62)

Before this change, JVM JaCoCo recorded 362/2,562 lines (14.1%) and 253/1,524 branches (16.6%) across compiled debug production classes. This includes Compose rendering and Android code that the JVM suite cannot execute. Use class-level misses to find candidate logic, not the overall percentage as a target. Android instrumentation coverage is not merged into this report.

After this change, 68 JVM cases pass; JaCoCo records 367/2,562 lines (14.3%) and 258/1,524 branches (16.9%). The three additional cases are the stacked-board ghost check and two saved-queue BehaviorSpec cases. The baseline Android run completed 71 cases with zero failures and one intentional `EnergyScenarioTest` skip. No Android test was rewritten, consolidated, or removed because the audit did not establish an obsolete or equivalent replacement.

Known remaining gaps: exact visual rendering on physical devices and broad device/API compatibility are covered by targeted manual checks rather than deterministic JVM assertions. The opt-in energy scenario reports runtime statistics, not a power-consumption claim. Future changes to platform media, system bars, or app-store packaging should be checked on the affected devices and distribution channel.

## Signed update checks (#115)

Follow [publishing/UPDATES.md](publishing/UPDATES.md) for the signed old-to-new installer checks in addition to the automated suites. Use an isolated Android 11/API 30 emulator and the API 35 emulator, selecting each adb serial explicitly. Store fixtures, installer-result XML, UI hierarchies, and before/after preferences/session files under ignored `app/build/verification`. Preserve the actual terminal status and installed version; adb installation of the baseline alone does not exercise self-update.

The installed SDK manager wrapper delegates to Android CLI. On this Windows setup, the verified package-install form is `android.exe --no-metrics --sdk=<SDK-directory> sdk install --ignore-outdated-xmls system-images/android-30/default/x86_64`. The legacy semicolon-separated package argument was split by that wrapper and did not install the image; direct Android CLI with the slash-separated package path succeeded. Create the isolated AVD with `avdmanager.bat create avd --name SwypetrisUpdate30 --package 'system-images;android-30;default;x86_64' --device pixel_2`, then wait for `adb -s <serial> shell getprop sys.boot_completed` to report `1` before running tests.
