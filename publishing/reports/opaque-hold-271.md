# Full-opacity Hold — #271

Hold now uses the shared block renderer's default `alpha = 1f`, matching the active piece. The only production change removes the previous `.16f` available / `.055f` used alpha argument. Both availability states keep the same selected connected artwork and brightness. Intrinsic transparency in the supplied artwork remains identical to the active renderer; this change does not flatten or repaint the assets.

Geometry, label, empty spaces, input, saved state, exchange restrictions and accessibility descriptions are unchanged. No replacement unavailable-state indicator was added. Active, ghost, Next and animation rendering are unchanged.

## Verification

The obsolete faintness assertion was replaced by all twelve themes × seven shapes. Available and used previews must be pixel-identical while their accessibility descriptions remain distinct. Each of four cells is compared with the ordinary active block path at the same cell size, orientation and solid background. The comparison excludes only the two-pixel hardware/software sampling fringe and requires mean RGB distance below 0.04 inside each cell. Source artwork alpha is preserved on both sides. Existing painted-edge alignment, bracket-free corners, HUD layout, Next contrast and Hold gameplay/persistence tests remain.

Seven selected Android cases passed in 98.031 s including build/runner overhead. Strict debug lint and detekt passed in an 87 s Gradle invocation. The normal tests retain portrait Activity behavior; no landscape lock or new device-specific geometry was introduced. The explicit `holdCapture=true` export supplies native available previews, matching active references and representative used-Hold portrait fixtures; it does not replace assertions.

API review used authenticated Context7, whose result covered general Compose guidelines rather than the requested drawing contract. The Android reference timed out; [official AndroidX DrawScope source](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/drawscope/DrawScope.kt) confirms the existing alpha/default drawing contract. That current source is not presented as the exact binary version; this app continues using its existing Compose BOM 2024.09.00 and existing overload, without an API/dependency change.

The explicit export passed in 23.982 s runner / 28.158 s including installation and transfer. The 171 originals are retained in ignored output. The [all-theme comparison sheet](media/issue-271/hold-all-themes.png) preserves original preview pixels; its upper/lower rows are Hold/active reference, not before/after. The [portrait fixtures](media/issue-271/hold-portrait-fixtures.png) show Classic, Solarized Light and Gruvbox with used Hold; these are isolated native HUD fixtures on a solid comparison background, not full gameplay screenshots. Both sheets were visually inspected: material/color and shape match, holes stay empty, and the label remains below the preview. Portrait overviews are uniformly scaled for presentation. No physical-device visual acceptance is claimed.

Final exact-head verification and merge are recorded in the PR and consolidated issue comment. This follow-up is outside published v1.6.0 and does not publish an additional release.
