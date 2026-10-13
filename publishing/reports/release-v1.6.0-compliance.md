# Release 1.6.0 compliance and first-launch investigation (#215)

Reviewer: Codex. Release owner: RoyMatus. Review date: 2026-10-13. A complete review is required because this release delivers the reopened epic #236 children #259/#264/#189/#210. The authorized destination is GitHub Releases; preparing RuStore submission files is included, uploading or submitting them is not. #269/#270/#271 follow this release separately. This document distinguishes inspected application facts from unavailable historical/store evidence and does not certify legal eligibility or future moderation approval.

## Identity and historical evidence

Intended package: `ru.itoltec.swypetris`, version 1.6.0 / code 24, minSdk 24 / targetSdk 36. Preparation starts from main f7c11881dc20c04baf7d836daa585fb4b17cbc2b. No v1.6.0 release or tag existed when checked on 2026-10-13. Exact signed-candidate identity and runtime results are recorded below when available; this preparation revision does not authorize publication.

The developer reported that a RuStore moderator rejected an installation for immediately requiring an update and referenced section 8.5. The exact submitted APK/AAB, its hash/version/signers, the original prompt and the live console configuration are unavailable. Consequently, an outdated submission, source selection, stale metadata or another historical defect remain hypotheses. Testing a new candidate cannot establish the cause of that rejection.

The previous public GitHub release v1.5.1 was freshly inspected and all four assets downloaded without an Authorization header. Their hashes matched GitHub's asset digests. Its APK is 35,443,848 bytes, SHA-256 `654b52e865809e96f56e763b32435521b8cff64b5e3a36ae9843edbbe24671b2`; aapt reports the expected package, version 1.5.1 / code 23, min24 / target36. apksigner verification passed with APP certificate SHA-256 `04e8937a933bd53c80ae16f450bdda86e97e8addeabbd15ce8814eb9f42b66dc`. update.json agrees with the version/code/APK hash. This is a public baseline, not the unidentified rejected submission.

## Current official sources

- [Complete RuStore requirements](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps): all sections 1–12 and their full subsections read on 2026-10-13. No effective/update date was stated in the inspected page. Retained HTML: 247,119 bytes, SHA-256 `f33ac37c70a2c8ccf302f651d7a65b2f2347b334cf13d360518b8c7f3ab4aab0`. The matrix indexes individual provisions; it does not reproduce the rules.
- [GitHub Terms A–S](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service), effective 2026-04-27; [Acceptable Use, sections 1–11 and enforcement](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies); [About releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases); [Licensing guidance](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository): full article bodies reviewed on 2026-10-13.
- [Additional Product Terms](https://docs.github.com/en/site-policy/github-terms/github-terms-for-additional-products-and-features), effective 2026-08-27, and [Community Guidelines](https://docs.github.com/en/site-policy/github-terms/github-community-guidelines): reviewed for actual repository/API/Projects/Releases use. No new Actions, Codespaces, npm/Packages, Pages or Copilot execution is part of this delivery. Builds/tests run locally; SonarQube Cloud is the project's separate analysis exception.

## Inspection evidence

The [GitHub General Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement), effective 2026-04-27, was also reviewed in full on 2026-10-13. GitHub handles technical service data for the existing direct update channel; no new developer analytics or account-data collection is introduced.

**E1 — functionality and updates.** AppUpdates chooses RuStore only for installer `ru.vk.store`; API30+ uses getInstallSourceInfo, earlier supported APIs use the guarded legacy method. Both channels require a strictly newer versionCode. Automatic installation defaults off and requires explicit consent. The normal launch check can show a dismissible newer-version offer; offline failure is silent unless manually requested. Download integrity checks retain expected package, version, size/hash and original signer validation. No production update-selection defect has been established or hidden by suppressing a legitimate update. New native tests exercise equal/older/offline responses and postponement of a newer offer to start gameplay without a download. Signed candidate online/offline and upgrade evidence remains required below.

**E2 — manifest and data.** Source and merged debug manifest were inspected; the exact release manifest is independently checked below. Portrait MainActivity, non-exported game update receiver and scoped postcard provider remain. Direct permissions serve vibration/network/consented installation; merged SDK/AndroidX permissions additionally include network state, boot scheduling and an app-scoped signature permission. No broad gallery, camera, microphone, contacts, location or advertising-ID permission is requested. The app has no account, ads, purchases, hosted social feed or developer gameplay analytics. Local settings/results/name, private photo copy/crop, session, optional postcard sharing, Android backup and RuStore SDK10.5.1 technical analytics/metrics are disclosed in privacy.txt. SDK selection is lazy and channel-specific. External app/browser/email sharing is user initiated; the recipient's handling is outside Swypetris.

**E3 — resources and dependencies.** Current source, music/theme provenance, legal-notices.json, eight license assets, generated geometry and the owner-approved #189 material/field handoff were inspected. Existing original/synthesized tracks and owner-supplied Menu Melody/drop resources retain their provenance and notices; there is no new recording or rights transfer in #215. Theme names identify adapted palettes; the app does not claim endorsement by their projects. Kotlin/AndroidX/Compose/ZXing and RuStore SDK versions remain unchanged. Runtime fonts use existing platform/own drawing paths. No confirmed rights, impersonation or prohibited-content conflict was identified in the inspected material; this does not invent unavailable third-party contractual/account facts.

**E4 — secret exposure.** A bounded detector inspected 596 tracked working paths, 304 reachable commits and 249,467 textual history lines: zero known GitHub-token/AWS-key/private-key-header matches and zero named private keystore containers. It emits no matched secret values. Binary history and unreachable objects are not exhaustively decoded; current signing files remain outside Git. Artifact archive inspection is recorded below. These limits are explicit rather than claiming universal secret absence.

**E5 — confirmed corrections.** Draft store metadata still described 1.5.0/code22 and ten themes; it now describes 1.6.0/code24, twelve themes and actual shipped behavior. Both Russian privacy copies now disclose the authorized one-time rules7 reset of old records/history/Continue while retaining settings/name/photo and ordinary later saves. No claim is made that editing repository files updated the store console or published the privacy webpage. Version24 supersedes the independently checked public code23; historical v1.5.1 is preserved.

## RuStore matrix

All numbered source requirements were reviewed. `Compliant` below is limited to inspected shared product/materials; console-only fields remain `unverified` for a future RuStore submission. Store-only unknowns do not authorize that submission and, under #215's explicit delivery contract, do not block the separately authorized GitHub release. Runtime/artifact-dependent conclusions remain provisional until the exact candidate checks below pass.

| Provision | Status and evidence / destination limit |
| --- | --- |
| 1.1 | Compliant: E1 native working puzzle; actual candidate runtime gate required. |
| 1.2 | Compliant: both destination scopes are explicitly separated in this review. |
| 1.3 | Compliant: app, submission, update and publisher identities are distinguished. |
| 1.4 | Unverified for RuStore: actual owner-console declarations and submission are manual. GitHub publication is explicitly owner-authorized. |
| 2.1 | Compliant: E1/E3 inspected puzzle, fruit and instrumental assets contain no extremist material. |
| 2.2 | Compliant: E3 no sexual content in inspected assets or flows. |
| 2.3 | Compliant: E3 no interpersonal violence, threats or abuse; block-clearing effects only. |
| 2.4 | Compliant: E3 no self-harm content or service. |
| 2.5 | Compliant: E3 no tragedy-themed content. |
| 2.6 | Compliant: E3 no controlled-substance content or commerce. |
| 2.7 | Compliant: E3 no alcohol/tobacco content or commerce. |
| 2.8 | Compliant: E3 no weapons or dangerous-goods service. |
| 2.9 | Not applicable: no health/medical functionality or advice. |
| 2.10 | Not applicable: no financial/credit service. |
| 2.11 | Not applicable: no wagering, lottery or monetary prizes. |
| 2.12 | Not applicable: no access-circumvention service. |
| 2.13 | Compliant in inspected scope: E3 provenance/notices retained; no content-redistribution service. |
| 2.14 | Compliant in inspected scope: E1/E2/E4 consent, update integrity, local data and bounded security checks. |
| 2.15 | Compliant: E3 no relationship/sexuality themed material in the product. |
| 2.16 | Compliant: E3 no political/state-themed material. |
| 3.1 | Unverified for RuStore console: owner must complete the actual rating questionnaire; no rating invented from puzzle genre. |
| 3.2 | Unverified for RuStore console: owner must declare intended audience; E2 contains no ads, purchases or account. |
| 3.3 | Not applicable: no cryptocurrency/mining functionality. |
| 3.4 | Not applicable to app functionality: no organizational/political publishing; external owner eligibility is not certified. |
| 3.5 | Not applicable: private local name/photo and optional OS sharing are not a hosted UGC service. |
| 3.6 | Not applicable: no hosted communication/content-distribution platform. |
| 4.1 | Compliant in inspected provenance: E3 notices and owner-provided/generated materials; no confirmed rights conflict. |
| 4.2 | Compliant: no claimed official partnership; E3 original identity and acknowledgements. |
| 4.3 | Not applicable: no hosted UGC service. |
| 4.4 | Compliant: own app identity, no government-service impersonation or WebView clone. |
| 4.5 | Compliant inspected workflow: provenance retained; external legal disputes are not inferred or certified. |
| 4.6 | Compliant inspected content: own source/art/UI identity; no asserted endorsement or copied service identity. |
| 5.1 | Compliant packaged disclosure after E5; public HTTPS privacy URL and console linkage remain unverified for RuStore. |
| 5.2 | Compliant inspected flows: E2/E5 local data, SDK technical data, backup, selected photo and explicit postcard sharing disclosed. |
| 5.3 | Compliant inspected app: E2 scoped permissions and consent/denial paths; exact release manifest checked below. |
| 5.4 | Not applicable: no account to create/delete; local-data removal instructions are supplied. |
| 6.1 | Compliant prepared materials: native free puzzle, E1/E5; actual console type remains unverified. |
| 6.2 | Compliant: Russian app, Russian prepared listing/privacy; English engineering release notes do not replace store copy. |
| 6.3 | Unverified for RuStore declaration: compare actual release manifest/E2 with console answers before submission. |
| 6.4 | Compliant corrected draft E5; actual live listing remains unverified. |
| 6.5 | Unverified for RuStore: proposed puzzle category must be selected and checked in console. |
| 6.6 | Current signed candidate captures inspected and supplied; actual console media remain unverified until the owner uploads/reviews them. |
| 6.7 | Prepared owner contact retained; actual store contact fields/delivery remain unverified. |
| 6.8 | Unverified for store support/FAQ setup; no console writes performed. |
| 7.1 | Not applicable to this delivery: no store-review replies or moderation actions. |
| 7.2 | Not applicable: no in-app rating request. |
| 7.3 | Compliant inspected app: no incentivized rating or review gate. |
| 7.4 | Not applicable to this delivery: no review/rating manipulation operation. |
| 7.5 | Not applicable: no store-review complaint submitted. |
| 7.6 | Unverified owner store operations: future monitoring is not claimed as performed. |
| 8.1 | Compliant source functionality E1; API35 native checks and exact signed runtime required. No invented exhaustive device matrix. |
| 8.2 | Compliant: native Kotlin/Compose game, not a WebView wrapper. |
| 8.3 | Not applicable: no hosted UGC. |
| 8.4.1 | Not applicable: app is not tablet-only. Portrait layout remains supported. |
| 8.4.2 | Compliant inspected source: no Play Integrity or competing-store distribution lock; actual RuStore-delivered artifact remains unavailable. |
| 8.5 | New candidate/version/update tests address current delivery. Historical rejected version and live store freshness remain unverified; no historical cause asserted. |
| 8.6 | Not applicable to this destination: zero RuStore submission operations; no daily submission-limit or exemption claim. E3 separately covers fixed generated artwork. |
| 9.1 | Not applicable: no payments or payment SDK. |
| 9.2 | Not applicable: no purchase terms or paid access. |
| 9.3 | Not applicable: no paid trial/subscription. |
| 9.4 | Not applicable: no advertising SDK. |
| 9.5 | Not applicable: no displayed ads. |
| 9.6 | Not applicable: no advertising format. |
| 9.7 | Not applicable to ads/payments; audience declaration remains 3.2's owner action. |
| 9.8 | Not applicable: no behavioral advertising data flow; E2 separately discloses technical update SDK data. |
| 9.9 | Not applicable to this delivery: no ad/payment operation. |
| 10.1 | Unverified future store process: no upload or review started. |
| 10.2 | Not applicable to measured delivery time: no moderation-duration claim. |
| 10.3 | Historical rejection is reported only; exact evidence unavailable as described above. |
| 10.4 | Not applicable to this delivery: no removal order/action handled. |
| 11.1 | Not applicable: no sanction decision or appeal performed. |
| 11.2 | Not applicable: no appeal submitted. |
| 11.3 | Not applicable: no restoration claim. |
| 12.1 | Cross-check covered by E1–E5 and rows above; not an independent legal/account certification. |
| 12.2 | GitHub/shared candidate checks below; remaining RuStore-only actions explicitly retained, not silently marked complete. |

## GitHub matrix and recommendations

| Source scope | Status / evidence |
| --- | --- |
| Terms A | Compliant inspected workflow: repository, content, API and release identities are explicit. |
| Terms B | Existing owner account and explicit publication authorization; no new account/access or eligibility representation made. |
| Terms C | Compliant inspected use: E1–E4; no harmful automation or content conflict identified. |
| Terms D | Compliant inspected content/provenance: E3; no new owner rights transfer or broad license chosen. |
| Terms E | Not applicable: target repository/release are public, no private-repository disclosure requested. |
| Terms F | No confirmed rights dispute found in E3; no takedown/counter-notice operation. |
| Terms G | Compliant: GitHub/service identities are not presented as the game's author/endorser. |
| Terms H | Compliant scoped repository API automation, existing credentials, no token exposure or access expansion. |
| Terms I | Additional terms reviewed for actual products; no Actions/Codespaces/npm/Packages/Pages/Copilot execution added. |
| Terms J | No GitHub AI feature is invoked by this delivery; Codex is a separate tool. No GitHub training/data preference is changed. |
| Terms K | Not applicable: no GitHub beta product is introduced. |
| Terms L | Not applicable: no new GitHub billing/payment purchase. |
| Terms M | Not applicable: no account cancellation/deletion operation. |
| Terms N | No GitHub communication preference or support-contact change. |
| Terms O | No GitHub warranty or app-store approval represented. |
| Terms P | No altered service-liability promise in release materials. |
| Terms Q | Owner-authorized existing account workflow; external contractual liability is not independently certified. |
| Terms R | Current effective source was reviewed; old reports were treated as historical. |
| Terms S | No contractual/jurisdictional change made; external account facts are not invented. |
| AUP1 | Compliant inspected content: E3/E4; no confirmed unlawful/private-rights exposure identified. |
| AUP2 | Compliant inspected assets: no abusive/extremist/discriminatory content. |
| AUP3 | Compliant inspected content: E3 provenance, original identity and E4 bounded privacy/secret checks; no confirmed rights conflict. |
| AUP4 | Compliant workflow: scoped reviewable writes, no impersonation or spam. |
| AUP5 | Compliant inspected app/workflow: signed update integrity, no active attack, malicious payload or unauthorized service access. |
| AUP6 | Compliant workflow: no resale or exploitation of GitHub service access. |
| AUP7 | Compliant scoped use of this owner's repository/API data; no personal-information harvesting or unsolicited outreach. |
| AUP8 | Compliant inspected source/artifacts subject to E4's limits; no private account data or credentials intentionally published. |
| AUP9 | Compliant observed transfer scope: bounded release/asset downloads and pushes, no excessive bulk operation; no quota guarantee inferred. |
| AUP10 | No improper promotion/incentive operation included. |
| AUP11 and enforcement | No enforcement appeal or evasion performed; current source reviewed. |
| Releases limits | Candidate asset inventory must fit the current 1,000-assets / less-than-2-GiB-per-file limits; exact values below. Existing release/tag will not be overwritten. |
| Licensing recommendation | Adopted: preserve dependency/resource credits and licenses. Deferred: changing the owner's root code license is outside scope; public hosting alone does not require selecting an open-source license. |
| Release recommendations | Adopted: versioned tag, accurate notes, checksums, update metadata, original signer and independent public downloads. |
| Community guidance | Adopted: concise English PR/issue evidence, scoped changes and consolidated completion comments. |

## Candidate checks and decision

Development evidence: detekt and Android-test assembly passed in 50 s Gradle time. AppUpdatesTest, UpdateCheckTest, AutomaticUpdatesTest and UpdateApkTest passed 14 native cases, zero skips, in 112.470 s on the owned headless API35 emulator. The new cases confirm usable gameplay after equal/older/offline automatic checks and after postponing a genuinely newer offer without download/installation consent. This is controlled response testing, not live RuStore or historical-submission reproduction.

Precommit signed preparation completed in 246.234 s, including 154 passing JVM cases and release lint. Source identity is the stated base plus the reviewed uncommitted #215 changes, not an exact-main release claim. Retained summary: `release-20261013-110938-816.json`.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Swypetris.apk | 46,164,620 | `ec03679581141bc1886960f386937ffd84f021dbd57ad6b878207e3bb1fd268a` |
| Swypetris-1.6.0.aab | 47,640,640 | `d3a00edce88cef98923d66f7d2a0b9a3c7ae3333810556d8dc1b0ced4eabaa69` |

The APK retains APP certificate `04e8937a933bd53c80ae16f450bdda86e97e8addeabbd15ce8814eb9f42b66dc`; the AAB uses UPLOAD certificate `f4f7384dd99a23cfeb47573fa67391e272c01fda86d1ee30e659faa47e625b03`. These are distinct intended roles; compare against actual RuStore console values before any manual submission. Every listed file is below GitHub's per-asset size limit. Release aapt inspection confirms package/version/min24/target36, portrait launcher and absence of debuggable. The seven merged permissions retain the rationale in E2; ProfileInstaller's exported receiver requires DUMP, while the game update receiver and postcard provider are not exported.

Bounded archive inspection covered all 151 APK and 161 AAB ZIP entries, with zero known credential/private-key pattern or private-keystore-container detections. Twelve packaged disclosure/license files and 24 material/field WebP resources matched source bytes in both archives. This supplements E4 and does not prove absence of every possible secret encoding.

The actual public signed v1.5.1 passed launch/settings/new-game/pause/Continue smoke on the isolated portrait API35 emulator. An `adb install -r` update to the candidate preserved firstInstallTime and the user-selected landing-ghost setting, removed old Continue once, and allowed a new game whose Continue survived a process restart. Baseline UID was not retained, so no UID-comparison result is claimed. Two auxiliary probe errors were diagnosed and preserved: Android reported `appId` rather than the assumed `userId`, and concatenating node text with content-description obscured the score prefix. Continuing the same installed update with corrected observations passed; neither error required a production change. Evidence: `issue-215-candidate/upgrade-result.json`, retained failed-probe records, XML and logcat.

The exact candidate also passed separate clean-install online and offline signed runtime smoke: menu, settings, new game, pause and Continue. Android reported a validated default network before the online case and `Active default network: none` before the offline case; the emulator's original Wi-Fi/mobile-data settings were restored afterward. Online metadata was the then-public older code23, not a forced response. Evidence: `issue-215-candidate/online-smoke`, `offline-smoke`, associated network snapshots and logs. Actual unmodified candidate screenshots replace the four store-listing captures; menu/settings/Classic and Solarized Light were visually inspected in portrait. This is emulator evidence, not physical-device or RuStore acceptance. Pixel 7 was unavailable over adb after #210's merge; its exact-main installation remains pending.

**Publication remains blocked at this preparation revision only on delivery gates.** The inspected candidate has no confirmed unresolved shared-product violation; unavailable historical/store-only facts retain the explicit scope limits above. Complete the final protected PR merge and independent exact-main full/signed release gates, then reassess artifact identity and any affected findings. After publication, independently download and verify public tag/SHA/assets/update.json/checksums/signature and reassess first launch against the now-current public metadata. Reserve `Closes #215` for the completion-report PR after that verification. The epic stays open for #269/#270/#271.

RuStore-only owner actions: identify the historical rejected artifact if retained; compare actual console app/upload/distributed signer values; upload the verified intended candidate; publish/link the current Russian privacy page; confirm live listing, media, audience/rating, permissions/data declarations and support fields; manually submit and track moderation. Preparing files does not complete those actions or prove store acceptance. They do not expand this GitHub-only publication authorization.
