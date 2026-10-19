# Local delivery without repeated work

This workflow implements the approved epic308 retrospective. Existing exact-head/current-base PR gates, exact-main signed gates, signatures, strict dependency verification, Sonar and device acceptance remain mandatory. No productivity percentage is established.

## RuStore: prepare, attempt once, reconcile

`tools/release/Invoke-RuStoreDelivery.ps1` takes explicit `PlanPath`, `ReceiptPath`, `StateDirectory` and (except Prepare) `VersionId`, private `ProviderPath` and `CredentialPath`. The provider stays outside Git; the tool reuses its protected token refresh without widening its read-only allowlist. Never include credentials in plans, logs or PRs.

Use the reviewed candidate plan/receipt contract from the final epic308 release: exact-main signed provenance; compliance, installed-candidate/icon, screenshot, manifest/SDK and public-privacy evidence; hash-bound APK/icon/screenshots; concise Russian whatsNew, description and explicit declarations. The canonical icon checksum is deliberately pinned in the candidate validator; an authorized icon change requires reviewing/updating that contract rather than accepting arbitrary artwork.

1. `-Action Prepare`: validate local proof and published GitHub APK/version identity. No store writes.
2. Create or select the exact owned MANUAL/100% draft in the console. Inspect/clear stale files before uploading. Write a local clearance JSON containing `versionId`, the returned `planSha256`, and `emptyDraftObserved: true`; this records an actual console observation, not an API-empty-array inference.
3. `-Action Inspect`: reconcile both endpoints. `-Action Upload -ClearancePath <file>` uploads once. An exclusive attempt file is flushed before HTTP. Timeout, rejection or interruption blocks repetition, even when the server package list is empty. Bounded HTTP/API rejection details are saved privately, with credential-bearing responses suppressed and prior errors retained. Resolve the actual console/server state; never delete the attempt marker to retry.
4. After an acknowledged upload, `-Action UploadMedia` uploads reviewed icon/screenshots once each. Ambiguous media writes also retain individual attempt markers. Inspect existing inherited screenshots; do not silently accumulate old media.
5. `-Action Verify` anonymously downloads the exact materialized package from its observed official CDN path, with byte/time bounds, then checks one main package, exact bytes, metadata and media. Optional `StoreArtifactPath` selects a new download destination, never a pre-existing unbound file. Only the observed RuStore outer ZIP layout (one APK and optional bounded dexopt profiles) is accepted; verification streams the APK without extracting arbitrary paths. Unknown layouts require inspection. A hidden DRAFT can use this tool's acknowledged one-time upload and exact server version, but is explicitly not reported as remote byte verification.
6. Review the saved detail's permissions, privacy categories/rationale and SDKs against the candidate evidence. Mark the generated review candidate `reviewed: true` without changing its binding. `-Action Submit -ReviewPath <file>` requires fresh matching values. Acknowledgement is not moderation approval.
7. Once both endpoints actually report READY_FOR_PUBLICATION, run Verify on the materialized server artifact again, review, then `-Action Publish -ReviewPath <file>`. Verify ACTIVE and the public catalog/version/icon/notes separately. No pending/unknown state authorizes publication.

Reuse the same state directory for the same version. Preserve failed attempt records. A recovered console upload does not become a fabricated API acknowledgement. The existing version2064894596/#330 handoff remains authoritative; do not replace it with a new upload merely to adopt these tools.

Official contracts checked2026-10-19: [APK upload and hidden drafts](https://www.rustore.ru/help/work-with-rustore-api/api-upload-publication-app/apk-file-upload/file-upload-apk), [v2 detail](https://www.rustore.ru/help/work-with-rustore-api/api-upload-publication-app/app-version-detail). Undocumented/ambiguous server state requires console inspection.

## Fail before expensive stages

The PR runner checks compatible SCM/worktree volumes before building. After assembling/verifying reports, it checks the installed Swypetris signer before Sonar and installation. A mismatch requires a dedicated debug emulator or an explicitly authorized reset of only the owned emulator's test app; no automatic uninstall was added. Main promotion also checks SCM compatibility before staging reports.

Sonar uses the explicit local JDK with `sonar.scanner.skipJreProvisioning=true` and `sonar.scanner.javaExePath`, retaining analysis and Quality Gate. These properties are supported by the pinned Gradle scanner7.3.1 ([Sonar documentation](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/analysis-parameters/parameters-not-settable-in-ui)).

After a Gradle/toolchain change, run `tools/release/Test-IdeGradleSources.ps1 -JavaHome <jdk> -RefreshDependencies`. It resolves the current distribution's source ZIP under STRICT verification. Review new publisher hashes independently; never generate/accept verification metadata automatically. Ordinary unchanged tickets do not need a forced dependency refresh.

Use focused behavior and neighboring UI checks before the final gate: fruit geometry includes HUD layering; theme gestures include navigation and sound; fixture/resource changes include lint/detekt. This short feedback loop does not replace the final required gate. Preserve compatible caches and passed eligible checkpoints.

## Evidence retention and worktree completion

`Delivery-Evidence.psm1` exports `Save-DeliveryEvidence`, `Test-DeliveryEvidence` and `Save-WorktreeEvidence`. The first archives an explicitly selected bound evidence manifest plus optional release artifacts. The latter retains all verification/dist files and APK/AAB files from a worktree, creates/verifies an original-source Git bundle, and retains a source ref. Destinations must be new; failed partial archives are not permission to delete originals. Archives remain private local files unless separately reviewed for publication.

Run `Complete-Worktree.ps1 -Path <owned tree> -ArchiveDirectory <verified archive>` for a live preview. It checks repository registration, exact merged PR head or main ancestry, open PRs, tracked/untracked changes, protected artifacts and unknown ignored files. Squash-merged source is retained independently of main ancestry. It rejects the current tree and requires a matching archive/head/bundle. The owner must establish no running process or in-flight release uses that tree before supplying `-ConfirmInactive`; a preview without this observation is ineligible. Only `-Remove` performs `git worktree remove` without force, after fresh checks; branches are retained separately. Never archive keys or discard unknown local configuration to obtain clearance.

Completion: protected merge and closure, portable evidence verified, cleanup preview reviewed, unused merged worktree removed when safe. A worktree needed by an in-flight release or failed check is retained, with its reason stated. No automatic destructive post-merge hook is introduced.

Removal enables Git for Windows long-path support only for that command (`-c core.longpaths=true`), without forcing deletion or changing global settings. A partial native removal must be diagnosed against both filesystem and Git registration; no blind removal retry or automatic fallback deletion is performed. See the [Git for Windows FAQ](https://github.com/git-for-windows/git-for-windows.github.io/blob/main/content/faq.md).

## Measurements and human acceptance

`Measure-AndroidChecks.ps1 -Serial <explicit emulator> -JavaHome <jdk> -SdkDirectory <sdk>` runs three comparable focused checks, stopping on failure. Opt-in profiles separate preview setup/update/capture/compare and native-audio setup/load waits. Files use AGP's additional test output directory, copied to the host before normal APK cleanup, and a unique run ID independent of device clocks. The collector requires matching files and exactly980 captures/784 comparisons. It does not shorten native playback, change waits or prune matrices. Existing XML and runner JSON retain case counts, stage durations, reuse origins and failures; active coding time remains unknown unless recorded.

Choose an optimization only after repeated matching profiles identify overhead. Require the original assertions/matrix, meaningful defect detection and matching before/after environment. Profile exports are measurements, not a passed regression by themselves.

For subjective audio acceptance, `New-ListeningHandoff.ps1 -ProvenancePath <signed proof> -OutputPath <new local JSON>` binds an unaccepted listening checklist to the exact APK. Record real device/route/volume/result before final release preparation. Global owner acceptance remains valid when explicitly given; never invent per-theme/headphone outcomes. Automated gain measurements do not establish perceived quality.
