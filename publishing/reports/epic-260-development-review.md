# Epic #260 development review

Review date: 2026-10-14. Delivery owner: RoyMatus. Scope: local verification, trustworthy evidence reuse and the expressly approved update behavior. Implementation issues ran sequentially; the first process changes were merged before later branches were based on them. No GitHub Actions build/test run was dispatched. Builds, tools, instrumentation and signing ran on Windows locally; SonarQube Cloud remained the authorized analysis exception.

## Delivered scope

| Issue | Delivered result | PR |
| --- | --- | --- |
| #285 | Source inventory before Gradle, preparatory-PR closure protection, startup diagnostics and direct-install signer/hash preflight | [#287](https://github.com/RoyMatus/Swypetris/pull/287), correction [#288](https://github.com/RoyMatus/Swypetris/pull/288) |
| #286 | Evidence-based issue/epic templates, metadata/status rules, standalone journals and completion conventions | [#290](https://github.com/RoyMatus/Swypetris/pull/290) |
| #265 | One complete Android inventory without repeated picker/UI preflights; six retired workflow routing tests removed | [#291](https://github.com/RoyMatus/Swypetris/pull/291) |
| #282 | Hourly eligible launch checks, menu-version manual checks with a persistent rolling 59-attempt budget, server/SDK cooldown, explicit download/install choice and updated privacy copy | [#294](https://github.com/RoyMatus/Swypetris/pull/294) |
| #292 | Optional detailed journals, selected-evidence completion drafts, concise epic reporting and risk-based phone installation; obsolete Actions PRs #240–244 closed | [#298](https://github.com/RoyMatus/Swypetris/pull/298) |
| #299 | Schema-two identical-tree Full PR-to-main promotion, verified merge identity/report hashes and fresh main Sonar | [#301](https://github.com/RoyMatus/Swypetris/pull/301) |
| #300 | Every existing pixel comparison retained; detailed failure strings allocated only on a mismatch | [#302](https://github.com/RoyMatus/Swypetris/pull/302) |

#261/#262 are retained historical preflight work. #263 was canceled because the owner retained SonarQube Cloud. #289 was canceled after the existing logo matched the supplied reference; no artwork replacement was required.

## Selected measurements

One owner used one Gradle invocation, four workers and one explicit headless API35 emulator. The resumed host profile confirmed local capacity. The following are actual stage/runner measurements; they are not human active work, comparable benchmarks or additive epic elapsed time.

| Final gate | Selected evidence | Android stage seconds |
| --- | --- | ---: |
| #285 correction | 122 Python, 140 Pester passes/2 platform skips, 158 JVM, quality/Sonar; 193 Android passes/4 opt-in skips; separate picker/UI preflights still present | 906.546 |
| #286 | Tool/docs scope: 122 Python, 142 Pester passes/2 skips; no app build | N/A |
| #265 | 116 Python, 142 Pester passes/2 skips, 158 JVM, quality/Sonar; 193 Android passes/4 skips, including former preflight scenarios | 942.832 |
| #282 | 161 JVM, quality/Sonar; 194 Android passes/4 skips | 1102.434 |
| #292 | Tool/docs scope: 116 Python, 145 Pester passes/2 skips; no app build or phone install | N/A |
| #299 | 119 Python, 145 Pester passes/2 skips, 161 JVM results UP-TO-DATE, quality/Sonar; 194 Android passes/4 skips | 918.558 |
| #300 | 119 Python, 145 Pester passes/2 skips, 161 reused JVM, quality/Sonar; 194 Android passes/4 skips | 788.818 |

The #282 Android runner itself measured 1100.072 s and #299 measured 917.586 s; their enclosing gate stages include process overhead. Reused JVM results are not fresh executions. The #300 Android runner measured 787.649 s; its LineClear target case measured 9.660 s. The new Full evidence promotion and independent signed-release checks are recorded below.

Optional journals have zero unfinished intervals. Recorded active-phase lower bounds (partial coverage) were #285 765.129 s, #286 344.534 s, #265 227.221 s, #282 2075.428 s, and #292 611.526 s. These observed Implementation/Review/ReleasePreparation/Rework intervals include supervision and are not guaranteed attention time. #299/#300 active work and unrecorded preparation, gaps, waiting and total epic elapsed time are Unknown. No timings are reconstructed from commits. CI check count/duration for new builds/tests: zero, because none were scheduled; remote Sonar waits remain inside the local scanner stage.

## Profiling and rework

Retained #299 XML identified the real music-loop check at 98.394 s, NextQueue's shape-independence case at 89.026 s, and LineClear's shard/settlement case at 38.042 s. The music test retains its real repeat coverage. Removing redundant idle waits in NextQueue did not establish a useful gain (class 98.620 → 100.454 s; case 72.866 → 72.260 s), so that experiment was reverted.

LineClear's unchanged five-case class passed before and twice after the allocation change. The target case measured 32.916 s before, then 12.591 and 11.931 s; class runners measured 59.543, 49.929 and 38.085 s. This supports the bounded allocation change, but one baseline and two follow-ups do not establish a stable causal speedup. Same coordinates, themes, phases, reduced-motion and settled-result comparisons remain.

Retained failures were not counted as passes: #285 shared-cache quality failures and a signer parser that initially missed real versioned apksigner output required diagnosis/correction; #286 had an invalid nested-array test fixture; #265 first used the wrong Python import context; #282's asynchronous reservation change exposed six focused assertions, a shell lacked SDK configuration, and stale privacy text invalidated an early candidate; #299 invocations omitted the PR number and then SDK path. Corrected scopes and final exact-head gates passed. The first #300 full gate was interrupted at the owner's pause request on 2026-10-13; its partial logs were preserved and it supplied no passing evidence. The next-day run used a separate output directory but was stopped after ten UI failures. Its retained screenshot/window dump showed a SystemUI ANR dialog owning focus; event logs placed that dialog before instrumentation. The framework last-ANR query did not retain a matching record, so the underlying system hang cause is Unknown. An owned-emulator reboot removed the dialog; all failed classes and neighbors passed 25/25 in 100.249 s on the unchanged clean head before a new full gate. No timeout, assertion or app code was relaxed.

## Evidence-supported improvements

| Improvement and supporting observation | Expected benefit | Validation preserving quality |
| --- | --- | --- |
| Reuse Full PR reports only after verified identical-tree squash merge; #299 provides strict source/report checks | Avoid a second unchanged full instrumentation/tool run before release; actual promotion result below, no benchmark saving inferred | Verify merge identity, exact main/tree, immutable hashes, complete inventory and fresh main Sonar; independently verify signed artifacts/runtime |
| Keep a single full instrumentation inventory; prior correction spent 38.002 s picker + 69.492 s UI preflights in addition to full Android | Avoid repeated scenarios and process overhead; unlike run times do not quantify savings | Full XML must still contain picker, victory UI and SmokeTest cases; focused development checks remain available |
| Allocate visual assertion details only on failure; matching LineClear samples above | Reduce successful comparison allocation without reducing comparisons | Preserve equality predicates/coordinates and rich mismatch diagnostics; focused five-case and complete final regression |
| Fail early on an observed blocking system window; the resumed run continued into ten UI failures while a pre-existing SystemUI ANR dialog owned focus | Avoid spending instrumentation time in an unusable emulator; no time saving quantified | Check boot completion/window focus without dismissing errors or relaxing tests, retain diagnostics, and validate both blocked and healthy emulator cases |
| Test tool output contracts against real signed APK output; #285 parser correction is direct evidence | Catch certificate parser incompatibilities before delivery | Retain positive versioned signer and conflicting-digest rejection tests plus actual signed preflight |

## Release and evidence retention

[Swypetris v1.8.0](https://github.com/RoyMatus/Swypetris/releases/tag/v1.8.0), code26, was published from exact clean main `5742d207294ab23e6631379b7d21697b085522b3`. Full source evidence was successfully promoted to schema two after PR #302's verified identical-tree squash merge: source/destination tree `27d5286e66e5fb12e5b9623bc893c820b4a01cad`, fresh main Sonar stage 98.858 s. Existing Full Android/tool reports were reused, not re-executed on main. This demonstrates the reuse path, not a measured end-to-end time saving.

Independent signed-release verification passed in 111.940 s; the signed build took 16.199 s with compatible caches and reused JVM results. APK/AAB signatures and original certificates passed, followed by actual signed menu, Settings, new-game and pause/Continue UI checks on the isolated emulator. Final APK SHA-256 `cece0ad6229d574d9087d510bbf88b93081af393f1172f7c3cf8b38a57813ac4` equals the #282 candidate already used for the Pixel 7 1.7.0 → 1.8.0 in-place upgrade. Today's installed-file readback matched it and launch returned Status: ok; no redundant reinstall or subjective free-play verdict is claimed. The phone retains Swypetris.

The publisher preview passed before the explicitly authorized publication. `Verify-PublishedRelease.ps1` then independently downloaded all four public assets without authorization headers and passed in 18.969 s: tag/commit, APK hash/size/package/code26, APP certificate, update metadata, checksums and local provenance matched. Evidence: ignored `published-v1.8.0-20261014-073512-166/summary.json`. The AAB remains local. See the [exact-artifact compliance review](release-v1.8.0-compliance.md) for both-platform findings and retained RuStore-only limitations. No RuStore submission was performed.

Selected evidence is retained under ignored `app/build/verification/epic-260/`, earlier `local-20261013-*` final gates, runner JSON/XML and the immutable release snapshot. The review draft was generated with `New-EpicReview.ps1` from explicitly selected summaries and XML; no historical cloud runs were imported. Public reports omit machine paths/device identities. Local evidence is operator-controlled and the approved merge script enforces it; GitHub UI/independent API merges can bypass that local gate. No store moderation or subjective audio/visual acceptance is inferred.
