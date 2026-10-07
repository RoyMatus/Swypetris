# Approved menu sky assets (#176)

The immutable visual baseline is `09c2436015d4058f1d485c748427dfb8e44483c5:design/issue-176/menu-sky-preview.html`.
The production assets are exported from its running canvas, including the final cathedral occlusion correction. Cloud forms are not regenerated or mirrored.

`Export-MenuSky.cjs` requires Node, Playwright 1.63.0, Sharp 0.35.5 and a Chromium browser. These are preparation tools only, not Android dependencies. Install them under ignored build output:

```powershell
npm install --prefix app/build/issue-176 playwright@1.63.0 sharp@0.35.5 --no-audit --no-fund
git show 09c2436015d4058f1d485c748427dfb8e44483c5:design/issue-176/menu-sky-preview.html | Set-Content -Encoding utf8 app/build/issue-176/reference.html
$html = Get-Content app/build/issue-176/reference.html -Raw
$inner = [System.Net.WebUtility]::HtmlDecode([regex]::Match($html, 'data-srcdoc="([\s\S]*?)"></iframe>').Groups[1].Value)
Set-Content -Encoding utf8 app/build/issue-176/reference-inner.html $inner
$env:NODE_PATH = (Resolve-Path app/build/issue-176/node_modules).Path
node tools/art/Export-MenuSky.cjs app/build/issue-176/reference-inner.html 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
```

Outputs are eight transparent cloud sprites, two small transparent sky-repair patches, and artwork-space cloud/sky mask runs plus registered nighttime stars. Patches reconstruct only pixels changed by the approved reference's cloud removal (53,727 native daytime pixels; 32,841 nighttime pixels). Original full-resolution scene resources remain unchanged. At runtime the original and patch use the same crop/intro transform; the patch is transparent everywhere else. No entire-scene repaint or runtime reconstruction is performed.

`MenuSkyMotion.kt` preserves the reference's 32-second base travel, individual speed ratios, offscreen recycling, 2.7–4.65-second independent star periods and 4.2-second tower pulse. The tower glow remains at artwork `(92, 395)` even when cropped out. Both masks, scene, sprites and halo sizes share artwork coordinates `(941, 1671)` and one uniform cover transform, followed by the existing intro approach transform. Existing lower-scene tint and travel decoration remain unchanged.

Bitmaps, mask paths and halo gradients are cached before drawing. The frame clock reads time during drawing and observes `RESUMED`; leaving the menu disposes it. System-disabled animation renders time zero. Infinite-frame policy also lets ordinary Compose tests become idle. The logo-only handoff test disables sky animation so transparent logo pixels are compared against a stationary backdrop; dedicated sky tests exercise motion separately.

Verification artifacts belong under `app/build/issue-176/`. `MenuSkyTest` captures both lighting variants at 16:9, 19.5:9 and 20:9 through the production renderer and checks foreground stability across cloud loops, plus lifecycle/disabled-clock behavior. A normal-speed emulator recording and visual reference comparison are still required for delivery; unit tests and deterministic captures alone do not establish animation fidelity. Performance measurements on an emulator are indicative only and do not replace the separate #170 investigation.
## Verification of #176

- `tools/Verify-Tests.ps1 -Suite Fast -JavaHome C:/Users/pilig/.jdks/jbr-21.0.11`: assembleDebug, lintDebug, testDebugUnitTest and JaCoCo passed; 132 JVM tests, no failures. Final production-code run: `app/build/verification/fast-20261007-085153-548.log`.
- Explicit emulator `emulator-5556`, Android API 35: selected MenuSkyTest, LaunchIntroTest and LaunchIntroRecreationTest passed, 9 tests (`app/build/issue-176/android-final.log`). Initial failures were corrected by using the infinite-animation frame policy and disabling moving sky only in the transparent-logo handoff test.
- A subsequent direct runner invocation of MenuSkyTest passed all 3 tests, including the added landscape crop check (`app/build/issue-176/instrument-final.log`). Together these runs cover 10 distinct Android tests. The test APK build also passed.
- Captured 28 production-renderer images: both themes at 16:9, 19.5:9 and 20:9 at 0, 8, 32 and 53 seconds, plus landscape at 0 and 55 seconds. Foreground pixels stay identical; sky pixels change. A landscape crop excluding the sky stays entirely identical, so offscreen stars/glow are not relocated. Clock tests verify background freeze, resume without accumulated elapsed time and disabled animation.
- Registered Android/reference comparison images use the same uniform cover, existing 8% menu approach and existing lower tint. Exact approved sprites, mask runs, cloud positions and timing are retained; the untouched original scene keeps its native resolution. Browser/Android image sampling differs at individual texture pixels. Comparison artifact: `app/build/issue-176/reference-android-comparison.png`.
- Normal-speed light/night recordings and the running approved browser reference are retained under `app/build/issue-176/`; extended warmed-menu recordings cover a complete slow-cloud loop. Review includes tower-edge passage, offscreen recycling, independent star brightness and red tower glow. The Activity stays portrait as configured; landscape is tested directly through its renderer.
- With the actual emulator animator scale set to zero, repeated sky captures have zero differing pixels and gfxinfo records zero additional rendered frames. The scale was restored to 1 afterwards.
- Indicative emulator measurement without screen recording: 1,193 frames, 6 janky (0.50%), median 21 ms, 90th percentile 24 ms, zero slow bitmap uploads. Screen recording under host load produced 37.12% jank, so recorded smoothness is not a 60-FPS guarantee. Real-device performance investigation remains separately scoped in #170.
- The connected Pixel 7 was detected over Wi-Fi (API 37). The attempted debug update was rejected because the existing app has a different signature; the installed app and its data remain intact. Since the required checks are covered by the emulator, no physical-device test or uninstall was performed.
- User-requested AGENTS.md additions require Wi-Fi/USB device discovery, the emulator by default, a physical phone only for justified real-device checks, and `-no-window` unless an emulator window is needed. Pre-existing unrelated AGENTS.md edits are excluded from this change.
