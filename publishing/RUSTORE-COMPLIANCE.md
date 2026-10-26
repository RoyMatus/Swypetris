# RuStore compliance memory and request gate

Owner instruction recorded2026-10-26. Source: the complete requirements supplied by the owner and [current official RuStore rules](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps), reviewed2026-10-26. This is a practical summary, not a substitute for the current full rules or legal advice. Recheck affected official provisions when their meaning/version is uncertain and perform the full release review in [RELEASE-COMPLIANCE.md](RELEASE-COMPLIANCE.md).

## Before accepting implementation scope

Check every request, ticket and acceptance criterion against applicable mandatory requirements before implementing it. Do not implement a confirmed conflict even when the conflicting behavior appears in a user request or ticket. Explain the concrete conflict, cite the provision and evidence, and propose a compliant alternative. Continue independent compliant work. Do not ask for an exception to evade the rule, conceal behavior from review, change accounts to evade a rejection or describe a violation as compliant.

Uncertainty is not proof of a violation: investigate it, label the finding unverified, and hold only the affected action/publication until resolved. Recommendations are not prohibitions. Do not invent additional platform obligations. The parent performs the initial scope check; use the `store_compliance` agent for substantial/uncertain scope and before release publication. The agent reviews evidence without duplicating builds or publishing.

## Practical requirements

| Area / sections | Required application to Swypetris |
| --- | --- |
| Identity and responsibility,1 | Keep package, version, publisher, contact and submission identities accurate. Actual submission/approval/publication are separate states. |
| Prohibited content,2; regulated categories,3 | No unlawful/extremist/sexual-exploitation/harmful content, illicit goods, fraud, malware, unauthorized redistribution or prohibited circumvention. No prohibited political/relationship content under the platform rules. Finance, gambling, crypto, medical, communications and other regulated functions require their applicable conditions; the current game has none. Assess actual functionality rather than names. |
| Age and children,3.1–3.2,9.7 | Rating must match actual material. No unsuitable content/ads/purchases or unauthorized child-data transfer; no behavioral ads at0+/6+. Restrictions specifically for young-child apps must not be confused with every general-audience0+ game. |
| Rights and identity,4 | Have lawful grounds for code, music, images, fonts and names; retain notices/provenance. No impersonation, misleading affiliation or near-duplicate app publication. Local user content and hosted UGC have different obligations. |
| Privacy,5 | Accessible Russian policy in app and listing: data categories, purpose, retention/deletion and responsible contact. Disclose actual SDK behavior; no hidden profiling or unsupported privacy claims. Obtain consent where required. Accounts, if introduced, require in-app deletion. |
| Permissions,2.14,5.3,6.3 | Only necessary justified permissions; no deceptive consent or pressure after refusal. Optional permission denial must preserve unrelated functionality. Forbidden permissions must be absent from the merged/signed artifact, not merely omitted in one source manifest. |
| Confirmed project rejection | RuStore rejected1.11.2/code35 for REQUEST_INSTALL_PACKAGES. Never restore this permission or the custom APK installer in the RuStore-distributed game. UPDATE_PACKAGES_WITHOUT_USER_ACTION is also removed. Use store-managed updates; any future distribution change needs a new review. An explanatory permission rationale did not satisfy moderation. |
| Store copy,6.1–6.5 | Accurate free/paid declarations, Russian listing and supported language/help; installed and listing names match. No clickbait, profanity, misleading claims, third-party storefront links or claims for unavailable features. Keep relevant category/tags and truthful release notes. Russian naming is described as a recommendation in6.4; do not silently treat it as a blanket prohibition on an existing name. |
| Media/contact,6.6–6.8 | Icon512×512 PNG/JPG, at most3MB, opaque and matching installed icon. Phone screenshots at most3MB, maximum2160×3840; actual current gameplay, no OS/other-app UI or unrelated ads. Use consistent portrait orientation for this game. Optional video must be public VK-hosted and meet documented limits. At least one developer contact. FAQ/video are optional. |
| Reviews,7 | No rating gate, incentives, fake reviews/downloads or abusive complaints. Review replies must be relevant and must not request private data or promote other stores. |
| Quality,8 | Completed stable native functionality, supported Android compatibility, correct layout, no forced unavailable dependency or store-blocking integrity checks. Keep RuStore version current with other distribution. No duplicate app/account workaround. Test actual startup/offline gameplay, navigation and update/data preservation. |
| Ads/payments,9 | Declare every actual ad/payment SDK and purchase/subscription flow. Clear costs/rubles/renewal/cancellation before consent. No disguised, uncloseable, accidental or out-of-session ads. Swypetris currently has no ads, payment SDK or purchases; reassess if this changes. |
| Moderation and enforcement,10–12 | Resubmit only after fixing violations; retain exact artifact/metadata evidence. The stated72-hour moderation window is a platform process, not an agent guarantee. Never claim approval from tests or an accepted upload. Follow applicable takedowns/ongoing duties. |

## Evidence and decisions

Use `compliant`, `violation`, `not applicable` with reason, or `unverified`. Separate mandatory rules, recommendations and project gates. For each blocker report the triggering behavior, exact provision, file/artifact/console evidence and compliant alternative. Inspect manifest/dependencies/data/resources, signing, metadata/media/privacy and actual runtime results. A green build is not a legal, rights, account, moderation or final-publication certification.

Store-only declarations require live console/API evidence. Exact signed artifacts, original certificates, full local release gates and supplemental release scenarios remain required by project policy. Preserve credentials and existing signing identity. The compliance agent never expands access, sends appeals, publishes or performs heavy verification without assignment.
