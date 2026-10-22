# Local verification and release gates

All builds, JVM/Android/tool tests and signed release verification run on local computers. GitHub stores source, PRs, issues and published releases; GitHub Actions and self-hosted Actions runners are not used. SonarQube Cloud is the explicit analysis-service exception: its scanner runs locally, sends analysis results to Sonar and waits for the remote Quality Gate. This is not an offline-only workflow.

## Scope and commands

The project has one Android module, `:app`, AGP 9.1.1, Gradle 9.8.2, Java 11 source/target compatibility and JUnit Platform/Vintage. Use JDK 21 and the configured Android SDK. [TESTING.md](../TESTING.md) owns the inventory; `tools/ci/select_checks.py` owns conservative path-to-check selection. “CI” in retained helper names does not authorize hosted execution.

| Change | Required local checks |
| --- | --- |
| App component | Debug build, strict debug lint including tests, detekt, complete debug JVM suite and JaCoCo, Sonar Quality Gate, mapped Android classes plus SmokeTest on API 35 |
| Shared/unmapped build or verification input | Complete JVM/Android/tool regression and quality gates |
| Documentation/instructions only | Diff, links, selection and evidence; no Android build, device tests or Sonar |
| Allowlisted local API/verification/host tooling | Selected Pester/Python contract tests; no app build unless app/build inputs also change |
| Release automation | Selected local PowerShell/Python tests including actual platform-specific release-tool tests |
| Exact-main release candidate | Full regression plus independent release lint, signed APK/AAB signature/certificate checks and signed APK runtime smoke |

For intermediate development, use `tools/Verify-Tests.ps1` with `FocusedJvm` or `FocusedAndroid`, explicit `-TestClass`, `-MaxWorkers`, and an explicit emulator `-Serial` for device work. `Fast` runs debug build/lint/JVM/JaCoCo, not detekt/Sonar/device checks; it is not the complete final gate. The final PR/release regression entrypoint is `tools/Invoke-LocalChecks.ps1`; inspect its parameter help for the current interface. Merge through `tools/Manage-PullRequest.ps1` with the resulting evidence.

The gate interface is `-Mode PR|Full -BaseSha <40-character commit> -Python <executable> -JavaHome <JDK> -Serial <emulator> -MaxWorkers 4`, with optional `-OutputDirectory` under ignored output. PR app analysis also needs `-PullRequestNumber <number>` (base branch defaults to `main`). Prepare the code and English PR text locally, push the branch and create a draft PR to obtain that number before the final local scan. This bootstrap push does not authorize merge or claim checks passed; complete final evidence before marking the PR ready and merging. Windows is the only mandatory verification platform. No Linux report or WSL installation is required. `Manage-PullRequest.ps1 -Action Inspect|Merge -EvidencePath <evidence.json> -Python <executable>` validates the local result; preview merge with `-WhatIf`.

The runner validates Python, applicable PR/main identity, JDK 21, compile SDK/build tools, required Sonar settings and explicit API 35 emulator readiness before tool suites or Gradle. `-SdkDirectory`, `local.properties`, `ANDROID_HOME` and `ANDROID_SDK_ROOT` must agree when present; conflicting locations fail rather than using different SDKs for Gradle and adb. The validated SDK/JDK are propagated to child processes and the previous process environment is restored. Documentation/tool-only scope has no Android or Sonar prerequisites. Readiness checks boot state, model/build identity and observable blocking ANR/crash windows; boot completion alone is not a health assertion. Device failures retain bounded window/screenshot diagnostics without dismissing dialogs or retrying tests.

Checkpointing is explicit: add `-EnableCheckpoints` to preserve immutable stage snapshots, then use `-ResumeFrom '<original candidate.json or evidence.json>'` in a new empty output directory after diagnosing an interruption. Only passed Python, Pester, quality and quality-report stages may be reused. The runner rechecks clean source/base, analysis identity and volatile device conditions; Sonar and device stages execute again. Source/tree, selection, tool/config bytes, runtime identity, dependency and report hashes bind reuse. Ineligible snapshots retain rejection diagnostics and the required stage executes; failed stages never become passing evidence. Quality reuse also requires the live worktree build outputs to match the snapshot, because later stages consume those outputs. Reused checks retain their original duration and origin and are labelled as reused, never fresh execution. Original failure directories are preserved; this contract retains schema-one evidence and schema-two identical-tree promotion, and does not replace the signed-release gate.

For a PR whose final gate ran in `Full` mode, `tools/release/Promote-LocalEvidence.ps1 -PullRequestNumber N -EvidencePath <PR evidence.json> -Python <executable> -JavaHome <JDK>` may reuse the immutable local reports after squash merge. It requires the PR head tree to equal current remote main's tree, a verified merged PR identity, passed original version-one Full evidence and its report hashes. It reruns Sonar locally for the actual main branch and writes self-contained version-two evidence with both original and fresh results. A selected PR gate, altered tree, stale base, failed/missing report or failed main Quality Gate cannot be promoted. The release verifier still requires exact-main Full evidence and independently builds/checks signed artifacts and runs signed runtime smoke. Keep the original PR worktree and evidence until promotion completes.

Use the diagnosed [execution profile](EXECUTION-PROFILES.md). One owner reserves one Gradle process and one isolated headless emulator; the current 8-CPU host budget is four workers. Preserve compatible caches. Missing Windows capacity blocks mandatory checks. Unix helper tests and the retired cloud-shell fixture are optional; their omission is not claimed as Linux coverage. `tools/Invoke-RemoteChecks.ps1` always rejects requests, including `-WhatIf`.

## Evidence and merge enforcement

The local gate records the exact head and base SHA, selected scope, outcomes, durations and report hashes. Missing, failed, incomplete or modified required results cannot authorize a merge. The checked tree must be clean and match the PR head; the remote main base must remain current. Update the branch and validate the new state when main changes. The merge API receives the expected head SHA to reject a concurrent head update.

GitHub requires a PR and preserves main/tag integrity protections, but has no required hosted `android` status after migration. The local script is the test gate; a merge through GitHub UI or another API client bypasses it. Local reports are not independent GitHub attestation and do not protect against a malicious operator controlling both source and evidence. See [POLICY.md](POLICY.md).

Retain stdout/stderr, XML, quality reports and device diagnostics in ignored verification output. A build/install is not an instrumentation pass. Every selected Android class must have a passing case; a full suite must account for its declared inventory, including documented opt-in skips. Launcher smoke and SmokeTest cover distinct behavior; signed runtime smoke does not replace complete debug instrumentation or physical-device/store acceptance.

The full local gate executes the complete Android instrumentation suite once. Picker and UI focused scenarios remain available for development diagnosis and are included in that complete inventory; separate preflight reruns are not required evidence. The launcher smoke still runs before instrumentation so a broken app launch fails early.

Use a single risk-based plan per ticket. Reuse successful evidence only while its inputs/configuration remain valid; mark cached/up-to-date JVM tasks as reused. Do not habitually clean, rerun unchanged suites or retry failures until green. Preserve diagnostics, identify the cause and rerun the failed scope and affected neighbors. Record actual queue, build, analysis, emulator and test time separately; do not infer speedups from unlike scopes or cold/warm comparisons.

## SonarQube Cloud exception

Provide the existing organization/project configuration and an analysis token to the local scanner through private local configuration/environment. Never print, commit or upload the token. A GitHub Actions secret cannot be read back to provision a local token. Keep automatic analysis disabled to avoid duplicate scans. App changes require a successful Quality Gate (`sonar.qualitygate.wait=true`); missing credentials, failed analysis or timeout block verification. Documentation-only changes need no Sonar scan.

The dedicated Swypetris gate retains new-code reliability/maintainability A, duplication at most 3%, and no High-or-higher new security issues. Medium/Low findings and hotspots require review. Coverage remains reported without a gate threshold; do not silently change these rules. The scanner excludes generated/build directories and PNG/OGG/WAV assets, includes Kotlin/Java sources and consumes complete debug JVM JaCoCo XML. Check imported coverage and analysis identity rather than equating scanner startup with success.

## Local release and historical evidence

`tools/release/Verify-LocalRelease.ps1` validates full local evidence for the exact current main commit, builds signed APK/AAB using the existing release builder, checks public certificate fingerprints and exercises the signed APK on an explicit emulator. It writes local release provenance binding commit, version, evidence and artifact hashes. Verification never publishes.

`tools/release/Publish-LocalRelease.ps1` is a separate explicit action with reviewed English release notes and verified provenance. It refuses an existing release/tag and preserves protected release tags. Complete [release compliance](RELEASE-COMPLIANCE.md) when applicable before publishing. See [RELEASE.md](RELEASE.md) for commands and private signing-key storage. Do not delete or migrate existing GitHub secrets as part of ordinary verification.

Archived YAML under `tools/ci/legacy-workflows/` is non-executable historical evidence. Legacy schema 3 proof/context/provenance validators remain for old published releases; they do not authorize a new cloud run or stand in for current local release evidence. Existing historical reports retain their measured cloud results with their dates. No workflow or Dependabot Actions schedule remains active in tracked configuration.

## Dependency controls

Strict Gradle verification checks reviewed SHA-256 artifact/metadata entries in `gradle/verification-metadata.xml`; verification must never regenerate or bypass them. Follow [DEPENDENCY-VERIFICATION.md](DEPENDENCY-VERIFICATION.md) for review, platform coverage and trust limits. Pester remains 6.2.0, and the Gradle distribution checksum remains pinned. Archive-only Actions pins are historical fixtures, not a reason to schedule dependency-update workflows.

## detekt policy

The `dev.detekt` plugin is pinned to `2.0.0-alpha.3`, the first release whose compatibility table matches AGP 9.1.1 / Gradle 9.3.1 and supports AGP built-in Kotlin without the old-DSL workaround. This is a prerelease dependency, not a stable 2.0 release. Application dependencies and compiler versions are unchanged. The plain `:app:detekt` task scans all `src/**/*.kt` (main, JVM tests, Android tests) without type resolution. Type-resolution-only rules are not claimed to run. It uses default rules plus validated configuration and fails on Warning or Error. HTML and SARIF are retained as artifacts; no formatting plugin, autocorrection, or baseline is added. The framework naming convention is documented below.

Lint explicitly fails on Error/Fatal, includes test sources, and generates HTML/XML/SARIF. Warnings retain existing severity; no rule is disabled. Local verification retains failure reports and rejects failed or missing required results. Existing-code violations must be fixed or individually reviewed in a separately approved policy; a green CI result is not promised by enabling these checks. See [CI-AUDIT.md](CI-AUDIT.md).

Screenshot test methods that use the API-26 capture API declare `SdkSuppress(minSdkVersion = 26)`; screenshot helpers declare `RequiresApi(26)`. Other methods retain their existing supported API range. The API-35 local emulator executes these screenshot tests. Lint has no baseline. The delivery correction also uses `Typeface.NORMAL` and the public `ViewModelProvider` factory in tests instead of a literal font style and restricted `ViewModelStore.put`.

Compose naming policy: FunctionNaming ignores only @Composable declarations because the official Compose API guidelines prescribe PascalCase for Unit-returning UI components. Ordinary functions retain the default camelCase check. This is a targeted framework convention, not a legacy baseline; other rules and failure thresholds are unchanged. Reference: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-api-guidelines.md
