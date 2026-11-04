# Swypetris 1.11.4 release compliance review

Owner: RoyMatus. Independent review: 2026-11-04. Trigger: epic #339. **GitHub Releases only**; no RuStore writes/moderation claim. GitHub publication, public verification and required release acceptance passed within the evidence below.

## Identity and source findings

Main: `d6a60627c9c617c44593d8cd3d886e2189ebcd16`, [PR #342](https://github.com/RoyMatus/Swypetris/pull/342), same tree as reviewed head c4a02d6. #334 closed/Done; epic open. Package `ru.itoltec.swypetris`, 1.11.4/code 37, minSdk 24, targetSdk 36, portrait.

#324 fills menu edges with safe controls. #334 adds three fruit columns, outlined quantities without a badge and approved compact labels above 999 with exact accessibility counts. Against 1.11.3 source `531f51991120aac6c1e86bc4a6f6f0cb9bf40f24`, manifest/assets/resources/notices/privacy, SDK/dependency declarations/verification, ProGuard and update/data owners are unchanged. App build configuration changes only code 36→37.

Russian privacy source discloses local data/photos/sharing/backup and existing update-service technical data. Its reader/HTML and license notices remain unchanged. Reuse of [1.11.3 baseline](release-v1.11.3-compliance.md) is source-bounded, not new-artifact approval or external-rights/account certification.

## Official sources

Reviewed **2026-11-04**: complete current [RuStore requirements §§1–12.2](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps); [GitHub Terms](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service); [AUP](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies); [Additional Product Terms](https://docs.github.com/en/site-policy/github-terms/github-terms-for-additional-products-and-features); [Community Guidelines](https://docs.github.com/en/site-policy/github-terms/github-community-guidelines); [Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement); [trade controls](https://docs.github.com/en/site-policy/other-site-policies/github-and-trade-controls); [Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases); [licensing guidance](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository).

Terms/Privacy effective 2026-04-27; Additional Product Terms 2026-08-27; RuStore/AUP date not established. Same-day research reused. Existing repository/API/Projects/Releases scope; no new hosted/paid product. RuStore legal summaries are not legal certification.

## Classified findings

Statuses apply only to stated evidence; **no confirmed mandatory source conflict was found**.

| ID / provisions | Classification | Finding / status |
| --- | --- | --- |
| R1: RuStore 8.1/8.5 | Mandatory platform rule | Signed runtime smoke and final Pixel update/readability acceptance passed: **compliant within tested scope**. |
| R2: RuStore 2.14/5.3/6.3 | Mandatory platform rule | Signed APK omits installer permissions/DEBUGGABLE; retained permissions match baseline; original signers verified: **compliant in inspected artifacts**. |
| R3: RuStore 5.1–5.2/8.4.2 | Mandatory platform rule | Privacy/update/SDK owners unchanged; no new collection or store-blocking protection. Packaged policy/notices match source; anonymous immutable policy URL matches Git blob: **compliant in inspected scope**. |
| R4: RuStore 2–4 | Mandatory platform rule | No new content/asset/rights issue: **compliant in source scope**, not rights certification. |
| R5: RuStore 3/7/9 | Conditional mandatory rules | No new ads, payments, account, hosted UGC, rating or age-sensitive feature: **not applicable to changed scope**. |
| R6: RuStore 6/10–12 | Destination-specific rules | Store listing/media/console/moderation: **not applicable**, no submission. |
| R7: RuStore 8.5 | Mandatory platform rule | Other-app-store currency language does not expressly mandate simultaneous GitHub release. No confirmed conflict overrides GitHub-only authorization; current store state is not inferred. |
| G1: Terms D.3/H; AUP 3/8 | Mandatory platform rules | Existing rights/private-information/API boundaries retained. Finite changed-history/archive review found no hits/key-container paths: **compliant within scan scope**, not exhaustive certification. Public-release identity was independently verified. |
| G2: Releases/trade controls | Mandatory platform rules | GitHub stable release/tag at exact main and four public assets independently verified: **compliant within inspected distribution scope**. No restricted-party/end-use finding. |
| G3: licensing/media guidance | Official recommendations | Notices/truthful portrait evidence: **adopted**. New license decision unnecessary; store video/FAQ **not applicable**. |
| P1: POLICY/CI/TESTING | Project checks | Full PR/main evidence and prepublication supplementals passed below. Signed gate and Pixel acceptance passed. Prepublication/public-file/live signed-update checks **passed within documented fixture scope**. |

## Actual technical evidence

Full gate passed at c4a02d6/base69d04ff: 182 JVM freshly executed; 238 Android passed/5 skips; 132 Python passed; 181 PowerShell passed/2 skips; lint/detekt/coverage/Sonar passed. Android runner 880.759s is nested in stage 881.519s. Identical-tree main promotion passed with fresh Sonar 112.631s and reused regression.

Prepublication supplementals: 3 passed/0 skips in 218.397s; 75 firework PNGs/manifest, 12 material and 25 menu PNGs. Menu background/resume/game/menu preserved the same game/theme. No FPS/energy/leak claim. Evidence under `app/build/verification/epic-339/`: `pr-342-full-5/evidence.json`, `main-promoted/evidence.json`, `supplemental-pre/summary.json`/exports. Ordinary skips were not acceptance; energy stayed excluded.

## Exact artifacts and final acceptance

Immutable signed gate `local-release-20261104-121300-627` passed in **271.102s**; its signed build `release-20261104-151300-703` took 122.451s (nested, not additive), reusing 182 UP-TO-DATE JVM results. Provenance binds full main SHA above. Original certificate SHA-256: app `04e8937a933bd53c80ae16f450bdda86e97e8addeabbd15ce8814eb9f42b66dc`; upload `f4f7384dd99a23cfeb47573fa67391e272c01fda86d1ee30e659faa47e625b03`.

| Local artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Swypetris.apk | 76002884 | `a69ddac18d42161ab25473b917d4a1f451b3c0ec024457fe2f30d1797b945c75` |
| Swypetris-1.11.4.aab | 77533454 | `56a51962c2f32ad685e1b433a076b6449dbdc6ef4d2038ad3c7d6a0257eedcd9` |
| update.json | 136 | `5311dd6e4864c5dd4a8ce8f649818fa16402ee22528e59e48b4e92b138ddaf87` |
| SHA256SUMS.txt | 160 | `beb0e7543b06d409436c0e09e733d59226db8af8fa9ed7a66e9927571483b5a9` |

Both archives passed ZIP integrity; all 13 source assets match packaged bytes; no private-key container paths found (`final-archive-audit.json`). Signed APK badging/manifest/permissions confirm package/version 37/1.11.4, min 24/target 36, portrait 1, no DEBUGGABLE or installer permissions. Retained SDK network/reboot/internal-receiver permissions match baseline. AAB is local verification, not RuStore delivery.

Anonymous [immutable privacy HTML](https://raw.githubusercontent.com/RoyMatus/Swypetris/d6a60627c9c617c44593d8cd3d886e2189ebcd16/publishing/privacy.html) matches its Git blob (`privacy-readback.json`); the initial checkout comparison differed only in CRLF. Finite changed-history scan: 4 commits, 34 blob versions, 33 paths, no pattern hits (`source-secret-review.json`). Neither scan establishes exhaustive secret/legal compliance.

Pixel 7 old code 36→exact release 37 `install -r` passed without uninstall/data clear; Continue, score 14882 / lines 37 / held I remained. Two high-count fixture cases passed in 10.011s, producing 12 PNGs; light/dark/photo backgrounds at font scales 1/2 were visually reviewed for unclipped, readable outlines. The app-signed debug fixture was distinct from production. Exact non-debuggable production code 37 was restored afterward with the release APK hash and the same retained game state (`pixel-release/summary.json`). An existing debug test-package certificate mismatch was resolved by cleaning only that test package; no phone app uninstall/clear occurred.

`Publish-LocalRelease.ps1 -WhatIf` completed successfully (`publication-preview.log`); no publication is claimed from the preview.

[GitHub v1.11.4](https://github.com/RoyMatus/Swypetris/releases/tag/v1.11.4) was published as a stable release with four assets at main d6a60627c9c617c44593d8cd3d886e2189ebcd16. Publisher public verification passed (`published-v1.11.4-20261104-152412-134`); independent `Verify-PublishedRelease.ps1` verification passed in 18.544s (`published-v1.11.4-20261104-152445-843`). Public APK hash, package/code37, original signer and provenance match this report. These public checks do not substitute for instrumentation.

**Live signed-update integrity passed: 1 case, 0 skips, 34.052s** (`signed-update/summary.json`). A saved public release-API JSON response was supplied because anonymous API access was limited. The actual public update.json and 76,002,884-byte APK were downloaded live without authentication; expected version/package/SHA-256/original signer validated positively, without an expected-mismatch branch. This does **not** establish an unrestricted anonymous release-API request.

Fixture boundary: the compatible target was current unminified debug code with 1.11.3/code36 metadata and the original app signer, not the historical production runtime. The published historical target/current test pair had failed at startup with missing Kotlin Intrinsics/AndroidX tracing classes, before download assertions. Real production old-to-new installation/data retention is independently covered by Pixel. An emulator offline condition and an inaccessible shell-pushed external JSON copy were diagnosed; the supported saved JSON was placed in app-owned internal storage. Failed logs were retained. No fixture was published. Exact production code 37 was restored on the emulator and the test package removed; Pixel already retained the final production release.

**Decision: GitHub-only release publication and required acceptance COMPLETE for the exact recorded main/artifacts, within the documented test and scan limits.** No applicable mandatory finding remains unresolved in this review. The epic remains open only for report delivery/closure sequencing. Reassess any changed artifact/source/metadata/rule. No RuStore publication, moderation approval or exhaustive legal certification is implied. Active work/wait time: Unknown.
