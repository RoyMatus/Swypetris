# Delivery improvements after epic #308

Reviewed 2026-10-19 for [#331](https://github.com/RoyMatus/Swypetris/issues/331). Implementation follows the owner's approved retrospective. Production application sources, assets, dependencies, version and released artifacts remain unchanged. The controlled mutation described below was temporary and restored byte for byte.

## Delivered changes

- A versioned RuStore command separates preparation, inspection, one-time upload, media verification, moderation submission and publication. Exclusive attempt records precede writes; ambiguous responses never authorize automatic retries. Fresh version/package, anonymous server artifact download, original APK bytes inside the outer ZIP, media and reviewed declarations bind progression. Safe bounded rejection details survive, without storing credential-bearing responses. WhatIf checks fresh eligibility without a local journal or store write.
- Early checks reject incompatible Sonar Git/worktree volumes and installed/candidate signer mismatch. The scanner uses the explicit supported local JDK while retaining analysis/Quality Gate. An opt-in STRICT Gradle Sources resolution check covers IDE/toolchain changes without regenerating metadata.
- Hash-bound evidence archives retain original source bundles/refs and release artifacts. Preview-first completion checks current PRs, source identity, changes, activity acknowledgement and protected files before non-forced Git removal. Per-command long-path support avoids the reproduced Windows removal failure; partial removal has no blind fallback.
- The visual development selection now includes neighboring HUD layering/bounds checks. Listening handoffs bind an unaccepted checklist to exact signed provenance. Test phase exports use AGP additional output and unique run IDs, independently checking the complete measured matrices.

Operational commands and trust limits: [DELIVERY-EFFICIENCY.md](../DELIVERY-EFFICIENCY.md). Exact-head/current-base gates, complete required instrumentation, signatures, dependency verification, quality thresholds and external/device acceptance are retained. Local reports remain operator-controlled evidence, not independent attestation.

## Measured native-test optimization

The SoundPool test manipulates native state on the main thread and explicitly waits for all sample-load callbacks. It does not render Compose UI. Pinned Compose test 1.7.0 source confirms that runOnIdle additionally waits for UI idleness; runOnUiThread blocks until the main-thread action finishes. The optimization changes only five dispatch calls in this test. The native SoundPool, all 12 themes/pairs, ready-state waits, stream/cache/pending-event/release assertions and other test bodies remain.

Three sequential runs before and three after used the same local host, API 35 emulator, JDK/SDK, four workers and assets. Each run passed the same seven cases, with zero skips. Production and preview/profile Kotlin bytes matched; reversing only the five dispatch replacements reproduced the recorded baseline native-test source hash.

| Run | Native select + play blocks before | After | Native load wait before / after | Preview capture before / after |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 48.328 s | 0.167 s | 3.398 / 2.442 s | 64.874 / 66.475 s |
| 2 | 48.477 s | 0.135 s | 5.149 / 5.079 s | 66.551 / 68.395 s |
| 3 | 48.592 s | 0.068 s | 5.254 / 2.510 s | 65.995 / 66.615 s |

The median per-run combined duration of the two measured native blocks changed from 48.477 to 0.135 s. This is a scoped observation supporting removal of unnecessary UI-idle waits, not a total-test, full-gate or epic productivity percentage. Build/task reuse and other overhead are outside these phase timings; no cold-versus-warm causal claim is made. Load-time variation is not attributed to the dispatch change.

Every trial retained 980 screenshot captures, 784 comparisons and 980 state updates; each native select/load/play phase covered 12 themes. Capture dominates the measured preview body, so its full visual matrix and synchronization were retained. The real three-repeat music test was not shortened. Repeated executions are not additional unique coverage.

A controlled temporary removal of production streams.clear() caused the optimized native test to fail with `expected:<1> but was:<2>`. Original production SHA-256 `8878333e85c5a7a2fe0496c7e15b54dec01b0a277a845979db888ff93ae03113` was restored before all three GREEN trials. The original failed XML/logs and restoration check remain retained. The first result-parser check expected failure attributes; actual JUnit stores failure text, which was inspected without rerunning the unchanged expected failure.

## Development evidence and limits

- RuStore Prepare checked the actual GitHub 1.11.2/code35 tag, metadata and APK identity. Read-only live Verify downloaded the 80,289,715-byte Store ZIP in 42.120 s, proved the contained signed APK identity and checked icon/screenshots/listing. No new upload, submission or publication occurred; #330 retains the pending external delivery.
- Strict IDE Sources resolution passed in a Gradle-reported 7 s. Deterministic behavior RED/GREEN covered hidden-draft retry refusal, preserved/restricted rejection details, missing HUD neighbors and native Windows long paths. Ported candidate/archive helpers also received negative fixtures and live readbacks; test-first ordering is not claimed for those ports or observational wrappers.
- Final development lint/detekt passed in a Gradle-reported 3m18s; the Android-test source analysis accounted for the observed long file walk. Its model had the expected app/test roots. No cause for the duration beyond that observed work is asserted and no lint scope was excluded.
- Selected earlier tool development runs passed 133 Python cases and 211 Pester cases/two existing platform skips. Later focused checks cover the added profile validator and non-OK HTTP 200 handling. Final exact-head evidence and actual final counts belong to the issue's consolidated completion record; these development snapshots alone do not authorize merge.
- A quality development attempt exposed nested/overlong measurement code; helper extraction retained assertions. Six coverage-promotion fixtures needed the newly required SCM dependency, then passed. A redundant owned Gradle invocation was stopped; diagnostics for the failed attempt's long lint file walk were preserved before cancellation. No unrelated process or global daemon stop was used.
- The first seven-case profiling trial passed, but private app files disappeared during normal AGP APK cleanup. Diagnostics were retained and export moved to the documented additional-output mechanism, verified through six successful trials. No uninstall behavior or permission was weakened to collect measurements.
- Initial local device capacity was blocked by 13.164 GiB free disk. Six confirmed merged/unused worktrees were archived and removed; a fresh probe selected Local/four workers/one emulator at 20.426 GiB free. An additional native removal partially failed with Invalid argument; remaining paths included lengths up to 288 characters. The exact native failure cause is not conclusively established; a separate long-path fixture supplies the preventative change's RED/GREEN evidence. The residual directory and verified archive remain: automatic review rejected recursive PowerShell deletion with `blocked by policy`; the blocked deletion was not retried through another mechanism. Other work needing its artifacts remains retained.

Selected raw evidence is retained under local issue331 verification output, baseline/optimized phase-profile directories and private source/evidence archives. Failed, reused and fresh results remain distinguished. Active development, waiting and total delivery time are unknown; overlapping check durations are not added into elapsed work.
