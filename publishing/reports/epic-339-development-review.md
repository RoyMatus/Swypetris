# Epic #339 development review

2026-11-04. Epic #339: menu edges (#324), then fruit HUD (#334). Implementation and release acceptance complete; report delivery/epic closure follows. GitHub release only; no RuStore submission.

#324 merged through [PR #341](https://github.com/RoyMatus/Swypetris/pull/341) as `69d04ff`. Menu artwork fills the window with safe controls. [PR #342](https://github.com/RoyMatus/Swypetris/pull/342) arranges fruits in three columns, removes filled badges and adds outlined quantities anchored to their fruit. The owner approved exact labels through 999, compact larger labels and exact accessibility counts. Merged as d6a6062; #334 is closed/Done. Its Full gate and final release acceptance passed.

Local checks used one heavy-check owner, four workers and a headless API 35 emulator. Unrelated changes/caches/diagnostics were preserved; no cloud execution or energy measurement.

## Observed checks

Durations measure invocations/stages, not active work. Overlapping cases, examples and screenshots are not additive.

| Scope | Actual result | Duration |
| --- | --- | --- |
| #324 TDD | Menu top expected 0, observed 136 pixels; focused GREEN: 13 passed | RED 105.733s; GREEN 65.712s |
| #324 final gate | Passed; 236 Android passed, 5 opt-in skips; 181 JVM results validated as UP-TO-DATE reuse | Android runner 886.933s |
| #334 grid / formatter TDD | Third-fruit row assertion failed before implementation; formatter expected 1K, observed 1000. Formatter GREEN: 1 JUnit case with 15 examples | Grid RED 19.824s; formatter RED 19.888s / GREEN 9.033s |
| #334 focused checks | 12 integration cases passed; corrected Borderless fixture: 3 passed; expanded formatter: 1 case with 17 examples | 75.628s; 78.473s; 7.491s |
| #334 final Full gate | 182 JVM cases freshly executed; 238 Android passed, 5 opt-in skips; 132 Python passed; 181 PowerShell passed, 2 skips; lint/detekt/coverage/Sonar passed | Quality 35.842s; Sonar 108.596s; Python 34.254s; PowerShell 38.843s; Android runner 880.759s |

Evidence under `app/build/verification/`: `epic-339/pr-341-final/evidence.json`, `epic-339/pr-342-full-5/evidence.json` and selected focused summaries listed in the checkpoint. Full evidence binds exact head c4a02d6 / base 69d04ff. Android stage 881.519s includes the runner above; do not add them. Portrait PNGs supplement geometry/accessibility checks; appearance exports followed geometry GREEN.

## Failures and recovery

Source inventory rejected the unclassified formatter; explicit mapping and 22 passing selector tests corrected it. Missing local Pester files were restored at pinned 6.2.0 with official SHA-512 verification, without an upgrade. Ignored SDK-path escaping was corrected and lint refreshed (23s).

Full attempt 4 stopped after an obsolete full-count expectation conflicted with approved 26.8K. Its 236.379s Android run is failed/incomplete. Diagnostics preceded retry; the corrected fixture retains exact accessibility assertions. Earlier tool checkpoints were reused, not fresh. Attempt 5 passed on the corrected head.

Git-incident recovery advanced only the existing topic commit via API with `force=false` and readback; main protections/local gates remained.

## Separate instruction work and remaining limits

[PR #340](https://github.com/RoyMatus/Swypetris/pull/340) reduced core guidance from 18 to 7 files and 30,305 to 17,243 normalized bytes (43.1%). Five skills, 30 links/anchors, TOML and its documentation-only gate passed; no Android build was run for those edits. Text reduction is not speed/quality proof.

Identical-tree main promotion passed with fresh Sonar (112.631s), reusing bound regression. Three prepublication supplemental cases passed with no skips in 218.397s: 75 firework, 12 material and 25 menu PNGs; lifecycle preserved the same game/theme. Signed gate passed (271.102s); nested signed build 122.451s reused 182 JVM results. Archive/privacy/history audits passed within their finite scope. Pixel update and final high-count acceptance passed: 2 fixture cases in 10.011s/12 PNGs; exact non-debuggable production was restored with Continue/game state retained. GitHub v1.11.4 was published with four assets at exact main; publisher and independent public verification passed (independent run 18.544s). Live signed-update integrity passed: 1 case, 0 skips, 34.052s. A saved public release-API JSON response handled API limits; update.json and the exact public APK downloaded live anonymously and passed version/package/hash/original-signer validation. The target was compatible current debug code with historical version metadata, not the actual historical runtime; Pixel separately proves production upgrade retention. Startup/runtime-class, emulator offline and external-JSON access failures were diagnosed/logged before the successful app-owned internal JSON fixture. No anonymous release-API success is claimed. Exact production was restored and the test package removed; no fixture was published. Deliver the reports before epic closure.

Active work, waiting and complete delivery time are Unknown without a comprehensive journal. No cold/warm causal comparison is supported. Inventory/label failures support reviewing new-file routing and affected visible/accessibility assertions before push; preserve all gates and claim no numerical speedup.
