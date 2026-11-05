# Issue 170: Pixel energy investigation

Measurement date: 2026-11-05. Publication scope authorized by the owner: GitHub Releases only.

## Finding and change

The menu sky continued rendering behind the expanded notification shade even though the Activity remained RESUMED. `rememberMenuSkyTime` now requires window focus as well as its existing enabled/lifecycle conditions. Losing focus freezes the phase; regaining focus continues from that phase. A later gameplay optimization removes temporary coordinate objects from collision checks using the same shared coordinate projection. Artwork, animation speed, layout, gameplay and audio rules are preserved.

Three 120-second measurements per build on the physical Pixel show the following:

| Expanded shade, classic theme, music off | Before: public 1.11.4 | After: release-type 1.11.5 candidate |
| --- | --- | --- |
| App CPU seconds, individual runs | 43.342 / 44.573 / 49.818 | 0.056 / 0.004 / 0.003 |
| Presented app frames | 7,211 / 7,206 / 7,208 | 0 / 0 / 0 |
| Whole-device charge used, mAh | 16 / 16 / 14 | 6 / 6 / 6 |

The mean app CPU utilization falls from approximately 38.259% to 0.018% of one core (99.95% less app CPU **in this obscured-menu scenario**). This is not a 99.95% battery-saving claim. Whole-device charge includes the display, radios, other apps and profiler; the two-minute charge readings have coarse resolution. No general maximum-drain guarantee follows from one device.

## Device, artifacts and conditions

Physical Pixel 7, Android 17 build CP3A.261005.002.A1; Wi-Fi adb, unplugged. Initial battery was 96%, 26.8 C. Captures retain per-run battery/thermal readings, display/build/network snapshots and original settings. Controlled active captures use brightness 128/255, adaptive brightness off, requested 60 Hz and portrait orientation. Ambient conditions, background system activity and changing state of charge are not fully controlled. Emulator API 35 checks establish correctness/harness behavior, never physical energy consumption.

Selected endpoint battery temperatures: visible menu before 26.2–28.4 C / after 27.9–28.7 C; shade before 28.4–28.7 C / after 28.4–28.6 C; gameplay before 27.5–28.9 C / after 27.9–28.9 C. These are endpoint ranges, not continuously sampled maxima or proof of identical thermal conditions.

Public baseline 1.11.4/code 37, release commit `d6a60627c9c617c44593d8cd3d886e2189ebcd16`: installed APK SHA-256 `a69ddac18d42161ab25473b917d4a1f451b3c0ec024457fe2f30d1797b945c75`, matching the public release asset. Task base `ced5adf48c0302108ebf0dca9d14779f1196fa8e` has identical application source/build configuration. Physical focus-fix candidate 1.11.5/code 38 SHA-256: `ce9424fb3eef2f33c12befff5a226a19ebefe483f69fd6412b648eba753aa8a9`. Both use the existing app signer. **Physical after samples predate the later collision-allocation optimization**; its battery impact is not measured. Exact-main artifacts and final acceptance belong to the release compliance report.

## Visible menu comparison

Three valid 600-second samples per build, classic theme, music off, ordinary visible public/release-type menu:

| Measurement | Before | After |
| --- | --- | --- |
| App CPU seconds | 301.785 / 286.306 / 292.243 | 289.891 / 282.106 / 298.274 |
| App CPU, percent of one core | 50.298 / 47.718 / 48.708 | 48.315 / 47.018 / 49.713 |
| Whole-device charge used, mAh | 56 / 64 / 64 | 62 / 56 / 56 |
| Whole-device charge rate, mAh/h | 336.4 / 384.4 / 384.3 | 372.4 / 336.3 / 336.4 |

CPU ranges overlap and visible animation continues. These observations do not establish an ordinary-menu energy improvement. The optimization targets hidden rendering rather than reducing approved visible effects.

Baseline `production-menu/1` is excluded because an APK backup was transferred during capture. Selected baseline samples are `production-menu/2`, `/3` and `production-menu-extra/1`. Selected candidate samples are `production-menu-after/1..3`. Charge is calculated from in-trace counters, not battery snapshots taken after trace transfer by the initial idle collector.

## Gameplay comparison

Three complete 600-second synthetic native-clock runs per build:

| Measurement | Before | After |
| --- | --- | --- |
| App CPU seconds | 67.018 / 67.733 / 68.568 | 67.521 / 67.821 / 66.848 |
| App CPU, percent of one core | 11.170 / 11.289 / 11.428 | 11.253 / 11.303 / 11.141 |
| Whole-device charge used, mAh | 44 / 42 / 42 | 46 / 44 / 46 |
| Whole-device charge rate, mAh/h | 264.5 / 252.4 / 252.4 | 276.5 / 264.4 / 276.5 |
| Presented frames | 1,907 / 1,904 / 1,899 | 1,905 / 1,900 / 1,911 |
| App deadline misses | 3 / 7 / 4 | 4 / 6 / 4 |
| p95 presentation, ms | 37.107 / 37.554 / 37.264 | 37.033 / 37.415 / 37.327 |

CPU/frame observations are similar. Device charge readings are slightly higher after; this sequence, at changing state of charge and with intervening phone calls, cannot isolate an application-energy change. No gameplay drain improvement or exact gesture-latency equivalence is claimed from these captures. The later collision fast path is assessed separately below.

Selected after samples are `pixel-game-after/1`, `/2` and `pixel-game-after-replacement2/1`; all have passing instrumentation and complete ~600-second markers. A call interrupted `pixel-game-after/3` (331-second marker), and the first replacement was interrupted before the owner's pause/resume. Both partial runs remain retained and excluded; successful earlier samples were not repeated.

A linear estimate from the observed candidate gameplay rates is approximately **264–276 mAh for one hour / 528–552 mAh for two hours**, for the entire phone at matching settings. These are forecasts from shorter observations, not measured one-/two-hour drain, and do not establish a percentage of usable battery capacity.

## Gameplay allocation optimization

`fits` previously created a list of four transformed cells on every collision check, including repeated checks down the board for the landing hint/hard drop. A shared inline primitive-coordinate projection now serves both collision checks and the existing list-returning renderer interface; collision checks consume coordinates directly. Rules, cell order, rotations and timing remain the same.

A warmed local JDK 21 allocation test over 100,000 checks measured **27,542,704 bytes before / 176 bytes after** (275.42704 / 0.00176 bytes per call). The original implementation failed the allocation budget; the optimized implementation passed. ThreadMXBean reports approximate per-thread allocated heap bytes; it is not live heap size. This is JVM allocation evidence, not an Android energy percentage or an end-to-end speed benchmark. No further long phone series was run; complete optimization of every game subsystem is not established.

Correctness comparison covers 37,800 cases: seven figures, four rotations, two boards (empty/occupied), x=-4..10 and y=-24..20. Both projected cells and collision decisions match the original object-based projection, including hidden rows and out-of-bounds positions. `CollisionCheckTest` (2 cases), the affected `GameEngineTest` suite and detekt passed; RED/GREEN logs/XML are retained under `setup/collision-*`.

## Short idle observations

One ~60-second release-type observation per condition: backgrounded app used 0.170 seconds CPU / zero presented app frames; screen-on force-stopped control had no observed app process; screen-off app used 0.235 seconds CPU / zero presented app frames. Whole-device charge dropped 4 mAh in each. These short, coarse observations do not establish overnight drain, a causal background energy difference or zero ongoing CPU/network work. The screen-off device reported Dozing with always-on setting 0; no deep-sleep claim is made.

Original display settings were restored (adaptive brightness 1, brightness 128, timeout 60,000 ms, min refresh 0.0, peak Infinity); animator duration scale remained 1.0. Temporary instrumentation was removed, the release-type app remained installed, and the unrelated acceptance package was preserved. Continuous owned logcat captures were stopped after measurement.

## Exploratory theme/music samples

Secondary profiling was shortened rather than repeating the complete primary comparisons. These are single native-clock debug samples, not three-run estimates of theme/music energy differences:

| Menu variant | Seconds | App CPU, percent of one core | Whole-device charge, mAh |
| --- | ---: | ---: | ---: |
| Classic, silent | 150.001 | 61.677 | 16 |
| Classic, music | 60.000 | 52.463 | 8 |
| GitHub Light, silent | 60.000 | 66.699 | 8 |

Different durations, startup/cache effects, debug overhead and gauge resolution prevent causal rankings. In particular, this does not show that music saves energy or that one theme is intrinsically more efficient. No animation-disabled battery cohort was completed and no visual tradeoff was implemented. These samples exercise the variants; they do not justify further production optimizations.

One optional music sample was stopped deliberately when shortening the plan. Another failed its 10-second Activity readiness precondition and has no usable workload; diagnostics remain retained. Starting the same fixture with a fresh app process then passed. The exact cause of the earlier readiness failure is unverified; it is not reported as an app crash or passing energy evidence.

## Workloads and analysis limits

Gameplay is a separate debuggable, production-key-signed cohort with isolated test storage, native Activity/media timing, one input per second, resets every 30 seconds or on loss, and a checked synthetic two-line clear at reset. Subsequent pieces are random. Driver CPU is included; these results cannot be labelled minified production gameplay drain. The player's app is updated with the same signer, without uninstalling it; temporary test preferences do not replace the saved player session.

Trace CPU comes from scheduler slices attributed to the app process or explicit workload-marker process. Scheduling slice counts are not wakeup counts. `doFrame` durations are CPU-side callbacks, not GPU duration or end-to-end presentation. Power rails cover the entire device and can overlap; they are never summed. GPU hardware counters, exact recomposition/allocation attribution, per-app energy and a causal network-energy breakdown are not established by these captures. Source inspection confirms existing lifecycle pause/audio suspension and update-check throttling owners; this alone is not a runtime power measurement.

Perfetto detached-session attach/stop preserves final producer packets. The former SIGTERM collector lost complete workload markers; the former Compose virtual-clock fixture suppressed infinite menu animation. The corrected native harness produced a complete 5.001-second smoke marker and continuous frames on the emulator. These are measurement fixes, not app battery improvements.

Use `trace_processor_shell query --full-sort -f tools/energy-metrics.sql <trace>` for instrumented windows. Standalone production/idle captures use the retained `idle-metrics.sql`. Full sorting removed out-of-order parser errors in the shade comparison without changing its reported values; original processing outputs remain retained. Inspect both `error` and `data_loss` statistics. Baseline ordinary-menu captures dropped three and one overlapping complete graphics slices respectively; one baseline shade capture dropped one. These ambiguities limit graphics interpretation and are not hidden as error-free traces.

## Tests and retained evidence

Behavior TDD: the focus-loss test failed on the baseline (clock advanced from 1.008 to 11.008 seconds while unfocused), then all five `MenuSkyTest` cases passed after the change, including frozen-phase resume. Native menu/game smokes and invalid-configuration guards passed. An invalid palette was first accepted silently, then rejected explicitly after the harness validation fix. Tool smoke verified restoration of all five display settings and compressed trace-transfer hash identity.

Removed only two unreachable ABSOLUTE software-render fixtures. ROUND and POSTCARD exports remain (45 deterministic frames). `AbsoluteVictoryUiTest` still checks current record sharing/Continue/native chooser behavior and remains; legacy save and unbounded-score compatibility tests also remain.

Local raw evidence is ignored under `app/build/energy`: `check-plan.md`, `setup/`, `production-menu/`, `production-menu-extra/`, `production-menu-after/`, `public-panel-before/`, `public-panel-after/` and `pixel-game-baseline/`. Identity/network/full logcat details remain private. Raw traces, SQL, instrumentation logs, parser diagnostics and hashes are retained; source control contains this redacted review. Active work/wait time is unknown.

At the owner's request, continuous app-UID and package-filtered system logcat monitoring began during baseline gameplay run 2. This differs from run 1's monitoring overhead. AppOps `attributionTag` errors and HAL/codec/binder warnings were retained for a separate investigation; their root cause and responsibility are not established. A system-service error is not automatically an app defect.

## References

Checked on 2026-11-05: [Android BatteryManager](https://developer.android.com/reference/android/os/BatteryManager), [Power Profiler](https://developer.android.com/studio/profile/power-profiler), [WindowInfo](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/WindowInfo), [Perfetto CLI](https://perfetto.dev/docs/reference/perfetto-cli) and [trace buffers/data loss](https://perfetto.dev/docs/concepts/buffers). Android/Compose contracts were researched through authenticated Context7; no dependency change was required.

The [JDK 21 ThreadMXBean contract](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.management/com/sun/management/ThreadMXBean.html) was checked directly after Context7 could not return snippets for that contract. The test rejects unavailable counters rather than accepting a negative sentinel as a zero-allocation result.
