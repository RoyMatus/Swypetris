# Current local release process

All release builds and checks run locally. Verification and publication are separate commands; neither a version edit nor a merge automatically publishes anything. Complete the applicable [release compliance review](RELEASE-COMPLIANCE.md) before publication.

Before delivery, run `tools/release/Test-DeliveryEnvironment.ps1 -JavaHome '<JDK 21>' -Python '<python executable>' -CheckSigning`. This read-only preflight reports checkout/head, stale tracking-main, executable readiness and repository permissions without printing credentials. Read access does not prove fine-grained token permission for a write endpoint. Use the existing HTTPS/GCM credential for automated Git transfers on this Windows host; preserve SSH configuration. `Invoke-CheckedGit` in `tools/Verification.psm1` checks exit codes and bounds process/output waiting. After an ambiguous push, inspect the remote SHA before retrying. Compare fetched main with the GitHub API before creating a branch.

`Build-Release.ps1` accepts explicit `-JavaHome` (defaults to process JAVA_HOME). Signature verification always passes that JDK to apksigner and restores the previous process environment, including on failure. No machine/user environment change is needed.

1. Check out the clean exact current remote `main` commit. Finish `tools/Invoke-LocalChecks.ps1 -Mode Full` with all required local platform evidence and Sonar Quality Gate, or use the validated identical-tree Full PR evidence promotion in [CI.md](CI.md), which reruns main Sonar. Both paths produce exact-main Full evidence. A selected PR gate cannot be promoted.
2. Verify the signed candidate on an explicitly selected isolated emulator:

```powershell
./tools/release/Verify-LocalRelease.ps1 -EvidencePath '<full-local-evidence.json>' `
  -Serial emulator-5560 -Python '<python executable>' -JavaHome '<JDK 21>' -MaxWorkers 4
```

The verifier checks full evidence, APK/AAB signatures and public certificate fingerprints, then resets only Swypetris on the selected emulator for signed runtime smoke. This can erase that emulator's Swypetris data. It produces an immutable snapshot and `local-release-provenance.json` under ignored verification output, binding the exact commit/version, artifact/report hashes, certificates and diagnostics. It does not publish or replace physical-device/store acceptance.

3. Only when publication is explicitly authorized, review English notes and preview the separate publisher:

```powershell
./tools/release/Publish-LocalRelease.ps1 -ProvenancePath '<local-release-provenance.json>' `
  -NotesFile '<reviewed English release notes>' -JavaHome '<JDK 21>' -Python '<python executable>' -WhatIf
# Remove -WhatIf only for the authorized publication.
```

The publisher refuses an existing release/tag, stages assets as a draft and publishes only after validation. Never overwrite protected tags or a release after a partial failure; inspect and preserve diagnostics before recovery. After publication, use `Verify-PublishedRelease.ps1 -Tag v<version> -LocalProvenance -JavaHome <JDK>` to validate public files. The historical `-ReleaseRunId` path remains available for releases published by the former cloud process.

Signing material remains outside the repository in `$env:USERPROFILE/.swypetris-signing` by default (`-SigningDirectory` can select an authorized alternative). Preserve the separate app/upload keys and DPAPI credentials. Never print/upload keys or credentials. `Build-Release.ps1` alone builds and verifies artifact signatures but does not provide full regression/runtime evidence or authorize publication. Backup instructions below remain applicable; the version-specific store text is historical.

---

# Выпуск Swypetris 1.4.1

Этот документ описывает исторический выпуск 1.4.1. Для каждого нового существенного релиза сначала выполните [единый актуальный аудит RuStore и GitHub](RELEASE-COMPLIANCE.md) и заполните [отчёт](templates/RELEASE-COMPLIANCE-REPORT.md) по точным файлам кандидата. Читайте полные текущие требования RuStore и актуальные источники GitHub; старый текст ниже не подтверждает соответствие нового выпуска. Не публикуйте при обязательных нарушениях или непроверенных обязательных пунктах. Технические проверки описаны в [CI.md](CI.md).

Идентификатор `ru.itoltec.swypetris`, код версии `8`, Android 7.0+ (minSdk 24), targetSdk 36. Игра бесплатная, без рекламы, покупок и регистрации. Инструкция проверена по официальным источникам 21 сентября 2026 года; магазины могут обновить формы и требования.

## Готовые файлы

- `dist/1.4.1/Swypetris.apk` — установка на Android и загрузка в RuStore; подписан постоянным ключом приложения.
- `dist/1.4.1/Swypetris-1.4.1.aab` — загрузка в Google Play; подписан отдельным upload key. На телефон как обычный APK не устанавливается.
- `dist/1.4.1/SHA256SUMS.txt` — контрольные суммы.
- `store-listing-ru.md`, `media/` — описание и изображения карточки.
- `privacy.html` — публичная политика, которую ещё нужно разместить на сайте.

APK публикуется как asset `Swypetris.apk` публичного GitHub Release `v1.4.1`. Постоянная ссылка в новых версиях приложения: `https://github.com/RoyMatus/Swypetris/releases/latest/download/Swypetris.apk`. До публикации asset эта ссылка не работает; проверьте загрузку без входа в GitHub перед распространением QR-кода. Уже установленная версия 1.0.0 по-прежнему указывает на неопубликованный `v1.0.0`. AAB предназначен для Google Play и не публикуется как APK для установки.

Старый тестовый пакет предыдущей версии — другое приложение. Релиз устанавливается рядом и не удаляет его историю. Перенос сохранений между пакетами не реализован.

## RuStore: бесплатная публикация физлицом

1. Войдите в [RuStore Консоль](https://console.rustore.ru/), создайте аккаунт разработчика-физлица и завершите предложенную проверку. [Регистрация бесплатна](https://www.rustore.ru/developer), продажа приложения не требуется.
2. Приложения → Добавить приложение → название **Swypetris** → Загрузить версию. Выберите APK из списка выше. Игра предназначена для телефона/планшета, не отмечайте поддержку ТВ.
3. Заполните название, краткое и полное описание из `store-listing-ru.md`, категорию «Головоломки», контакты и сведения о данных. Возрастную маркировку выберите по фактическому содержимому и анкете магазина.
4. Загрузите непрозрачный `icon-512.png` и минимум три скриншота из `media/`. Дополнительный баннер предназначен для Google Play.
5. Разместите `privacy.html` на публичном HTTPS-сайте и укажите рабочую ссылку. Проверьте страницу без входа в аккаунт. Предлагаемый адрес: `https://itoltec.ru/swypetris/privacy/`; сам адрес ещё не означает, что файл там опубликован.
6. Укажите бесплатное распространение, проверьте доступ ко всем функциям без авторизации, отправьте на модерацию. После одобрения опубликуйте версию согласно выбранному режиму консоли.

[Официальная инструкция RuStore](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication). Последующие APK должны иметь тот же пакет и сертификат, а `versionCode` нужно увеличивать.

## Google Play: бесплатная игра, платная регистрация

1. Создайте **личный** аккаунт [Play Console](https://play.google.com/console/). Разовый регистрационный взнос — **25 долларов США**, даже если все игры бесплатные. Пройдите проверку личности, контактов и Android-устройства, которую запросит консоль. [Начало работы](https://support.google.com/googleplay/android-developer/answer/6112435?hl=ru).
2. Россия указана среди стран, поддерживающих регистрацию разработчиков; доступность оплаты конкретной картой проверяется в консоли. Не подменяйте страну проживания и документы. При отказе платежа обратитесь в поддержку Google. Бесплатные приложения остаются доступны в РФ. [Страны](https://support.google.com/googleplay/android-developer/answer/9306917?hl=en), [ограничения платежей](https://support.google.com/googleplay/android-developer/answer/11950272?hl=en).
3. Создайте приложение **Swypetris**, язык русский, тип «Игра», цена «Бесплатно». Заполните карточку материалами из этой папки: иконка, баннер 1024 × 500, скриншоты, контактный email, описание.
4. До первой загрузки правильно настройте **Play App Signing**: выберите собственный ключ подписи приложения, а не новый ключ, генерируемый Google. Экспортируйте `swypetris-app.p12` официальной утилитой PEPK по инструкции и с ключом шифрования из вашей консоли. Пароль ключа вводится локально. Зарегистрируйте отдельный upload certificate `certificates/swypetris-upload.pem`; затем загружайте AAB, подписанный upload key. Если экран предлагает использовать сертификат первой загрузки как постоянный ключ приложения, сначала завершите передачу app key. [Официальная инструкция по подписи](https://developer.android.com/studio/publish/app-signing).
5. Заполните «Контент приложения»: наличие рекламы — нет; вход для проверки не требуется; анкета рейтинга — по содержимому; целевая аудитория — ваше фактическое решение, а не автоматически назначенный возрастной рейтинг. Заполните Data safety после сверки определений с локальным хранением, системным backup и сетевой проверкой обновлений через RuStore SDK или GitHub. Укажите публичную политику; её текст также есть внутри игры. [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).
6. Для личного аккаунта, созданного после 13 ноября 2023 года: организуйте закрытое тестирование минимум с **12 участниками**, подключёнными **непрерывно 14 дней**. Пригласите реальных участников, дайте ссылку подключения, соберите обратную связь. Внутреннее тестирование не заменяет это требование.
7. После выполнения условий запросите доступ к production и ответьте на вопросы о тестировании. Одобрение не автоматическое. Затем создайте производственный выпуск, укажите страны распространения и отправьте на проверку. [Требования тестирования](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

Новый выпуск Google Play использует **AAB**, не этот универсальный APK. [Android App Bundle](https://developer.android.com/guide/app-bundle). Аккаунты магазинов, платежи, загрузка PEPK и отправка на модерацию выполняются владельцем; подготовка файлов сама по себе не публикует игру.

## Обновление внутри приложения

Версия 1.4.1 загружает подписанный APK внутри приложения и передаёт его установщику Android. Автоматическая загрузка и установка включаются после первого явного согласия; установка ожидает главное меню и сохранение партии. Режим можно отключить в настройках. Разрешение установки запрашивается только при необходимости. Канал RuStore использует SDK магазина. Проверки APK, хранение, ограничения Android и порядок проверки выпуска описаны в [UPDATES.md](UPDATES.md).

## Повторная сборка и сохранность ключей

Нужны JDK 21, Android SDK 36.1, PowerShell 7.4+. Задайте `JAVA_HOME` и путь SDK в локальном `local.properties` (не в Git). Затем:

```powershell
./tools/release/Build-Release.ps1
```

Локальная сборка повторно использует Gradle daemon и build cache. Для диагностики можно передать `-NoDaemon`; задачи APK, JVM, release lint и AAB сохраняются. Нужны `JAVA_HOME` и Android SDK. Скрипт проверяет APK через apksigner, AAB через jarsigner и сверяет сертификаты с `publishing/certificates/` до выдачи результата. Логи и JSON с общим временем, временем каждого ключа и признаком повторного использования JVM-результатов находятся в игнорируемом `app/build/verification/release-*`. Время прогретой сборки сравнивайте при одинаковых исходниках и версии; `UP-TO-DATE` / `FROM-CACHE` не означают новое выполнение тестов. Обязательные проверки публикации сохраняются.

Скрипт собирает полный комплект во временном каталоге внутри `dist` на том же диске. Блокировка версии исключает одновременную сборку одного выпуска. После успешных проверок APK и AAB он формирует контрольные суммы только для `Swypetris.apk`, `Swypetris-<version>.aab`, `update.json` и `RuStore-icon.png`, затем заменяет `dist/<version>` с резервным переименованием предыдущего каталога. Ошибка сборки сохраняет предыдущий комплект; ошибка переноса вызывает восстановление. При принудительном завершении процесса между переименованиями может остаться каталог `.backup`: сохраните его и проверьте комплект перед ручным восстановлением. Это не гарантия восстановления после аварийного отключения питания.

Для нового выпуска измените `swypetrisVersion` в `gradle.properties` и увеличьте `versionCode` в `app/build.gradle.kts`. После локальной проверки точного коммита `main` публикация выполняется отдельной явно разрешённой командой, описанной выше. Автоматического выпуска больше нет. Локальная сборка сама ничего не публикует. Без `update.json` приложение не сможет подтвердить доступность обновления. Проверьте публичные ссылки без авторизации до распространения сборки.

Ключи находятся вне проекта, в `$env:USERPROFILE/.swypetris-signing`. `swypetris-app.p12` — неизменная идентичность приложения; `swypetris-upload.p12` — ключ загрузки Google Play. Пароли в `app.clixml` и `upload.clixml` защищены Windows DPAPI и читаются только тем же пользователем на этой Windows. Не сохраняйте эти файлы в Git, общие папки или релизы GitHub.

**До переустановки Windows обязательно создайте переносимую копию.** Выберите сильный пароль, сохраните его в менеджере паролей, а архив — на отдельном офлайн-носителе:

```powershell
./tools/release/Export-SigningBackup.ps1 -Destination 'E:/Swypetris-signing-backup.json'
```

Скрипт запрашивает пароль локально и шифрует оба ключа вместе с их паролями AES-256-GCM. `E:` — пример вашего резервного носителя. Для восстановления в новый каталог на другой Windows:

```powershell
./tools/release/Restore-SigningBackup.ps1 -Backup 'E:/Swypetris-signing-backup.json'
```

Не запускайте инициализацию новых ключей вместо восстановления утраченных. Потеря app key может сделать дальнейшее обновление APK в RuStore невозможным. Пароли и ключи не отправляйте в чат.

## История Git

Проект существовал до создания репозитория. Первые 10 коммитов — последовательный импорт готовых подсистем с настоящими датами, а не выдуманная хронология разработки. Промежуточные коммиты импорта не обещают самостоятельный работоспособный выпуск; первым опубликованным тегом будет `v1.4.1`.
