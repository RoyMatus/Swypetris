# Efficient development with evidence

Run verification locally using [EXECUTION-PROFILES.md](EXECUTION-PROFILES.md). Diagnose before an epic, continuation on another machine or standalone ticket; share its profile and budget across agents. Missing local capacity blocks required checks and never authorizes cloud execution. SonarQube Cloud remains the explicit analysis-service exception. Exact-head local PR evidence and exact-main signed release gates remain mandatory. See CI.md and TESTING.md for scope.

## Prepare an issue or epic

Use the ready-to-fill [issue](templates/issue.md) and [epic](templates/epic.md) templates. State an English title, the concrete request/problem, observed evidence, unknowns, intended outcome, authorized scope and exclusions, execution order, measurable acceptance criteria, and checks selected for each behavior risk. Record RoyMatus as assignee/delivery owner while preserving the actual author and closure history. Assignment or a prepared specification never implies implementation. An epic lists ordered children, their boundaries, real technical dependencies, completion/release gates and evidence/report links. The epic's written queue and native sub-issue order must agree; schedule order alone does not create a blocking dependency. Close the epic only when required children and delivery gates are actually complete.

Choose Work Type, Priority, Testing and labels from evidence, not a default guess. Put unstarted open issues and epics in Backlog even when fully prepared; move to In Progress when execution starts and Testing when implementation is complete and required verification runs. Retain explicit On Hold until the owner resumes it, then use Backlog if execution has not restarted. Done means delivered; cancellation uses the factual `not_planned` reason and an explanation, never a false completion. Assign an approved target milestone only when one exists; use Unscheduled, Merged - unreleased, Historical - release unverified, or Not planned as applicable. A merge/closure timestamp does not establish release inclusion or date. Repository, timestamps, linked PRs, reviewers and parent/progress values may be derived by GitHub or inapplicable. Explain unavailable data; do not fabricate values to fill every cell.

Inspect with `tools/Update-Issue.ps1 -IssueNumber N -Inspect -IncludeContent` and `-ListOptions` when selecting options. Preview each supported mutation with `-WhatIf`, apply it, then re-read the issue and relevant Project fields through the same API; verify native parent/child order and issue state separately. Use `tools/Manage-PullRequest.ps1` for PR preview/merge. Final PR bodies contain `Closes #N`; preparatory PRs must pass `-KeepOpenIssueNumbers N` and preserve the issue's open state. If `ProjectAccessError` occurs, report it once and stop Project operations until credentials change; record unavailable fields in the issue summary. Do not infer Project automation or milestone delivery from an attempted write.

## One issue at a time

1. Inspect affected behavior and list the risk each selected check covers. Use focused JVM checks for rules and focused emulator checks for UI/platform integration at meaningful checkpoints on the selected executor. One owner schedules heavy checks; reuse valid evidence across agents. Tool/instruction changes do not justify an app build by themselves.
2. Keep compatible caches in the owned worktree through sequential issues. Preserve others' worktrees/processes. Do not clean or force rerun without evidence.
3. Before the final push, finish code, tests, review, PR text, notes and measurements belonging in the PR. Reuse valid unchanged local evidence. Complete the local gate for the final clean SHA and current main base; subsequent meaningful changes require applicable revalidation. Merge through Manage-PullRequest.ps1 with validated evidence; never dispatch cloud checks.
   For a preparatory PR that must leave an issue open, pass `-KeepOpenIssueNumbers <number>` to the merge script. It checks both closing keywords and native GitHub closing links immediately before merge. Reserve `Closes #N` for the completion PR.
4. While local checks run, prepare completion text and inspect evidence without modifying the tested commit. Do not start implementation of the next epic issue before merge/closure.
5. After a failure, preserve the full log and device snapshot, diagnose the cause, then verify the failed scenario and affected neighbors. No automatic retry-to-green. `Verify-Tests.ps1` captures bounded emulator diagnostics on device readiness/test failures; manual capture is also available:

```powershell
./tools/release/Save-EmulatorDiagnostics.ps1 -Serial emulator-5560
```

Each adb command has a ten-second bound. Snapshots contain selected device properties, app memory and host emulator process IDs/memory/CPU; they neither restart a device nor assert an OOM/root cause. They do not collect arbitrary environment variables, process command lines or global logcat. Full test logs remain necessary. Local failures retain the selected runner/device diagnostics.

Record the reason in the existing runner's JSON (omit sensitive details):

```powershell
./tools/Verify-Tests.ps1 -Suite FocusedJvm -TestClass BackgroundCropTest `
  -Reason 'Verify crop bounds after changing geometry'
```

## Optional time journal and concise completion

Detailed phase tracking is optional. Use `tools/release/Record-EpicWork.ps1` and the [execution log convention](templates/execution-log.md) when phase timing will answer a concrete question. Pass `-Epic` only for an actual epic; otherwise the helper writes an ignored `ticket-<issue>/work.jsonl`. One owner writes the journal and stops an active interval on leaving or waiting. Never reconstruct missing intervals from commits or check duration. Without a journal, active work and waiting time are Unknown; retain actual check durations from runner evidence. `CIWait` describes historical CI only and never authorizes cloud checks.

```powershell
./tools/release/Record-EpicWork.ps1 -Epic 187 -Issue 190 -Action Start -Phase Implementation
# Keep the returned Id. Stop when leaving this phase, including when waiting:
./tools/release/Record-EpicWork.ps1 -Epic 187 -Issue 190 -Action Stop -Phase Implementation -Id '<returned-id>'
# Standalone ticket, no invented epic identifier:
./tools/release/Record-EpicWork.ps1 -Issue 282 -Action Start -Phase Review -Context 'Diff review'
```

Allowed phases: Implementation, LocalChecks, Waiting, CIWait, Review, ReleasePreparation, Rework. Only one phase is active per journal. Stop and start at observed boundaries; forgotten/missing work is unknown, not estimated. Journals live under ignored `app/build/verification/epic-<number>/` or `ticket-<number>/`. Phase duration measures a recorded wall interval, not CPU utilization or guaranteed human attention. LocalChecks includes supervision/tool runtime; separate runner JSON records tool/check duration, test counts, SHA, freshness and failures. Do not add check durations to phase time or sum overlapping test/CI jobs into delivery elapsed time. Elapsed delivery time is first observed start to final observed stop only when both boundaries cover delivery; otherwise state partial/unknown.

Project #7 has no numeric time field (schema inspected 2026-10-13). If a journal was kept, `Get-DevelopmentTicketTime` provides a measured lower bound from completed Implementation, Review, ReleasePreparation and Rework intervals. `Complete` needs an independently reviewed claim that the journal covers all work; otherwise use Partial. With no trustworthy intervals, say Unknown, never zero. Check duration, active work, wait and delivery elapsed are different quantities and must not be added together.

Prepare one short English [completion comment](templates/completion-comment.md) during final verification. `tools/release/New-TicketCompletion.ps1` can draft it locally from explicitly selected runner summaries, local gate evidence and an optional journal; review the actual outcome before posting. Post once after protected merge and confirmed closure, then re-read the API. State delivered behavior, PR, actual selected checks/results and material limitations. Describe a failure, retry or optimization only if substantive; keep detail in local evidence. Do not require a per-ticket bottleneck narrative. Cancellation receives a decision summary, not an implementation claim; open/On Hold work receives no completion comment.

## Prepare the post-merge review

Select only this epic's actual JSON summaries, XML and CI run IDs; do not glob historical output indiscriminately:

```powershell
./tools/release/New-EpicReview.ps1 -Epic 187 `
  -SummaryFile 'app/build/verification/fast-<timestamp>.json','app/build/verification/release-<timestamp>.json' `
  -TestXml 'app/build/test-results/testDebugUnitTest/TEST-<class>.xml' `
  -RunId 38127364391,38128435348,38128435349
```

The generator produces a new ignored English draft, never overwrites a public report and never writes to GitHub. It retains failed checks, fresh/cached distinction, dirty/SHA identity, incomplete intervals and selected CI run/attempt identities. CI jobs are paginated. Job durations are not queue time or elapsed epic time; overlapping jobs and repeated test cases are not summed into unique coverage. XML profiling lists the slowest measured cases and excludes absent timing rather than inventing it.

Review selected evidence and write one concise `publishing/reports/epic-<number>-development-review.md` after implementation merges; link it from the epic. Focus on cross-ticket outcomes, actual check costs, substantive failures and limits. Use an optimization section only for a demonstrated opportunity; give evidence, predicted benefit and validation preserving assertions, isolation and required gates. Compare matching successful scope/environment when enough observations exist. Do not claim a guaranteed percentage from one warm build pair or repeat ticket comments.

After independent verification of the public release assets, prepare one documentation PR containing the exact-artifact release compliance report and epic development review when all evidence is ready. Verify the reports and epic link together. Pull main after each merge. Install on a physical phone when the changed behavior, update compatibility or release acceptance requires it; a documentation or local-tool-only merge does not require a phone install.

## Verify the published release locally

```powershell
./tools/release/Verify-PublishedRelease.ps1 -Tag v1.5.0 -ReleaseRunId 38128435349 `
  -JavaHome 'C:/Users/pilig/.jdks/jbr-21.0.11'
```

This read-only command resolves the tag (including annotated tags), checks publication provenance bound to the release commit, downloads all three public assets without authentication, checks API size/digest and checksum entries, validates update metadata against the APK package/version, and reuses the existing original-certificate signature verifier. Logs/downloads/JSON stay ignored. A failure is never a passed summary. It performs no build, installation, instrumentation, publication or Project operation and does not replace the existing exact-main proof gate or manual device/store acceptance. For locally published releases use `-LocalProvenance`; retain `-ReleaseRunId` only for historical cloud publications. PowerShell 7, Android build-tools and a JDK are required for native APK verification.

Implementation references: [PowerShell web downloads](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/invoke-webrequest), [JSON parsing](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/convertfrom-json). Consult a new, changed or uncertain API contract once and reuse that research until its scope changes.
