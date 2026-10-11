# CI/CD security and release hardening — 2026-10-11

## Scope and delivery boundary

Reviewed main `e3906b864dcf4df651acc88d1329a69d6f812d9f`, the four workflows, release helpers, live repository protections and recent runs. The owner approved implementation as a reviewed PR plus repository security settings, **without merging or publishing an application release**. Signing is restricted to code on protected main. The owner explicitly accepted temporary signing unavailability in the old workflow after repository signing secrets are removed.

The execution profile selected Local: eight logical CPUs, approximately 48 GiB RAM, four Gradle workers, one Gradle process and one emulator maximum. Tool checks ran locally; final Android regression belongs to the required PR CI. Separate agents owned local transactions, evidence validation and CI prerequisites; one owner scheduled real signing builds.

## Changes and trust boundaries

- `release-signing` Environment has exactly one branch policy: branch `main`. Both local signing identities were checked against the checked-in public certificates, then six Environment secrets were written and their metadata reread. The six repository signing secrets were removed and their absence verified. No secret values were printed or stored in reports.
- Active tag ruleset **Protect release tags #24877707** prohibits updates and deletion of `refs/tags/v*`, with no bypass actors. Creation remains possible. Publication atomically establishes a tag at the verified SHA, rejects a conflicting concurrent creator and uses `gh release create --verify-tag`. Existing releases are not overwritten.
- Automatic release verification follows a completed successful `Android CI` main-push run. The resolver checks the repository, active workflow identity, latest run/attempt, main ancestry and changed release configuration before candidate checkout. Fork/PR runs and branch/tag manual dispatches cannot authorize signing. Manual dispatch from main can verify an existing version tag on main history, without publishing.
- Trusted control scripts and candidate source have separate checkouts. Candidate SHA is explicit rather than inferred from the `workflow_run` control SHA. Schema 3 evidence includes both JVM variants' raw results, debug lint, detekt, coverage and existing complete instrumentation/APK evidence; old schemas fail closed. No waiting runner or duplicate automatic debug/JVM regression is needed.
- Independent release lint, APK/AAB signatures, certificate fingerprints, archive validation and actual signed-APK smoke remain required. The latest source attempt is rechecked immediately before publication. Publication provenance binds the candidate, source CI artifact and publishing attempt; later reruns do not rewrite historical acceptance.
- Actions use upstream full commit SHAs, Pester is fixed at 6.2.0, and Gradle 9.3.1 has its official distribution checksum. Weekly Dependabot checks cover Actions. Checkout credentials are not persisted. Explicit job timeouts and main CI's headless emulator configuration are reused.
- A selective Windows release-tool job is a prerequisite of the stable required `android` result. Documentation does not start this job. Failed/missing prerequisite results cannot become passing app evidence.
- Local release builds stage the complete APK/AAB/metadata/icon set under a version lock. Failed builds/signatures leave the prior destination intact; promotion uses backup/rollback. Checksums enumerate exactly the four intended files.

## Verification evidence

| Risk | Observed check | Result |
| --- | --- | --- |
| Local tool and transaction regressions | Complete Windows Pester tool suite | 79 passed, one Unix-only skip; 14.797 seconds |
| Routing, corrupt evidence, failed gates and untrusted inputs | Python contract suite at integration checkpoint | 92 passed, one Linux-only skip; 25.186 seconds |
| Newly added explicit source SHA/no-wait CLI behavior and prerequisite evidence | Focused final gate/evidence tests | 8 gate and 20 evidence tests passed; includes four new CLI cases after the full checkpoint |
| Final manual/automatic YAML routing | Focused workflow tests, actionlint, PowerShell parsing and diff review | Passed; final workflow tests 4/4 |
| Post-publication recorded-attempt correction | Focused provenance tests | 7/7 passed; 2.94 seconds; reused other unchanged tool evidence |
| Actual GitHub response shape | Read-only resolver probe against live main and successful historical main CI | Manual resolved without publication; historical automatic candidate reached the expected existing-release rejection |
| Incremental signing with different keys | Real app-key `assembleRelease`, upload-key `bundleRelease`, repeated upload-key `bundleRelease`; four workers, build cache, no clean/rerun flag | All certificate checks passed. APK retained app identity; AAB retained upload identity. Durations 45.77 / 7.41 / 4.01 seconds; repeated bundle had 48 of 49 tasks up-to-date |

An initial `python` invocation during the audit resolved to a Windows Store alias, not an interpreter. Implementation reused the existing embedded Python 3.13.15 runtime. Python test messages about instrumentation are synthetic report fixtures, not device-test claims. Required PR CI supplies Linux shell execution and full real app regression; its final result is reported on the PR rather than preclaimed here.

The first PR CI run [38136586384](https://github.com/RoyMatus/Swypetris/actions/runs/38136586384) passed its Windows job (39 seconds), but Linux Pester exposed six transaction failures: `Get-Item` required `-Force` for dot-prefixed staging directories. The failed android job lasted 57 seconds; its complete log was preserved in ignored verification output. Both root and transaction-path inspection now include `-Force`. An added Windows Hidden-attribute reproduction covers root/staging/destination promotion and cleanup; the focused transaction suite passed 7/7 in 1.87 seconds. Linux confirmation uses the required CI on the corrected commit, not an optional duplicate run.

Run [38136802560](https://github.com/RoyMatus/Swypetris/actions/runs/38136802560) confirmed the Linux fix: 74 Pester passed/seven platform skips and all 97 Python cases passed; build, lint and JVM validation also passed. Sonar then reported six `githubactions:S7631` findings on downstream checkout jobs. The prior context job/API resolver already checked repository origin; downstream jobs now additionally carry explicit same-repository/main-event predicates so this boundary is locally visible at every privileged checkout. Routing tests reject forks and PR events in those jobs as well. No rule suppression or Quality Gate change was used; the full failed log is retained.

The real signing experiment supports removing the global AAB `--rerun-tasks` for the current Gradle/AGP configuration. It does **not** establish a Linux speedup or compare cold/warm builds causally. Baseline run 38128435349 spent 293 seconds in duplicate regression and 673 seconds in an evidence-wait step. Removing these costs primarily reduces runner usage; delayed signing after source CI may increase end-to-end release latency. Measure comparable future releases separately.

## Activation and remaining limits

Repository secret restrictions and tag rules are already active. The workflow changes require the PR to be merged by the owner. Until then, the old signing workflow cannot access the migrated keys. No application version, release tag or release was created by this task.

After merging and verifying that **all workflows on main** use full action SHAs, enable repository `sha_pinning_required` through the Actions permissions API, preserving `enabled` and `allowed_actions`, then reread it. This repository-wide switch is deliberately deferred: activating it against the existing main workflow tags would break CI. No new privilege grant is needed or authorized.

An end-to-end automatic publication and an actual Environment-backed manual signed run cannot be demonstrated before this PR is merged without violating the delivery/signing boundaries. The next authorized release must verify the full workflow and published artifacts. Post-publication provenance checks require unexpired source/provenance artifacts. Directory promotion is rollback-safe for handled errors, not a crash-atomic swap; abrupt process termination can leave uniquely named staging/backup directories for recovery.

## Documentation sources

Authenticated Context7 was used for GitHub Actions trust boundaries and PowerShell contracts. API details and release semantics were cross-checked against official sources: [secure Actions use](https://docs.github.com/en/actions/reference/security/secure-use), [Environment restrictions](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments), [Actions secrets API](https://docs.github.com/en/rest/actions/secrets), [rulesets API](https://docs.github.com/en/rest/repos/rules), [GitHub CLI release creation](https://cli.github.com/manual/gh_release_create), and [Gradle distribution verification](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:verification). No dependency major upgrade was inferred from newer documentation examples.
