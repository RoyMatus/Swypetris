# Fixed artwork package for Swypetris issue #296

Version: `swypetris-record-history-296-v1`. Target: the **trophy-right** record card selected in the design conversation. This is an asset handoff, not an implemented Android feature.

## Mandatory implementation contract

**Use these exact supplied files. Do not generate, redraw, trace, recolor, reinterpret, crop from the old mockup, substitute emoji/HUD icons, or create alternative trophy/fruit artwork.** Copy the package's `app/` subtree into the implementation checkout at the same relative paths. Verify the imported bytes against `SHA256SUMS.txt`. Do not copy the source atlas, preview images or documentation into app resources.

The nine PNGs have real alpha transparency. `record_trophy.png` contains the trophy with an unobstructed pedestal and no fruits. Each `record_fruit_*.png` contains one fruit type only. `record_quantity_badge.xml` supplies the exact circular background; the number is live text, not painted into a bitmap. `record_artwork_layout_v1.json` fixes the layer positions, drawing order, badge centers and card measurements.

The PNG artwork was separated with the built-in imagegen tool using the existing Victory image as its edit reference. Covered parts of the pedestal and fruit silhouettes were reconstructed. These are derived independent sprites, not a claim of recovering the original artist's hidden layers or retaining every source pixel. The attached package, rather than another generation, is the frozen input for the implementation agent. The original `victory_foreground.png` and the Victory screen remain unchanged.

## Files to import

| Target directory | Files |
| --- | --- |
| `app/src/main/res/drawable-nodpi/` | `record_trophy.png`, `record_fruit_cherry.png`, `record_fruit_banana.png`, `record_fruit_grapes.png`, `record_fruit_strawberry.png`, `record_fruit_apple.png`, `record_fruit_pear.png`, `record_fruit_pineapple.png`, `record_fruit_watermelon.png` |
| `app/src/main/res/drawable/` | `record_quantity_badge.xml` |
| `app/src/main/assets/` | `record_artwork_layout_v1.json` |

The side-by-side alternative with a centered trophy is no longer an implementation choice. The narrow-screen fallback below keeps the same artwork and data; it is an accessibility/layout fallback for the trophy-right design.

## Composition

The JSON's design canvas is **1000 by 1050 proportional units**. These are not device pixels. Fit this canvas uniformly into the available artwork area. Transform every rectangle and badge center by the same scale and offset. Draw the full trophy first, followed by positive-count fruit layers in ascending `zIndex`; draw badges last. Preserve each PNG's aspect ratio with contain/center fitting inside its specified rectangle. Use the existing game's pixel-image filtering, without tint or color filters. Trophy alpha is **0.84**; earned fruit alpha is **1.0**.

The fruit list is the existing order: CHERRY, BANANA, GRAPES, STRAWBERRY, APPLE, PEAR, PINEAPPLE, WATERMELON. Use the persisted result's quantities through the existing shared reward logic, not the live game state or a new scoring formula. Count 0: omit both fruit and badge. Count 1: draw fruit only. Count greater than 1: fruit plus its decimal count in a circular badge. Keep every other fruit in its fixed slot when one is absent. No silhouettes, placeholders, duplicates or invented awards.

Current rules can derive counts from the record's score; historical rules require their established compatibility behavior. The JSON examples are verification fixtures, not a second runtime reward owner.

## Circular quantity badges

- Use `record_quantity_badge.xml`, **16 dp** at normal font scale, without tint. The background is charcoal `#242424` with the supplied subtle outline. Center a white numeric label over it; no multiplication sign, prefix or permanent rasterized number.
- Text: **11 sp**, weight **600**, one line, no font padding. Show the actual quantity; never shrink it into illegibility or truncate it.
- At increased font scale, use a circle diameter of `max(16 dp, measured label width + 6 dp, measured label height + 4 dp)`.
- Leave at least **1 dp** between badge circles. If the fixed centers would overlap, use the stacked layout and enlarge its artwork canvas until every visible circle has that gap. Compute required scale from the actual normalized distances between visible badge centers; keep all centers and fruit positions unchanged. Reserve **4 dp** outside the canvas for edge badges. At the supported font scales, do not solve overlap by shrinking the text, moving badges to other fruits or changing the circle into a pill.
- Expose one accessible fruit/quantity description. Treat the trophy and badge background as decorative; do not announce them twice.

## Card layout and text

Keep `ScreenArtHeader`, `ThemedCard`, the active game palette, page insets, portrait orientation and vertical scrolling. The JSON fixes the sizes and labels; do not redesign the surrounding screen.

- Page margin: **12 dp** horizontally. Card content padding: **14 dp**. Gap between cards: **12 dp**.
- Header text `Лучший результат`: **12 sp / 600**, palette `secondary`. Score: **34 sp / 700**, palette `accent`, digits grouped with a narrow non-breaking space (`788 267`). `очков`: **12 sp**, palette `muted`.
- Player name: **18 sp / 600**, palette `text`, left aligned. Date: **12 sp**, palette `muted`, established local date/time formatting (`13 окт. 2026 · 18:37`). Preserve long names by wrapping.
- Hero minimum height: **188 dp**. The text is left and the artwork right with **8 dp** between them. Use **48%** of inner card width for the artwork, bounded to **144–184 dp**; the text column needs at least **144 dp**.
- Use a stacked arrangement if either minimum width cannot fit, font scale is at least **1.3**, measured text does not fit, or enlarged badges need more room. Text remains left aligned; place the same composition centered below it at **180 dp** initially, enlarging for badge fit as described above. No alternate art or hidden values.
- Under the hero, show the established divider and an aligned row of four metrics in this order: `Строки`, `Уровень`, `Время`, `Круги`. Values are **19 sp / 500** with palette `text`; labels **12 sp** with palette `muted`. Gap **8 dp**, top margin **8 dp**, top padding **14 dp**. Reflow to two columns if measured labels/values do not fit. Do not change their font scale to force four columns.
- Lower cards use the same alignment with name and score across the top, date below, metrics below that. No trophy. Omit an empty award row. Reuse these supplied fruit files if an earned-fruit row is shown.

## Checks before delivery

1. Confirm all 11 imported runtime files match the package hashes and Android accepts the PNG/vector resources.
2. Render the six supplied score/count fixtures; compare with the packaged actual-asset previews. In particular, a zero-award card exposes the complete pedestal, and 42,500 has exactly four fruits and no quantity badges.
3. Verify no quantities come from a different stored result or the current session. Preserve record eligibility, stored history and the existing reward owner.
4. Verify the selected card layout on relevant portrait widths and font scales, including 1.0, 1.5 and 2.0. Check clipping, text contrast, badge separation and accessibility with the actual Compose implementation.
5. Finish the ticket's focused tests and mandatory local final PR gate. Package checks and browser previews do not replace native Android rendering/instrumentation or release gates.

No app integration, gameplay verification, implementation merge or release is claimed by this package. Issue #296 remains in Backlog until implementation starts.

## Android documentation scope

The existing project uses Compose BOM 2024.09.00; no dependency upgrade is part of this handoff. Context7 was consulted, but its retrieved result did not establish the specific density qualifier contract. The official [Android density documentation](https://developer.android.com/training/multiscreen/screendensities) confirms the `nodpi` placement, and [Compose image documentation](https://developer.android.com/develop/ui/compose/graphics/images/customize) describes aspect-preserving fitting. Consulted 2026-10-13. These references support resource handling, not an assertion that this UI has been implemented.
