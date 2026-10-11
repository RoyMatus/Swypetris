# Choose work placement from the execution host

Before an epic, its continuation on a different machine, or a standalone implementation ticket, run this once in the owned worktree:

```powershell
./tools/Get-ExecutionProfile.ps1 -Context 'Epic or ticket number'
# Supply -JavaHome and -SdkDirectory if discovery misses an existing installation.
```

The read-only probe records CPU count/architecture, total/available RAM, free workspace disk, JDK 21, the project's actual compile SDK platform, a host-compatible API 35 image, emulator acceleration and connected device identities. It does not build, install, boot an emulator or benchmark. JSON and bounded command logs stay under ignored `app/build/verification/host-<timestamp>/`. Do not publish device identities or local paths. Windows and Linux memory probes are supported; Linux uses available cgroup memory limits. Other hosts or incomplete probes need explicit measurements before heavy local work.

The **execution host**, not the chat client, determines capacity. A phone connected to a capable remote workstation can use that workstation. A session without a suitable local shell cannot run verification; report the missing executor and leave the gate blocked. Never dispatch a cloud workflow as fallback. A profile is a scheduling recommendation, not a claim that an executor exists. If no executor is available, prepare the changes/check plan through available tools and report that execution remains unavailable; never claim an unexecuted check passed.

## Initial budgets

These project heuristics are starting points, not Android minimum requirements or measured performance guarantees. Use actual free resources, hardware acceleration, container quotas and observed build behavior. A single busy CPU sample means wait/recheck, not permanently label the computer weak. Missing PATH configuration is not weak hardware: first check installed JDK/SDK locations.

| Profile | Initial criteria | Placement and concurrency |
| --- | --- | --- |
| Local | At least 8 logical CPUs, 24 GiB RAM total, 10 GiB available, 20 GiB free disk; JDK/SDK, API 35 image and acceleration ready | Edit, analyze, build and run focused tests locally. One Gradle invocation, one headless emulator, `min(6, floor(CPU/2))` workers initially. |
| Constrained local | At least 4 logical CPUs, 8 GiB total RAM, 4 GiB available, 10 GiB free disk and JDK/SDK; local emulator criteria unmet | Edit and run JVM/build checks locally with 2 workers, one Gradle invocation; device checks wait for suitable local capacity. |
| Blocked | Build criteria unmet or unknown, or no local execution shell | Keep cheap editing/review/tool tests where possible; required heavy checks remain blocked until a suitable local executor is available. |

Share one profile and resource reservation across agents and worktrees on the same host. Do not launch competing Gradle builds/emulators to appear parallel. Parallelize independent reading, implementation and review only when useful. Recheck after moving machines, toolchain/resource changes, OOM or sustained slowdown; do not repeat diagnosis per command. The probe requires x86/x86_64 images for an X64 host and arm64-v8a for Arm64; unknown architectures remain unverified. Check the actual chosen AVD before booting: installed compatible images and acceleration do not prove that AVD is configured correctly or boots successfully. A suitable connected physical device is an exception for device-specific behavior, not an automatic replacement for an emulator.

Pass the selected budget explicitly; both verification and release runners accept `-MaxWorkers` without changing global Gradle properties:

```powershell
./tools/Verify-Tests.ps1 -Suite FocusedJvm -TestClass GameEngineTest -MaxWorkers 4 `
  -JavaHome '<profile toolchain.javaHome>' -Reason 'Changed collision boundary'
```

Do not blindly increase Gradle heap, enable all parallel modes, delete caches, run `clean`, force `--rerun-tasks`, upgrade dependencies or replace the graphics backend. Preserve compatible worktree/Gradle caches. Measure a slow build before tuning it; an OOM or reproducible bottleneck is evidence for changing the budget.

## One check plan and one evidence owner

| Change or event | Smallest useful development check |
| --- | --- |
| Instructions/documentation only | Diff, links and consistency; zero Android tests/builds. |
| Local verification/host/release tool | Focused Pester/contract tests and syntax; zero app tests unless behavior or build inputs also change. |
| Pure game rule/geometry change | Affected JVM classes at a meaningful checkpoint; add neighboring state rules where coupled. |
| Compose/platform/persistence change | Relevant Android classes plus affected JVM rules; explicit isolated emulator serial. |
| Shared lifecycle/build/CI-selection contract or unresolved regression | Broader checks justified by affected behavior; the CI selector retains full regression for shared/unmapped inputs. |
| Final PR commit | Final local gate after batching code, tests and documentation; evidence must bind exact head and current main base. |
| Ordinary merge | Confirm result and closure; no automatic repeat of the successful full suite. |
| Version/release commit | Exact-main local full regression and independent signed-release gate; focused checks cannot replace release evidence. |

The parent owns final integration. Give delegated work concrete ownership and existing results; do not ask every role to repeat repository discovery, host diagnosis, builds or full tests. Reuse successful evidence only while its relevant code, dependencies, environment and test configuration remain applicable. Cached JVM results are reused results, not a fresh execution. After a failure, retain logs, fix/diagnose, then rerun the failed scenario and affected neighbors. Stop once the selected checks and mandatory gates pass; broaden only for changed inputs or unresolved risks. Prefer deterministic clocks/seeds and framework idling over sleeps, without deleting assertions or isolation to gain speed.

## Local execution only

All builds, Android/JVM/tool checks and signed release verification use local scripts. SonarQube Cloud remains an explicit service exception: the scanner runs locally and waits for the remote Quality Gate. No GitHub Actions dispatch or self-hosted Actions runner is permitted. `Invoke-RemoteChecks.ps1` rejects requests.

A missing local executor, unavailable API 35 emulator, or missing platform needed by a required tool test is a concrete blocker. A Windows run with Linux tests skipped is not Linux coverage. Install/configure an appropriate local environment within authorization or report the incomplete gate; never turn this into cloud fallback. Preserve diagnostics before changing toolchain/environment and then recheck the host profile.

## Compact task handoff

> Read the ticket and affected code. Reuse the current host profile or diagnose this execution host if new. State the changed behavior, file ownership, resource budget and smallest risk-covering checks. Implement, review and run those checks; reuse valid evidence across agents. Batch the final PR commit and complete its required local gate. Preserve failures and report actual counts/durations, cached results and limitations. After an epic merge, publish the development review required by AGENTS.md.

Record actual phase/check timing with the existing [development workflow](DEVELOPMENT-WORKFLOW.md). Compare matching scope/environment over several runs; do not infer productivity from commit timestamps or promise a speedup from one cached build. Research and the evidence behind this policy are in the [2026-10-11 audit](reports/development-testing-audit-2026-10-11.md).
