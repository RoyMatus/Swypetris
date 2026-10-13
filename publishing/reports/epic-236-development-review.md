# Epic #236 development review

[Swypetris 1.5.1](https://github.com/RoyMatus/Swypetris/releases/tag/v1.5.1) was published on 2026-10-12 at 12:19:52 UTC from exact main `882388d44d8ca2ef75cdf021db5f43e7bbc48349` after [release PR #257](https://github.com/RoyMatus/Swypetris/pull/257). Public downloads, tag, notes, package/version and original certificate were independently verified. All five child issues of that historical delivery are closed. Measurements below are observed records; unknown intervals are not estimated.

## Delivery

Five issues completed sequentially: #192 reconciled existing delivery; #211 / PR #253 clarified result/archive labels without changing stored comparison semantics; #234 / PR #254 made the starting-level explanation readable at narrow width and large fonts; #248 / PR #255 replaced the authorized fireworks on both victory screens/postcard and provided durable motion evidence; #205 / PR #256 reconciled regression selection, portrait fixtures, inventory and bilingual documentation. Local Windows verification migration PR #252 preceded this sequence. Release preparation PR #257 sets 1.5.1/code 23.

Builds/tests use one local Windows Gradle invocation, max-workers 4 and one explicitly selected headless API 35 emulator on an 8-CPU/~47.87 GiB host. SonarQube Cloud is the existing explicit exception, with scanner execution local. Actions is disabled; no optional cloud dispatch, WSL or hosted build was used in this delivery. Connected Pixel 7 was not used.

## Verification measurements

| Gate | Test evidence / reuse | Gate seconds | Unique Android cases |
| --- | --- | ---: | --- |
| Local migration / PR #252 | Python 120; Pester 121 passed / 2 optional Unix skips; JVM 152 reused | see retained migration evidence | 174: 171 passed / 3 skipped |
| #211 / PR #253 | JVM 152 reused; quality, Sonar, focused Android | 137.732 | 15 passed |
| #234 / PR #254 | JVM 152 and JaCoCo; quality, Sonar, focused Android | 236.514 | 18 passed |
| #248 / PR #255 | JVM 154 fresh; full Android; picker 1 and UI 14 separate repeated preflights | 799.911 | 175: 172 passed / 3 skipped |
| #205 / PR #256 | Python 120; Pester 121 passed / 2 optional Unix skips; JVM 154 UP-TO-DATE; full Android | 741.802 | 175: 172 passed / 3 skipped |
| Release PR #257 | JVM 154 fresh; quality/Sonar; full Android, repeated picker 1 and UI 14 | 800.967 | 175: 172 passed / 3 skipped |
| Exact main release gate | Python 120; Pester 121 passed / 2 optional Unix skips; JVM 154 reused; quality/Sonar; full Android | 827.581 | 175: 172 passed / 3 skipped |

Final #248 Android duration was 545.937 seconds; #205 548.565 seconds. Framework progress reports can show 178 events including skips; XML contains 175 unique cases. Separate preflights are repeated executions, not additional unique coverage. Capture is opt-in and intentionally skipped in the normal regression. The other normal-run skips are EnergyScenarioTest.gameplay (explicit energyScenario flag) and SignedUpdateScenarioTest.liveGitHubDownloadChecksSignerBeforeInstaller (explicit signed-update fixture required).

Focused #234: 2 passed in 40.502 seconds. #205 selector regression reproduced three failing subcases before the selection fix; 45 focused tool cases passed in 0.474 seconds afterward. That focused checkpoint was reused, followed by the required final PR tool gate. No additional human aesthetic/listening assertion was added.

## Firework measurements and limitations

See [committed #248 evidence](../evidence/issue-248/README.md) for actual screen recordings, decoded phase frames, postcard, sample data and phase-wrap proof. On the same software-rendered API 35 emulator and capture harness, fixed-phase median milliseconds/render before → after were: round small 2.100 → 1.188, round large 3.318 → 1.580, absolute small 3.028 → 1.342, absolute large 3.953 → 1.817, postcard 3.806 → 1.630. Each fixture used 30 warm-ups and five samples of 60 renders. ART process-wide allocation measurements include harness work; no zero-allocation claim is made.

Actual recorded screen gfx timings remained high: round p50/p90 81/109 → 77/97 ms, absolute 81/105 → 81/101 ms; jank approximately 85–89%. This is one sequential before/after pair on software graphics. It does not establish physical-device FPS, battery savings or causal real-device improvement. Selected decoded frames were inspected; full video playback and human aesthetic approval are not claimed.

## Failures, waiting and rework

- A network/ADB block interrupted capture. Windows firewall block-rule presence was observed, but the listed SID differed from the current shell. No global firewall rule or unrelated service was changed. Connectivity later returned; the cause is unknown. Failed runs/diagnostics remain ignored.
- Two baseline capture attempts stalled at ActivityScenario.launch. A jdb/JDWP stack on the existing owned test process located Instrumentation.startActivitySync waiting for idle. Main rendering continued (835 frames observed); blaming a frozen GPU would be unsupported. Switching ANGLE to software alone did not resolve launch.
- The capture harness now starts the actual Activity asynchronously, observes resumed/focused lifecycle on main with bounded timeouts, records production motion phase and explicitly destroys its owned Activity. Both final before/after captures passed, with no debugger attached during measurement. This avoided changing the production clock or adding ActivityScenario idle assumptions to a continuously animated screen.
- Earlier prototype motion/phase assertions and soft-keyboard save synchronization required focused correction; diagnostic failures are not counted as passes. Consecutive phase comparisons accommodate complete extinction, and the save test closes the IME before scrolling.
- A stale local origin/main was caught against the GitHub API before release edits. An explicitly checked fetch and fast-forward included #205; no reset or user-edit loss occurred.
- Windows SSH authenticated and transferred the release branch, but its close path hung. Remote PR head was independently verified. An attempted stop raced with process exit. The main fetch used the existing authorized HTTPS credential after remote merge identity verification; shared SSH configuration is preserved. No speculative API-module change was made: the reproduced 403 read correctly threw.
- Supplied named fine-grained PAT authenticated RoyMatus but lacked required repository writes. Existing generic GCM credential for the same account successfully completed authorized PR writes. No rights were expanded or credentials copied into tracked files. Project operations stayed stopped after prior insufficient scope; native issue state is verified instead.

Active implementation time is incomplete. Recorded #211 implementation: 422 seconds; #234: 76 seconds; initial #248: 132 seconds; #205: 1457 seconds; release preparation: 1514 seconds. These are journal intervals, not complete task durations. Older 2470-second LocalChecks interval includes an unobserved gap and is excluded from continuous active-work totals. Additional capture diagnosis/rework/waiting intervals are unrecorded and unknown. Required gate elapsed times include builds, installs and checks; they must not be added to overlapping active work or used as total epic elapsed time. No Actions queue/run duration is invented for this local execution.

## Concrete improvements

| Improvement | Supporting evidence | Expected benefit | Quality-preserving validation |
| --- | --- | --- | --- |
| Keep compatible caches in the owned worktree | #205 reused 154 JVM results with unchanged inputs; quality stage 37.632 s versus #248 118.861 s with different work | avoid unnecessary compilation; no causal cold/warm speedup claimed | retain Gradle input invalidation, full test inventory and exact-head evidence; rerun when inputs change |
| Check native Git exit codes and compare main SHA before branch creation | stale origin/main caught before release edits; Windows SSH close anomaly | prevent rework on stale base and ambiguous publication identity | checked fetch, API SHA comparison, clean current-main assertions and no protection bypass |
| Use bounded lifecycle launch for continuously animated capture | stalled startActivitySync stack; renderer continued; final asynchronous captures pass | prevent indefinite capture idle waits | actual Activity focus/resume/destroy, finite timeout, real phase-wrap proof and unchanged production clock |
| Keep capture opt-in and reuse recorded completed checkpoints | default full regression retains objective tests; paired clips generated separately | avoid repeating expensive evidence export in every routine gate | normal regression retains phase/bounds/state assertions; rerun capture for relevant renderer changes |
| Profile slow tests before changing synchronization | full Android approximately 546–549 s, XML timings available | identify actual setup/case bottlenecks instead of weakening assertions | use preserved slow-case XML and diagnostics; keep storage isolation, portrait constraints, required full PR/main/release gates |
| Stop work recording during waits and retain failure summaries | partial journal and unknown capture gaps | improve future time estimates and reduce false productivity claims | observed Start/Stop boundaries, separate elapsed gate durations, never reconstruct unknown active time |

No additional speedup is claimed from unmatched scope or one before/after run. Human visual/listening judgments and unmeasured physical-device performance remain separate acceptance evidence.


## Enforcement limitation

GitHub enforces protected PR merges and immutable release tags. The selected local test evidence is enforced by repository scripts, not an independent GitHub attestation: UI/other API clients can bypass that local gate, and an operator who forges both reports/source is outside its protection. Delivery used the approved validating merge/publisher scripts; no bypass actors, force push, tag replacement or weakened checks were introduced.

### Measured slow cases for follow-up profiling

In the final #205 full Android XML, the longest recorded cases were MusicIntegrationTest.realMenuPlayerRepeatsThreeTimesAndKeepsPositionAcrossNavigation 97.505 s; NextQueueTest.previewIsIndependentOfActiveShapeRotationAndHeight 67.511 s; BorderlessHudTest.digitalScoreAlignsWithHintAndFruitsStayInRightLane 31.498 s; LineClearUiTest.shardsStayClippedAndSettleToTheExistingClearResult 27.770 s; UpdateCheckTest.failedAutomaticAttemptIsPersistedAcrossRecreationButManualCanBypassInterval 17.469 s; MenuSkyTest.bothThemesKeepArchitectureAnchoredAcrossPhoneCropsAndCloudLoops 10.966 s. XML case time excludes build/install overhead. These concrete cases are profiling targets, not authorization to remove real-repeat, geometry, clipping, persistence or clock coverage.


## Published release and final gates

The release is non-draft/non-prerelease, its protected tag resolves to the exact main SHA above, and its notes match the committed 1.5.1 notes. The public APK is 35,443,848 bytes, package ru.itoltec.swypetris, versionName 1.5.1/versionCode 23, SHA-256 `654b52e865809e96f56e763b32435521b8cff64b5e3a36ae9843edbbe24671b2`. Original APP signer SHA-256: `04e8937a933bd53c80ae16f450bdda86e97e8addeabbd15ce8814eb9f42b66dc`. APK, update.json, SHA256SUMS.txt and local-release-provenance.json were downloaded without authorization headers and checked against API digests/local proof. AAB remains local, upload-key verified; no RuStore submission occurred.

Exact-main full Android took 628.774 seconds versus the release PR's 559.797 seconds. Same 175-case scope passed with regular test progress; the cause of the timing difference was not measured. No speedup/regression attribution is inferred from this pair. Main quality stage took 4.039 seconds with valid cached JVM/Gradle results; Sonar took 83.899 seconds. Repeated picker/UI preflights took 27.470/53.124 seconds; they are not new unique coverage.

The independent exact-main signed release gate took 60.748 seconds, including APK/AAB original-certificate checks, release lint/JVM reuse and actual menu/settings/new-game/pause/Continue smoke on the owned emulator. Pre-merge signed candidate build took 126.993 seconds; its APK/AAB identities were independently compared to the main candidate and matched. Public-download verification passed in 23.287 seconds. Whole publication preparation/transfer elapsed and complete active report-writing time are unknown; they are not inferred from the release timestamp.

One publication WhatIf failed before any write because the fresh PowerShell process had neither JAVA_HOME nor Java on PATH. Direct apksigner diagnostics reproduced exit 1; explicitly selecting the existing JDK changed exit to 0 with the correct original certificate. The subsequent WhatIf and actual publisher passed on unchanged artifacts. Operators should configure the process JDK before invoking the current publisher; an early explicit toolchain preflight would shorten diagnosis without weakening signature checks. No signing/source/gate workaround was introduced.

The [complete two-platform compliance review](release-v1.5.1-compliance.md) was prepared and approved locally before publication, then reassessed for actual exact-main artifacts. Public provenance regression evidence digest: `1c48e8c3daa33d85da3097abb1d13e712c98954e4c22fbf8a4a911627364edf7`. Detailed raw diagnostics and failed-run logs remain ignored; public proof contains only generic emulator identity and hashes/filenames, not host paths or physical-device identifiers.

Implementation PRs: [#253](https://github.com/RoyMatus/Swypetris/pull/253), [#254](https://github.com/RoyMatus/Swypetris/pull/254), [#255](https://github.com/RoyMatus/Swypetris/pull/255), [#256](https://github.com/RoyMatus/Swypetris/pull/256). Migration PR: [#252](https://github.com/RoyMatus/Swypetris/pull/252). The final documentation PR closes [epic #236](https://github.com/RoyMatus/Swypetris/issues/236) after this observed publication. Its own final merge/check duration is not known when this report is committed and is recorded in that PR's actual evidence rather than fabricated here. Owned worktrees/capture baseline/reference branch are retained for needed caches and diagnostics; unrelated local work was preserved.

## Reopened delivery: HUD, music and initial materials (2026-10-12)

This section records the later delivery without changing the historical v1.5.1 publication above. The epic was reopened; #259, #264 and #189 merged independently in [PR #266](https://github.com/RoyMatus/Swypetris/pull/266), [PR #267](https://github.com/RoyMatus/Swypetris/pull/267) and [PR #268](https://github.com/RoyMatus/Swypetris/pull/268). Their closures and Project Done values were read back. None is claimed included in the previously published v1.5.1 artifact.

| Issue / exact verified PR head | JVM evidence | Full Android unique results / runner seconds | Quality / Sonar wrapper seconds |
| --- | --- | --- | --- |
| #259 / 63c0a5cc28a1c1ba9b277693ba025964b311dfa6 | 154 valid reused cases | 177: 174 passed / 3 opt-in skips; 788.179 | 56.327 / 102.055 |
| #264 / 5cecd65b470678b4df38c15624ebf21a7a003b00 | 154 fresh cases | 179: 176 passed / 3 opt-in skips; 840.561 | 175.654 / 153.458 |
| #189 / 4996e4434423a5367e6349de5057e930e9065315 | 154 valid reused cases | 182: 178 passed / 4 opt-in skips; 783.067 | 7.834 / 72.012 |

Every listed final gate passed exact-head/current-base integrity and inventory validation, quality/lint/detekt/JaCoCo, Sonar and picker/UI preflights. Preflights repeat cases from full regression and are not additional unique coverage. #189 additionally passed Python 121 and Pester 128 / two allowed platform skips after its tool correction. Android wrapper times were 789.453, 842.882 and 783.671 seconds respectively; runner duration is separate from installation/preflight/build overhead. Differing code, caches and host load prevent causal speed comparisons.

Focused #259 HUD checks passed 11 cases in 107.825 s. #264's real-popup/AudioTrack regression passed 21 cases in 290.792 s, preserving genuine background/focus interruption behavior; no acoustic/headphone test is inferred. #189's scene-checked native rendering observations passed in 55.926 s (current) and 51.028 s (pre-change APK). Its shared SwiftShader timings and size limits are detailed in [the materials report](issue-189-materials-verification.md), with [all twelve comparisons](../design/block-materials-189/implementation/README.md). Initial victory-scene measurements were invalidated by visual review and are not gameplay performance evidence.

### Failure, rework and resource records

#259 exposed an asynchronous sharesheet Back leak; its test cleanup now waits for the real app window/root before restoring flags. #264's intermediate transfer timing briefly paused music; a real-player command audit rejected it and the final implementation uses explicit native transfer acknowledgements. #189's first clean gate passed application regression but failed evidence validation on the unregistered optional native measurement. The exact identity was registered; near-match skips still fail. Tool regression also required explicitly classifying the new shared atlas owner as full scope. Failed candidates were preserved, never promoted or bypassed. Earlier SDK/signing/style/report failures and emulator diagnostics remain retained.

Work journals record observed boundaries, not total reconstructed human implementation time. Some phases mix implementation, checks and artifact review; complete pure-category active time is unknown. On 2026-10-13 the report helper encountered locale-dependent date conversion with PowerShell 7.6. Running the unchanged helper with InvariantCulture parsed retained ISO journal timestamps successfully. No invented replacement timings or tool policy change were made.

Signed main was installed on the owner-requested Pixel 7 after #264 and #189. #189 installation used exact main ada8d174a5cdde32817379309ed2822d76b5813b, original APP signer, adb update install and exact installed-APK hash readback; build/install/verification elapsed 201.175 s and launch returned Status ok (cold start 240 ms). Package remains 1.5.1/code23 pending the separately verified new release. These are installation/launch facts, not instrumentation, phone FPS, listening acceptance or a published-release claim. Work paused at the owner's request afterward; no active work time is recorded for that overnight pause.

### Evidence-based improvements for subsequent tickets

| Evidence | Proposed improvement / expected benefit | Validation preserving quality |
| --- | --- | --- |
| #189 application gate reached its final validator before the exact opt-in registration omission surfaced | Review every new optional measurement identity and source classification before the final gate; catch known inventory errors earlier | Exact allowlist contract rejects near matches and required behavior skips; complete final gate remains mandatory |
| Initial #189 fixture accidentally measured Victory rather than gameplay | Assert scene/state and inspect native samples before collecting all themes; avoid invalid measurement work | Keep PLAYING/no-victory assertions plus actual frame inspection; retain rejected samples separately |
| #264 controller-only observation missed transient SILENT | Observe commands around real native window/AudioTrack transfers at development checkpoints; reduce later integration rework | Preserve popup, background, audio-focus and noisy interruption coverage, without loosening time/assertion bounds |
| #210's 29-case checkpoint had one obsolete version assertion, while 28 neighbors succeeded | Recheck failed class plus migration neighbors; reuse unchanged successful checkpoint evidence | Preserve version/comparability assertions and retain the complete mandatory clean-head gate |

These are concrete opportunities, not guaranteed percentage speedups. No successful unchanged full suite is rerun by habit; a changed head/base, test input or gate contract still requires applicable validation.

## Resumed scope (2026-10-13)

The live epic adds #269 connected atlas/fragments, #270 exact non-Classic audio and #271 full-opacity Hold after #210 and the independently published #215 current release. They do not block that release or earlier children's closure. Asset-storage branches are retained and must not be merged as application implementation. No extra automatic release is authorized for those follow-ups. The epic stays open until all required children complete.

#210 implementation and its development checks are described in [the gravity/reset report](issue-210-gravity-reset.md). The owner previously waived the additional final balance approval hold and authorized the one-time old-progress reset in the implementation conversation. This does not supply missing human gameplay evidence. Final PR/merge results are recorded in that implementation PR and consolidated issue comment; current-release publication remains #215 work.

### #210 completed delivery

[PR #272](https://github.com/RoyMatus/Swypetris/pull/272) merged as f7c11881dc20c04baf7d836daa585fb4b17cbc2b on 2026-10-13. Issue closure and Project Done were read back. Final clean head 2a0ae1f8f8e701760bb20061fc3bd71507c2f332 / base ada8d174a5cdde32817379309ed2822d76b5813b passed 154 valid cached JVM cases, lint/detekt/JaCoCo, local Sonar analysis with Quality Gate, one picker and 14 UI preflight cases, and 184 unique Android cases (180 passed, four registered opt-in skips). Android ran freshly in 784.251 s; gate wrappers measured quality 138.601 s, Sonar 157.018 s and Android 785.424 s. Runner progress printed 188 completions; the XML-backed validator reports 184 unique identities, so duplicate notifications are not additional coverage.

Earlier #210 failures and repairs are preserved in the implementation report. They include obsolete curve/history fixtures, an animation measurement starting before the first draw, a parent draw observer missing the child Canvas clock dependency, and native popup focus readiness before synthetic lifecycle callbacks. The first scene-ready checkpoint passed 13 cases in 195.307 s, but a later one-case probe still exposed the missing observation; that failure was not hidden by the earlier pass. The final observer fix retained the five-frame bound and real 600 ms timer; the focused native case and detekt passed in 28 s Gradle time with 11 recorded frames. CPU draw costs exclude asynchronous GPU work.

Before measurement, establish an actual drawn scene and observe the same state dependency as the measured rendering node; before synthetic lifecycle transitions, acknowledge the real host focus transfer. These concrete preconditions can prevent avoidable full-gate rework while preserving timing, completion and audio behavior assertions. Future gravity changes should include native GameTimerTest and JVM GameEngineTest at the development checkpoint, and future one-time migrations should audit clear/seed/model fixture ordering. No percentage saving or cold-versus-warm causality is inferred.

Primary main was pulled. Signed-main APK preparation took 139.713 s and verified the original APP certificate; the prepared APK SHA-256 is 76e87e9cb24b53a966f6528b6376196b5b4df3ce2eeffde15e6ec3d99e370d4f. Pixel installation remains pending at this report revision: the paired phone went offline and connections to its advertised endpoints timed out. Preparation is not installation or physical acceptance. The completed #210 gate is retained while #215 uses the same owned delivery worktree and compatible caches; no second heavy Gradle invocation overlapped the signed-main build.

## #215 preparation merge and release-tool repair — 2026-10-13

[PR #273](https://github.com/RoyMatus/Swypetris/pull/273) merged preparation/version 1.6.0 into main `8bcd94c6cc98e90b3cf74825a07b2c73153b5ca4`. The final PR gate bound head `6e1edd88006c5b55cf32c6c6eb2daa34c7efe50a` to base `f7c11881dc20c04baf7d836daa585fb4b17cbc2b`: quality 38.586 s, Sonar 60.660 s, picker preflight 29.708 s, UI preflight 64.234 s, full Android wrapper 704.306 s. The runner measured 703.643 s with 184 passes and four registered opt-in skips (188 unique test identities); 154 valid cached JVM results were retained. Signed preparation, fourteen focused update tests, actual online/offline/upgrade acceptance and their limits are detailed in the [compliance report](release-v1.6.0-compliance.md). No release is claimed here.

The initial exact-main Full gate stopped in tool tests before Android checks: 126 Pester passes, two failures and two registered Windows skips. Both failures were in journal/report integration. PowerShell 7.6.6's JSON conversion returns date objects; passing them to a string Parse API implicitly formats an invariant date, which ru-RU parsing rejected on October 13. The same conversion can also discard fractional seconds and offset information. The repair converts already-typed dates directly and parses strings with invariant culture. A focused 26-case workflow run passed in 4.23 s, including ru-RU/en-US, mixed UTC/offset JSON instants and fractional duration checks. Exact-main release verification must still pass after protected integration; the failed gate is retained and never reused as success.

The authenticated Context7 PowerShell documentation was consulted; its returned 7.7 reference documents timestamp conversion introduced in 7.5. The repair uses existing DateTime/DateTimeOffset APIs and does not require a 7.7 feature or change the host locale. One improvement is to retain post-twelfth-day, fractional and mixed-offset fixtures in both locales: these expose format assumptions without depending on today's date or weakening journal pairing checks. No time saving is quantified.

GitHub closed #215 after both preparation merges; each closure was promptly reversed and open/Testing state was read back. The first PR also contained a negated closing phrase, which was corrected, but attributing closure only to that phrase was incomplete: PR #274 contained no closing keyword and GraphQL still reported a native closingIssuesReferences link. [GitHub documents](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/creating-a-branch-for-an-issue) that an issue-created branch automatically links its PR; [linked PRs](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue) close the issue on default-branch merge. Evidence is retained in issue-215-closure-diagnosis.json. Future preparation merges must inspect both text and native closing relations and retain only a non-closing reference until publication; final completion merges still use the required closing relation. Primary main was pulled after both merges; Pixel installation remains pending connectivity.

## #215 verified GitHub release — 2026-10-13

[v1.6.0](https://github.com/RoyMatus/Swypetris/releases/tag/v1.6.0) is published from `f5b228d418a12534619a12ea865a3acf03096385`, version 1.6.0/code24. [Compliance and artifact evidence](release-v1.6.0-compliance.md) records the complete current RuStore/GitHub matrix, exact identities, first-launch/upgrade checks and remaining owner actions. The tag preserves the delivered #259/#264/#189/#210 behavior. #269/#270/#271 remain subsequent work and are not in this release. No RuStore upload/submission or physical acceptance is claimed.

| Measured stage | Result and duration |
| --- | --- |
| #215 focused update checkpoint | 14 native passes, 112.470 s; detekt/test APK assembly 50 s Gradle time |
| Signed candidate preparation | 246.234 s, 154 freshly executed JVM passes; APP and UPLOAD roles verified |
| PR #273 full native gate | 184 passes / 4 registered skips, 703.643 s runner; valid cached JVM results reused |
| Initial exact-main attempt | Stopped in Pester: 126 passes / 2 failures / 2 registered platform skips; no Android run started |
| Timestamp repair checkpoint / PR #274 | 26 focused Pester passes, 4.23 s; final gate 121 Python passes and 131 Pester passes / 2 platform skips |
| Corrected exact-main Full gate | 121 Python passes, 131 Pester passes / 2 skips; quality 4.299 s, Sonar 88.704 s; picker 29.439 s and UI 60.682 s wrappers; 184 native passes / 4 skips, 711.992 s runner (712.621 s wrapper) |
| Exact-main signed verification | 66.065 s including actual signed launch/settings/new-game/pause/Continue; signed build 11.828 s, 154 unchanged JVM results reused |
| Independent public verification | 16.839 s; public tag/commit, APK/original signer, metadata, checksums and provenance passed |

The byte-identical candidate/release APK allowed reuse of the signed offline and upgrade evidence, while the exact-main gate and independent public verification still ran. After publication, a fresh install of the downloaded public APK passed online first-launch/gameplay smoke with current public code24 metadata. No cloud CI runs were dispatched; Sonar was the sole remote analysis service. These stages overlap in covered behavior and are not additive unique coverage. Cache reuse explains recorded task outcomes, not a measured causal speedup between different scopes or hosts.

The signed upgrade probe initially assumed `userId` instead of this device's `appId`; a second probe concatenated text and description and missed the visible score. Both failures were retained and corrected without a production change. Reuse the existing fieldwise accessibility matcher and retain complete before/after package dumps in future upgrade probes; validate the same setting/migration/restart outcomes, rather than weakening assertions. Expected benefit is fewer probe-only retries; the saving is unmeasured. The exact-main gate's date failure likewise supports locale/offset/fraction fixtures, not changing the host locale to avoid a failing test.

Original-signer APKs for both subsequent main merges were prepared in the retained installer worktree (63.580 s for `8bcd94c`, 18.231 s for `f5b228d`) while Pixel connectivity was unavailable. Those two installer APKs share hash `816e707389b485e8586f28db976027e7ec254a7846131b6cfcd084328e578212`; the published APK has separately verified hash `ec03679581141bc1886960f386937ffd84f021dbd57ad6b878207e3bb1fd268a`. A ZIP-entry comparison found identical entry contents between the installer and release APKs; it does not equate their full-file hashes or claim an installation. The prior #210 prepared APK and all failed diagnostics remain retained.

The journal snapshot used for this report contains completed #215 intervals of Implementation 419.720 s, LocalChecks 774.623 s, ReleasePreparation 2403.456 s, Review 90.751 s and Rework 222.037 s. The current completion-report review interval is excluded while unfinished. These are explicitly recorded phase boundaries, not total development time; unrecorded work/waiting remains unknown. Runner durations above separately account for checks that continued after active-work recording stopped. Generated evidence draft: `review-draft-20261013-123426-917.md`.
