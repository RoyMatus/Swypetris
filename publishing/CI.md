# Continuous integration

The repository has one Android Gradle module, `:app`. It uses AGP 9.1.1, Gradle 9.3.1, Java 11 source/target compatibility, JUnit 4 and Kotest JVM tests through JUnit Platform/Vintage. Android/Compose tests are under `app/src/androidTest`. The full test inventory and audit are in [TESTING.md](../TESTING.md).

## Checks

| Event | Checks |
| --- | --- |
| Pull request to `main` with changes beyond Markdown and agent TOML | Debug APK build, `lintDebug`, `testDebugUnitTest`, JaCoCo XML/HTML report; SonarQube Cloud analysis after setup |
| Push to `main` with changes beyond Markdown and agent TOML | Debug APK build, `lintDebug`, all variant JVM tests via `:app:test`, JaCoCo report; Sonar main analysis after setup |
| Pull request or `main` push changing only Markdown and agent TOML | SonarQube Cloud analysis; Android build, lint, tests, coverage, and report upload skipped |
| `v*` tag or manual release verification | Full Android instrumentation suite on an API 35 emulator, launcher smoke check, signed release APK and AAB builds, `lintRelease`, APK/AAB signature and archive checks |

Test, lint, and coverage reports are attached to CI runs. Signed release artifacts are attached to release verification runs. A successful workflow verifies a candidate; it does not publish a release or replace a manual device/store review.

For changes limited to Markdown and `.codex/agents/*.toml`, Android build, lint, JVM tests, coverage, and report upload are skipped. SonarQube Cloud still runs because its Quality Gate is required for merging. Other changes, including Gradle, manifest, and workflow edits, run the normal PR checks. A newer push to the same PR cancels its older in-progress Android CI run; pushes to `main` are not cancelled.

On Linux/macOS, run the PR checks locally with `bash ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:jacocoDebugUnitTestReport`. Run all JVM variants with `bash ./gradlew :app:test`. Run instrumentation on a selected emulator with `ANDROID_SERIAL=<serial> bash ./gradlew :app:connectedDebugAndroidTest`. Use JDK 21 for the Sonar scanner. On Windows, `tools/Verify-Tests.ps1` runs and summarizes the fast checks or instrumentation on an explicitly selected emulator; see [TESTING.md](../TESTING.md).

JaCoCo XML is at `app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml`. It measures production Kotlin classes compiled for the debug variant, excluding generated `R`, `BuildConfig`, and `Manifest` classes. It includes only JVM-test execution; Android instrumentation/Compose UI coverage is not merged. Do not interpret its overall percentage as complete application coverage.

## SonarQube Cloud setup (Issue #63)

1. Import `RoyMatus/Swypetris` into SonarQube Cloud and choose CI-based analysis. Disable automatic analysis if the imported project enabled it, to avoid duplicate analyses. Record the exact organization key and project key shown there; do not infer either from the GitHub name. Confirm the SonarQube Cloud region and configure it if the project uses the US instance.
2. Add repository **Actions variables** `SONAR_ORGANIZATION` and `SONAR_PROJECT_KEY` with those exact values. If the project is in the US region, also add `SONAR_REGION=us`; the EU instance needs no region variable. Add repository **Actions secret** `SONAR_TOKEN` with a token that has analysis permission. On GitHub, open **Settings → Secrets and variables → Actions → Secrets → New repository secret**. Paste the token there, not in chat, a file, or a PR.
3. Run CI on `main` to establish a baseline, then inspect a PR analysis and confirm that the JaCoCo XML was imported. The scanner is skipped until both variables exist. Pull requests from forks skip token-based analysis because GitHub does not pass repository secrets to forked PR workflows.
4. The project uses the dedicated `Swypetris` Quality Gate: new-code reliability and maintainability ratings must be A, new-code duplication must be at most 3%, and new security issue severity must stay below High. Medium/Low security findings and hotspots require manual review. Coverage is reported without a gate threshold until the test audit in #62. Historical findings do not block the gate. Require the SonarQube Cloud quality-gate GitHub check on `main` after verifying it on a PR.

The scanner excludes Gradle `build` and generated directories plus PNG, OGG, and WAV assets. Kotlin and Java sources remain in scope. Its coverage input is the XML report above. If coverage is missing, check that `:app:jacocoDebugUnitTestReport` ran and that the XML exists in the CI artifact. If scanner authentication fails, check project keys, region, token permission, and the `SONAR_TOKEN` secret without printing the token. For a failed Quality Gate, inspect the SonarQube Cloud project/PR dashboard and the GitHub check.

## Release signing secrets

The local release process uses separate keys for the directly distributed APK and Google Play upload AAB. Configure these **repository Actions secrets** before using `release-check.yml`:

| APK application key | AAB upload key |
| --- | --- |
| `SWYPETRIS_APP_KEYSTORE_BASE64` | `SWYPETRIS_UPLOAD_KEYSTORE_BASE64` |
| `SWYPETRIS_APP_STORE_PASSWORD` | `SWYPETRIS_UPLOAD_STORE_PASSWORD` |
| `SWYPETRIS_APP_KEY_ALIAS` | `SWYPETRIS_UPLOAD_KEY_ALIAS` |

Base64 values must encode the corresponding `.p12` keystore files. The workflow restores them into the runner's temporary directory and does not commit them. Missing secrets deliberately fail the signed-release job. The verification step checks the APK and AAB signatures and compares each signer's SHA-256 certificate fingerprint with the matching public certificate in `publishing/certificates/`. `jarsigner -verify -strict` is deliberately not used: Android upload certificates are self-signed and strict chain validation rejects a valid app bundle.
