# Графика Swypetris

Создано встроенным imagegen. В приложение включены PNG с настоящим альфа-каналом: тёмная подложка удалена отдельным редактированием imagegen. Прозрачны также промежутки между полосами и кубиками. Файлы перенесены из результатов генерации без программной ретуши. Все экраны используют один прозрачный логотип; адаптивный значок получает отдельную системную подложку, а системная заставка — прозрачный foreground.

Логотип: `app/src/main/res/drawable-nodpi/swypetris_logo.png`.

Иконка: `app/src/main/res/drawable-nodpi/swypetris_mark.png`.

## Промпт логотипа

A flat vector-style retro videogame logo on uniform solid dark navy background #0B1020. Absolutely no light effects. Centered recognizable silhouette of Saint Basil's Cathedral, Moscow, with varied onion domes and central tall spire. The whole silhouette is formed ONLY by 24 thick horizontal light cyan bars separated by equally clear dark navy gaps, like a horizontal striped stencil. No windows or fine detail. Below cathedral, separated by empty navy space, write exactly SWYPETRIS in a crisp square pixel block alphabet, each letter assembled from interlocking tetromino pieces in cyan purple pink yellow mint and blue. Nine readable uppercase letters S W Y P E T R I S. Lettering is wide, cathedral a little narrower than lettering. Entire mark fits inside image with generous padding, no cropping. Landscape 3:2. Flat solid colors ONLY. No glow, gradients, blur, neon, bloom, shadow, shine or texture. This is a clean 2D printed emblem, not luminous signage.

## Промпт иконки

Create a square Android app icon matching this logo. Show ONLY the striped Saint Basil's Cathedral emblem, no lettering. Recognizable onion domes and central spire, flat pale cyan horizontal bars with clean dark gaps, thick enough for a tiny icon. Under the cathedral place three small connected tetromino shapes in lavender, coral and gold. Solid navy #0B1020 square background, no rounded outer border (Android applies its own icon mask), no glow, no texture, no gradients. Center the entire cathedral plus blocks within the central 62% of the canvas so circular Android icon masks never cut its domes or base. Crisp simple flat shapes.



## Кубок победы

Иллюстрация создана imagegen и сохранена без ретуши в `app/src/main/res/drawable-nodpi/victory_trophy.png`. Надпись, кнопки и восьмисекундный салют рисуются интерфейсом отдельно.

Точный промпт:

> Create a polished pixel-art victory illustration for Swypetris, a retro falling-block mobile arcade game. Landscape 3:2 composition, a large celebratory trophy constructed from colorful beveled tetromino-like square blocks, surrounded by exactly eight recognizable pixel fruits: cherries, banana, grapes, strawberry, apple, pear, pineapple, watermelon slice. Deep navy background with small gold stars, a few stylized pixel fireworks in upper corners, joyful warm highlights, crisp 16-bit Sega era pixel art, dimensional bevel highlights and shadows, coherent handcrafted game art, clean silhouette and balanced spacing, trophy central, fruits arranged around its base. No letters, no text, no numbers, no logos, no watermark. This is the final illustration for a congratulations screen; actual heading, buttons, and animated fireworks are added by app code.

## Прозрачные версии и заставка — 2026-09-21

Логотип: imagegen edit, 1536 × 1024 RGBA, 78,7% полностью прозрачных пикселей.

> Edit this exact existing SWYPETRIS logo. Remove ONLY the dark navy background, making it true transparent alpha everywhere around and between the artwork, including all gaps between cyan horizontal stripes and colored letter cubes. Preserve the cathedral silhouette, all cyan stripes, cross shapes, exact nine-letter SWYPETRIS lettering, pixel-block shapes, their positions and original colors. No redesign, no new shadow, no matte, no glow spreading into background, no checkerboard baked in. Keep original landscape 3:2 composition and canvas margins. Output one transparent PNG logo. This is a precise background-removal edit for use on both white and dark UI backgrounds.

Значок: imagegen edit, 1254 × 1254 RGBA, 72,5% полностью прозрачных пикселей.

> Precise background-removal edit of the attached SWYPETRIS app icon artwork. Preserve the exact cyan horizontal striped cathedral silhouette, crosses and purple/red/yellow blocks, with their current original colors, shapes, positions and square canvas. Remove all navy background to true alpha transparency including empty gaps between stripes and around the colored blocks. Keep artwork opaque with clean antialiased edges. No added glow, no cast shadow, no matte, no baked checkerboard, no border or rounded square. Output one transparent PNG, maintaining existing design; this is for an Android adaptive icon foreground, the system supplies its separate background.

`python tools/branding/decompose_logo.py` читает альфа-канал и записывает только Kotlin-координаты в `LogoPieces.kt`; PNG не изменяется. 24 горизонтальные полосы определяются по прозрачным промежуткам. Координаты сетки букв соответствуют текущему рисунку, в том числе смещённым кубикам S и W. 87 кубиков состоят из 124 областей: мелкие сглаженные края прикреплены к ближайшему кубику той же буквы и не летают отдельно. Скрипт проверяет, что каждый непрозрачный пиксель принадлежит ровно одной области, и сохраняет SHA-256 PNG рядом с координатами.

`LaunchIntroMotion` задаёт стабильные задержки и траектории, `LaunchIntroOverlay` рисует области исходного изображения на Canvas. После 2650 мс заставка показывает тот же слой `GameTitle`, что и меню; при появлении кнопок слой логотипа уже не меняется. Тёмной панели вокруг логотипа больше нет. Системная заставка Android 12+ использует `swypetris_icon_foreground`, фон остаётся отдельным ресурсом системы.

Текущее время читается только в фазе рисования Canvas и обновления graphicsLayer. Состояния завершения вычисляются через `derivedStateOf`, поэтому вся иерархия меню не перекомпоновывается каждый кадр.
