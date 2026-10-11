# Epic #187 development review

Prepared after the epic release merge on 2026-10-11. The owner requested ongoing analysis and a report after every epic merge; AGENTS.md now requires that workflow. This report distinguishes observed event intervals and checks from unavailable active-work time.

## Scope and evidence

Five feature tickets were merged on 2026-10-10: #190/#191/#194/#221/#193 via PRs #220/#222/#223/#224/#225. They complete the remaining background, victory, absolute postcard, DROP audio and APK-size work. Earlier epic tickets are recorded in the checked epic checklist. Yesterday's handoff, PR descriptions, Git timestamps, current API check-run timestamps and downloaded signed artifacts are the evidence; prior narrative claims are attributed to those records, not presented as new local tests.

| Ticket / PR | PR creation → merge (Moscow) | Elapsed PR interval | Final required `android` job |
| --- | --- | --- | --- |
| #190 / [#220](https://github.com/RoyMatus/Swypetris/pull/220) | Oct 9 12:16:01 → Oct 10 17:35:28 | 29 h 19 m 27 s; spans overnight | 746 s / 12 m 26 s |
| #191 / [#222](https://github.com/RoyMatus/Swypetris/pull/222) | Oct 10 17:52:03 → 19:12:47 | 80 m 44 s | 801 s / 13 m 21 s |
| #194 / [#223](https://github.com/RoyMatus/Swypetris/pull/223) | Oct 10 19:24:03 → 20:25:59 | 61 m 56 s | 783 s / 13 m 03 s |
| #221 / [#224](https://github.com/RoyMatus/Swypetris/pull/224) | Oct 10 20:26:44 → 21:19:08 | 52 m 24 s | 441 s / 7 m 21 s |
| #193 / [#225](https://github.com/RoyMatus/Swypetris/pull/225) | Oct 10 21:23:44 → 21:51:30 | 27 m 46 s | 788 s / 13 m 08 s |

The five final required jobs total 3,559 seconds = 59 m 19 s. This is a lower bound on CI computation, not total waiting or implementation time: earlier attempts and independent signed-release checks add work, while Sonar and release jobs can overlap. The interval from first to last Oct 10 merge is 4 h 16 m 02 s, not a measured working day. #190 began the previous day. Creation/merge times exclude pre-PR work and include waiting; there is no trustworthy full-day active-coding breakdown.

## Where time went

- Rendering scope was substantial: approved storyboards, generated foreground artwork, three fireworks phases, portrait/font-size fixtures, fixed controls, postcard generation and native sharing. These are documented changes, but time spent generating images or editing code was not measured.
- Final full Android instrumentation in PR #220 was 391.488 s (154 passed/two skips), #222 397.811 s (155 passed/two skips), and #223 396.590 s (156 passed/two skips). Counts grew as meaningful regressions were added. Picker and victory/theme/sharing preflights ran separately (1+3, 1+13, then 1+14); these deliberately repeat some scenarios and must not be described as extra unique tests.
- #224 used selected Android scope (14 passed) instead of full UI regression; 13 phone feedback/music cases passed in 102.203 s. Its independent release verification also ran: regression 271 s, signing/runtime 357 s, instrumentation 653 s. Those jobs overlap; summing them does not give elapsed wall time. Audible DROP acceptance came from the owner, not recorder assertions.
- #225 had successful full CI before a finishing measurement-report commit, then another final required full CI (788 s). The PR documents identical app/build inputs. Preparing reports before the final push would avoid this particular repeat while preserving final-commit checks.
- Emulator disappearances caused failed attempts and diagnosis. The release handoff records runs 38077632381 and 38078470440, successful JVM/preflights, ADB refusal and no confirmed OOM/native dump cause. The retained ANGLE backend subsequently passed required CI; it does not prove a universal root cause. #225 additionally retried only its invalid SDK-image-download job. Failed/partial tests are not passes.
- Publication preparation includes exact-key signing, archive/hash checks, resource byte comparisons and current platform review. On resumption, current RuStore source sections disagreed with the old report; references were reassessed against all twelve current sections. No evidence establishes the date of that source change. This rework is a reason to retain dated source snapshots and requirement mappings.

## Changes implemented during resumption

[PR #228](https://github.com/RoyMatus/Swypetris/pull/228) adds FocusedJvm/FocusedAndroid, rejects empty/wrong/stale reports, times runs, records commit/environment and labels cached JVM results. [PR #230](https://github.com/RoyMatus/Swypetris/pull/230) fixes VibrationSettingsTest selection and current inventory documentation. [PR #232](https://github.com/RoyMatus/Swypetris/pull/232) reuses daemon/cache locally, verifies both signing roles, restores signing environment after failure and adds the epic-review rule. Required checks and test assertions remain enforced.

Actual local observations on this Windows computer:

| Situation | Scope / result | End-to-end time |
| --- | --- | ---: |
| First focused JVM invocation | BackgroundCropTest: 3 passed; included compilation | 108.786 s |
| Focused JVM, two classes | BackgroundCropTest + GameEngineTest: 15 passed | 14.397 s |
| Fast after filtered JVM selection | Debug build/lint/JaCoCo, all 149 JVM passed | 179 s |
| Repeat unchanged Fast | 149 prior JVM results reused, UP-TO-DATE; no new execution | 6.680 s |
| First focused Android | SmokeTest: 1 passed, isolated API 35; included compilation/install | 137.890 s |
| Focused settings Android | VibrationSettingsTest: 1 passed, same emulator | 19.401 s |
| Local automation | 37 Pester passed (18 API + 12 verification + 7 release); 52 Python discovered/51 passed/one platform skip | Pester 4.31 s; Python 0.766 s |
| Original warm signed 1.4.14 builder | APK/AAB, 149 JVM results reused | 36.187 s |
| Updated consecutive signed 1.4.14 builders | Correct original APP/UPLOAD certificates and identical artifacts; JVM results reused | 10.070 / 9.038 s |
| Explicit NoDaemon diagnostic | Both signatures passed; timing variation unexplained | 107.293 s |
| Signed local 1.5.0 build after version change | 149 JVM freshly executed/passed; release lint, APK/AAB signing checks passed | 277.039 s |

The warm build pair improved by 27.149 s / 75.0%, approximately 4.0×. This is a single measured comparison with matching application/Gradle/version inputs, not a guarantee or an isolated causal estimate for daemon versus cache. Cold compilation and changed version inputs are not comparable to cached runs. [Detailed benchmark](local-release-build-2026-10-11.md) includes parser and failure-fixture defects found and fixed during implementation. Actual signed 1.4.14 and 1.5.0 smoke passed menu/settings/new-game/pause/Continue locally; no duration was separately measured.

## Next improvements without reducing quality

| Improvement | Supporting evidence / expected benefit | Quality-preserving validation |
| --- | --- | --- |
| Focused local checks at meaningful implementation checkpoints, one final required PR run | Focused settings was 19.401 s; full CI is commonly 12–13 minutes. Expected fewer unnecessary broad runs; exact per-epic saving is unmeasured | Choose by risk, verify report/class/commit identity, retain final all-JVM/selected-or-full CI |
| Finish review, notes and measurement reports before the final push | #225 repeated full CI for report-only finishing work. Potentially one roughly 13-minute run in that observed pattern | Review the complete final diff locally, then wait for successful CI on its actual final SHA |
| Keep one owned worktree's caches through sequential tickets; reuse daemon | Warm release 36.187→9.038 s, identical artifact hashes | No clean/forced rerun by habit; retain fresh tests when behavior changes and explicit cache labels |
| Diagnose stale CI once progress diverges from recent comparable runs | Prior engine exits and SDK-image failure cost retries; cause partly unresolved | Save logs first, inspect active step, cancel only the owned stalled run; never treat rerun luck as defect diagnosis |
| Retain dated policy snapshots and machine-readable check/timing records | Cross-computer source mapping mismatch and missing active-work measurements | Hash/redact evidence, record date/SHA/run/attempt; do not store credentials or invent missing timings |
| Keep local compute for additional tests; use cloud only for enforced statuses/publication | This session's focused tests and signed builds ran locally; existing release evidence requires Ubuntu CI | Preserve branch protection and exact-main release proof; do not move extra local tasks into Actions |

No tests, preflight assertions, signing gates, Sonar gates or portrait restrictions were removed. Optimizing test bodies, weakening full release scope or changing runner enforcement requires separate evidence and is not implemented by this report.

## Final release evidence

- [Release PR #226](https://github.com/RoyMatus/Swypetris/pull/226) merged as `4dd3b3ea1a5959a799eb320a97f896be8955dd65`; epic #187 and all twelve children were independently confirmed closed. All epic checkboxes are checked. Project fields remain unavailable under the existing credentials; native closure is not a claimed Project field update.
- [Final PR CI 38127364391](https://github.com/RoyMatus/Swypetris/actions/runs/38127364391): `android` 812 s, Android step 541 s; raw full instrumentation 418.532 s, 156 passed/two opt-in skips/zero failures. Picker preflight 1 passed in 8.745 s, UI preflight 14 passed in 29.672 s. JVM XML: 149 passed; JVM/coverage step 17 s. The PR proof targets GitHub's synthetic merge SHA `adcd9ccb54ea55ef1bac8c9be0602a9741b1b0a7`, verified against the PR merge ref, rather than incorrectly equating it with branch head `c4273c4b5943cc7f57986a8c08d6ea02961d35ea`.
- [Exact main CI 38128435348](https://github.com/RoyMatus/Swypetris/actions/runs/38128435348): `android` 649 s, Android step 426 s; full raw instrumentation 329.226 s, 156 passed/two skips/zero failures. Picker/UI preflights passed 1/14 in 7.093/19.827 s. JVM XML: 149 passed; JVM/coverage step 9 s. Downloaded archive digests, configuration, complete 158-case source inventory, tested APKs and run/attempt/commit identity were independently validated locally. The faster main run has the same app/test inputs; timing variation is not attributed to a new optimization without evidence.
- [Main release 38128435349](https://github.com/RoyMatus/Swypetris/actions/runs/38128435349): regression 293 s, signed release 325 s, instrumented-evidence verification 683 s, publish 11 s; all succeeded. Instrumented verification waits for/reuses exact-main full Android proof rather than executing another full Android suite. Release regression has 149 passing JVM XML cases. Signed release smoke passed menu/settings/new-game/pause/Continue; portrait frames were inspected. Jobs overlap and are not summed into elapsed development time.
- [Published v1.5.0](https://github.com/RoyMatus/Swypetris/releases/tag/v1.5.0) is non-draft/non-prerelease, tagged to the exact merge SHA above, with matching release notes. APK `Swypetris.apk`: 35,443,724 bytes, SHA-256 `cd5e0a671bc220be4749a3ef7024d955119ceaa38ef9ede584b02d7dc6fd794d`; AAB: 36,894,790 bytes, SHA-256 `229d7b909918673a276124fe58aab344e5ceec1f4b034c8d5a652a2ef68cbb80` (retained evidence, not a public install asset).
- Original APP/UPLOAD certificates matched. Package `ru.itoltec.swypetris`, version 1.5.0/code 22, minSdk 24/targetSdk 36 and portrait manifest verified. APK/AAB manifests match the reviewed candidate. Packaged privacy/credits match canonical Git bytes; all 31 current image/audio resources and four native entries match. The only changed APK ZIP payload versus the earlier signed candidate is `META-INF/version-control-info.textproto`; final hashes were measured directly. Archive CRCs and GitHub APK/update/checksum consistency were checked locally before publication.
- All three public assets were downloaded without authentication and matched the previously verified main artifacts/API SHA-256 digests. Public `update.json` SHA-256: `02b4001c415e0e311ff08c8970c8668dfc8e9c013ab459b8efdd198e4fef99ca`; `SHA256SUMS.txt`: `adf9c69e46bd6cf3fa2cbba3e8d6d4d4af7aba0709eef91970beec23c769a37f`.
- Pixel 7/panther, API 37, explicitly selected Wi-Fi serial `adb-2A141FDH20079N-D963Rp._adb-tls-connect._tcp`: this exact publicly downloaded APK installed successfully with `-r`, without uninstall. Launch succeeded; portrait menu/settings were exercised and screenshots inspected; returned to menu without starting a new game. Swypetris remains installed. This is a physical-device install/UI check, not a claimed full instrumentation rerun or new audible DROP audition.

Raw logs, JSON, verified archives and device captures are retained in ignored `app/build/verification/` in the owned worktree. Artifact download/inspection overhead was not separately timed; final PR proof/report archives total 84,010,430 bytes. Post-merge documentation uses a separate PR and no Android build; it is merged after publication to avoid cancelling same-ref main CI. RuStore submission/moderation and #215 remain outside this GitHub release.

