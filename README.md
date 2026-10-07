# Swypetris

[English](#english) · [Русский](#русский) · [Download APK / Скачать APK](https://github.com/RoyMatus/Swypetris/releases/latest)

## English

Swypetris is a free Android falling-block puzzle controlled with one finger. It combines a ten-column playfield, swipe-based movement and rotation, a gesture-operated Hold slot, Marathon progression, and an eight-fruit collection that leads to repeatable victory rounds.

The game supports **Android 7.0 and later** (API 24+). The application interface is currently **in Russian**; this README provides English and Russian documentation. Gameplay works offline, without advertising, purchases, registration, or an online leaderboard.

### Download and start playing

1. Open [GitHub Releases](https://github.com/RoyMatus/Swypetris/releases/latest) and download `Swypetris.apk` from the release assets.
2. Install the APK. Android may ask you to allow installation from the application used to open it.
3. Open Swypetris and choose **Новая игра** (New game). **Как играть** (How to play) contains an illustrated guide inside the app.

The skippable launch introduction assembles the logo from blocks. The main menu has an animated illustrated sky, the Swypetris wordmark, and New game, Continue, Settings, How to play, Results, Contacts, and Exit buttons. Continue is available for an unfinished game. The menu's version number also opens an update check.

### One-finger controls

Use the playfield as the touch surface; there are no separate movement, rotation, drop, or Hold buttons.

| Gesture | Action |
| --- | --- |
| Move left or right | Move horizontally; continuing the movement advances the piece in steps. |
| Swipe up or diagonally up-right | Rotate clockwise. |
| Swipe diagonally up-left | Rotate counterclockwise. |
| Short tap | Move down one cell: manual Soft Drop. |
| Deliberate, predominantly vertical swipe down | Hard Drop to the lowest reachable position and lock immediately. |
| Hold almost still for 300 ms, then swipe up | Store the active piece in Hold, or exchange it with the stored piece. |
| Android Back during play | Pause into the main menu; use Continue to resume. |

Hold is available once until a piece locks. Held pieces return in their initial orientation at the spawn position. Release your finger after a Hold exchange before controlling the replacement. Holding still does not accelerate gravity; releasing a prepared Hold without swiping up cancels it without a Soft Drop. The readiness pulse respects the vibration setting.

Gesture distances use density-independent units and account for Android touch slop. Default thresholds start at 12 dp for horizontal movement, 24 dp for rotation, and 48 dp for Hard Drop. A downward drop needs at least twice as much vertical as horizontal movement. A second finger cancels the gesture. Piece changes reset motion anchors so old movement does not accidentally control the replacement.

### Playfield and rules

- The visible field is **10 columns × 20 rows**, with two additional spawn-display rows at the top. The engine maintains 20 hidden rows above the visible field.
- All seven tetrominoes come from shuffled **seven-bags**, each containing one of every piece type.
- Rotations use **SRS** wall kicks, with separate transition tables for I and for J, L, S, T, Z.
- Grounded pieces have a **500 ms lock delay**. Successful movement or rotation from the ground can reset it at most **15 times per piece**. Hard Drop locks immediately.
- A blocked spawn ends the game. Locking a piece entirely above the visible field also ends it; partly hidden placement is allowed.
- Completed rows disappear column by column over **600 ms**, alternating left-to-right and right-to-left between clear events. Multiple rows clear together. Gravity and piece commands wait until the effect finishes; then the board collapses, points are awarded, and the next piece spawns.

The portrait gameplay surface adapts to available space; cells may be rectangular. In the current HUD, **Hold is at the upper-left**, **the digital score is at the upper-right**, and collected fruits are below the score. Next appears at the actual spawn position when the active piece leaves enough space beneath the preview. It is independent of the optional landing Ghost. Hold dims when unavailable. The score turns gold just before the next level and pulses on line changes.

### Marathon levels and speed

New games use one Marathon speed system. Settings offers a **starting level from 1 to 15**, default 1. Changing it affects the next new game; Continue keeps the current game's starting level.

The scoring level depends on cumulative cleared lines:

```text
level = max(startingLevel, 1 + clearedLines / 10)
```

Here `/` means integer division. A level-1 start reaches level 2 after 10 lines. A level-5 start first advances to level 6 after 50 lines; a level-15 start reaches level 16 after 150 lines. Later levels advance every ten lines, with no level-15 cap. Drop points and fruit rewards do not increase the level.

Gravity follows a Tetris Worlds-style Marathon curve, with progression **stretched by a factor of three after the selected starting level**:

```text
steps = startingLevel - 1 + (level - startingLevel) / 3.0
secondsPerRow = (0.8 - steps × 0.007) ^ steps
```

Initial speed is approximately 1 second per row at starting level 1, 355 ms at starting level 5, 64 ms at starting level 10, and 7.06 ms at starting level 15. Gravity is capped at **20G** (1,200 rows per second), with a minimum interval of 833,334 ns. The 500 ms lock delay remains at high speed. Gravity, lock, and line-clear timing remainders survive pauses and restoration.

### Scoring

The current rules version is **6**. Placement awards use the level **before** the clear. A clear that increases the level does not retroactively receive the new multiplier.

| Placement | Base points, multiplied by level |
| --- | ---: |
| Single / Double / Triple / Tetris | 100 / 300 / 500 / 800 |
| T-Spin Mini without a clear / Single / Double | 100 / 200 / 400 |
| Full T-Spin without a clear / Single / Double / Triple | 400 / 800 / 1,200 / 1,600 |

- **Back-to-back:** eligible consecutive difficult clears receive 1.5× their base award.
- **Combo:** consecutive line-clearing placements add `50 × combo count × level`; the first clear has count zero.
- **Perfect Clear:** emptying the entire logical board adds 800 / 1,200 / 1,800 / 2,000 × level for one / two / three / four lines. A back-to-back four-line Perfect Clear adds 3,200 × level instead.
- **Manual Soft Drop:** 1 point per cell. **Hard Drop:** 2 points per cell. These awards are not multiplied by level.
- Automatic gravity gives no points. Score addition is capped safely at `Int.MAX_VALUE`.

### Fruits and victory rounds

Every **10,000 points** awards the next fruit:

**Cherry → Banana → Grapes → Strawberry → Apple → Pear → Pineapple → Watermelon.**

Collecting all eight completes a round: the first victory threshold is **80,000 points**. A dedicated victory screen shows a block-built trophy, the fruit collection, the completed round, and total score. Play waits for an explicit continuation action.

The next round starts on an empty board while retaining **score, cleared lines, level, starting level, and speed**. Points above the threshold are retained. Each additional round requires another 80,000 cumulative points. Fruit quantities reflect total score, including awards that cross several thresholds at once.

### Pause, autosave, and personal results

Back pauses into the main menu. Backgrounding or losing window focus, including opening the notification shade, also stops play. Returning to the foreground does not resume gameplay automatically: choose Continue.

Local autosave preserves the board, active and next pieces, remaining seven-bag queue, Hold state, score, lines, rounds, starting level, lock delay, and timing progress. Activity recreation restores a paused game; reopening the app offers Continue from the menu. New game replaces the active save. A lost game cannot be continued. Incompatible older active saves are rejected without deleting settings or historical results.

Results shows the latest finished game's score, lines, level, active play time, and fruit collection, plus personal record history. Pauses are excluded from play time. A new personal best opens a record celebration and optional name entry. Current code persists new record-setting results; previously stored history remains available, and records from different rules versions are kept separate. Results are local, without a shared online ranking.

**Reset statistics** asks for confirmation and removes stored history and records, including older ones. Settings and the current game are preserved.

### Settings and appearance

Selections persist between launches:

| Setting | Choices / default |
| --- | --- |
| Starting level | 1–15; default 1; applies to new games. |
| Landing Ghost | On or off; off by default. |
| Sound effects | Independent switch; on by default. |
| Vibration | Independent switch; on by default. |
| Music | Off, shuffle all, or one of eight tracks; shuffle all by default. |
| Color theme | Twelve themes; Classic by default. |
| Automatic updates | Requires explicit consent; can be disabled. |

Themes: **Classic, Monokai, Gruvbox Dark, VS Code Dark+, Dracula, Nord, Solarized Light, Solarized Dark, GitHub Light, Tokyo Night, Catppuccin Mocha, and SynthWave '84**. They affect piece and interface colors, block textures and finishes, and themed artwork. Solarized Light and GitHub Light use light backgrounds. The picker previews the blocks.

### Music, sound, and vibration

The gameplay playlist contains eight bundled electronic arrangements: **Korobeiniki, Kalinka, Kamarinskaya, Barynya, Svetit mesyats, Vo sadu li, v ogorode, Trepak, and Dance of the Sugar Plum Fairy**.

One selected track repeats. Shuffle mode begins each cycle with Korobeiniki, then plays the other seven in random order without repeats. Tracks and repetitions have a 1.5-second gap. The gameplay playlist pauses when navigating away from play; ordinary menu pages, including Settings, use a separate menu theme. Victory and record celebrations use a one-shot fanfare. Music Off disables these musical modes too; effects and vibration remain independent.

Effects use Android's media volume. Haptics accompany supported game events and Hold readiness; enabling vibration gives a test pulse. Devices without amplitude control use shorter fallback pulses. Pausing stops active feedback, while a paused line-clear response can preserve its remaining time. The app does not change system sound or vibration settings.

### Updates, sharing, and privacy

**RuStore installations use the RuStore update SDK; direct APK installations use GitHub Releases.** Checks are available from Settings and the menu version; launch checks follow the app's check policy. Playing does not require a successful network check.

Automatic downloading and installation require explicit consent and run from the main menu after saving the game. The option can be disabled. Android may still require installation permission or confirmation. Direct APK updates are checked for download size, SHA-256, package identity, version, and signing compatibility before installation.

Contacts offers developer links, APK download access, and a sharing dialog with a QR code and Android's share action. These actions open the selected external application; messages are not sent automatically.

The app does not send player names, scores, or settings to the developer. Update requests go to GitHub or RuStore under those services' policies. Settings and historical results may participate in Android backup; active sessions and update files are excluded. See the bundled [privacy policy](app/src/main/assets/privacy.txt), its [HTML version](publishing/privacy.html), and **Contacts → Privacy** inside the app. Credits and bundled license notices are available through **Contacts → Licenses and rights** and [legal-notices.json](app/src/main/assets/legal-notices.json).

### Build and verification

The application uses **Kotlin, Jetpack Compose, and Material 3**. Use **JDK 21** to match CI, an Android SDK supporting the configured compile SDK **36.1**, and the checked-in Gradle wrapper. Configure the SDK path in `local.properties` or your SDK environment. The application ID is `ru.itoltec.swypetris`; release version information is in [gradle.properties](gradle.properties) and [app/build.gradle.kts](app/build.gradle.kts).

Build a debug APK on Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. On Linux or macOS, use `./gradlew` instead of `gradlew.bat`.

Standard local build, lint, JVM tests, and coverage:

```powershell
.\tools\Verify-Tests.ps1 -Suite Fast
# If JAVA_HOME is not configured, add -JavaHome with your JDK 21 directory.
```

For narrower JVM checks, use `:app:testDebugUnitTest` with Gradle's `--tests` filter. Instrumentation requires a ready isolated emulator and an explicit serial:

```powershell
adb devices -l
.\tools\Verify-Tests.ps1 -Suite Android -Serial emulator-5554
```

Replace the example serial with your emulator's actual serial. [TESTING.md](TESTING.md) documents verification and test inventory; [PERFORMANCE.md](PERFORMANCE.md) covers performance measurement. CI selects checks by changed files; documentation-only changes do not need Android regression runs. These commands describe available checks, not a claim that all have run for the current revision.

API documentation: `:app:dokkaGenerate`. Signed distribution builds use [tools/release/Build-Release.ps1](tools/release/Build-Release.ps1), external signing credentials, and output under `dist/<version>/`. See [music generation](tools/music/README.md) and [theme provenance](tools/themes/README.md) for resource details.

### Source layout

Kotlin files below live under `app/src/main/java/ru/itoltec/swypetris/`:

| Files | Responsibility |
| --- | --- |
| `GameEngine.kt` | Android-independent board, pieces, seven-bag, Hold, placement, victory, and loss. |
| `GameRules.kt`, `Placement.kt`, `Srs.kt` | Scoring, progression, placement classification, and rotation kicks. |
| `GameTimeline.kt`, `GameTimer.kt` | Gravity, lock, and line-clear timing. |
| `GestureController.kt` | One-finger gesture recognition. |
| `GameViewModel.kt` | Screen transitions, lifecycle, settings, and game coordination. |
| `GameSession.kt`, `GameStorage.kt`, `GameResults.kt` | Autosave, storage, and personal results. |
| `MainActivity.kt`, `GameLayout.kt`, screen files | Compose UI, Canvas playfield, HUD, and layout. |
| `GamePalette.kt`, `GameArt.kt`, `MenuSkyMotion.kt` | Themes, artwork, and menu sky effects. |
| `GameMusic.kt`, `PlaylistClock.kt`, `AndroidGameFeedback.kt` | Music, playlist timing, effects, and haptics. |
| `AppUpdates.kt`, `Update*.kt` | Update checks, download validation, and installation. |

JVM tests are in `app/src/test/`; Android integration/UI tests are in `app/src/androidTest/`. `tools/` and `.github/workflows/` contain build/release, media generation, verification, and repository automation.

**Developer:** Roy Matus · [itoltec.ru](https://itoltec.ru/) · [Telegram](https://t.me/RoyMatus) · [Email](mailto:piligrim18@gmail.com)

---

## Русский

Swypetris — бесплатная головоломка с падающими блоками для Android, управляемая одним пальцем. В игре сочетаются поле шириной десять клеток, перемещение и повороты жестами, «Запас» (Hold), развитие по правилам Marathon и коллекция из восьми фруктов, после сбора которой можно переходить к следующим победным кругам.

Игра поддерживает **Android 7.0 и новее** (API 24+). Интерфейс приложения сейчас **на русском языке**. Играть можно без интернета; в приложении нет рекламы, покупок, регистрации и онлайн-таблицы лидеров.

### Скачать и начать играть

1. Откройте [GitHub Releases](https://github.com/RoyMatus/Swypetris/releases/latest) и скачайте `Swypetris.apk` из файлов выпуска.
2. Установите APK. Android может попросить разрешить установку из приложения, в котором вы открыли файл.
3. Запустите Swypetris и нажмите **«Новая игра»**. В разделе **«Как играть»** есть иллюстрированная инструкция.

При запуске вступительная анимация собирает логотип из блоков; её можно пропустить. В главном меню расположены анимированное иллюстрированное небо, надпись Swypetris и кнопки «Новая игра», «Продолжить», «Настройки», «Как играть», «Результаты», «Контакты» и «Выход». «Продолжить» доступна для незавершённой партии. Нажатие на номер версии в меню позволяет проверить обновления.

### Управление одним пальцем

Жесты выполняются на игровом поле; отдельных кнопок перемещения, поворота, броска и «Запаса» нет.

| Жест | Действие |
| --- | --- |
| Движение влево или вправо | Сдвиг активной фигуры; дальнейшее движение перемещает её по шагам. |
| Свайп вверх или вверх-вправо | Поворот по часовой стрелке. |
| Свайп вверх-влево | Поворот против часовой стрелки. |
| Короткий тап | Спуск на одну клетку: ручной Soft Drop. |
| Выраженный преимущественно вертикальный свайп вниз | Мгновенный бросок до упора и немедленная фиксация: Hard Drop. |
| Почти неподвижное удержание 300 мс, затем свайп вверх | Убрать активную фигуру в «Запас» или обменять её с сохранённой. |
| Системная кнопка или жест «Назад» во время игры | Пауза и главное меню; возобновление кнопкой «Продолжить». |

«Запас» доступен один раз до фиксации фигуры. Фигура из запаса появляется в исходном положении и ориентации. После обмена отпустите палец, прежде чем управлять заменой. Неподвижное удержание не ускоряет падение; отпускание после подготовки «Запаса» без свайпа вверх отменяет действие без спуска на клетку. Импульс готовности учитывает настройку вибрации.

Расстояния жестов задаются в независимых от плотности экрана единицах с учётом системного порога касания Android. Базовые пороги: 12 dp для горизонтального перемещения, 24 dp для поворота и 48 dp для броска. Для броска вертикальное смещение должно как минимум вдвое превышать горизонтальное. Второй палец отменяет жест. При смене фигуры опорные точки сбрасываются, чтобы предыдущее движение не вызвало случайную команду для новой фигуры.

### Поле и правила

- Видимое поле — **10 столбцов × 20 строк**, сверху отображаются ещё две строки для появления фигур. В логике игры над видимым полем предусмотрено 20 скрытых строк.
- Семь видов тетромино поступают из перемешанных **мешков по семь фигур**, по одной фигуре каждого вида в мешке.
- Повороты используют **SRS** со смещениями у стен и препятствий. Для I и для J, L, S, T, Z используются отдельные таблицы переходов.
- После касания опоры действует **задержка фиксации 500 мс**. Успешное перемещение или поворот с опоры может сбросить её не более **15 раз для одной фигуры**. Мгновенный бросок фиксирует фигуру сразу.
- Если новая фигура не помещается в месте появления, партия заканчивается. Полная фиксация выше видимого поля также означает проигрыш; частично скрытое размещение допускается.
- Строки исчезают по столбцам за **600 мс**. Направление чередуется между событиями очистки: слева направо, затем справа налево. Несколько строк очищаются одновременно. До завершения эффекта падение и команды остановлены; затем блоки сдвигаются, начисляются очки и появляется следующая фигура.

Игровая поверхность в портретной ориентации подстраивается под доступное пространство; клетки могут быть прямоугольными. В текущем интерфейсе **«Запас» находится слева сверху**, **цифровой счёт — справа сверху**, фрукты — под счётом. Следующая фигура показывается в месте появления, когда активная фигура оставляет достаточно места под превью. Эта подсказка не зависит от «Тени падения». Недоступный запас отображается приглушённо. Перед следующим уровнем счёт становится золотым; изменение числа линий запускает пульсацию.

### Уровни и скорость Marathon

Для новых партий используется единая система скорости Marathon. В настройках выбирается **начальный уровень от 1 до 15**, по умолчанию — 1. Изменение применяется к следующей новой партии; продолжение сохраняет начальный уровень текущей игры.

Уровень для начисления очков определяется общим числом очищенных линий:

```text
уровень = max(начальный уровень, 1 + очищенные линии / 10)
```

Здесь `/` означает целочисленное деление. При старте с уровня 1 переход на уровень 2 происходит после 10 линий. При старте с уровня 5 первый переход на уровень 6 — после 50 линий; при старте с уровня 15 переход на уровень 16 — после 150 линий. Далее уровень повышается через каждые десять линий, без ограничения уровнем 15. Очки за спуск и награды-фрукты уровень не повышают.

Скорость основана на кривой Marathon в стиле Tetris Worlds, но её развитие **растянуто втрое после выбранного начального уровня**:

```text
steps = начальный уровень - 1 + (уровень - начальный уровень) / 3.0
секундыНаСтроку = (0.8 - steps × 0.007) ^ steps
```

Начальная скорость — примерно 1 секунда на строку для уровня 1, 355 мс для уровня 5, 64 мс для уровня 10 и 7,06 мс для уровня 15. Максимум — **20G**, то есть 1 200 строк в секунду; минимальный интервал — 833 334 нс. Задержка фиксации 500 мс сохраняется и на высокой скорости. При паузе и восстановлении сохраняются остатки интервалов падения, фиксации и очистки строк.

### Начисление очков

Текущая версия правил — **6**. Награда за размещение рассчитывается по уровню **до** удаления строк: очистка, повысившая уровень, не пересчитывается с новым множителем.

| Размещение | Базовые очки, умножаемые на уровень |
| --- | ---: |
| Одна / две / три / четыре строки | 100 / 300 / 500 / 800 |
| T-Spin Mini без очистки / с одной / с двумя строками | 100 / 200 / 400 |
| Полный T-Spin без очистки / с одной / с двумя / с тремя строками | 400 / 800 / 1 200 / 1 600 |

- **Back-to-back:** последовательные подходящие сложные очистки получают 1,5× базовой награды.
- **Комбо:** последовательные размещения с очисткой добавляют `50 × номер комбо × уровень`; у первой очистки номер равен нулю.
- **Perfect Clear:** полное опустошение логического поля добавляет 800 / 1 200 / 1 800 / 2 000 × уровень за одну / две / три / четыре строки. Для back-to-back Perfect Clear на четыре строки добавляется 3 200 × уровень вместо 2 000.
- **Ручной спуск:** 1 очко за клетку. **Мгновенный бросок:** 2 очка за клетку. Эти награды не умножаются на уровень.
- Автоматическое падение очков не даёт. Счёт защищён от переполнения и ограничен значением `Int.MAX_VALUE`.

### Фрукты и победные круги

Каждые **10 000 очков** дают следующий фрукт:

**Вишня → Банан → Виноград → Клубника → Яблоко → Груша → Ананас → Арбуз.**

Сбор всех восьми завершает круг: первая победа наступает при **80 000 очков**. Отдельный экран победы показывает кубок из блоков, коллекцию фруктов, пройденный круг и общий счёт. Продолжение требует явного нажатия кнопки.

Следующий круг начинается на пустом поле, сохраняя **очки, очищенные линии, уровень, начальный уровень и скорость**. Очки сверх порога сохраняются. Каждый следующий круг требует ещё 80 000 очков в общем счёте. Количество фруктов определяется суммарными очками, включая награды, пересекающие сразу несколько порогов.

### Пауза, автосохранение и личные результаты

«Назад» ставит игру на паузу и возвращает в главное меню. Уход в фон или потеря фокуса окна, включая открытие шторки уведомлений, также останавливают игру. Возврат в приложение не возобновляет её автоматически: нужно нажать «Продолжить».

Локальное автосохранение включает поле, активную и следующую фигуры, остаток мешка, состояние запаса, очки, линии, круги, начальный уровень, задержку фиксации и прогресс таймеров. При пересоздании Activity партия восстанавливается на паузе; после повторного запуска меню предлагает продолжить игру. Новая партия заменяет активное сохранение. Проигранную партию продолжить нельзя. Несовместимые старые активные сохранения отклоняются без удаления настроек и прежних результатов.

Результаты показывают очки, линии, уровень, активное время и фрукты последней завершённой партии, а также историю личных рекордов. Паузы не входят в игровое время. Новый рекорд открывает поздравление и необязательный ввод имени. Текущий код сохраняет новые рекордные результаты; прежняя история остаётся доступной, а рекорды разных версий правил учитываются отдельно. Результаты локальные, общей онлайн-таблицы нет.

**«Сбросить статистику»** требует подтверждения и удаляет сохранённую историю и рекорды, включая старые. Настройки и текущая партия сохраняются.

### Настройки и оформление

Выбранные параметры сохраняются между запусками:

| Настройка | Варианты / значение по умолчанию |
| --- | --- |
| Начальный уровень | 1–15; по умолчанию 1; для новых партий. |
| Тень падения | Включена или выключена; по умолчанию выключена. |
| Звуковые эффекты | Независимый переключатель; по умолчанию включены. |
| Вибрация | Независимый переключатель; по умолчанию включена. |
| Музыка | Выключена, случайный порядок или одна из восьми композиций; по умолчанию случайный порядок. |
| Цветовая тема | Двенадцать тем; по умолчанию классическая. |
| Автоматические обновления | После явного согласия; можно отключить. |

Темы: **Классическая, Monokai, Gruvbox Dark, VS Code Dark+, Dracula, Nord, Solarized Light, Solarized Dark, GitHub Light, Tokyo Night, Catppuccin Mocha и SynthWave '84**. Они меняют цвета фигур и интерфейса, текстуры и обработку блоков, тематическое оформление иллюстраций. Solarized Light и GitHub Light используют светлый фон. В настройках есть предварительный просмотр блоков.

### Музыка, звуки и вибрация

Игровой плейлист содержит восемь встроенных электронных аранжировок: **«Коробейники», «Калинка», «Камаринская», «Барыня», «Светит месяц», «Во саду ли, в огороде», «Трепак» и «Танец Феи Драже»**.

Выбранная композиция повторяется. В случайном режиме каждый цикл начинается с «Коробейников», затем остальные семь звучат в случайном порядке без повторов. Между записями и повторами — 1,5 секунды тишины. При выходе из игры плейлист приостанавливается; обычные страницы меню, включая настройки, используют отдельную мелодию меню. Для победы и рекорда предусмотрены однократные фанфары. Выбор «Выключена» отключает и эти музыкальные режимы; эффекты и вибрация управляются независимо.

Эффекты используют медиагромкость Android. Вибрация сопровождает предусмотренные игровые события и готовность запаса; включение переключателя даёт проверочный импульс. На устройствах без управления амплитудой используются более короткие импульсы. Пауза останавливает активный отклик, при этом остаток отклика очистки строк может сохраняться. Приложение не меняет системные настройки звука и вибрации.

### Обновления, обмен ссылкой и конфиденциальность

**Для установки из RuStore используется SDK обновлений RuStore; для прямой установки APK — GitHub Releases.** Проверка доступна в настройках и по нажатию на версию в меню; проверки при запуске выполняются по правилам приложения. Успешное подключение к сети для игры не требуется.

Автоматическая загрузка и установка требуют явного согласия и выполняются из главного меню после сохранения партии. Режим можно отключить. Android при необходимости запрашивает разрешение или подтверждение установки. Для прямых APK проверяются размер загрузки, SHA-256, имя пакета, версия и совместимость подписи.

В контактах есть ссылки разработчика, доступ к скачиванию APK и диалог обмена ссылкой с QR-кодом и системной функцией «Поделиться». Эти действия открывают выбранное внешнее приложение; сообщения не отправляются автоматически.

Игра не передаёт разработчику имя игрока, результаты или настройки. Запросы обновлений обрабатываются GitHub или RuStore по правилам этих сервисов. Настройки и история могут включаться в резервные копии Android; активная партия и файлы обновлений исключены. Подробности — во встроенной [политике конфиденциальности](app/src/main/assets/privacy.txt), её [HTML-версии](publishing/privacy.html) и на странице **«Контакты → Конфиденциальность»**. Сведения об источниках и сторонних лицензиях доступны в **«Контакты → Лицензии и права»** и [legal-notices.json](app/src/main/assets/legal-notices.json).

### Сборка и проверки

Приложение написано на **Kotlin с Jetpack Compose и Material 3**. Используйте **JDK 21**, как в CI, Android SDK с поддержкой настроенного compile SDK **36.1** и Gradle wrapper из репозитория. Укажите путь к SDK в `local.properties` или настройте окружение SDK. Идентификатор приложения — `ru.itoltec.swypetris`; версия задаётся в [gradle.properties](gradle.properties) и [app/build.gradle.kts](app/build.gradle.kts).

Сборка отладочного APK в Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

Результат: `app/build/outputs/apk/debug/app-debug.apk`. В Linux и macOS используйте `./gradlew` вместо `gradlew.bat`.

Стандартные локальные проверки сборки, lint, JVM-тестов и покрытия:

```powershell
.\tools\Verify-Tests.ps1 -Suite Fast
# Если JAVA_HOME не настроен, добавьте -JavaHome с путём к каталогу JDK 21.
```

Для узких JVM-проверок используйте `:app:testDebugUnitTest` с фильтром Gradle `--tests`. Инструментальные тесты требуют готового изолированного эмулятора и явного серийного номера:

```powershell
adb devices -l
.\tools\Verify-Tests.ps1 -Suite Android -Serial emulator-5554
```

Замените пример фактическим номером своего эмулятора. Порядок проверок и перечень тестов описаны в [TESTING.md](TESTING.md), измерения производительности — в [PERFORMANCE.md](PERFORMANCE.md). CI выбирает проверки по изменённым файлам; для изменений только документации Android-регрессия не требуется. Эти команды описывают доступные проверки и не означают, что все они выполнены для текущей ревизии.

API-документация: `:app:dokkaGenerate`. Подписанные сборки выпускает [tools/release/Build-Release.ps1](tools/release/Build-Release.ps1), используя учётные данные подписи вне репозитория; результат помещается в `dist/<версия>/`. Подробности ресурсов: [генерация музыки](tools/music/README.md) и [происхождение тем](tools/themes/README.md).

### Структура кода

Kotlin-файлы ниже находятся в `app/src/main/java/ru/itoltec/swypetris/`:

| Файлы | Назначение |
| --- | --- |
| `GameEngine.kt` | Поле, фигуры, мешок, запас, размещение, победа и проигрыш без зависимости от Android. |
| `GameRules.kt`, `Placement.kt`, `Srs.kt` | Очки, уровни, классификация размещений и смещения поворотов. |
| `GameTimeline.kt`, `GameTimer.kt` | Таймеры падения, фиксации и очистки строк. |
| `GestureController.kt` | Распознавание жестов одним пальцем. |
| `GameViewModel.kt` | Переходы экранов, жизненный цикл, настройки и координация игры. |
| `GameSession.kt`, `GameStorage.kt`, `GameResults.kt` | Автосохранение, хранилище и личные результаты. |
| `MainActivity.kt`, `GameLayout.kt`, файлы экранов | Compose-интерфейс, поле на Canvas, индикаторы и компоновка. |
| `GamePalette.kt`, `GameArt.kt`, `MenuSkyMotion.kt` | Темы, иллюстрации и эффекты неба в меню. |
| `GameMusic.kt`, `PlaylistClock.kt`, `AndroidGameFeedback.kt` | Музыка, таймер плейлиста, эффекты и вибрация. |
| `AppUpdates.kt`, `Update*.kt` | Проверка обновлений, проверка загрузок и установка. |

JVM-тесты находятся в `app/src/test/`, Android-тесты интеграции и интерфейса — в `app/src/androidTest/`. `tools/` и `.github/workflows/` содержат сборку и выпуск, генерацию ресурсов, проверки и автоматизацию репозитория.

**Разработчик:** Roy Matus · [itoltec.ru](https://itoltec.ru/) · [Telegram](https://t.me/RoyMatus) · [Email](mailto:piligrim18@gmail.com)
