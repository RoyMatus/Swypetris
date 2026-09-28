# Test suite audit (#62)

## How to run the suites

Use JDK 21 and the Android SDK. On Windows, `./tools/Verify-Tests.ps1 -Suite Fast` runs the PR build, lint, JVM tests, and JaCoCo checks, then prints a short XML-backed summary. For Android tests, start an isolated emulator and run `./tools/Verify-Tests.ps1 -Suite Android -Serial emulator-5554`, replacing the serial with the connected emulator's actual serial. Pass `-JavaHome <JDK21-directory>` if Java is not on `PATH` and `JAVA_HOME` is unset. Complete Gradle logs are saved under ignored `app/build/verification/`. On Linux/macOS use `./gradlew` and `ANDROID_SERIAL=<serial>` directly.

JVM tests use JUnit 4 for concise rule checks and Kotest BehaviorSpec for multi-step state behavior. Gradle runs both through JUnit Platform and Vintage. Android framework, activity lifecycle, media decoding, and Compose interactions remain in `androidTest` with AndroidX JUnit 4 and Compose test APIs. New tests should use controlled clocks and seeded or injected random sources; a sleep or an actual device belongs only in an explicitly opt-in measurement scenario.

PR CI runs the fast JVM suite and uploads test and JaCoCo reports. The release verification workflow runs the full Android suite on an API 35 emulator and uploads its reports. The `EnergyScenarioTest` workload is opt-in via `energyScenario=true`; it is skipped in the normal suite.

## Inventory and decisions

Baseline on 2026-09-28: 12 JVM test classes with 65 JUnit methods; 21 Android test classes with 71 methods, plus the `IsolatedStorageRule` fixture. The following review compares each class with current game behavior. Tests marked **keep** have a distinct regression purpose; no class was removed merely to reduce the count.

| Test class | Level and behavior checked | Decision |
| --- | --- | --- |
| `DifficultyTest` | JVM: Easy/Medium/Hard curves, late-game bounds, selected difficulty across rounds | Keep; covers current three-mode progression. |
| `GameEngineTest` | JVM: movement, collision, rotation, drops, line clears, game over, bag, gravity | Add stacked-board ghost case; the original ghost check used an empty board. |
| `GameFeedbackTest` | JVM: command-to-feedback rules | Keep; integration feedback test checks playback effects separately. |
| `GameLayoutTest` | JVM: fruit placement across sizes and shapes | Keep; Android UI tests separately check rendered bounds. |
| `GameRulesTest` | JVM: score and level boundaries, mixed descent, feedback priority | Keep; assertions match current score rules. |
| `GestureControllerTest` | JVM: taps, rotation, reversal, diagonal protection, hard drop, piece change, clear | Keep; each boundary or state transition protects a distinct input behavior. |
| `LaunchIntroMotionTest` | JVM: deterministic animation progress and haptic values | Keep; device tests check rendered intro and lifecycle. |
| `LineClearAnimationTest` | JVM: column order, timing boundary, completed-clear count | Keep; UI clear test checks pause and rendering. |
| `PlaylistClockTest` | JVM: seeded shuffle, exact gaps, pause, legacy selection | Keep; injected clock avoids wall time. |
| `RecordHistoryTest` | JVM: strict records per difficulty and legacy group | Keep; UI results test checks persistence and entry. |
| `RewardsTest` | JVM: fruit thresholds, fixed line reward, cell-based gesture steps | Keep; all three are current rules, though the gesture case spans two areas. |
| `VictoryRulesTest` | JVM: threshold, overshoot, second round, victory over loss | Keep; state transitions are independent of the victory UI test. |
| `BrandNavigationTest` | Android UI: menu order, compact layout, contacts and intent failure | Keep; needs Compose and platform intents. |
| `EnergyScenarioTest` | Android measurement: repeatable gameplay workload | Keep opt-in; its real-time sleep is for measurement, never a normal correctness assertion. |
| `ForegroundUiTest` | Android lifecycle: notification shade, task reuse, insets | Keep; requires system UI and Activity state. |
| `GameFeedbackIntegrationTest` | Android integration: sound/vibration settings and single-event behavior | Keep; complements pure feedback rules. |
| `GameTimerTest` | Android model: injected scheduler, pause remainder, clear and terminal deadlines | Keep; deterministic despite Android model construction. |
| `GameUiTest` | Compose UI: settings, controls, recreation, pause, record and layout | Keep; exercises user actions beyond model-only tests. |
| `GestureDensityTest` | Compose input: pointer across spawn and pixel-to-dp conversion | Keep; device density behavior cannot be established by JVM gestures alone. |
| `HelpHudTest` | Compose UI: help, HUD bounds and score pulse | Keep; visual semantics and font scale. |
| `LaunchIntroRecreationTest` | Android lifecycle: intro across recreation and background | Keep; distinct from intro frame tests. |
| `LaunchIntroTest` | Compose UI: skip, Back, animation stages and icon assets | Keep; covers observable launch behavior. |
| `LegalScreenTest` | Compose UI: offline notices and bundled license | Keep; verifies packaged content. |
| `LineClearUiTest` | Compose/model: clearing pause, controlled time and cancellation | Keep; complements pure line-clear rules. |
| `MigrationTest` | Android storage: prior history migration | Keep; requires persisted Android data. |
| `MusicIntegrationTest` | Android media: fanfare mode and packaged audio decoding | Keep; JVM playlist tests cannot decode Android media. |
| `MusicSettingsFruitTest` | Compose UI: picker, persistence, fruit visuals and gesture safety | Keep; verifies current UI and interaction rules. |
| `PublicationTest` | Android UI/assets: offline privacy and store-media export | Keep; validates packaged privacy and generated media. |
| `ResultsIntegrationTest` | Compose/storage: record name, game-over result and history | Keep; user flow and persistence. |
| `SessionLifecycleTest` | Android model/storage: preferences, resume, gesture, clear, corruption, victory | Keep; covers current lifecycle rules with controlled clock. |
| `SessionWriteTest` | Android storage: snapshot writes and board cache | Keep; protects persistence integrity. |
| `StatisticsResetTest` | Android UI/storage: confirm versus cancel | Keep; protects destructive settings action. |
| `VictoryThemeIntegrationTest` | Compose/model: victory continuation, palettes, hints, large font | Keep; covers state and presentation together. |

The audit found no proven obsolete or interchangeable test group to remove. The Android classes need framework services or verify actual Compose behavior; moving them to JVM would require an Android simulator or duplicating the UI mechanism. Newly added `SessionQueueBehaviorSpec` covers ordered seven-bag restoration and rejection of a corrupt queue, which the previous tests did not exercise directly. Its `Given/When/Then` structure makes the saved-session transition explicit.

## Coverage and limits

Before this change, JVM JaCoCo recorded 362/2,562 lines (14.1%) and 253/1,524 branches (16.6%) across compiled debug production classes. This includes Compose rendering and Android code that the JVM suite cannot execute. Use class-level misses to find candidate logic, not the overall percentage as a target. Android instrumentation coverage is not merged into this report.

After this change, 68 JVM cases pass; JaCoCo records 367/2,562 lines (14.3%) and 258/1,524 branches (16.9%). The three additional cases are the stacked-board ghost check and two saved-queue BehaviorSpec cases. The baseline Android run completed 71 cases with zero failures and one intentional `EnergyScenarioTest` skip. No Android test was rewritten, consolidated, or removed because the audit did not establish an obsolete or equivalent replacement.

Known remaining gaps: exact visual rendering on physical devices and broad device/API compatibility are covered by targeted manual checks rather than deterministic JVM assertions. The opt-in energy scenario reports runtime statistics, not a power-consumption claim. Future changes to platform media, system bars, or app-store packaging should be checked on the affected devices and distribution channel.
