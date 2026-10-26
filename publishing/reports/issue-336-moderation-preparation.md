# RuStore moderation correction — 1.11.3 preparation

Review date: 2026-10-26. Owner: RoyMatus. Issue #336. This is a complete review triggered by a confirmed rejection and changed update permissions. Publication requires the subsequent exact-artifact review and technical gates; this preparation record is not a publication verdict.

## Evidence and correction

RuStore API version2064894596 is REJECTED (1.11.2/code35); public version2064837892 is ACTIVE (1.0.0/code1). The supplied moderator message explicitly requires removal of REQUEST_INSTALL_PACKAGES. The earlier [1.11.2 report](release-v1.11.2-compliance.md) permission rationale did not establish acceptability and is superseded by this rejection.

1.11.3/code36 removes the custom PackageInstaller implementation, its private receiver, unknown-source settings action and download/install dialogs. Manifest removal markers exclude REQUEST_INSTALL_PACKAGES and UPDATE_PACKAGES_WITHOUT_USER_ACTION, including dependency contributions. GitHub update consent opens the release page in a browser; RuStore installations retain their FLEXIBLE store-managed flow without GitHub fallback. Legacy update files/preferences are discarded; gameplay data remains untouched. Privacy and update documentation describe the new behavior.

## Current sources

The complete official [RuStore requirements](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps), sections1–12.2, were opened on2026-10-26 and the owner supplied their complete text in the same session. No effective date is stated. Legislative summaries are platform text, not an independent legal opinion. [GitHub Terms](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service), [AUP](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies), [release rules](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases), and [licensing guidance](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository) were opened for this review. Existing repository/API/Releases use continues; no new hosting, paid service or permissions are introduced.

RuStore SDK10.5.1 official documentation and public API draft/upload contracts were checked. Context7 did not resolve the relevant official SDK; unrelated third-party libraries were not substituted.

## Requirement coverage

The individual baseline exclusions in [1.6.0](release-v1.6.0-compliance.md) and inspected resource provenance in [1.11.2](release-v1.11.2-compliance.md) are retained only where reassessed below. No new media, game mechanics, dependency, advertisement, purchase, account or hosted user-content service is introduced.

| Provisions | Applicability, inspected evidence and current result |
| --- | --- |
| 1.1–1.4 | Native game and existing owner-authorized account/destinations. App/package/version and server state explicitly identified. Owner remains responsible for account/contractual duties; no independent eligibility certification. |
| 2.1–2.8,2.15–2.16 | Puzzle blocks, fruit counters, instrumental music and existing artwork; no new content. No prohibited-content finding in inspected source/provenance. |
| 2.9–2.12,3.3 | Not applicable: no medical, finance, gambling, crypto, mining or network-circumvention functionality. |
| 2.13,4.1–4.2,4.4–4.6 | Existing original/provided artwork, synthesized recordings and packaged palette licenses/source notices. No new third-party resource or imitation claim. No confirmed rights conflict; external legal rights are not independently certified. |
| 2.14,5.3,6.3 | Confirmed violation in rejected APK. Installer removed; installed-package regression now passes. Exact signed APK permissions/debug flag/certificate remain release gates. Each retained SDK permission still requires artifact and declaration comparison. |
| 3.1–3.2 | Existing console arcade/0+ classification; no ads, purchases, violence or sexual content introduced. This is a general-audience puzzle, not an app specifically aimed at preschool children. Preserve truthful age/content metadata. |
| 3.4 | No such publishing/content feature identified; external owner legal status is not independently certified. |
| 3.5–3.6,4.3,5.4,8.3 | Not applicable to hosted services: no account, communication platform or public content hosting. Local optional name and OS postcard sharing do not introduce server-side UGC. |
| 5.1–5.2 | Both Russian privacy copies now disclose browser/store-managed updates, retained local game data, SDK technical data, deletion and contact. No new data flow. Public versioned policy and console declarations must match after publication. |
| 6.1 | Free game, no paid functionality; preserve explicit no-purchase/payment declarations. |
| 6.2 | Russian interface, help and listing remain available. |
| 6.4 | Keep identical Swypetris installed/listing name. Update description/notes truthfully; no store-promotional link in store copy. Public privacy URL is a policy document, not a download storefront. |
| 6.5 | Arcade and existing relevant search tags; server readback required for candidate. |
| 6.6 | Unchanged canonical opaque icon and actual portrait gameplay/menu media must be verified against source/current app; no obsolete screen or OS overlay may be submitted. |
| 6.7 | Existing public developer contact retained; final server readback required. |
| 6.8 | Optional FAQ deferred: not needed for this maintenance update. |
| 7.1–7.6 | No review outreach, ratings manipulation or gating requested or introduced; no review mutation performed. |
| 8.1–8.2,8.4–8.5 | Native playable offline game, portrait lock retained, no integrity/store exclusion. Focused tests pass; full regression, exact signed smoke and physical update acceptance remain required. Store and GitHub will receive the same version/APK. |
| 8.6 | One corrected submission planned; no bulk app generation/publication. |
| 9.1–9.9 | No payments, ads, subscriptions or advertising SDK; corresponding declarations must remain explicit. Other conditional monetization rules are not applicable. |
| 10.1–10.4,11.1–11.3 | Resubmit the corrected existing app; no appeal or alternate-account publication. Actual moderation/approval remains an external result, never inferred from technical checks. |
| 12.1–12.2 | Cross-reference the applicable evidence above; no claim that automated review establishes exhaustive legal compliance. Final artifact/metadata/runtime checklist remains mandatory. |
| GitHub Terms/AUP | Existing authorized repository/API/release activity, rights/privacy notices preserved. No new access, hosted builds or paid product. Source/artifact secret scan and immutable version provenance remain required. |
| GitHub Releases/licensing | New immutable tag, files below asset limits, original certificates, checksums and public verification required. Root license selection remains an owner choice; hosting does not force an open-source license. |
| Recommendations | Adopt current native portrait screenshots, concise truthful notes and independent artifact readback. Optional video/FAQ deferred. No recommendation is silently promoted to a mandatory platform rule. |

## Local check plan and actual development evidence

Execution profile: Local, 8 logical CPUs, 47.9GiB RAM, four workers, one Gradle invocation and one headless API35 emulator. Pixel7 identified for signed upgrade/data-retention acceptance. No cloud builds or energy measurement.

- TDD RED: installed-package permission test failed on unchanged production code with the expected REQUEST_INSTALL_PACKAGES assertion (1 test/1 failure).
- GREEN: 20 update policy, consent, persistence, legacy-cleanup and permission cases passed in98.838s. Routing/permission/cleanup selection then passed3 cases in6.421s after final browser-action coverage was added.
- Debug/test APK compilation and detekt passed. Intermediate test Context/Application type mismatch and line-length finding were corrected; failed logs retained. Initial emulator installation had a signer mismatch from an old fixture; its APK/diagnostics were retained and only the isolated emulator game was replaced. Pixel data was not cleared.
- Old download-recovery test was replaced because the production downloader was removed. Signed public-download integrity coverage remains an explicit test-only fixture; it no longer invokes a removed installer. No gameplay regression assertions were removed.

Final exact-head Full gate, exact-main signed gate, supplemental scenarios, Pixel acceptance, signed artifact identity and final store metadata are pending at this preparation commit. The completion report must record their actual results before claiming release completion.

## Follow-up source and declaration review

The source-inventory gate required classifying LegacyUpdateCleanup.kt for full regression; 21 selector tests passed. The first complete quality stage found an API26-only ActivityMonitor constructor in the new routing test (project minSdk24). It was replaced with the compatible IntentFilter-based blocking monitor; lint/detekt/test APK build passed, then both compliance tests passed in6.49s. Python131 and PowerShell181 tests passed in the preceding full attempt; original checkpoint evidence is retained for eligible reuse.

Read-only inspection of the cached RuStore10.5.1 bytecode found host-package version queries and explicit RuStore package/signature checks, update events, diagnostic events and SDK UUID fields. No getInstalledPackages/getInstalledApplications references were found in the six inspected AAR class jars. The broad installed-app declaration therefore describes Swypetris/RuStore information, not an asserted whole-device inventory. The metrics scheduler uses a network-constrained persisted periodic JobScheduler job (1440minutes); RECEIVE_BOOT_COMPLETED supports this job. Both privacy copies now explicitly disclose deferred background diagnostic delivery and reboot persistence. This is bounded static evidence, not a claim about the separate RuStore application's backend.

The rejected listing's old installer rationale and v1.11.2 privacy URL must be replaced, alongside the version-specific moderator note. Its CDN icon and three screenshots were downloaded and visually inspected: the menu shows the old version, so capture current native release screenshots before submission. Preserve the explicit empty advertising/payment/purchase declarations and the four scoped SDK data types; verify server readback.
