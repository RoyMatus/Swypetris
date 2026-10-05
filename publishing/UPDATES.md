# In-app updates

Issue #115 adds downloading and installing the signed universal APK inside Swypetris. Checks at startup, the clickable menu version, and Settings use the same channel selection. APK installations use public GitHub Releases anonymously; installations from `ru.vk.store` use RuStore SDK 10.5.1. A failed RuStore check never switches to GitHub.

## Consent and safe installation

Automatic downloading and installation are disabled until explicit first consent. The consent dialog offers Allow, Manual only, and Later; Settings can disable the preference. Automatic installation waits for the resumed main menu. A running game is never interrupted by an update dialog or installer. Before committing an installation, the existing game model pauses and pending settings, history, session, and update-preference writes are flushed to disk. The existing session format and gameplay rules are unchanged.

Manual updates show download progress, cancellation, readiness, and an Install action. Denying installation permission or postponing an update leaves gameplay available. Postponement suppresses the same offer for 24 hours; manual checking can reopen it. Automatic installation failures require an explicit retry instead of repeated unattended attempts.

## Validation and storage

Release metadata must match `v<versionName>`, the exact repository asset URL, a positive version code, a 64-character SHA-256, and the uploaded asset size (maximum 256 MiB). Downloads follow a bounded set of HTTPS redirects to GitHub's asset hosts. APK size/hash, package ID, universal-package shape, newer version code/name, and compatible signing certificates are checked before installation. Android performs its own final installation verification.

Partial files are deleted after cancellation/failure and on recovery. The validated APK and its metadata use private application storage excluded from backup. An APK is revalidated after restart and before installation; successful updates and invalid/orphaned files are cleaned up. Saved APKs expire after seven days and are removed on the next launch. Installer session IDs and results are persisted independently of the Activity, including when the application's process is replaced.

## Android and RuStore

Android 8+ uses the per-source installation permission, requested only when installation is needed. Earlier supported Android versions retain the system confirmation/security-settings flow. Android 12+ requests `USER_ACTION_NOT_REQUIRED` for a self-update and declares `UPDATE_PACKAGES_WITHOUT_USER_ACTION`; this is a request, not a guarantee. The private explicit mutable result receiver handles `STATUS_PENDING_USER_ACTION`, success, and failures. Confirmation is opened from the foreground UI rather than launching an Activity from a background callback.

Sources checked on 5 October 2026: [Android SessionParams](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)), [Session.commit](https://developer.android.com/reference/android/content/pm/PackageInstaller.Session#commit(android.content.IntentSender)), [SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo), [RuStore SDK 10.5.1](https://www.rustore.ru/help/sdk/updates/kotlin-java/10-5-1), and [RuStore application requirements](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/requirement-apps). RuStore download/completion uses FLEXIBLE for manual updates and SILENT after automatic-update consent. Store/account availability and moderation still require the real RuStore channel; emulator installer checks establish Android APK behavior.

## Release verification

Run the standard Fast checks and API 35 instrumentation suite, then build the signed APK/AAB with `tools/release/Build-Release.ps1`. In addition, exercise a signed old-to-new installation on an isolated pre-Android-12 emulator and a modern emulator. Record the actual installer result, installed version, permission/confirmation behavior, and preservation of settings, history, and the complete session. A flag request, compilation, or APK installation from adb alone is insufficient proof of the in-app installer.

Use local signed fixtures for the installer test before publishing. Keep those fixtures and complete logs in ignored `app/build/verification`. Check public release metadata and asset hashes without authentication after publication; app builds must contain no GitHub credentials.

Verified locally on 5 October 2026: 114 JVM tests passed; API 35 instrumentation completed 97 tests with zero failures and one intentional energy-test skip. Five targeted update/consent tests passed on API 30, including after the permission-return fix. Signed APK/AAB verification passed for 1.4.1/code 8 with the expected application/upload certificates, bundle validation, metadata and hashes.

Signed installer fixtures exercised Android 11 (API 30): permission denial/postponement retained gameplay, permission grant resumed readiness, Android returned STATUS_PENDING_USER_ACTION and displayed its real confirmation, followed by a successful installation. The first API 30 fixture delivered STATUS_SUCCESS; during the final fixture Android replaced the process and did not deliver its terminal callback. The system session history recorded INSTALL_SUCCEEDED (mFinalStatus=1), the installed version became 1.4.1/code 8, and restart recovery cleaned the APK. Android 15 (API 35) completed a consent-enabled self-update from 1.4.0/code 7 to 1.4.1/code 8 without an installation-confirmation dialog, with STATUS_SUCCESS. Saved session and settings remained byte-identical; Android 11 also retained a nonempty game history. The signed 1.4.1 APK was exercised through both installers. A subsequent cleanup-reporting fix checks failed file deletions and is covered by a focused regression test that preserves cancellation. The API 35 launcher smoke showed version 1.4.1 and Continue for the retained game.

These installer fixtures staged the signed APK in private app storage; they prove validation/installation and persistence, not a prepublication live GitHub download. Stream cancellation, truncated/oversized input and checksum rejection are covered by JVM tests. Live RuStore account/store completion was not exercised and is not established by the emulator results.
