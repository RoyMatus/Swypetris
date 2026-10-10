# Release APK size investigation (#193)

The signed pre-optimization APK from commit `7e9de3a1ba06c726ecbb59db739e88b3bde18eda` is **46,774,728 bytes** (46.774728 MB decimal / 44.608 MiB), SHA-256 `eaf7289ab80ae917dc44fc0950e4e56cfa2efe0bbf69fe0e4619398261d5866b`. [Non-publishing verification run](https://github.com/RoyMatus/Swypetris/actions/runs/38071964104) passed original-certificate signing/archives, 149 JVM tests and full Android regression (156 passed, two existing opt-in skips, zero failures). This is version 1.4.14/code 21 baseline, not a new published release.

ZIP analysis and AAPT resource/string mapping found these compressed contributions:

| Contribution | Bytes |
| --- | ---: |
| Music and sound | 23,213,516 |
| Images | 12,240,703 |
| Two DEX files | 10,724,553 |
| Four supported native ABIs combined | 37,392 |

The menu PCM loop is 8,502,516 bytes; the two largest backgrounds are 2,308,078 and 2,133,390 bytes. DEX inspection found 11,400 Material icon classes / 23,529 defined methods, making unused dependency code a substantial candidate for shrinking. Class/method counts are not dependency-specific compressed sizes.

## Changes and limits

Enable standard AGP 9.1.1 R8 and resource shrinking using the existing optimized default rules. Preserve all game/state/update owner classes and RuStore/VK SDK classes with explicit package-boundary keep rules; vendor and AndroidX consumer rules remain active. This intentionally gives up further optimization within these boundaries to reduce reflection/state/update compatibility risk. Unused dependency code is still removable. No blanket keep rule for all dependencies is added.

Remove the old unreferenced `victory_trophy` PNG (1,529,323 packaged bytes); #194 already replaced its consumers with the shared trophy/fruits foreground. No current drawing or audio asset is reencoded, resized or changed. Signed release verification now exercises the actual optimized signed APK on a clean headless API 35 device: menu, settings, new game, pause and Continue, with screenshots and crash logs. Full existing instrumentation still exercises the debug build and is reported separately.

Keep every supported ABI: removing compatibility for 37 KB is rejected. Preserve the original PCM/OGG samples and image pixels; lossy conversion is rejected. Deflating raw audio would break descriptor-based playback. GitHub continues to receive a universal APK; AAB store splits can reduce device downloads but cannot replace that APK. No dependency or compiler upgrade is included.

## Measured optimized result

The original-key signed APK from optimization commit `8c15b28d58843b59f937033a8a27f7998604f7f4` is **35,443,056 bytes** (35.443056 MB decimal), SHA-256 `c5ee8f7ba658a81d846d4d84ee4f30cc65eaed21e5d413bf1df9453926dd0a76`. Reduction: **11,331,672 bytes / 11.331672 MB / 24.2261%**. DEX compressed payload fell from 10,724,553 to 1,292,476 bytes (two files to one).

Every one of the 31 current production image/audio resources has the same packaged SHA-256 as in the baseline, including the approved DROP, menu loop, all music and current victory foreground. All four native library payloads are byte-identical. ZIP paths may be shortened; comparison uses AAPT resource mapping and decompressed-entry hashes, not filenames alone. The old unused trophy and unused dependency resources are absent. No quality conversion took place.

[Release verification](https://github.com/RoyMatus/Swypetris/actions/runs/38075631295) passed original-certificate APK/AAB signing and archive checks, JVM/lint/detekt/coverage/Sonar checks, and actual signed-release menu/settings/new-game/pause/Continue smoke. The saved release menu/settings/game screenshots were inspected; backgrounds, icons, text and gameplay rendering remain intact. A separate instrumentation job initially failed before testing because the SDK downloaded an invalid system-image ZIP; its log is preserved and only that job was restarted after the identical official image succeeded on another runner. The complete required [cloud PR CI](https://github.com/RoyMatus/Swypetris/actions/runs/38075616218) passed on the optimization commit. These are separate checks; signed smoke is not represented as full release instrumentation.

Reproduction: dispatch the existing release verification workflow on the optimization branch, download its original-key `github-release` artifact and compare `Swypetris.apk` size/hash with the baseline. Inspect `release-shrinker-reports` (effective own/vendor/AndroidX consumer rules, usage/mapping) and `signed-release-smoke`; run required full PR CI. Documentation-only finishing commits do not change the measured app/build/workflow configuration; their required PR check still runs before merge. A future versioned release has its own exact-commit publication checks and artifact hash.
