# Wide theme cards: approved implementation handoff

The user selected **Wide cards** ("Широкие карточки") on 2026-10-13. Implement this layout; the earlier two-column alternative is not part of this handoff.

This package is prepared against `RoyMatus/Swypetris` commit `7b8e1bfa5a4e6669750d53457156f565160802a8`. It contains ready-to-integrate source, not an Android release. The issue remains open until integration, the required local PR gate, and a protected merge are complete.

## Files and precedence

1. `WidePalettePicker.kt` is the authoritative Android component, in package `ru.itoltec.swypetris`.
2. `integration.patch` adds that component and `WidePalettePickerTest.kt`, delegates the existing `PalettePicker` entry point to it, and updates the existing non-overlap assertion for the approved arrangement. Apply this patch instead of copying the same files twice.
3. `design-spec.json` records exact dimensions, responsive rules and resource identities.
4. `wide-cards.png` is the approved wide-card visual reference. `wide-cards-narrow.png` shows its narrow-layout fallback.
5. `wide-cards.html` is a standalone, interactive reference containing all 12 existing themes. Open it locally; select any card to inspect the selection treatment. It is a design reference, not an Android dependency.
6. `verification.md` distinguishes checks actually run during package preparation from remaining integration/release gates.
7. `SHA256SUMS.txt` verifies the package files. `theme-picker-wide-handoff.zip` contains the same package without recursively including itself.

The Kotlin source and JSON define Android dimensions. Browser pixels, browser font metrics, screenshot scaling and the surrounding preview frame are illustrative. Preserve the existing settings section header, random-theme control, backgrounds outside individual cards and portrait orientation.

## Exact design contract

| Element | Value |
| --- | --- |
| Layout | One full-width card per existing theme; preserve `GamePalettes.all` order |
| Scroll ownership | Existing settings page; no nested scroll, carousel, search, filters or new dialog |
| Card spacing / corners | 8dp / 13dp |
| Card height | Minimum 88dp; grow with content; no fixed-height text clipping |
| Background / title color | Each existing `GamePalette.background` / `.text` |
| Unselected border | 2dp, `#354059` |
| Selected border | 3dp, active palette's T-piece color; `#AF55E8` for Classic |
| Normal title | 19sp, 27sp line height, semibold, start aligned, full name with natural wrapping |
| Normal content padding | Start 28dp, end 12dp, top/bottom 10dp |
| Title-to-art gap | 14dp |
| Normal preview | 140 × 68dp, seven 32 × 32dp blocks, 4dp gaps |
| Block arrangement | First row I/O/T/S; centered second row Z/J/L; x offsets 0/36/72/108dp then 18/54/90dp |
| Selection marker | 16dp circle, start 5dp/top 7dp; 12dp check; decorative semantics |
| Responsive trigger | Actual available card width <294dp **or** system font scale ≥1.4 |
| Responsive title | Above artwork, 18sp/26sp, semibold; full wrapping; no ellipsis |
| Responsive padding / gap | 14dp on all sides; 12dp between title and artwork; reserve 20dp at title end for check |
| Responsive preview | One row I/O/T/S/Z/J/L; 4dp gaps; block size `min(40dp, (cardWidth - 28dp - 24dp) / 7)` |
| Responsive selection marker | 19dp at end 7dp/top 7dp |
| Interaction | Entire card selects; one `Role.RadioButton` semantics node with selected state; group uses `selectableGroup()` |

## Use existing artwork and state

Do not redraw, recolor, regenerate or duplicate theme images. Use `blockMaterial(item)` and `bevelBlock(..., material = material, type = type)` from the project. They already draw the connected material atlases with the correct crop, filtering and palette identity. The small sprites embedded in the HTML are reference-only previews extracted from those assets, not replacement production assets.

Keep `GamePalettes.all`, all stable palette IDs/names, `LocalGamePalette`, and `model.options.setPalette(id)` as the existing sources of truth. Selecting any card, including the current theme, must still disable random theme changes and persist the choice. Preserve the existing `paletteGrid`, `palette_<id>_preview`, `palette_<id>_art` and `palette_<id>_label` tags. The decorative check adds `palette_<id>_check`.

## Integration

Use a fresh implementation worktree based on the current main branch and inspect changes since the pinned base. Run these commands from its repository root, replacing the path with the downloaded package directory:

```powershell
git apply --check /path/to/package/integration.patch
git apply /path/to/package/integration.patch
```

The patch is self-contained; no dependency or resource addition is required. Do not apply it with `--reject`, whitespace-tolerant guessing, or replace the whole current `GamePalette.kt` from an old snapshot. If surrounding code changed, preserve newer unrelated changes and reproduce the three small integration steps explicitly:

1. Add `WidePalettePicker.kt` under `app/src/main/java/ru/itoltec/swypetris/`.
2. Replace only the body of `PalettePicker(model)` with `WidePalettePicker(LocalGamePalette.current) { model.options.setPalette(it) }`, removing the now-unused imports and three old `PREVIEW_BLOCK_*` constants.
3. Add `WidePalettePickerTest.kt` under `app/src/androidTest/java/ru/itoltec/swypetris/` and change the existing overlap assertion in `VictoryThemeIntegrationTest.paletteNamesFitWithoutOverlappingPreviews`: the label must be **left of or above** the preview, with both kept inside the card. Retain the full-text and `hasVisualOverflow` assertions.

## Required acceptance

- All 12 themes appear once, in their existing order, as a single vertical list.
- Normal portrait layouts show a readable title on the left and the seven large material samples on the right.
- Narrow screens and font scales 1.4–2.0 use the specified stacked fallback; complete names and all samples stay visible without overlap or horizontal scrolling.
- Selected state uses both the border and check, and is exposed to accessibility services.
- Selection persists and preserves random-theme behavior; gameplay, sounds, theme assets and neighboring settings remain unchanged.
- Verify relevant portrait widths and aspect ratios, including 240/320/393/600dp constraints and normal/large font scales. Do not enable landscape for testing.
- Run focused `WidePalettePickerTest`, the affected picker methods in `VictoryThemeIntegrationTest`, and `MaterialRenderingTest.everySettingsCardUsesItsMaterialAtNormalAndLargeFonts` as appropriate. Reuse valid package-preparation results only when their inputs match; see `verification.md`.
- Complete the repository's final local PR gate for the exact implementation head/current main base before merging. Reference the ticket with `Closes #N` in that final implementation PR. This handoff alone does not close the issue or establish release readiness.

## Framework references

Authenticated Context7 returned official, current Compose documentation for [adaptive constraints](https://developer.android.com/develop/ui/compose/layouts/basics) and [radio-group accessibility](https://developer.android.com/develop/ui/compose/components/radio-button). These are general API contracts, not version-pinned evidence for the repository's Compose BOM `2024.09.00`; the local compile/test results in `verification.md` establish the actual compatibility checked here.
