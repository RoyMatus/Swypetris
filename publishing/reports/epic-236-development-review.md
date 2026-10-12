# Epic #236 development review

[Swypetris 1.5.1](https://github.com/RoyMatus/Swypetris/releases/tag/v1.5.1) was published on 2026-10-12 at 12:19:52 UTC from exact main `882388d44d8ca2ef75cdf021db5f43e7bbc48349` after [release PR #257](https://github.com/RoyMatus/Swypetris/pull/257). Public downloads, tag, notes, package/version and original certificate were independently verified. All five child issues are closed. Measurements below are observed records; unknown intervals are not estimated.

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
