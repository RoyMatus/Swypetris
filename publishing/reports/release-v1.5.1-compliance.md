# Swypetris 1.5.1 release compliance review

Review owner: Codex; release owner: RoyMatus; date: 2026-10-12. Complete review triggered by epic #236. Destination: GitHub Releases only. RuStore submission and issue #215 remain outside this delivery. The signed pre-merge candidate was reviewed below. Independent exact-main technical gates remain required before the explicit local publisher; merging the version PR does not dispatch disabled Actions.

## Current sources

The complete current [RuStore requirements](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps), sections 1–12 and subsection text, were reviewed on 2026-10-12. No page effective date is stated. The historical section numbers in [the 1.5.0 baseline](release-v1.5.0-compliance.md) are superseded by its correction and this current mapping; no change date is inferred.

[GitHub Terms](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service) (effective 2026-04-27), [Acceptable Use](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies), [Additional Product Terms](https://docs.github.com/en/site-policy/github-terms/github-terms-for-additional-products-and-features) (effective 2026-08-27), [Community Guidelines](https://docs.github.com/en/site-policy/github-terms/github-community-guidelines), [release guidance](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases), and [licensing guidance](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository) were reviewed. Other page update dates are not stated. Actions is disabled, confirmed through the repository API; no Actions, Codespaces, Packages or Pages deployment is used for this release. API use is limited to this authorized repository workflow.

## Findings

Statuses below apply to inspected product/distribution evidence; they do not certify external account facts or a store approval. Source section labels are references, not copied legal text.

| ID | Source / category | Evidence and applicability | Status / decision |
| --- | --- | --- | --- |
| R1 | RuStore 1.1–1.4; mandatory | Native functioning puzzle; owner authorized GitHub publication. | compliant for shared product; store account/console duties not applicable to this destination |
| R2 | RuStore 2.1–2.16; mandatory | Puzzle, fruit, trophy and instrumental resources; no prohibited content/service identified in inspected product. | compliant; special regulated services not applicable |
| R3 | RuStore 3.1–3.2; mandatory | No mature product content or behavioral advertising. | compliant content; store audience questionnaire not applicable, no rating invented |
| R4 | RuStore 3.3–3.4; mandatory | No crypto/mining, political or organization publication features. | not applicable; external owner eligibility is not inferred |
| R5 | RuStore 3.5–3.6, 4.3, 8.3; mandatory | Private local image/name and optional OS sharing; no hosted user-content or communication platform. | not applicable to hosted moderation/account obligations |
| R6 | RuStore 4.1–4.2, 4.4–4.6; mandatory | Existing resource provenance, dependency notices and owner-authorized supplied/generated materials retained; new renderer uses game-owned geometry. | compliant in inspected provenance; no confirmed rights or impersonation conflict |
| R7 | RuStore 5.1–5.4, 6.3; mandatory | Packaged privacy/credits, private storage and scoped image selection; consent-based sharing/update installation; no account. Actual merged permissions and signing reviewed below. | compliant; store disclosure forms not applicable |
| R8 | RuStore 6.1–6.2, 6.4; mandatory | Free Russian UI, actual version and accurate English GitHub notes; no paid access. | compliant for shared product/materials; no claim of store listing upload |
| R9 | RuStore 6.5–6.8; mandatory where applicable | No RuStore card/media/contact/FAQ changes. GitHub motion evidence is engineering evidence, not store screenshots/video. | not applicable to this destination |
| R10 | RuStore 7.1–7.6; mandatory | No app rating barrier, review incentive or spam flow. | compliant in inspected app; store review operations not requested |
| R11 | RuStore 8.1–8.6; mandatory | Native portrait app, preserved offline play, API 35 regression and actual signed smoke below. No compulsory login or Play Integrity requirement. | compliant in inspected technical scope; store quotas/distribution declarations not applicable |
| R12 | RuStore 9.1–9.9; mandatory | No ads, purchases, payment SDK or subscriptions. | not applicable |
| R13 | RuStore 10.1–10.4, 11.1–11.3; process | No store submission, sanction or appeal action. | not applicable; #215 unresolved outside scope |
| R14 | RuStore 12.1–12.2; legislative summary/checklist | Applicable content, rights, privacy and stability cross-check above. | compliant inspected product; external legal/account facts not certified |
| G1 | GitHub Terms B/C/D/F/H; mandatory | Existing owner repository, explicit publication authorization, scoped API writes, retained notices and asset provenance. | compliant inspected content/use; account eligibility remains owner's responsibility |
| G2 | GitHub Acceptable Use 1–11; mandatory | No harmful executable campaign, deceptive app, credentials in intended assets or automated spam. Bounded history scan described below. | compliant inspected content/workflow; no exhaustive secret-detection claim |
| G3 | Additional Product Terms Actions; mandatory when used | Actions disabled via API; local Windows build/test/publication, Sonar scanner uses existing SonarQube Cloud exception. | not applicable to new Actions execution; no service quotas inferred |
| G4 | About releases / asset limits; mandatory | Four uploaded files, each below 2 GiB; exact sizes below. Existing tags/releases never overwritten. | compliant after candidate inventory verification |
| G5 | Licensing guidance; recommendation | Existing per-resource/dependency notices retained. Root licensing decision belongs to owner. | adopted notices; root-license change deferred outside scope |
| G6 | Releases guidance; recommendation | Version tag, accurate notes, APK/update/checksums and original signer verification. | adopted |
| G7 | Community Guidelines; recommendation | English reviewable PRs and consolidated issue completion comments. | adopted |
| P1 | Project policy; project check | Final PR and separate exact-main full local gates, original APP/UPLOAD keys, signed runtime smoke and downloaded public release verification. | pre-publication gates passed below; public-download verification follows publication |
| P2 | Protected design/device rules; project check | Portrait lock preserved; only authorized copy/fireworks changed. Objective bounds/state/pixel/PCM tests retained. | compliant in actual tests; no human listening/aesthetic acceptance claim |

Current RuStore 8.1 mentions Android 8.0; GitHub distribution retains minSdk 24/Android 7.0 compatibility. This is broader declared compatibility, not a claimed Android 7 device test or new RuStore acceptance. Physical-device FPS/battery and live store console/privacy URL are outside the verified evidence.

## Inspected coverage and unchanged inputs

Manifest, resource/assets tree and dependency declarations are unchanged in Git from v1.5.0 to the release PR head. Existing update SDK and its transitive diagnostics disclosures remain; SDK absence is not claimed. Existing HTTPS/download checksum/original-signature/version bounds and private FileProvider behavior are covered by retained regressions. No new permissions, authentication, advertising, payments, endpoints or user-data services were introduced. Both victory screens/postcard, current/archive results and starting-level accessibility were inspected and tested in their implementation PRs; their final merged behavior is covered again by release gates.

Privacy and license/source bytes are compared to the actual signed APK below. Package, merged permissions, exported components, portrait lock, debuggable flag, SDKs, certificates, archive contents and version are checked against the signed candidate rather than inferred from a debug build.

## Exact candidate and publication decision

Pre-merge platform review completed for the signed identity below; publication remains blocked on the independent exact-main full regression and signed runtime verification. This report is reassessed for the actual main artifacts before publication. Public report will contain hashes and generic emulator identity only; local host paths, credentials, private keys and raw logs stay ignored.



### Pre-merge signed candidate (historical after merge)

Source `94f48f76434829910557846d22c76e0ae06b94f0`, PR #257. Package `ru.itoltec.swypetris`, version 1.5.1/code 23, minSdk 24, targetSdk 36. Build-Release completed in 126.993 seconds: signed APK plus release lint and valid reuse of 154 JVM tests, then independently upload-key signed AAB. Signature verification matched the repository's original public certificates.

| Candidate | Bytes | SHA-256 | Signer SHA-256 |
| --- | ---: | --- | --- |
| Swypetris.apk | 35,443,848 | `654b52e865809e96f56e763b32435521b8cff64b5e3a36ae9843edbbe24671b2` | `04e8937a933bd53c80ae16f450bdda86e97e8addeabbd15ce8814eb9f42b66dc` |
| Swypetris-1.5.1.aab (local only) | 36,902,798 | `ddff258c825add54ae61490593bd9cbdfc83d634b42d2298333d6d6a131f539d` | `f4f7384dd99a23cfeb47573fa67391e272c01fda86d1ee30e659faa47e625b03` |

The actual release manifest retains portrait MainActivity, non-exported update receiver/FileProvider/vendor launcher/startup provider/metrics job, and the AndroidX exported ProfileInstallReceiver guarded by DUMP. No debuggable release flag. Merged permissions: VIBRATE, INTERNET, REQUEST_INSTALL_PACKAGES, UPDATE_PACKAGES_WITHOUT_USER_ACTION, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED and the app-scoped AndroidX dynamic-receiver signature permission. Gameplay vibration, consent-based update installation and vendor diagnostic/network/job purposes remain disclosed; no broad gallery, location, contact, microphone or camera permission.

Twelve packaged source assets (privacy, legal notices, music/theme provenance and eight theme licenses) match committed source byte-for-byte. All 48 packaged res/ entries match the local signed 1.5.0 baseline, including music and artwork; this resource comparison is not a claim of matching public APK identity. Generated baseline profile assets differ as expected with code changes. No private-container filenames or known credential/header patterns found in the inspected APK or AAB. The history scan covered 188 reachable commits and 2,486,929 text-patch lines, zero known-prefix/private-key matches and no tracked private-keystore containers. This is a bounded detector, not a guarantee against every secret format.

Pre-merge technical gate: 154 JVM passed (fresh), lint/detekt/JaCoCo and Sonar passed; API 35 Android 175 unique cases, 172 passed/3 opt-in skipped in 559.797 seconds. Picker 1 and UI 14 repeated preflights passed. Gate elapsed 800.967 seconds; build-related release inputs selected full Android scope, not unrelated local tool tests. No platform conflict or applicable mandatory unresolved finding identified for this candidate. Exact-main, signed runtime and public-download gates are independent pending actions, not claimed completed here.


### Final exact-main reassessment before publication

Source: `882388d44d8ca2ef75cdf021db5f43e7bbc48349`, the verified remote main and squash merge of PR #257. Full local gate passed in 827.581 seconds: Python 120 passed, Pester 121 passed/2 optional Unix skips, quality/JaCoCo/lint/detekt with valid reuse of 154 JVM cases, Sonar Quality Gate passed, actual Android 175 unique cases/172 passed/3 opt-in skipped (628.774 seconds), plus separately repeated picker 1 and UI 14 preflights. No Actions run was dispatched or inferred.

Verify-LocalRelease completed in 60.748 seconds on this exact clean main, including original APP/UPLOAD signature validation, release build/lint and actual signed menu/settings/new-game/pause/Continue smoke. Swypetris remains installed on the owned API 35 emulator. No physical-device acceptance is claimed.

Both main APK/AAB hashes, sizes and public signer fingerprints exactly match the pre-merge table above; these are independently observed results. Final merged manifest matches the reviewed candidate, twelve source disclosure/license assets still match committed bytes, all 48 res/ entries match the local 1.5.0 baseline, and bounded APK/AAB private-container/credential scans pass. Generated dexopt profiles are not treated as unchanged artwork. The reachable main history scan again reports 188 commits, 2,486,929 text-patch lines, zero known-prefix/header hits and no tracked private-key containers.

Public update.json is 135 bytes, SHA-256 `49b3eebf47a38a9d9e7419f2ab49ab9d97df11a9a9d144ac541051687229e24c`; SHA256SUMS.txt is 160 bytes, SHA-256 `872432567820630fd8b5f59d87fea4817bc5e167f83bbb4c874bdb2b8547083e`. Update metadata specifies version 1.5.1/code 23 and the exact APK digest. The publisher uploads these two files, APK and local-release-provenance.json; AAB, signing containers, raw logs and screenshots remain local. The inspected public provenance contains commit, certificate/artifact hashes, generic emulator identity and diagnostic filenames/hashes, with no host paths, physical-device identifiers or credentials. Four files are below the current release asset limits.

**Decision recorded before publication:** both platform reviews are complete for the exact intended main artifacts. No applicable mandatory violation, unresolved platform finding or rights conflict was identified in the inspected evidence. Destination-specific RuStore console/submission/age/media fields are excluded with reasons; no moderation approval or external account eligibility is certified. All pre-publication technical gates passed. Owner authorization permits explicit GitHub-only publication of v1.5.1, followed immediately by independent public downloads, package/version/hash/certificate checks. Post-publication verification is not preclaimed. Local evidence remains operator-controlled, not an independently signed GitHub attestation. Current review and diagnostic snapshots are retained in ignored verification output; this redacted report is committed by the post-publication documentation PR.

### Observed public verification after publication

v1.5.1 was published 2026-10-12 12:19:52 UTC, non-draft/non-prerelease; tag resolves to `882388d44d8ca2ef75cdf021db5f43e7bbc48349`. The publisher's separate Verify-PublishedRelease completed successfully in 23.287 seconds. All four unauthenticated downloads matched API asset digests and local provenance, APK original signer/package/version and update/checksum consistency passed; release notes match the committed notes. Public APK digest is exactly the final candidate digest above. Public provenance is 2,154 bytes, SHA-256 `52305cca5182ffc62fee7f0dc37dc6982b8e240e7d5c57cccdb6175282e5a5c9`. No existing release/tag was overwritten.

A preliminary WhatIf signature check failed solely because the fresh process lacked JAVA_HOME/Java on PATH. Explicit current JDK configuration resolved the directly reproduced tool error; signature verification then passed without altering artifacts. This was before publication and did not create a tag/draft. Main runtime/build/full-regression proof and successful public verification complete P1. Review limitations remain as stated; store submission and external account/legal certification are not claimed.
