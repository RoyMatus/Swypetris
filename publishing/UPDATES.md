# In-app update checks

Swypetris checks for updates on eligible launches at most once an hour; the version button starts a manual check. Settings has no update controls. Installations from `ru.vk.store` use RuStore SDK 10.5.1 and never fall back to GitHub. Direct APK installations check public GitHub Releases anonymously. Gameplay remains available offline, after errors and when an update is declined.

## Installation and consent

As of 1.11.3, the game does not download or install APKs. It declares neither REQUEST_INSTALL_PACKAGES nor UPDATE_PACKAGES_WITHOUT_USER_ACTION, has no PackageInstaller receiver and never opens unknown-source settings. Manifest removal markers also prevent dependencies from reintroducing these permissions.

For a GitHub update, explicit confirmation opens the validated version's HTTPS release page in an external browser. The user can download and install it through their browser and Android. For a RuStore update, the existing FLEXIBLE SDK flow delegates download and installation to the store after explicit player actions. The game pauses and persists its session/settings before requesting store completion. It never demands an update to play.

Legacy private update files and download/installation preferences are discarded on launch. Gameplay settings, records and saves are retained. Earlier automatic-install preferences cannot grant new consent.

## Check policy

The existing durable hourly automatic-check interval, rolling 59 manual starts per hour, joined requests and server cooldown/backoff behavior are preserved. GitHub's anonymous API budget is shared by external IP; local limits cannot guarantee availability. RuStore failures never switch distribution channels.

## Verification

`StoreComplianceTest` inspects the installed package permissions and checks that the GitHub action opens a release page instead of an APK installer. `UpdateDeliveryRecoveryTest` checks legacy cleanup without changing game settings. Update-check/persistence tests cover offline launch, postponement, throttling and durable request reservations.

Release checks must also inspect the final signed manifest and test an actual old-to-new package replacement with the original signer and retained game data. In-app PackageInstaller/unknown-source acceptance is obsolete because that feature was removed. `SignedUpdateScenarioTest.liveGitHubDownloadChecksSignerBeforeInstaller` retains its historical selector for automation compatibility: it downloads and validates the public APK in an opt-in test fixture only; it cannot invoke an installer. Use a compatible application-signed older-version fixture for the required postpublication integrity scenario. Public file/signature verification does not establish RuStore moderation or store-managed update completion.

Sources reviewed 26 October 2026: [RuStore SDK 10.5.1](https://www.rustore.ru/help/sdk/updates/kotlin-java/10-5-1) and [RuStore requirements](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps). The moderator explicitly rejected 1.11.2's REQUEST_INSTALL_PACKAGES declaration; the earlier permission rationale is superseded.

## Historical installer evidence (removed in 1.11.3)

Verified locally on 5 October 2026: 114 JVM tests passed; API 35 instrumentation completed 97 tests with zero failures and one intentional energy-test skip. Five targeted update/consent tests passed on API 30, including after the permission-return fix. Signed APK/AAB verification passed for 1.4.1/code 8 with the expected application/upload certificates, bundle validation, metadata and hashes.

Signed installer fixtures exercised Android 11 (API 30): permission denial/postponement retained gameplay, permission grant resumed readiness, Android returned STATUS_PENDING_USER_ACTION and displayed its real confirmation, followed by a successful installation. The first API 30 fixture delivered STATUS_SUCCESS; during the final fixture Android replaced the process and did not deliver its terminal callback. The system session history recorded INSTALL_SUCCEEDED (mFinalStatus=1), the installed version became 1.4.1/code 8, and restart recovery cleaned the APK. Android 15 (API 35) completed a consent-enabled self-update from 1.4.0/code 7 to 1.4.1/code 8 without an installation-confirmation dialog, with STATUS_SUCCESS. Saved session and settings remained byte-identical; Android 11 also retained a nonempty game history. The signed 1.4.1 APK was exercised through both installers. A subsequent cleanup-reporting fix checks failed file deletions and is covered by a focused regression test that preserves cancellation. The API 35 launcher smoke showed version 1.4.1 and Continue for the retained game.

These installer fixtures staged the signed APK in private app storage; they prove validation/installation and persistence, not a prepublication live GitHub download. Stream cancellation, truncated/oversized input and checksum rejection are covered by JVM tests. Live RuStore account/store completion was not exercised and is not established by the emulator results.
## Update diagnostics and signed fixtures (#181, 8 October 2026)

Download failures now retain an explicit reason (network, incomplete download, integrity, package/version, signer, storage, or persistence). Logcat tag `SwypetrisUpdates` retains the original exception and failing stage. Retry starts a new attempt rather than reopening an old failure. Validation remains strict and invalid files are removed before installation.

Local verification used patched historical-version fixtures (version 1.4.12/code 19), one debug-signed and one signed with the existing application key. The debug fixture downloaded the real GitHub 1.4.13 APK and rejected its incompatible signer with the specific message and no ready APK. The application-signed fixture downloaded and installed the real 1.4.13/code 20 asset through the app's PackageInstaller on API 35; Android's session history recorded success and the installed version was confirmed after process replacement. These are tests of the fix using historical version metadata, not claims that the original reported installation's certificate was inspected.

The emulator network's anonymous release-API request was rate-limited. The opt-in `SignedUpdateScenarioTest` accepts `signedReleaseJson`, an explicitly supplied saved release-API response, for this fixture condition; `update.json` and the APK still download anonymously from GitHub. The production app has no API credential or fixture bypass. A complete anonymous live release-API check remains dependent on GitHub's network rate limit.

When a physical phone is also connected, the locally installed AGP did not isolate `connectedDebugAndroidTest` with either `ANDROID_SERIAL` or `android.injected.device.serial`. Build test APKs locally, then install and run them with explicit `adb -s <emulator-serial>` commands. Keep the terminal `am instrument -w -r` result; an adb exit code alone does not prove test success.
API 30 verification also completed: the same application-signed historical fixture downloaded 1.4.13, presented Android's real INSTALL confirmation, and installed version 1.4.13/code 20 after confirmation. The scenario test passed before process replacement; installed package details and installer-session history are retained separately.
