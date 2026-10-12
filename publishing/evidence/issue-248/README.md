# Pixel firework motion evidence (#248)

Captured on 2026-10-12. [Ordinary victory](round/motion.mp4) and
[absolute victory](absolute/motion.mp4) are recordings of the actual Activity,
including the protected artwork, text, buttons and system bars. Each contains
launches, short flashes, outward sparks, falling historical trails, extinction
and a complete loop. The production animation clock advances throughout;
no Compose test clock or separate capture animation clock is used.

The ordinary clip is 10.541 seconds, the absolute clip 9.755 seconds. Codec startup
makes these shorter than the requested 12-second recording. Observed phase wraps
and file hashes are in [motion-proof.json](measurements/motion-proof.json).
The exporter checks decoded portrait frames and a complete observed cycle within
the recording duration. Decoded video frames and phase observations accompany
each clip; filenames identify video milliseconds, not exact animation phases.
[Renderer boundary frames](renderer-frames/) independently identify exact elapsed
times at small/large portrait resolutions and postcard geometry.

The [static postcard](postcard.png) uses the same pixel renderer. Selected decoded
frames were inspected for composition and readable controls; clip duration, hashes
and observed phase wraps were checked separately.
This does not claim human aesthetic acceptance, listening assessment or physical
device performance. The immutable design references remain linked from #248.

## Matched measurements

Baseline production was unchanged at `d1a02e62867ba74a82ccf5146d1fe08d02f8f5a4`.
Both variants used the identical capture/benchmark harness, one headless API 35
emulator, software GLES rendering, Vulkan disabled, four CPU cores and 2048 MiB
RAM. [Source hashes and conditions](measurements/source-and-conditions.json)
bind the new renderer and harness independently of subsequent documentation
commits. No production gameplay, state, artwork, foreground or clock owner changed.

Software drawing includes clearing a reused bitmap and creating the harness draw
scope at elapsed time 2400 ms: 30 warm-up renders, then five samples of 60 renders
for each fixture. [All 50 samples](measurements/benchmark-samples.tsv) are retained.

| Fixture | Before median ms/render | After median ms/render | Before/after median ART allocated bytes per 60 renders |
| --- | ---: | ---: | ---: |
| Round 360×800, density 1 | 2.100 | 1.188 | 458752 / 32768 |
| Round 1080×1920, density 3 | 3.318 | 1.580 | 491520 / 32768 |
| Absolute 360×800, density 1 | 3.028 | 1.342 | 688128 / 32768 |
| Absolute 1080×1920, density 3 | 3.953 | 1.817 | 688128 / 32768 |
| Postcard 1080×1440, density 3 | 3.806 | 1.630 | 688128 / 32768 |

ART counters are approximate, process-wide and include the harness; these are not
renderer-only allocations. This is one sequential before/after pair, with different
particle work at the sampled phase, not a causal battery or hardware FPS claim.
The bounded cached particles and trail samples avoid per-frame collection/bitmap
creation in production.

During real-screen recording, gfxinfo reported ordinary p50/p90 81/109 ms before
and 77/97 ms after; absolute 81/105 ms before and 81/101 ms after. Jank remained
high (approximately 85–89%): emulator software rendering, recording, instrumentation
and the complete scene contribute. [Raw gfxinfo](measurements/) is retained;
its GPU percentile sentinel of 4950 ms is not interpreted as a valid GPU timing.
No device smoothness or battery improvement is established by these measurements.

## Checks and failed experiments

Both final opt-in capture runs passed their single export case without skips.
The current invocation also passed detekt (88.181 seconds total). The five focused
JVM motion/particle cases passed before capture-only edits and are reused at this
checkpoint. Required exact-head regression and quality gates are reported in the
implementation PR; opt-in exports do not replace them or add unique coverage.

Earlier attempts are failed/interrupted evidence, not passes. A local networking
block prevented ADB/GitHub access; after restoration, ActivityScenario's synchronous
launch waited for UI idle while animation continued. JDWP showed the waiting
instrumentation stack. Changing graphics mode alone did not resolve that wait.
The exporter now starts asynchronously, observes RESUMED/focus on the main thread,
and finishes each Activity explicitly. Capture directories are separated by run
label to exclude stale exports. Private diagnostics remain in ignored build output.

API contracts were checked through Context7 and the primary Android references:
[Activity lifecycle monitoring](https://developer.android.com/reference/androidx/test/runner/lifecycle/ActivityLifecycleMonitor),
[Instrumentation](https://developer.android.com/reference/android/app/Instrumentation)
and [supported emulator graphics modes](https://developer.android.com/studio/run/emulator-acceleration).
