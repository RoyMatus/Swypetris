# CI/CD quality audit — 2026-10-08

This change is intentionally not ready to merge: newly enforced checks expose existing violations. No baseline, disabled rules, formatting sweep, application/UI changes, Quality Gate threshold changes, protection updates, or merge are included.

## Before

Audited local main `8c29b2733c09dea26713d9285ad2d146c1d3a096`, GitHub REST settings, run job/step results, downloaded run logs/artifacts, and public SonarQube Cloud APIs.

- Android CI runs on every PR to main and main push, without top-level path filtering. The always-present `android` job selects checks by changed files; unknown/unresolved inputs conservatively select full regression. PR runs cancel superseded PR runs; main pushes are not cancelled.
- The eight most recent runs at the initial snapshot contained seven completed successful runs and one running PR. No failed run was present in that sample; this is not a claim about all history. [Main run 37738451640](https://github.com/RoyMatus/Swypetris/actions/runs/37738451640) and [PR run 37737319313](https://github.com/RoyMatus/Swypetris/actions/runs/37737319313) ran build/lint, full JVM coverage, API 35 instrumentation/smoke, and Sonar successfully. [Documentation PR run 37735253370](https://github.com/RoyMatus/Swypetris/actions/runs/37735253370) selected `none` and intentionally skipped app checks.
- Existing `lintDebug` fails on errors by default and saves reports. It did not include test sources explicitly. No detekt plugin/configuration existed.
- Full runs produce JaCoCo XML at the configured app-module path. Downloaded main artifact 11532783869 contains 722 covered / 4157 executable lines and 561 covered / 2844 branches; Sonar reports 4157 lines to cover, 132 tests, and 18.3% combined coverage. The line-only JaCoCo ratio is 17.4%; combined line/branch calculation `(722 + 561) / (4157 + 2844)` rounds to 18.3%. This corroborates the full-run import. Default scanner logs do not print an explicit XML-import confirmation. Selected runs previously invoked filtered/no JVM tests without generating XML, despite running Sonar against that path.
- Sonar project `RoyMatus_Swypetris`, organization `sashamatus`, analyzes Kotlin files; recent PR analyses are present, including PR 198 with gate OK. Current main Quality Gate is OK. Automatic analysis setting is false. Project analysis history records two analyses for the same main SHA at 09:38:46 and 09:45:46 Moscow time, consistent with Android CI and version-release workflow both scanning main.
- Sonar variables and the SONAR_TOKEN secret name exist. Secret values were neither retrieved nor printed. Fork app PRs deliberately fail token-based Sonar and require moving reviewed code to a trusted branch; documentation-only forks need no scanner token. No pull_request_target workaround is added.
- Main protection requires `android` from GitHub Actions, app ID 15368. Administrator enforcement is enabled, force pushes/deletions disabled, PR reviews configured with zero required approvals, and strict up-to-date checking is false. There are no repository rulesets in the audited API response. Thus the required check is already enforced for ordinary/admin merges, but a passing stale-head check can remain sufficient after main changes. No destructive failed-check merge was attempted.
- Actions are enabled and allow all actions; mandatory SHA pinning is false. Gradle setup/cache already exists and is preserved.

## Changes

| Files | Change |
| --- | --- |
| `build.gradle.kts`, `app/build.gradle.kts`, `config/detekt/detekt.yml` | Add detekt 2.0.0-alpha.3, default rules, config validation, fail on Warning/Error, all application/test Kotlin sources, HTML/SARIF. Explicit strict lint with HTML/XML/SARIF and test-source analysis. |
| `.github/workflows/ci.yml` | Keep stable android job, run detekt, generate complete debug JVM coverage for every app change, retain mapped Android selection, save reports on failure, assert required step outcomes. Other checks continue after a quality failure without continue-on-error. |
| `tools/ci/select_checks.py`, `test_select_checks.py` | CI script changes now require full app regression; retain conservative fallback and existing mappings; cover shared Gradle/quality/resource inputs. |
| `tools/ci/verify_quality_results.py`, `test_quality_results.py` | Reject missing/invalid selection, skipped/masked/failed required steps, absent/empty JVM/coverage evidence, lint errors, missing detekt reports. |
| `.github/workflows/release-check.yml`, `tools/ci/wait_for_android_ci.py`, `test_release_gate.py` | Include detekt/report validation; main version release reuses latest successful push CI for the same SHA instead of rescanning. Pending/missing CI waits up to 30 minutes; failure/cancellation/skip/neutral rejects release. Tag/manual verification keeps its own branch analysis. |
| `publishing/CI.md`, `publishing/CI-AUDIT.md` | Document actual policy, audit evidence, blockers, and approval-dependent protection proposal. |

[Official detekt compatibility](https://detekt.dev/docs/introduction/compatibility/) lists alpha.3 against Gradle 9.3.1, Kotlin 2.3.21, AGP 9.1.1; it removes the AGP built-in Kotlin/old-DSL workaround. Local Gradle successfully configured and ran it. It is a prerelease, and the plain source task does not perform type resolution. No application dependency/compiler upgrades are made. Context7 provided detekt/GitHub guidance; it did not resolve relevant Android Lint documentation, so [official AGP 9.1 Lint API](https://developer.android.com/reference/tools/gradle-api/9.1/com/android/build/api/dsl/Lint) was used as fallback.

## After

- App PR: detekt + debug build/strict lint + complete debug JVM tests/JaCoCo + mapped Android tests/smoke + trusted Sonar analysis waiting for Quality Gate + final outcome assertion.
- Shared/unknown Gradle, workflow, quality configuration, or CI script change: full JVM variants and Android regression as well as the quality checks above.
- Documentation: successful selection plus final assertion; expensive app checks remain intentionally absent.
- GitHub API/release script-only change: corresponding focused tool checks plus final assertion.
- Main version release: its regression/signature/Android verification remains mandatory; successful same-SHA main Android CI supplies Sonar authorization before publishing. Tag/manual verification scans its own ref.

No extra workflow or cache layer was added. Every app run now generates unfiltered debug JVM coverage; mapped JVM filters remain selector metadata but no longer restrict the debug coverage suite. This is an explicit reliability-over-speed tradeoff and avoids running selected JVM tests and then rerunning the whole suite for coverage. Android/UI coverage is not merged into the JVM-only report.

## Verification

| Check actually executed | Result |
| --- | --- |
| Python CI selector/report/release gate unit tests | 23 tests passed, including negative cases for skipped/failed steps, empty evidence, wrong SHA and failed release prerequisite. |
| actionlint on both modified workflows | Passed (official release binary downloaded under ignored build output and SHA-256 checked against release checksums). |
| `git diff --check` | Passed. |
| `tools/Verify-Tests.ps1 -Suite Fast -JavaHome ...jbr-21.0.11` | Failed at strict lint; debug APK, JVM tests, and JaCoCo tasks completed in the same invocation. Full log under `app/build/verification/fast-20261008-102605-006.log`. |
| `:app:assembleDebug` separately | Passed (up-to-date verified task). |
| `:app:testDebugUnitTest :app:jacocoDebugUnitTestReport` separately | Passed (up-to-date after Fast run); 132 executed tests, 0 failures/errors/skips in 26 XML reports; JaCoCo has covered lines. |
| `:app:lintDebug` in Fast suite | Failed: 52 errors and 118 warnings. Errors: 50 NewApi, 1 WrongConstant, 1 RestrictedApi. Test-source analysis exposes captureToImage calls requiring API 26 with minSdk 24. No baseline/suppressions were created. |
| `:app:detekt` | Failed with 776 existing-code findings; HTML and SARIF created. Configuration/plugin task ran successfully before analysis rejected findings. Full log `app/build/verification/detekt.log`. |
| Local instrumentation/release signing | Not run: no app behavior changes; a pre-existing emulator and a Pixel 7 were connected; emulator ownership was not established. Neither was used for this audit. Historical CI instrumentation successes are not claimed as a new local test run. |
| New PR GitHub Actions | Inspect the PR's latest check result after publication. A green result is not expected while the known strict lint/detekt violations remain. |

## Remaining issues

- detekt findings include 340 MagicNumber, 171 MaxLineLength, 132 WildcardImport, 50 FunctionNaming, 16 CyclomaticComplexMethod, 13 LongMethod, 9 ComplexCondition, 4 NestedBlockDepth, plus other categories. Some naming/style findings need Compose-aware review; they are not automatically declared bugs. Resolving them requires a separately scoped review to avoid an unrelated formatting/refactoring sweep. No historical violations are silently ignored.
- 52 test-source lint errors block the expanded lint check. Resolve the actual API contracts/test requirements without suppressing them merely to pass CI or disabling existing tests.
- Existing Quality Gate thresholds are unchanged and have no coverage threshold. Its passing status is not proof of comprehensive test coverage/security review. Medium/Low security findings and hotspots still require review under the existing policy.
- Release same-SHA reuse is unit-tested but cannot be exercised as a real main release without merging/changing the version; neither is authorized here.
- Fork app PRs remain intentionally blocked until reviewed code is moved onto a trusted branch. Dependabot or other secret-restricted contexts need the same treatment.
- Protection cannot defend against an authorized contributor deliberately editing the quality policy to bypass checks. Review policy/config changes as well as check results.

## Manual actions / proposed changes requiring approval

1. Review and resolve existing lint/detekt findings before authorizing merge. A baseline is a separate decision and is not part of this PR.
2. Proposed protection change: keep required android/app 15368 and administrator enforcement, set required_status_checks.strict=true while preserving all other protection fields. This closes stale-main checking. Do not add a separate conditional Sonar check. No API write was made; owner confirmation is required.
3. Consider one required approving review and ownership review for workflow/quality-policy changes if the repository's reviewer setup supports it. This is a policy choice, not an applied change.
4. Keep CI-based Sonar analysis and automatic analysis=false. Confirm token ownership/rotation and analysis permissions in Sonar administration; public APIs and GitHub secret-name listing cannot verify those details. No token replacement or threshold change is required by the observed successful scans.
5. Approve merge explicitly only after required checks are green. Until then retain this branch/worktree/PR and its verification artifacts.

## Coordination update

During the audit PR 199 was merged into main (`19e42bb393602dec2cf7ff7acda0937de5e7eab8`). Its new UpdateFailure.kt was not in the explicit source map, causing the source-inventory test to fail on the synthetic merge of PR 200. The local audit branch was rebased onto that main commit and UpdateFailure.kt registered as a full-regression input. All 23 Python tests passed locally afterwards. Checks started before the owner's coordination instruction completed with 794 detekt findings, 52 lint errors / 120 warnings, and JVM XML totals {'tests': 134, 'failures': 0, 'errors': 0, 'skipped': 0}.

The owner instructed this chat not to run CI manually or merge while another process performs application work. The first PR 200 CI run was cancelled. The owner subsequently authorized publishing the latest pipeline and monitoring automatically triggered CI every 15 minutes. Publish the rebased branch and mapping/report correction; no manual dispatch, rerun, or merge is authorized. Assess only runs created after 2026-10-08 07:45:58 UTC, verify the checked commit contains this pipeline, and report completed results without treating runs of old configurations as validation.
