# Development and testing efficiency audit — 2026-10-11

## Scope and conclusions

Reviewed the current AGENTS.md, eight role profiles and eight project skills, test inventory, CI selector/gates, required workflows, local verification/release runners and recent work evidence. Application behavior and tests were not changed. The concrete avoidable cost was an app regression for a local verification-tool edit; the other improvements are scheduling and evidence-reuse rules whose productivity benefit must be measured on later work.

| Finding | Evidence | Change / quality boundary |
| --- | --- | --- |
| Local-only tool changes fell into unknown-tool full regression | [PR #235](https://github.com/RoyMatus/Swypetris/pull/235), [run 38130640884](https://github.com/RoyMatus/Swypetris/actions/runs/38130640884): the `android` job lasted 10:04:32–10:15:53 UTC, **681 seconds (11m21s)**. The change included `Verify-Tests.ps1`; app source was unchanged. | Explicit local-tool allowlist selects Pester/contract checks without app build. Unknown tools, actual CI/selector/workflow changes and mixed app changes retain conservative coverage. This audit itself changes the selector/workflow and therefore still requires full PR CI. |
| Release helper edits selected syntax only | Existing selector's `tools/release/` branch. | Add behavior tests alongside syntax. This spends small tool-test time to protect report/gate semantics while avoiding an app build unrelated to a helper edit. |
| Always-local wording did not cover weak/no-shell environments | AGENTS.md and development guide; roles lacked a shared execution budget. | Read-only host diagnosis and Local/Hybrid/Cloud scheduling; no permanent hardware assumption or repeated per-command diagnosis. |
| Additional local checks could be interpreted as duplicate full-suite passes | Existing instructions already encouraged focused checks, but did not explicitly distinguish the mandatory final PR run from an identical local prerequisite. No measured count of actual duplicate passes was established. | One risk-based plan/evidence owner, share successful applicable results across agents, batch final commit, no automatic full rerun after ordinary merge. |
| Cloud fallback otherwise required the full PR/release path | Existing hosted workflows focused on required regression and release. | Optional manual focused workflow before an open PR; exact SHA/classes/reason, independent status, no signing/publication. Wrapper refuses open-PR duplication. |
| Agent overhead can exceed benefit | Eight profiles had no mandatory full-suite-per-role requirement; triage's generic “actual checks” text was ambiguous. | Clarify triage needs no build. Delegate bounded independent ownership with shared context/results; one heavy-work scheduler. Keep durable root rules short and commands in one linked policy. |

No assertion, test isolation, lint severity, Sonar gate, branch protection, signed-artifact requirement or version was weakened. No evidence established obsolete application tests to delete. A full Android inventory is 158 cases (156 passed, two opt-in skips), with 149 JVM cases in the dated existing reports; repeated preflight cases are not extra unique coverage. The historical full Android XML time of 402.349 seconds is a different measurement from the 681-second CI job, which also includes setup/build/quality work. Neither is a promised runtime for every future run.

## Execution host and initial policy

The current Windows execution host has 8 logical processors, **47.87 GiB RAM total / 22.12 GiB available**, and **49.75 GiB free workspace disk** at the 10:40 UTC probe. JDK 21, compile platform `android-36.1`, an API 35 image and WHPX acceleration were found. Default discovery completed in **2.05 seconds** and selected **Local: one Gradle invocation, four workers, one headless emulator**. These are an instantaneous resource observation and an initial budget, not a benchmark or guarantee. The connected Pixel 7 was inventoried but not used; tool changes do not need phone instrumentation.

The probe derives the minor compile SDK from the project and finds an installed IntelliJ JDK when PATH/JAVA_HOME are absent. It checks available RAM/disk and supported container memory limits rather than total RAM alone. Missing measurements fail conservatively; missing configuration can be repaired without calling the hardware weak. Current capacity must be rechecked after moving machines or a material resource/toolchain change. A mobile chat client does not reveal the execution backend's capacity.

The complete thresholds, commands, check matrix, limitations and reusable task prompt live in [EXECUTION-PROFILES.md](../EXECUTION-PROFILES.md). They are project heuristics; tune them from matching successful runs and failures, not unsupported universal “powerful PC” numbers. Cloud implementation requires an actually available authenticated coding environment. Actions executes committed checks; it cannot migrate an agent or implement a ticket merely because a workflow exists.

## Research and adopted practices

Sources accessed 2026-10-11; Context7 was unavailable because of its quota, so official documentation was used directly.

- **Measure before tuning, preserve compatible caches and bound parallel work.** [Android build optimization](https://developer.android.com/build/optimize-your-build) and [Gradle performance guidance](https://docs.gradle.org/current/userguide/performance.html) support profiling and targeted cache/parallelism improvements. Current Gradle documentation is newer than this project's 9.3.1; no version-specific new feature or dependency upgrade was applied. Heap/parallelism changes need this workload's measurements.
- **Use accelerated emulators and appropriate test levels.** [Android emulator acceleration](https://developer.android.com/studio/run/emulator-acceleration) and [testing strategy](https://developer.android.com/training/testing/fundamentals/strategies) support checking acceleration and using focused lower-level feedback where it covers the behavior. Platform/Compose behavior still needs device tests; faster JVM tests do not replace that coverage.
- **Keep instructions concrete and reusable.** [OpenAI's Codex best practices](https://learn.chatgpt.com/guides/best-practices) and [AGENTS.md guidance](https://learn.chatgpt.com/docs/agent-configuration/agents-md) support durable project guidance, relevant context, clear checks and review. The repository now links a compact handoff prompt and policy instead of copying a long execution matrix into every role.
- **Empirical results are mixed.** [A 2026 repository-instruction study](https://arxiv.org/abs/2601.20404) reports lower median runtime/output tokens in its benchmark. [Evaluating AGENTS.md, v3 dated 2026-09-29](https://arxiv.org/abs/2602.11988) finds context files do not generally improve success and increase inference cost by over 20% on average in its evaluation. Different setups do not establish a universal speedup for Swypetris. Adopt concise non-obvious project constraints, then measure end-to-end outcomes here; do not copy a purported magic prompt.
- **Separate caches, results and dispatch acceptance.** [GitHub cache guidance](https://docs.github.com/en/actions/concepts/workflows-and-actions/dependency-caching) distinguishes reusable dependencies from run artifacts; no credentials enter caches. [Manual workflow documentation](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow) requires a dispatchable workflow on the default branch. Check exact run/SHA results after dispatch; a successful HTTP request is not passed tests.

## Actual validation before the PR

| Check | Result | Measured duration |
| --- | --- | --- |
| Host probe with default toolchain discovery | Local profile, no probe errors | 2.051s |
| All Pester tool tests, Windows | **60 passed, 0 failed, 1 Unix-only skip** | 8.618s |
| Python selector/gate/workflow contract suite | **62 passed, 0 failed, 1 platform skip** (63 discovered) | 21.574s |
| Focused recheck after native-probe error handling and portable diagnostic paths | **23 Pester passed**, host probe still Local | 3.84s for Pester |
| PowerShell parsing, actionlint, diff whitespace | Passed | Not timed separately |
| Local Android build/instrumentation | Not run: no app changes; required full CI covers the selector/workflow change | Not applicable |

Tests exercise host decisions, unknown/constrained/container resources, actual minor SDK parsing, argument/resource forwarding, failure reporting, unchanged environment restoration, mixed-path conservative selection, exact-SHA/class validation and remote dispatch rejection. Python “Android tests passed” fixture messages refer to synthetic XML validation, not real instrumentation. Windows skips the Unix launcher fixture; required Linux CI exercises it. Earlier focused development runs were diagnostic checkpoints, not additional unique coverage.

The new optional hosted workflow was not dispatched on this capable machine. Its syntax, input guards and argument binding were tested locally; actual hosted focused execution remains unverified until it is needed after merge. Direct UI dispatch can bypass the wrapper's open-PR guard; use the wrapper and policy. Mandatory CI/release workflows retain their independent evidence requirements. Native host probes support Windows/Linux, not complete macOS capacity discovery; image presence/acceleration does not prove a successful emulator boot.

The first required PR run, [38133026307](https://github.com/RoyMatus/Swypetris/actions/runs/38133026307), failed in the new Unix launcher fixture before app tests. Repository attributes force CRLF for PowerShell files even on Linux; the fixture's embedded shell script inherited those endings. Normalize that generated shell script to LF and expose the runner's diagnostic output. The failed job log was retained locally. Windows has no installed WSL/container executor, so confirmation of the Linux fixture uses the next required PR run rather than an additional optional workflow. This failure is part of the audit evidence, not a passed local Linux check.

## Measure the next epic

Continue the existing phase journal and mandatory post-merge epic report. Record selected host/profile, changed risk, check count and class scope, wall time, fresh versus cached results, retries, waiting and rework. Compare the same scope/environment across several runs; report median/range and separate queue/setup time. Do not infer work hours from commits or add overlapping durations as elapsed epic time.

The immediate expected saving is avoiding full app work for explicitly classified local-tool edits, replacing the observed 681-second class of job with tool checks and runner setup. Its future duration and overall development gain are not yet measured. Next candidates should come from observed slow test/build phases or recurring failures; profile before changing synchronization, sharding or cache settings. Keep quality evidence and required gates constant while comparing.
