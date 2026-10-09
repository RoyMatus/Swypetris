# Recurring release compliance review

This is the publication prerequisite owned by [POLICY.md](POLICY.md), alongside technical gates in [CI.md](CI.md). It adds no workflow, permission or publication authorization. Passing CI does not establish platform compliance or guarantee RuStore moderation approval.

## Review trigger

A **major game release** means a substantial release, regardless of semantic version: a feature/epic release; significant gameplay or user-flow changes; new distribution/update behavior; or changes to permissions, data handling, authentication, advertising, payments, third-party SDKs or resource licensing. Also perform a complete review before the first publication without a traceable completed review under this policy. Record the trigger; do not classify substantial changes as a patch to avoid review.

For a narrow maintenance patch with a completed baseline, review current source changes and reassess affected provisions, artifacts and publication materials against both platforms. Record the baseline and scope justification. Changed rules or uncertain impact expand the review; use a complete review when the extent cannot be established. Mandatory rules apply to every submission, including patches.

## Current sources

Before every complete review, **open and read the entire current RuStore requirements** and open and review all GitHub sources below, including linked policies for products actually used. Previous reports, snapshots, this checklist and memory do not replace current sources. Record URLs, review date, published effective/update dates when available, exact sections and changes. An inaccessible required source is unverified, never compliant.

| Source | Scope |
| --- | --- |
| [RuStore application requirements](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps) | Complete text and applicable submission/console provisions |
| [GitHub Terms of Service](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service) | Repository, history, content, APIs and linked product terms |
| [GitHub Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies) | Content, conduct, rights, privacy, security and service use |
| [About releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases) | Assets, current limits and relevant recommendations |
| [Licensing a repository](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository) | License information and rights; hosting alone does not require an open-source license |

Use one [report](templates/RELEASE-COMPLIANCE-REPORT.md) for both platforms. Enumerate applicable requirements separately and justify excluded sections. Distinguish **mandatory platform rules**, **official recommendations**, and **project checks**. Status: `compliant`, `violation`, `not applicable` with reason, or `unverified`. Recommendations additionally need an adopted/deferred-with-reason/not-applicable decision; do not automatically make them platform obligations. GitHub Releases is distribution, not an app-store approval process.

## Coverage and evidence

The report indexes these inspection areas; it cannot replace the complete source rules:

- Code, gameplay, fresh-install launch, network/update behavior (including immediate update demands), navigation and saved state.
- Merged manifest, permission rationale and denial behavior; dependencies/SDKs and transitive behavior; advertising, payments, accounts and user content when applicable.
- Code/music/images/fonts/other resources: origin, rights, license obligations and notices; content and age classification.
- Actual privacy/data flows, policy and declarations; external endpoints and security; repository history and intended assets for leaked credentials, signing material or personal data. Redact evidence; never copy secrets into a report.
- Compatibility, stability, relevant portrait sizes/font scales; exact signed release artifacts, package/versions, certificates, archives, hashes and actual checks. Use proportionate checks from CI.md, not an invented device matrix.
- Repository documentation, release notes, current asset limits; listing, screenshots, contacts, live privacy URL and console declarations. Repository files cannot prove live console/external-service state.

Record exact full SHA, filenames, SHA-256, package, versionName/versionCode, public signer fingerprints and destinations. Cite inspected submission files and logs, not just a branch or green debug build. Retain diagnostics in ignored output; commit a redacted report or link to appropriate restricted evidence.

## Publication decision

Publication requires documented reviews for **both** platforms and resolution of applicable mandatory violations and unverified mandatory items. Shared content must satisfy both; destination-specific packaging/metadata must satisfy its applicable rules. Reassess each fix against both platforms. Existing technical signing/regression gates remain required independently.

Correct confirmed violations within the assigned scope and record actual proportionate verification. External console settings, declarations, unavailable services and material license/product/legal decisions require explicit release-owner resolution; assign remaining actions and keep publication blocked. For apparently incompatible rules, record exact provisions/evidence, investigate a simultaneous solution and escalate unresolved conflicts before publication. Never silently waive a platform rule.

If artifacts, relevant settings, requirements or submission metadata change after review, invalidate affected findings, record the new identity/change and reassess affected provisions against both platforms. A new hash alone does not validate old findings. Blank findings or unresolved blockers make the report incomplete.

Source links reviewed for this process document on 2026-10-09. This is **not** a compliance verdict on the game or a completed release audit. Issue #215 owns the existing rejection; this process does not resolve it or authorize a release.
