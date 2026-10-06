# Continuous integration

The repository has one Android Gradle module, `:app`. It uses AGP 9.1.1, Gradle 9.3.1, Java 11 source/target compatibility, JUnit 4 and Kotest JVM tests through JUnit Platform/Vintage. Android/Compose tests are under `app/src/androidTest`. The full test inventory and audit are in [TESTING.md](../TESTING.md).

## Checks

| Event | Checks |
| --- | --- |
| Pull request to `main` or push to `main` changing an app component | Debug APK build, `lintDebug`, mapped JVM and Android test classes, and `SmokeTest` on an API 35 emulator; SonarQube Cloud analysis |
| Shared or unmapped build input | Debug build and lint, full JVM and Android regression, JaCoCo, smoke, and Sonar analysis |
| Documentation or AI instruction/script-only change | SonarQube Cloud analysis; no Android build or tests |
| GitHub API, release, or CI script-only change | Pester, PowerShell syntax, or selector unit tests respectively; no Android build or tests |
| `v*` tag or manual release verification | All JVM, Android, and CI/tool correctness tests, debug/release lint, JaCoCo, Sonar, launcher smoke, signed APK/AAB builds, signature and archive checks |

Available test and lint reports are attached to CI runs; coverage is attached when full regression runs. Signed release artifacts are attached to release verification runs. Tag/manual runs verify a candidate. A main-branch change to `gradle.properties` also runs release verification and publishes the APK only after regression, emulator/smoke, and signed artifact checks all pass. It does not replace a manual device/store review.

`tools/ci/select_checks.py` classifies changes from the event base commit. Repository Markdown outside `app/`, `AGENTS.md`, `.agents/`, and `.codex/` need no app tests. GitHub API, release, and CI scripts have their own checks. Changes to multiple app areas union their mapped test classes. `GameSession`, `GameViewModel`, `MainActivity`, Gradle, manifest, workflow, and any unknown path run full regression; unresolved diffs do too. The selector's output determines the CI steps, and an Android XML report check rejects an empty or incorrectly filtered run. `EnergyScenarioTest` remains an opt-in measurement. SonarQube Cloud still runs for instruction-only changes because its Quality Gate is required for merging. A newer push to the same PR cancels its older in-progress Android CI run; pushes to `main` are not cancelled.

New production Kotlin files must be registered in `SOURCE_GROUPS` or `FULL_SOURCES`; the release selector tests reject unclassified files. Register the layout and pixel tests for a new renderer alongside its mapping.

The mapped Android tests run before Sonar analysis in the same CI job. This keeps the currently required SonarCloud check pending if an app test fails. Release analysis uses the release ref name and waits for its Quality Gate result.

On Linux/macOS, run broad local checks with `bash ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:jacocoDebugUnitTestReport`; CI uses the selector above for focused changes. Run all JVM variants with `bash ./gradlew :app:test`. Run instrumentation on a selected emulator with `ANDROID_SERIAL=<serial> bash ./gradlew :app:connectedDebugAndroidTest`. Use JDK 21 for the Sonar scanner. On Windows, `tools/Verify-Tests.ps1` runs and summarizes the fast checks or instrumentation on an explicitly selected emulator; see [TESTING.md](../TESTING.md).

JaCoCo XML is produced for full regression at `app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml`. Selective CI does not report partial coverage as whole-app coverage. It measures production Kotlin classes compiled for the debug variant, excluding generated `R`, `BuildConfig`, and `Manifest` classes. It includes only JVM-test execution; Android instrumentation/Compose UI coverage is not merged. Do not interpret its overall percentage as complete application coverage.

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

## Cloud GitHub release

Changing `swypetrisVersion` on `main` triggers release verification. Increase the Android versionCode as part of the same release change. Only the final publish job receives contents-write permission; signing jobs keep read-only permissions. Signing keys remain in the runner temporary directory. APK metadata and SHA-256 are read from the verified signed APK, then `Swypetris.apk`, `update.json`, and `SHA256SUMS.txt` are published to a new version tag. An existing release is never overwritten. Tag/manual runs verify artifacts without publishing.
