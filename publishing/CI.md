# Continuous integration

The repository has one Android Gradle module, `:app`. It uses AGP 9.1.1, Gradle 9.3.1, Java 11 source/target compatibility, and JUnit 4 JVM tests. Android/Compose tests are under `app/src/androidTest`.

## Checks

| Event | Checks |
| --- | --- |
| Pull request to `main` | Debug APK build, `lintDebug`, `testDebugUnitTest`, JaCoCo XML/HTML report; SonarQube Cloud analysis after setup |
| Push to `main` | Debug APK build, `lintDebug`, all variant JVM tests via `:app:test`, JaCoCo report; Sonar main analysis after setup |
| `v*` tag or manual release verification | Full Android instrumentation suite on an API 35 emulator, launcher smoke check, signed release APK and AAB builds, `lintRelease`, APK/AAB signature and archive checks |

Test, lint, and coverage reports are attached to CI runs. Signed release artifacts are attached to release verification runs. A successful workflow verifies a candidate; it does not publish a release or replace a manual device/store review.

On Linux/macOS, run the PR checks locally with `bash ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:jacocoDebugUnitTestReport`. Run all JVM variants with `bash ./gradlew :app:test`. Run instrumentation on a selected emulator with `ANDROID_SERIAL=<serial> bash ./gradlew :app:connectedDebugAndroidTest`. On Windows, use `gradlew.bat` and set `$env:ANDROID_SERIAL` first for instrumentation.

JaCoCo XML is at `app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml`. It measures production Kotlin classes compiled for the debug variant, excluding generated `R`, `BuildConfig`, and `Manifest` classes. It includes only JVM-test execution; Android instrumentation/Compose UI coverage is not merged. Do not interpret its overall percentage as complete application coverage.

## SonarQube Cloud setup (Issue #63)

1. Import `RoyMatus/Swypetris` into SonarQube Cloud and choose CI-based analysis. Disable automatic analysis if the imported project enabled it, to avoid duplicate analyses. Record the exact organization key and project key shown there; do not infer either from the GitHub name. Confirm the SonarQube Cloud region and configure it if the project uses the US instance.
2. Add repository **Actions variables** `SONAR_ORGANIZATION` and `SONAR_PROJECT_KEY` with those exact values. Add repository **Actions secret** `SONAR_TOKEN` with a token that has analysis permission. Never add the token to a file or PR.
3. Run CI on `main` to establish a baseline, then inspect a PR analysis and confirm that the JaCoCo XML was imported. The scanner is skipped until both variables exist. Pull requests from forks skip token-based analysis because GitHub does not pass repository secrets to forked PR workflows.
4. Review actual Sonar findings and coverage alongside the test audit in #62. Present proposed hard blockers, warnings, exclusions, and thresholds to the owner as required by #63. Only after the owner chooses them should a Quality Gate or branch protection become merge-blocking. No `sonar.qualitygate.wait` or blocking Sonar check is enabled by this change.

The scanner excludes Gradle `build` and generated directories. Its coverage input is the XML report above. If coverage is missing, check that `:app:jacocoDebugUnitTestReport` ran and that the XML exists in the CI artifact. If scanner authentication fails, check project keys, region, token permission, and the `SONAR_TOKEN` secret without printing the token. For a failed Quality Gate after configuration, inspect the SonarQube Cloud project/PR dashboard and the GitHub check.

## Release signing secrets

The local release process uses separate keys for the directly distributed APK and Google Play upload AAB. Configure these **repository Actions secrets** before using `release-check.yml`:

| APK application key | AAB upload key |
| --- | --- |
| `SWYPETRIS_APP_KEYSTORE_BASE64` | `SWYPETRIS_UPLOAD_KEYSTORE_BASE64` |
| `SWYPETRIS_APP_STORE_PASSWORD` | `SWYPETRIS_UPLOAD_STORE_PASSWORD` |
| `SWYPETRIS_APP_KEY_ALIAS` | `SWYPETRIS_UPLOAD_KEY_ALIAS` |

Base64 values must encode the corresponding `.p12` keystore files. The workflow restores them into the runner's temporary directory and does not commit them. Missing secrets deliberately fail the signed-release job. Check artifact signatures and expected certificate fingerprints before distributing anything; this workflow verifies that the supplied keys signed the files but cannot know whether they are the intended production keys.
