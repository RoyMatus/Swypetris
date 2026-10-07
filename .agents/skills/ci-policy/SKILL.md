---
name: ci-policy
description: Change or review Swypetris GitHub Actions CI policy, including workflow triggers, required checks, and SonarQube status behavior.
---

# Swypetris CI policy

Use this skill for changes to `.github/workflows/ci.yml`, `release-check.yml`, or their CI policy. Consult [publishing/CI.md](../../../publishing/CI.md) for the documented check matrix and Sonar setup.

## Preserve the actual check matrix

- Pull requests and pushes to `main` select mapped JVM and Android tests plus an API 35 emulator smoke test for changed app components. Cross-cutting or unknown inputs run full regression.
- Documentation and isolated AI instruction/script changes run no app tests, Java/Gradle setup, or Sonar analysis. GitHub API, release, and CI scripts receive their own focused checks.
- A `v*` tag or manual dispatch runs the separate full release workflow: all JVM and Android correctness tests, debug/release lint, coverage, Sonar, smoke, signed APK/AAB builds, and artifact signature/archive checks.
- Keep `android` from GitHub Actions as the stable required check. App changes require trusted-branch Sonar analysis with valid configuration and `sonar.qualitygate.wait=true`; failed or unavailable Quality Gate results must fail that job. Do not require the separate SonarCloud check for documentation-only changes.

`tools/ci/select_checks.py` owns path classification and the source-to-test map. If the base commit cannot be resolved or the diff fails, it deliberately runs full regression. Keep that conservative fallback. Do not select an empty test suite for app behavior changes. Workflow and build configuration changes run full regression.

## Required status and workflow edits

Before changing triggers, conditions, job boundaries, or names, identify which exact GitHub status checks branch protection requires. A required check that is conditionally omitted, renamed, or moved can remain pending or disappear from the expected status list even when the work is otherwise green. Prefer skipping expensive steps inside a stable job when appropriate, and verify the resulting check names against the repository's configured requirements.

The CI workflow groups runs by workflow and PR number or ref. It cancels older runs only when a newer run of the same PR starts; main pushes are not canceled. Preserve that separation and confirm the latest PR commit receives the required check. Do not add speculative duplicate workflows or weaken required checks to make a run appear green.

Keep release verification separate from ordinary PR CI. It consumes signing secrets, checks the expected public-certificate fingerprints, and verifies artifacts; it does not publish a release. Keep secrets in GitHub Actions secrets and never emit their values.
