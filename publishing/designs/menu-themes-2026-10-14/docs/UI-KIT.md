# Menu UI resources

Use `raw/menu_ui_tokens.json` as the semantic menu-only palette. It contains background, panel, text, muted text, border, control/onControl, primaryFill/onPrimary, focus, scrim, nine action accents and state parameters for every stable theme ID. Matching named Android colors are in `values/menu_art_colors.xml`.

## Components and states

| Role/resource suffix | Existing use |
|---|---|
| primary | New game / main affirmative action |
| resume | Continue |
| settings, help, results, contacts | Existing main menu secondary tiles |
| destructive | Exit/reset/delete action according to existing behavior |
| back, link | Back/cancel and links/secondary navigation |
| card, dialog | Existing content surfaces and modal container |
| input | Existing text-entry surface |
| chip | Existing selected option or category |
| switch_on/off, radio_on/off, checkbox_on/off | Existing control visuals; keep accessible native interactions |
| slider_thumb, slider_track, progress, divider | Existing slider/progress/list decoration |

Each action has normal, pressed, selected, focused and disabled vector frames. The supplied selectors define priority disabled > pressed > focused > selected > normal. In Compose load the explicit state VectorDrawable, retain native text/icons on top, and bind state from the actual control. Text selectors contain the finished normal/disabled label colors.

Nominal action art is 56 dp high; primary/resume art is 320 dp wide and other roles 154 dp. These are scalable drawing viewports, not forced device geometry. Keep current measured control size, 18/8/18/8 nominal content insets where compatible, native adaptive labels and minimum 48 dp hit targets. Existing fonts and Android icons remain in use. Browser preview glyphs are not replacement app icons.

Corner geometry, border/detail paths and fills are already supplied. Scale masters into the current bounds; for surfaces with variable height, retain 12 dp corner radius and stretch the flat center rather than enlarging ornament/corners. Classic uses its small stepped cuts; SynthWave uses cut corners. The authoritative visual paths are the supplied vector masters; do not invent textures.

The switch/radio/checkbox art includes both actual values. Native pressed/focused/disabled overlays use the exact state tokens. Input error uses the theme's destructive accent as a 2 dp border; focused uses focusStrokeDp and focus; disabled text uses disabledContentAlpha. For chip selection use control/onControl. Slider-track and progress SVG/XML show a fixed sample value for appearance; drive the actual active segment from real progress/value using the supplied colors and dimensions. Do not freeze these controls at the artwork sample value.

Use text on panel/background for normal labels and muted for supporting text. Destructive accent is an icon/border cue; retain readable normal text. Semantic text contrast pairs are measured in docs/contrast.json. Disabled content is intentionally subdued and excluded from the normal-text ratio requirement. The sample viewer does not constitute a full Android accessibility audit.

Headers use source crop [0,40,941,660] in the common 941×1672 scene coordinates, then ContentScale.Crop in the existing header bounds, centered horizontally. The Solarized Light source is 1671 px high, so normalize its source Y coordinates by 1671/1672. Paint the existing header-side background gradient using the menu background token; retain readable titles.

Keep dialog scrim at the existing alpha with the supplied scrim hue. Use native indeterminate progress motion, focus behavior and accessibility announcements already present; scene motion must not run on non-MENU screens.
