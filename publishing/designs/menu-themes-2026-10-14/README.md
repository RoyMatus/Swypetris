# Swypetris: twelve menu themes — resource handoff

Prepared on 2026-10-14. This package is an implementation-ticket attachment. It does not implement or build the Android application. Integration belongs to a separate process.

## Read first

1. [Integration map](docs/INTEGRATION.md): exact resources, target code owners, scope and protected behavior.
2. [Animation contract](docs/ANIMATION.md): draw order, coordinate mapping, keyframes, timing, lifecycle and reduced motion.
3. [UI kit](docs/UI-KIT.md): nine action roles, five states, menu controls and semantic colors.
4. [Launcher / RuStore](docs/ICON.md): one identical 512×512 bitmap and ready launcher XML.
5. [Validation](docs/VALIDATION.md): actual asset checks and remaining Android acceptance work.

**Do not add a new cathedral logo inside the game UI. Keep the current SWYPETRIS wordmark, GameTitle and launch-intro wordmark resource/renderer.** The new cathedral icon is for the installed application's launcher and RuStore listing only.

Classic is the selected historical option 2, **Early color PC**: blue daylight, red-brick cathedral and a solid sun. It is not the original striped-sun Classic or the green Brick Game proposal. Only SynthWave '84 has a striped sun. Gruvbox's paving is wooden.

## Package contents

| Folder | Use |
|---|---|
| android/res/drawable-nodpi | 12 clean scene plates, 4 transparent sprite images/atlases, matching launcher bitmap |
| android/res/drawable, drawable-v26, values, color | Finished Android vector skins, selectors, semantic colors and launcher wrappers |
| android/res/raw | 12 authored animation timelines, sprite registration and 12 UI palettes |
| ui/svg | Matching scalable UI masters; no font or game-piece artwork baked into controls |
| ui/component-index.json | Dimensions, content insets, state and role names |
| store | RuStore PNG and full generated icon master |
| reference | 12 approved flat mockups, historical generation/source notes and the unchanged current wordmark image |
| preview | Browser resource viewer with actual timelines and menu-component examples |
| tools | Art-data rebuild and validation utilities only; no Android implementation |
| docs | Handoff, exact-file validation results, provenance and checksums |

Copy only the listed Android resources during implementation. Do not ship the reference screenshots, icon source, SVG masters, preview or authoring utilities inside the APK. The package deliberately contains no Kotlin application implementation.

## Preview

With Node.js available, run `node preview/serve.cjs` from the extracted package. Open http://127.0.0.1:57345/preview/index.html. Set `SWYPETRIS_ART_PORT` if the port is already occupied. No package installation, external service, upload or build is needed. The viewer binds only to localhost.

Use the theme selector, animation toggle and menu example tabs. These are visual samples, not a replacement navigation specification: existing Android layout, text, callbacks and behavior remain authoritative. Browser glyphs stand in for the existing Android icons.

## Integrity and provenance

SHA256SUMS.txt covers every deliverable except itself. The ZIP checksum is recorded alongside the archive. Generation prompts and source references are retained in docs/generation-provenance.json and reference/approved-prompts.json. Superseded historical prompt wording cannot override the integration instructions above.

PNG scene and sprite exports retain the image generator's output bytes. The store bitmap is a format-only downsample of its generated square master; the Android copy is byte-identical. Interface SVG/XML and animation data are explicitly authored assets. No new gameplay field or tetromino assets are supplied.
