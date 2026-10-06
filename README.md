# Swypetris

Тетрис для Android 7.0+ на Kotlin и Jetpack Compose, управляемый одним пальцем.

## Управление

| Жест | Действие |
| --- | --- |
| Движение влево / вправо | Сдвиг фигуры; дальнейшие шаги связаны с шириной клетки |
| Свайп вверх или вверх-вправо | Поворот по часовой стрелке |
| Свайп вверх-влево | Поворот против часовой стрелки |
| Длинный направленный жест вниз | Мгновенный бросок до упора |
| Short tap | One manual cell downward; grounded pieces retain their lock delay |
| Hold nearly still for 300 ms, then swipe up | Store or retrieve the held piece; once per lock, then release |
| Неподвижное удержание | Не вызывает действий и не ускоряет гравитацию |
| Системный «Назад» | Пауза и главное меню с Continue |

Пороги в dp: горизонталь — от 12, поворот — от 24, бросок — от 48; больший системный touch slop увеличивает пороги. Для поворота подъём должен быть не меньше горизонтального смещения, делённого на 1,5. Отклонение влево от 12 dp (или большего touch slop) выбирает поворот против часовой; почти вертикальный жест и правая диагональ — по часовой. Один штрих поворачивает только один раз. Для броска вертикальное смещение должно минимум вдвое превышать горизонтальное. Неопределённые диагонали вниз не вызывают действий. Второй палец отменяет жест. При смене фигуры палец остаётся активным, базовая точка обновляется; появление фигуры само по себе не вызывает движения или тапа. Во время удаления строк координаты продолжают отслеживаться без игровых команд.

## Экран и меню

The 10-column grid fills the entire screen, including two spawn-display rows above the 20 visible gameplay rows; cells may be rectangular. The transparent borderless HUD labels the actual score at the upper-left, Next at the engine spawn position, and Hold at the upper-right. Next uses thin theme-aware outlines with a faint flat fill, at the same cell size as the board. Its label and preview appear only after the active piece leaves one empty row between itself and the preview, independently of the landing Ghost setting. Labels respect display cutouts, fit narrow screens and enlarged fonts, and a long score stays left of the spawn lane. Hold has subtle corner brackets and dims when unavailable; an empty Hold draws no placeholder. Earned fruits remain below Hold. The grid and gesture surface retain fullscreen bounds. At nine lines within a level, the score turns gold; a line change starts a single 220 ms pulse without score changes restarting it. System insets are respected by the corner HUD.

Вверху меню расположен новый логотип: полосатый силуэт собора Василия Блаженного и надпись из цветных блоков. Ниже — компактная сетка прямоугольных кнопок без прокрутки в порядке «Новая игра», «Продолжить» (для незавершённой партии), «Настройки», «Как играть», «Результаты», «Контакты», «Выход». Рекорды показаны только на странице результатов. Ориентация приложения на телефоне фиксирована: поворот устройства не переворачивает экран.

Новые партии используют единую скорость Marathon по кривой Tetris Worlds; выбор Лёгкая / Средняя / Сложная удалён. Начальный уровень 1–15 выбирается для новой партии; продолжение сохраняет уровень начатой партии. Также доступны независимые переключатели «Тень падения», «Звук», «Вибрация» и выпадающий список «Музыка». Звук, музыка и вибрация по умолчанию включены; тень падения выключена. Следующая фигура показана контуром в исходной ориентации прямо в месте появления и доступна экранному диктору; рядом с активной фигурой превью временно скрывается. Настройка «Тень падения» управляет только контуром места приземления. Выбор сохраняется между запусками. Ниже настроек — выпадающий список десяти расцветок и образец семи блоков. Классическая выбрана по умолчанию; Solarized Light и GitHub Light светлые. Все темы меняют фигуры и интерфейс. Цветная середина блоков дополнена светлой верхней/левой фаской и тёмной нижней/правой. На время очистки строк тень падения скрывается.

## Правила

Pieces use shuffled seven-bags and SRS rotations with separate I and JLSTZ transition tables. The full board contains 20 hidden spawn rows and 20 visible rows. Grounded pieces have a 500 ms lock delay with at most 15 successful grounded move/rotation resets. Hard Drop locks immediately.

Заполненные строки исчезают по клетке каждые 60 мс, всего 600 мс. Первая очистка идёт слева направо, следующая справа налево; события чередуются. Несколько строк очищаются одновременно по столбцам. До окончания эффекта игра и управление фигурами остановлены. После него блоки сверху сдвигаются, начисляются очки и появляется следующая фигура.

Rules version 6 uses level-scaled Guideline-style awards: Single/Double/Triple/Tetris = 100/300/500/800 × level; T-Spin Mini Zero/Single/Double = 100/200/400 × level; Full Zero/Single/Double/Triple = 400/800/1200/1600 × level. Eligible consecutive difficult clears receive 1.5× base score. Combo adds 50 × combo count × level, starting at zero. Perfect Clear adds 800/1200/1800/2000 × level for 1–4 lines, or 3200 × level for a B2B Tetris. Automatic gravity gives no points; manual Soft Drop gives 1/cell and Hard Drop 2/cell, without level multiplication. Placement awards use the level captured before removal and saturate safely at Int.MAX_VALUE. Fruit thresholds remain 10,000 and 80,000 per round. Starting level (1–15, default 1) is selected in Settings and applies only to new games. Level is max(starting level, 1 + cleared lines / 10), independent of drop points and fruit rewards. A level-5 start first advances at 50 lines; a level-15 start advances to 16 at 150 lines, then every ten lines without a level-15 cap. Sessions and subsequent fruit rounds retain the chosen start. Incompatible older active sessions are discarded; historical results are unchanged. A threshold-crossing clear is scored at the pre-clear level; the next piece uses the new level. HUD always shows actual score and cues the final line before the next level. Gravity follows the single Marathon curve in `GameRules.kt`, with nanosecond timing in `GameTimeline.kt`.

Пауза, главное меню и уход в фон сохраняют прогресс удаления. Пересоздание Activity сохраняет партию на паузе. Новая игра сбрасывает анимацию и направление. Партия автоматически сохраняется локально, включая очередь фигур, уровень, прогресс удаления и остаток интервала гравитации. После полного закрытия приложение открывает меню с «Продолжить». Проигранную партию продолжить нельзя. Новая игра заменяет автосохранение. Потеря фокуса окна (в том числе шторка уведомлений) или уход в фон немедленно останавливает игру без автоматического возобновления. MainActivity использует singleTask для повторного запуска из launcher.

## Устройство кода и проверки

- `GameEngine.kt`: фигуры, состояния и правила без Android.
- `GestureController.kt`: распознавание жестов; пороги в `GestureConfig`.
- `GameViewModel.kt`: игровые часы, меню, настройка подсказок и рекорд.
- `LineClearAnimation.kt`: порядок исчезновения столбцов.
- `MainActivity.kt`: Compose-интерфейс и Canvas.

Классы и методы основного кода снабжены английскими KDoc-комментариями. Для сборки нужны JDK 17+ и Android SDK, путь к которому задан в `local.properties`.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
# Для инструментальных тестов сначала используйте изолированный эмулятор и явно задайте ANDROID_SERIAL:
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:dokkaGenerate
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Unit-тесты проверяют правила, жесты и последовательность удаления; UI-тесты — экраны и анимацию с управляемым временем. На устройстве также проверяются читаемость, бледность подсказок и компоновка на узком экране с крупным шрифтом и фиксация портретной ориентации.

Инвентаризация тестов, уровни проверок и результаты аудита описаны в [TESTING.md](TESTING.md).

`dokkaGenerate` создаёт HTML API-справочник в `app/build/dokka/html/index.html` и документацию в формате Javadoc в `app/build/dokka/javadoc/index.html`.

## Звук и вибрация

Переключатели независимы. На моторе с управлением амплитудой бросок и приземление после ускорения дают импульс 70 мс / 64, очистка строк — 600 мс / 64. Обычное автоматическое приземление тихое. Включение галки «Вибрация» даёт один проверочный импульс 100 мс / 110, без звука. Выключение останавливает только вибрацию; пауза сохраняет оставшуюся часть отклика очистки. Без управления амплитудой используется стандартная сила мотора: бросок длится 18 мс, очистка строк — 150 мс, проверочный импульс — 50 мс.

На Android 13+ используется категория `VibrationAttributes.USAGE_MEDIA`, на старых версиях — игровые `AudioAttributes`. Поэтому отключённая системная вибрация касаний не блокирует игровые эффекты; ограничения соответствующей категории Android сохраняются. Системные настройки приложение не меняет.

Собственные звуковые эффекты синтезируются `tools/Generate-Sounds.ps1`. `AndroidGameFeedback` использует SoundPool и медиагромкость.

## Музыка, победа и результаты

Музыка выбирается из десяти пунктов: «Выключена», «Все песни — случайный порядок» и восемь названий. Каталог: «Коробейники», «Калинка», «Камаринская», «Барыня», «Светит месяц», «Во саду ли, в огороде», «Трепак», «Танец Феи Драже». Одна песня повторяется; в общем режиме каждый круг обязательно начинают «Коробейники», затем остальные семь проигрываются в случайном порядке без повторов. Следующий круг снова начинается с «Коробейников». Между записями и повторами — 1,5 секунды тишины.

Оба режима сразу звучат в настройках. Новый выбор начинает запись сначала; повторный выбор ничего не сбрасывает. Меню, другие страницы, пауза игры и фон замораживают позицию или остаток межтрековой паузы. Возврат в настройки автоматически продолжает прослушивание; игру продолжают кнопкой. Выбор сохраняется в `music_selection`; прежняя галка `music=false` означает тишину, `true` — случайный плейлист. После перезапуска приложения очередь создаётся заново. Фанфары остаются однократными и не накладываются на песни; «Выключена» отключает и их.

Все записи переработаны в насыщенном стиле Sega: отдельные FM-инструменты, ответные фразы, подвижный бас, короткие аккорды и разнообразная перкуссия. Народные темы полностью проведены в собственных вариациях длительностью около трёх минут. Пьесы Чайковского сохраняют все разделы и нотные повторы: «Трепак» около 72 секунд, «Фея Драже» около 114 секунд. Полные нотные данные, происхождение редакций, источники и закреплённые зависимости описаны в [tools/music/README.md](tools/music/README.md). Готовые чужие записи и игровые семплы не используются. В APK включены Ogg Vorbis; генератор создаёт WAV и 30-секундные образцы.

Фрукты выдаются за каждые 10 000 очков: вишня, банан, виноград, клубника, яблоко, груша, ананас, арбуз. На поле только заработанные фрукты текущего круга: горизонтальные ряды, без теней будущих наград и без чисел. Значки 20 dp, промежутки 4 dp. Предпочтительное положение — относительно центра поля, со смещением вправо от области появления любой фигуры и превью следующей фигуры на 8 dp. Доступная ширина определяет перенос строк. Коллекция не перехватывает жесты и очищается при начале следующего круга. Полный набор при 80 000 очков открывает отдельное поздравление с кубком, фанфарами и восьмисекундным пиксельным салютом. До нажатия «Следующий круг» партия остановлена. Starting the next round clears the board and fruit display, draws fresh pieces, and retains score, line-derived level, gravity, cleared lines and active time. При превышении порога остаток очков сохраняется: после победы на 80 500 следующая вишня выдаётся при 90 000. Следующая победа — при 160 000.

Возврат через меню сохраняет ожидающее поздравление и не повторяет фанфары или салют. Победа имеет приоритет перед невозможностью появления следующей фигуры. Время поздравления в игровое время не входит; таблица получает один итог после проигрыша только при новом рекорде текущей версии правил, с числом завершённых кругов.

Рекорды содержат дату, имя, версию правил, очки, строки, уровень, время, круги и фрукты без множителей. Равные и меньшие результаты показываются только на экране окончания партии. Старая история не удаляется: таблица отображает последовательные рекорды отдельно для каждой версии правил; прежние записи сохраняют историческую сложность и версию правил. Новый рекорд открывает отдельный экран ввода имени; «Сохранить», «Пропустить» и системный «Назад» ведут к итогам. Новая игра до проигрыша не записывает незавершённую партию.

### Миграция и проверки

Прежние ключи (`record_v4`, `rules_4_migrated`, `difficulty` и другие) не удаляются. Новые рекорды вычисляются из `results_v2` отдельно для `rulesVersion: 6`, без сложности; старые записи и их метаданные сохраняются. `hints` по умолчанию false. Active sessions use schema 4 and rules version 6, with gravity and fractional lock/clear timing stored in nanoseconds. Incompatible active sessions are discarded while settings and result history remain intact. Снимок партии хранится отдельно в `swypetris_session`; завершённый снимок позволяет восстановить запись рекорда по стабильному ID без дубликатов. Автосохранение остаётся локальным и не входит в системный backup настроек и истории.

Unit-тесты проверяют циклы победы, превышение порога, порядок плейлиста и заморозку паузы. Инструментальные тесты проверяют переходы экранов, сохранение партии и тем, контур Next, его подпись и независимость от тени падения, вибрацию включения и декодирование всех восьми записей. Они используют `IsolatedStorageRule` и запускаются сначала на изолированном эмуляторе. Подключённый Pixel 7 используется только при необходимости проверить поведение реального устройства; serial всегда выбирается явно. Визуальные проверки включают ширину 320 dp и двойной шрифт. `tools/Check-Music.ps1` проверяет длительность, полную форму, контрольные суммы, клиппинг и плавные окончания декодированного Vorbis.

Обновление телефона: `adb -s <Wi-Fi serial> install -r app/build/outputs/apk/debug/app-debug.apk`, без удаления приложения и очистки данных. Лицензии и источники палитр и музыки включены в `app/src/main/assets`.

### Обновление оформления и навигации

Общий логотип заменён в меню, справке, результатах, поздравлении и контактах; иконка приложения использует компактный собор с цветными блоками. Изображения созданы imagegen, выбранные промпты и пути сохранены в `tools/branding/README.md`.

Справка содержит четыре короткие карточки жестов, основные правила и информацию о сохранении партии. Экраны результатов и окончания партии не содержат кнопок «Новая игра» и «В меню»: системная кнопка или жест «Назад» возвращают в главное меню.

Контакты: `piligrim18@gmail.com`, Telegram `@RoyMatus`, сайт `https://itoltec.ru/`. Карточки позволяют открыть нужное приложение или скопировать адрес; отсутствие приложения не вызывает сбоя.

Фанфары рекорда зависят от переключателя «Музыка», а не «Звук». При выключенной музыке поздравление остаётся тихим. `MusicPlayback` позволяет проверить этот выбор и однократный запуск без использования динамика. Фон и закрытие поздравления останавливают фанфары.

Направленный бросок распознаётся по расстоянию и преобладанию вертикальной оси, без требования определённой скорости пальца. После броска следующая фигура реагирует только на новое движение; отпускание старого касания не считается тапом.

## Заставка запуска

При новом запуске 24 полосы собора выезжают с чередующихся сторон за 0–1100 мс. С 450 до 2650 мс 87 кубиков надписи падают из-за верхнего края с разными задержками и небольшим наклоном. Затем кнопки появляются за 2700–3000 мс со сдвигом 12 dp. Последний кадр и обычное меню используют общий Canvas-рендерер PNG, без смены геометрии.

Касание или системный «Назад» только завершают заставку. В фоне часы заморожены; ViewModel сохраняет прогресс при пересоздании Activity. Возврат из игры, меню и фона не повторяет завершённую заставку. При нулевой системной длительности анимации сразу доступно меню. Звука и вибрации у заставки нет.

Прозрачные логотип и рисунок значка, промпты и воспроизводимая разметка частей описаны в [tools/branding/README.md](tools/branding/README.md).

### Hidden spawn buffer

Rotation uses SRS orientations 0, R, 2, L, with ordered wall/floor kicks. I has its own kick table; O changes orientation without moving its cells. Kicks and collisions also work in the hidden rows.

The logical board has 20 hidden rows above the 10×20 visible field. Piece coordinates use y=0 for the first visible row; negative y positions are hidden. Collision, ghost, and line shifting use all 40 rows. A blocked spawn causes block-out; locking all four cells above the visible field causes lock-out. Partial hidden placement is allowed. Saved sessions use schema 3 and rules version 5; incompatible older sessions are discarded without changing settings or result history.

### Lock delay

A grounded piece locks after 500 ms. A successful move or rotation from the ground resets this delay at most 15 times per piece. Airborne time freezes the remaining delay without restoring resets. Hard Drop locks immediately. Pausing, backgrounding, and session restoration preserve the remaining delay and reset counter.

### One-finger Hold

Hold nearly still for 300 ms, then swipe upward to store the active piece. A short readiness pulse respects the vibration setting. The same gesture retrieves the held piece by swapping it with the active one. Hold is available once until a piece locks; held pieces return at spawn position/orientation. Release without swiping to cancel without Soft Drop. Release after Hold before controlling the replacement. The upper-right preview under the localized Hold label shows the held piece inside subtle corner brackets; a dimmer preview means Hold is unavailable. No Hold button is used. Hold and availability persist with the game.

### Marathon gravity (rules 6)

Seconds per row are `(0.8 - (level - 1) * 0.007) ^ (level - 1)`. Level 1 takes 1 second per row; levels 5, 10 and 15 take approximately 355.197 ms, 64.152 ms and 7.059 ms. At level 19 and above gravity is capped at 20G (1200 rows/second at 60 Hz), represented by an interval of 833,334 ns rounded upward. Lock delay remains 500 ms with at most 15 move/rotation resets; no decreasing post-20G lock curve is applied. The simulation consumes elapsed time in chronological order, preserving gravity, lock and clear remainders across late callbacks, pause and restoration. It avoids empty gravity callbacks while grounded. New games apply the chosen starting level immediately.
