# Approved materials and portrait fields (#189)

The production artwork is derived from the three approved [material sheets](https://github.com/RoyMatus/Swypetris/tree/6c1ac9245b5c89fdbfde541aa10291d3d7f154d4/publishing/design/block-materials-189) and three approved [background sheets](https://github.com/RoyMatus/Swypetris/tree/fae02eb536b22ebd09796fe7822d5847cd879e13/publishing/design/block-materials-189). These immutable references remain authoritative.

`prepare-materials.py <reference-directory>` requires Pillow 12.3 and the six files named `materials-1.png` through `materials-3.png` and `backgrounds-1.png` through `backgrounds-3.png`. It extracts full-face hero material detail, center-crops without distorting features, downsamples to 256 pixels, and prepares seven pigment tiles from the existing palette colors in I/O/T/S/Z/J/L order. Copper and colored terrazzo chips retain their artwork colors; wet reflections and foil retain local hue variation around each piece pigment. The approved colored examples determine the pigment treatment. This is photographic material preparation, not regeneration or a procedural substitute. Presentation labels/shadows and sample tetromino geometry are excluded.

Backgrounds are exact RGB interiors saved as lossless WebP. Preparation asserts decoded pixel equality against every source crop and seven distinct atlas tiles. `asset-manifest.json` records coordinates and source/output SHA256 hashes. Runtime uses uniform `ContentScale.Crop` into the existing portrait field, with no repeating tiles, contrast overlay or image distortion. A selected user photo keeps its existing precedence, crop and score backdrop.

`BlockMaterial` is the shared filled-cell rendering owner for active/settled blocks, Hold, line-clear shards and every Settings example. The gameplay composition shares one decoded atlas; each Settings style remembers its own atlas. There is no generation or decoding inside the draw function. Existing cell gaps, field geometry, outline-only ghost/Next preview, HUD layering, Hold alpha, card layout/names/order/selection and gameplay state are retained. Decorative menu pieces retain their existing treatment. On Solarized Light and GitHub Light, the score uses black digits without a backdrop; photo mode keeps its existing white digits and dark panel.

## Actual UI comparisons

Every comparison includes the approved hero and colored examples, approved field interior, actual portrait gameplay at 393x873 dp/font 1 and 240x580 dp/font 2, native/enlarged settled-cell crops and both actual Settings cards. Enlargement uses nearest-neighbor sampling; gameplay overviews are scaled uniformly for presentation. The lower-field fading and faint Hold are existing HUD treatments, not modifications to the assets.

| Style | Comparison |
| --- | --- |
| Classic | [Native UI and approved sources](classic-comparison.webp) |
| Monokai | [Native UI and approved sources](monokai-comparison.webp) |
| Gruvbox Dark | [Native UI and approved sources](gruvbox-comparison.webp) |
| VS Code Dark+ | [Native UI and approved sources](vscode-comparison.webp) |
| Dracula | [Native UI and approved sources](dracula-comparison.webp) |
| Nord | [Native UI and approved sources](nord-comparison.webp) |
| Solarized Light | [Native UI and approved sources](solarized_light-comparison.webp) |
| Solarized Dark | [Native UI and approved sources](solarized_dark-comparison.webp) |
| GitHub Light | [Native UI and approved sources](github_light-comparison.webp) |
| Tokyo Night | [Native UI and approved sources](tokyo_night-comparison.webp) |
| Catppuccin Mocha | [Native UI and approved sources](catppuccin-comparison.webp) |
| SynthWave '84 | [Native UI and approved sources](synthwave_84-comparison.webp) |

The comparisons are review evidence, not an automated aesthetic judgment. Agent inspection found the identifying material features and readable pigment/cell separation in all twelve styles. No additional owner aesthetic approval or physical-device performance result is implied.

API references: [Compose bitmap drawing and caching](https://developer.android.com/develop/ui/compose/graphics/draw/overview), [uniform image cropping](https://developer.android.com/develop/ui/compose/graphics/images/customize), [Compose test clock synchronization](https://developer.android.com/develop/ui/compose/testing/synchronization). APIs were checked through the authenticated Context7 connection and compiled against the existing Compose 1.7.0 dependencies; no dependency update was made.
