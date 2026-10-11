# Efficient development with evidence

Use local resources for implementation and additional verification. The existing required PR status and exact-main release gates remain mandatory. See CI.md and TESTING.md for actual scope; this workflow does not change them.

## One issue at a time

1. Inspect affected behavior and list the risk each selected local check covers. Use focused JVM checks for rules and focused emulator checks for UI/platform integration at meaningful checkpoints.
2. Keep compatible caches in the owned worktree through sequential issues. Preserve others' worktrees/processes. Do not clean or force rerun without evidence.
3. Before the final push, finish code, tests, review, PR text, notes and measurements belonging in the PR. Wait for the required check on that final SHA; any subsequent meaningful change needs its own final check.
4. While CI runs, prepare completion text and inspect evidence without modifying the tested commit. Do not start implementation of the next epic issue before merge/closure.
5. After a failure, preserve the full log and device snapshot, diagnose the cause, then verify the failed scenario and affected neighbors. No automatic retry-to-green. `Verify-Tests.ps1` captures bounded emulator diagnostics on device readiness/test failures; manual capture is also available:

```powershell
./tools/release/Save-EmulatorDiagnostics.ps1 -Serial emulator-5560
```

Each adb command has a ten-second bound. Snapshots contain selected device properties, app memory and host emulator process IDs/memory/CPU; they neither restart a device nor assert an OOM/root cause. They do not collect arbitrary environment variables, process command lines or global logcat. Full test logs remain necessary. CI host failures still use existing workflow diagnostics.

Record the reason in the existing runner's JSON (omit sensitive details):

```powershell
./tools/Verify-Tests.ps1 -Suite FocusedJvm -TestClass BackgroundCropTest `
  -Reason 'Verify crop bounds after changing geometry'
```

## Record work, do not reconstruct it from commits

```powershell
./tools/release/Record-EpicWork.ps1 -Epic 187 -Issue 190 -Action Start -Phase Implementation
# Keep the returned Id. Stop when leaving this phase, including when waiting:
./tools/release/Record-EpicWork.ps1 -Epic 187 -Issue 190 -Action Stop -Phase Implementation -Id '<returned-id>'
```

Allowed phases: Implementation, LocalChecks, CIWait, Review, ReleasePreparation, Rework. Only one phase is active in this worktree's epic journal. Stop and start at actual boundaries; forgotten/missing work is unknown, not estimated. Journals and draft reports live under ignored `app/build/verification/epic-<number>/`. Concurrent writers to the same journal are unsupported; use one owner. Phase duration measures the recorded interval, not CPU utilization. Parallel test execution or work elsewhere can overlap and must not be double-counted.

## Prepare the post-merge review

Select only this epic's actual JSON summaries, XML and CI run IDs; do not glob historical output indiscriminately:

```powershell
./tools/release/New-EpicReview.ps1 -Epic 187 `
  -SummaryFile 'app/build/verification/fast-<timestamp>.json','app/build/verification/release-<timestamp>.json' `
  -TestXml 'app/build/test-results/testDebugUnitTest/TEST-<class>.xml' `
  -RunId 38127364391,38128435348,38128435349
```

The generator produces a new ignored English draft, never overwrites a public report and never writes to GitHub. It retains failed checks, fresh/cached distinction, dirty/SHA identity, incomplete intervals and selected CI run/attempt identities. CI jobs are paginated. Job durations are not queue time or elapsed epic time; overlapping jobs and repeated test cases are not summed into unique coverage. XML profiling lists the slowest measured cases and excludes absent timing rather than inventing it.

Review scope, failures, inferences and limitations, then write `publishing/reports/epic-<number>-development-review.md` after merge and link from the epic. Give each proposed optimization evidence, expected benefit and a validation that preserves assertions, isolation and required gates. Compare matching successful scope/environment across the next epic; report median/range and retries when enough observations exist. No guaranteed percentage follows from one warm build pair.

## Verify the published release locally

```powershell
./tools/release/Verify-PublishedRelease.ps1 -Tag v1.5.0 -ReleaseRunId 38128435349 `
  -JavaHome 'C:/Users/pilig/.jdks/jbr-21.0.11'
```

This read-only command resolves the tag (including annotated tags), checks the successful main publication workflow SHA, downloads all three public assets without authentication, checks API size/digest and checksum entries, validates update metadata against the APK package/version, and reuses the existing original-certificate signature verifier. Logs/downloads/JSON stay ignored. A failure is never a passed summary. It performs no build, installation, instrumentation, publication or Project operation and does not replace the existing exact-main proof gate or manual device/store acceptance. Windows PowerShell 7, Android build-tools and a JDK are required for native APK verification.

Implementation references: [PowerShell web downloads](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/invoke-webrequest), [JSON parsing](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/convertfrom-json). Context7 was unavailable during preparation; official documentation was reviewed instead.
