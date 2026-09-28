---
name: ci-policy
description: Change or review Swypetris GitHub Actions CI policy, including workflow triggers, required checks, and SonarQube status behavior.
---

# Swypetris CI policy

Use this skill for changes to `.github/workflows/ci.yml`, `release-check.yml`, or their CI policy. Consult [publishing/CI.md](../../../publishing/CI.md) for the documented check matrix and Sonar setup.

## Preserve the actual check matrix

- Pull requests targeting `main` run the debug APK build, `lintDebug`, fast debug JVM tests (`:app:testDebugUnitTest`), and JaCoCo report when a non-documentation/non-agent file changes.
- Pushes to `main` run the debug build and lint, all JVM test variants (`:app:test`), and JaCoCo report under the same change filter.
- A `v*` tag or manual dispatch runs the separate release workflow: API 35 emulator instrumentation and launcher smoke check, signed APK/AAB builds, release lint, and artifact signature/archive checks.
- Keep SonarQube Cloud analysis governed by its own configured variables, token availability, and fork restriction. Preserve an established required Quality Gate check; Android test skipping does not imply Sonar is disabled.

The Android change detector skips Android build, lint, JVM tests, and report upload only when every changed path is Markdown or `.codex/agents/*.toml`. If the base commit cannot be resolved or the diff fails, it deliberately runs Android checks. Keep that conservative fallback. Workflow or other code changes must continue to receive the applicable full PR or main checks.

## Required status and workflow edits

Before changing triggers, conditions, job boundaries, or names, identify which exact GitHub status checks branch protection requires. A required check that is conditionally omitted, renamed, or moved can remain pending or disappear from the expected status list even when the work is otherwise green. Prefer skipping expensive steps inside a stable job when appropriate, and verify the resulting check names against the repository's configured requirements.

The CI workflow groups runs by workflow and PR number or ref. It cancels older runs only when a newer run of the same PR starts; main pushes are not canceled. Preserve that separation and confirm the latest PR commit receives the required check. Do not add speculative duplicate workflows or weaken required checks to make a run appear green.

Keep release verification separate from ordinary PR CI. It consumes signing secrets, checks the expected public-certificate fingerprints, and verifies artifacts; it does not publish a release. Keep secrets in GitHub Actions secrets and never emit their values.
