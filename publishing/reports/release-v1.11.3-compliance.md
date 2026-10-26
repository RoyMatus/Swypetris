# Swypetris 1.11.3 release compliance review

Reviewer: Codex, with a separate read-only store-compliance review. Owner: RoyMatus. Review date: 2026-10-26. Complete-review trigger: RuStore rejection and changed installation/update permissions. This completes the artifact checks left pending in the [preparation review](issue-336-moderation-preparation.md). Issue [#336](https://github.com/RoyMatus/Swypetris/issues/336) remains open until store publication is confirmed.

## Identity and publication state

Release commit: `531f51991120aac6c1e86bc4a6f6f0cb9bf40f24`, merged through [PR #337](https://github.com/RoyMatus/Swypetris/pull/337). Package `ru.itoltec.swypetris`, version **1.11.3 / 36**, minSdk24, targetSdk36, portrait. [GitHub release](https://github.com/RoyMatus/Swypetris/releases/tag/v1.11.3) is published and independently verified. RuStore candidate **2064909684** was submitted at **2026-10-26T07:41:21.899Z**, with **INSTANTLY / 100%** publication after approval. Subsequent API state was **MODERATION**, not ACTIVE. The old public version remained 1.0.0/code1 at this checkpoint.

| Artifact | Destination | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Swypetris.apk | GitHub and RuStore | 75986500 | `5e852788ea29fc57f15758f4b3079b9d51a3dd0d85f3c79271b5e7fdc6506080` |
| Swypetris-1.11.3.aab | Local signed artifact; not uploaded | 77531264 | `f75f6b8175928b2df49c210b376f654d23187dd840eaa3186d866577d49cafb5` |
| update.json | GitHub | 136 | `81bdf78b80e6ed712525f3ba0ebe17659dd88bbb39ea7d6de4cc89cedcf28a4c` |
| SHA256SUMS.txt | GitHub | 160 | `6c1f4c3f1fc4400ad027babd46cf5ff5d8a8ee04799c202f1e288d3e593b1aeb` |

APP certificate: `04e8937a933bd53c80ae16f450bdda86e97e8addeabbd15ce8814eb9f42b66dc`. UPLOAD certificate: `f4f7384dd99a23cfeb47573fa67391e272c01fda86d1ee30e659faa47e625b03`. Both original signing identities are retained. Public [local provenance](https://github.com/RoyMatus/Swypetris/releases/download/v1.11.3/local-release-provenance.json) binds the commit, signed artifacts and regression/smoke evidence. No existing tag or release was overwritten.

## Sources and scope

The complete [RuStore requirements](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps), sections 1–12.2, were opened/read on 2026-10-26 and supplied in full by the owner. No effective date was stated. The preparation report records each applicable group and justified exclusions; this report supplies final artifact and live-service evidence. The platform's legislative summaries are not an independent legal opinion.

GitHub sources reviewed on the same date: [Terms](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service), [AUP](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies), [Additional Product Terms](https://docs.github.com/en/site-policy/github-terms/github-terms-for-additional-products-and-features) (effective 2026-08-27), [Community Guidelines](https://docs.github.com/en/site-policy/github-terms/github-community-guidelines), [Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement) (effective 2026-04-27), [trade controls](https://docs.github.com/en/site-policy/other-site-policies/github-and-trade-controls), [Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases), and [repository licensing](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository). Scope remains the authorized repository/API/Projects/Releases; no hosted build, runner or paid product was introduced.

Current official RuStore SDK10.5.1 and API draft, upload, media, detail and submission contracts were consulted. Existing resource provenance was reassessed against the [1.11.2 review](release-v1.11.2-compliance.md); no new game asset, music, font or external dependency was introduced. Account eligibility and external contractual rights are not independently certified.

## Final findings and coverage

Statuses describe bounded evidence, not guaranteed moderation approval. All rows below are mandatory platform requirements unless identified as a recommendation or project check.

| ID / provisions | Applicability and final evidence | Result / remaining action |
| --- | --- | --- |
| R1: 2.14, 5.3, 6.3 | Rejected 1.11.2 requested REQUEST_INSTALL_PACKAGES. Production installer, private receiver and unknown-source settings action removed. APK and decoded AAB manifests omit that permission and UPDATE_PACKAGES_WITHOUT_USER_ACTION; no debuggable attribute. | Corrected; compliant in inspected artifacts. RuStore reports no dangerous/critical/APPOP permissions. |
| R2: 5.1–5.2, 8.4.2 | RuStore installations retain store-managed FLEXIBLE updates without GitHub fallback. Direct APK installations use an explicit browser release-page action. Legacy installer files/preferences are cleaned without touching game data. SDK host/RuStore checks, technical events/UUID and persisted diagnostic JobScheduler behavior are disclosed. | Compliant in inspected code, privacy and declarations. Four technical data categories saved; no claim of whole-device app inventory. |
| R3: 2.14, 5.3 | Retained platform permissions: VIBRATE, INTERNET, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED; internal non-exported-receiver signature permission in artifacts. Reboot permission supports deferred SDK diagnostics, not a gameplay autostart or silent installer. | Compliant in bounded SDK/manifest review; rationale saved in RuStore. |
| R4: 6.1–6.7, 9 | Russian descriptions, exact Swypetris name, arcade/0+, contacts, current privacy URL, truthful update notes and moderator explanation read back. Empty ad/payment SDK and purchase arrays submitted. Server detects only TOOLS: RuStore App Update; purchases empty. | Compliant for inspected metadata/source. No ads, payments, subscriptions or rating incentives found; conditional rules not applicable. |
| R5: 6.6 | Three native signed-release screenshots, 720×1280 portrait, each below3MB, visually free of Android indicators. Opaque 512×512 icon, 557080bytes, matches installed artwork. All four CDN byte hashes match reviewed inputs. | Compliant. Consistent portrait recommendation adopted. Optional video and FAQ deferred as unnecessary. |
| R6: 8.1, 8.4–8.5 | Full local regression, exact-main signed smoke, supplemental rendering/lifecycle cases and actual Pixel7 update preservation passed. No network required for gameplay; no Play Integrity dependency. | Compliant for tested behavior/device scope; not a claim about every Android device. |
| R7: 2–4, 7 | Existing puzzle/fruit artwork and synthesized music/resource notices reviewed; no prohibited-content, impersonation, hosted UGC or manipulation finding. No account, communications, medical, financial, gambling, mining or circumvention feature. | Unchanged applicable content/rights findings reassessed; conditional service rules not applicable. No new rights conflict established. |
| R8: 1, 10–12 | Corrected existing app/account, one final APK, accurate version identity and automatic publication setting. | Submission accepted; actual approval/ACTIVE publication remains external and unverified. Owner/agent must read fresh state before claiming completion. |
| G1: GitHub Terms/AUP, rights/privacy/security | Authorized existing repository, no new third-party resource or data service. Bounded changed-text/source-filename/archive scans found no credential containers; tracked PEMs are public certificates. | No new concrete violation found; finite scan is not an exhaustive historical secret/legal certification. |
| G2: GitHub Releases/licensing | Protected new tag at exact main, asset sizes within limits, existing notices retained. Public APK/metadata/checksums/provenance verified. | Compliant for inspected distribution; hosting does not require changing the repository license. |
| P1: project gates | Exact-head Full gate, promoted exact-main gate with fresh main Sonar, original-key release gate and supplemental scenarios. | Passed; evidence below. No cloud builds or energy measurement. |

All13 inspected source assets match their packaged bytes in both APK and AAB; ZIP integrity checks pass. The public versioned Russian privacy text equals source after normalizing Git's CRLF/LF line endings. Android permissions, SDK/data flows, privacy, age/content, rights, gameplay/navigation, security, signing, documentation and listing/media coverage map to the findings above and the full preparation matrix.

## Actual verification

- TDD: the installed-package permission regression first failed for the original forbidden permission. Focused GREEN passed20 cases in98.838s; final routing/permission checks passed after the compatible ActivityMonitor correction. This expected RED is separate from final passing gates.
- Exact PR head `509cb9b4c51bae5a0d8346b6da02ac22ebdb3e8e`: full local gate passed; 181 JVM, 235 Android passed plus5 explicit opt-in skips, 131 Python, 181 PowerShell plus2 skips; lint, detekt and Sonar Quality Gate passed. Recorded full-gate duration1001.079s. Cached/up-to-date JVM outputs were validated and reused where reported.
- The squash merge preserved the tested tree. Main promotion revalidated evidence and passed fresh main Sonar. One intermediate scanner run failed with JGit MissingBlob despite native Git connectivity passing; a fresh single-use Gradle process succeeded without disabling SCM or changing source. Root cause is not established.
- Exact-main signed release gate and menu/settings/new-game/pause/Continue smoke passed. Initial local SDK-path/lint configuration errors were corrected; failed logs retained. No release source changed afterward.
- Required supplemental menu/material frame scenarios passed. Firework export first rejected an invalid label containing dots; the corrected `release-1-11-3` invocation passed in12.421s and retained75 frames plus manifest. These checks are not energy/FPS guarantees.
- Pixel7: original-signed1.11.2/code35 upgraded with `install -r` to the exact release APK, without uninstall or data clear. First-install identity retained; continued game still showed168753 points/135 lines with the same next/hold state; visible setting texts/check states matched. Installed APK hash equals the published artifact. The game remains installed.
- Post-publication SignedUpdateScenarioTest passed1 case in22.108s: public update.json and APK downloaded, bounded length/hash/package/newer version/original signer validated. A local original-signed debug fixture represented old code35. GitHub release JSON was supplied through the supported fixture argument because anonymous API access had returned a rate-limit error; actual metadata asset/APK downloads remained live. Test-only fixture was never published. Its initial Gradle afterEvaluate override failed; supported finalizeDsl fixed fixture configuration without source changes.
- Published-release verifier passed, including original certificate, package/version, public hashes and provenance. No installation-only result was counted as an instrumentation pass.

## RuStore delivery and independent readback

One APK upload was attempted. Its response was interrupted, so it was not blindly repeated. A fresh version query confirmed1.11.3/code36. The first icon transfer was explicitly rejected because its multipart media type was not PNG; setting the image part to image/png succeeded. All three screenshot uploads succeeded and replaced their existing ordinal slots; final detail contains exactly three screenshots.

The submitted candidate has exactly one main APK, only the RuStore update SDK detected, and no install-app permissions. Text, contacts, categories/tags, four data categories and no purchases were read back. The version-status endpoint confirms INSTANTLY/100%; the detail endpoint instead returns partialValue=-1. This inconsistency is retained without assuming that -1 means100%; eventual public rollout still requires verification. The server's74973076-byte package is an outer ZIP (`0cf5b8dd5f57491049129ff5d094980046dc10738984790c802792b67f2e8753`) containing one75986500-byte APK and two dexopt profile sidecars. Its contained APK hash exactly matches the signed/GitHub/Pixel artifact above. CDN icon/screenshots also match byte-for-byte.

No confirmed mandatory conflict remains in the reviewed candidate. GitHub publication is complete. RuStore submission is accepted and awaiting moderation; automatic publication is requested, not yet observed. This report does not certify all external rights/account facts or promise approval. Any subsequent rejection, changed artifact or metadata requires reassessment.

## Retained evidence

Ignored evidence is archived under `app/build/verification/issue-336-release/`: `moderation/{final-full-4,main-promoted,artifact-compliance,supplemental-evidence,pixel-before,pixel-after,signed-fixture,store-cdn}`, API plan/receipts/readbacks, review and failed-attempt logs; `local-release-20261026-071816-480`; and `published-v1.11.3-20261026-103358-719`. The archive must be retained before removing the owned worktree. Public reports exclude credentials, private signing material, device serials and absolute local paths. Active work/wait duration was not comprehensively recorded and remains unknown.
