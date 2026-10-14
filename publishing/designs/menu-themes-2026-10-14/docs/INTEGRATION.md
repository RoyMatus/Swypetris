# Integration instructions

## Scope and precedence

The user approved the twelve scene concepts, then selected historical Classic option 2 (Early color PC), and requested ready resources plus a ticket for another implementation process. Latest explicit branding decision: **no new in-app cathedral logo**. Keep the existing SWYPETRIS wordmark and its intro. This document, UI tokens and motion JSON supersede any conflicting wording in historical mockup prompts.

Reference code was inspected at main commit 5606b2502a2c89fa17304b0a6c3a00316c060d9c. Reconcile against the implementation checkout if main advances. Preserve other changes, especially the approved wide theme-picker design in issue #297.

## Screen boundary

Apply the new menu art/tokens to MENU, SETTINGS, HELP, CONTACTS, PRIVACY, LEGAL and the standalone RESULTS screen, plus dialogs opened from these screens. The animated full scene is on MENU; other menu pages use the provided scene header crop and static semantic surfaces.

Preserve PLAYING, GAME_OVER, RECORD, VICTORY, ABSOLUTE_VICTORY and POSTCARD. ResultsScreen is shared by RESULTS and GAME_OVER: do not let menu styling change GAME_OVER. Keep gameplay field backgrounds, tetromino material atlases, scoring, state, difficulty and game rules untouched. BackgroundCropPreview and game-field previews are not theme-menu backgrounds.

Maintain portrait orientation, responsive geometry, safe insets, font-scale behavior, existing menu rows and Continue visibility, callbacks, labels, semantics/test tags and navigation. A new logo, screen, tab, button or menu action is not requested. The HTML preview contains component demonstrations, not new product behavior.

## Resources and target owners

Paths below are relative to app/src/main.

| Existing owner | Required integration |
|---|---|
| java/ru/itoltec/swypetris/GameArt.kt — ThemeBackdrop / MenuSkyArtwork | Select the exact clean plate by stable palette ID from the table below. Draw matching motion data over it. Remove the old two-image day/synth selection, recolor matrix and old registered sky patches/clouds/glows from this new scene path; they do not align with the new paintings. |
| java/ru/itoltec/swypetris/MainActivity.kt — SwypetrisApp | Scope menu semantic tokens to the allowed screens. Keep GamePalettes and piece colors authoritative for gameplay. Do not globally replace MaterialTheme colors for excluded game screens. Preserve palette persistence and theme-randomization settings. |
| java/ru/itoltec/swypetris/MainMenu.kt — MenuTile / MenuTetrominoBackdrop | Use the supplied action role/frame/text color; retain current measured button geometry. Replace the old generic floating decoration with the chosen scene timeline to avoid double animation. Existing playable tetromino resources stay unchanged. |
| java/ru/itoltec/swypetris/ScreenDecor.kt — ScreenArtHeader / ThemedCard | Use the selected clean plate's header crop and menu surfaces/frame masters. Preserve header/card bounds, back button, typography and spacing. |
| java/ru/itoltec/swypetris/AppActionButton.kt | Apply role/state skins only inside the menu scope. Keep native interaction semantics, hit targets and callbacks. |
| SettingsScreen, HelpScreen, ContactsScreen, Legal/Privacy screens, standalone ResultsScreen | Apply exact semantic colors to cards, lists, empty states, fields, links, dividers, controls and dialogs. Preserve content and placement. Use the theme picker arrangement from #297 if present; this ticket does not replace it with the preview's simplified example row. |
| ShareAppDialog, APK/update dialogs, reset-statistics confirmation and legal dialogs | Use dialog/card/input/action resources; keep state, loading/error handling and all actions intact. Keep QR codes black/white with their existing quiet zone. |
| res/drawable-nodpi/ic_launcher_artwork.png and existing launcher XML chain | Replace from the supplied files. Preserve the manifest resource names and application identity. Follow ICON.md for adaptive padding. |
| GameBrand.kt, LaunchIntro.kt and res/drawable-nodpi/swypetris_logo.png | Preserve the existing wordmark resource and behavior. Do not replace these with the launcher icon or add a cathedral logo to the menu. |

The Android overlay contains completed drawable/color/raw resources, not a screen implementation. In Compose, choose the explicit vector state resources using existing interaction/selection/enabled state. Do not assume painterResource can load a StateListDrawable selector; it is provided for Android drawable consumers. No new UI/framework dependency is required.

## Stable ID mapping

Each ID uses `drawable-nodpi/menu_scene_<id>.png`, `raw/menu_motion_<id>.json`, the matching entry in `raw/menu_ui_tokens.json`, and `menu_<id>_*` UI resources.

| ID | Scene identity | Motion |
|---|---|---|
| classic | Early color PC; blue sky, solid cream sun, red brick | Eight colored floor tiles assemble and fade |
| monokai | Charcoal, lime, magenta, cyan; solid yellow moon | Slow square particles and dash glints |
| gruvbox | Warm autumn, grain-visible wooden block paving | Six leaves and amber lamp glow |
| vscode | Dark editor palette, steel-blue/teal cathedral | Seam glints and outlined squares |
| dracula | Purple Gothic night, whole lavender moon | Three bats, two fog wisps |
| nord | Snowy Arctic palette, whole moon, settled snow | Two snow depths and faint aurora glow |
| solarized_light | Cream paper/daylight, whole sun | Slow clouds and floor shadow |
| solarized_dark | Petrol/teal, amber whole moon | Teal clouds, stars and amber reflection |
| github_light | Clean blue daylight, light surfaces | White clouds and subtle green inlays |
| tokyo_night | Dark blue/purple, wet stone, whole moon | Rain, expanding water rings, reflection |
| catppuccin | Pastel Mocha night, whole moon | Breathing cat, occasional ear/tail poses, cloud/stars |
| synthwave_84 | Retrofuture neon purple/pink/cyan | Lasers, floor scans, sun-edge glow; the only striped sun |

Do not rename stored palette IDs, regenerate these backgrounds, invent extra decorations, repaint the wordmark, recolor game materials, or draw new buildings. Architecture is registered consistently in the supplied plates. Moving elements are separate and must remain behind controls.

## Implementation order

1. Inspect current code and execution profile; prepare one scoped check plan. Verify archive checksums.
2. Add the supplied resources and a menu-only theme binding, preserving stored IDs and game palette owners.
3. Integrate the scene mapper and deterministic timeline sampler from ANIMATION.md. Reuse lifecycle/system-animation handling already present in GameArt.kt.
4. Apply completed UI skins/tokens to current controls and menu dialogs. Use the mapping in UI-KIT.md; preserve layout.
5. Replace the launcher resource chain and retain the matching RuStore file for the eventual publication step.
6. Verify the acceptance cases in the ticket. Only the later implementation PR may close it.

## Acceptance scope for the implementation process

Capture all twelve portrait menus at the same dimensions with animation disabled and compare with the supplied clean plates/reference mockups. Also verify animation enabled, both Continue states, 320/360/412 dp widths, representative 16:9 and 20:9 portrait ratios and font scales 1.0/1.3/2.0. Layout may reflow according to existing adaptive rules; text cannot overlap, clip or lose its 48 dp touch target.

Verify menu-to-game-to-menu and background/resume preserve game state and theme. No active menu frame loop remains while playing, on another menu page or in the background. Toggling system animations off produces a stable static composition. Check theme switching repeatedly for stale sprites, old color filters and excessive bitmap retention.

Android compilation, instrumentation, real-device checks, performance measurements and store moderation are intentionally not claimed by this asset handoff. The implementing process must run selected local checks and the repository's final exact-head PR gate.
