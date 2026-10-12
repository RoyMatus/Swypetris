# Connected tetromino artwork: integration

The supplied images are prepared application assets. They require the renderer and saved-cell changes below; copying them over the old seven-tile materials alone cannot produce connected figure textures. This document describes implementation work, not work already completed or tested in the Android app.

## Asset contract

Copy the twelve `material_connected_<theme>.webp` files into `app/src/main/res/drawable-nodpi/`. Theme suffixes are `classic`, `monokai`, `gruvbox`, `vscode`, `dracula`, `nord`, `solarized_light`, `solarized_dark`, `github_light`, `tokyo_night`, `catppuccin`, and `synthwave_84`.

Each lossless RGBA atlas is **924 × 528 pixels**: seven columns in **I, O, T, S, Z, J, L** order and four rows in canonical `Tetromino.shape` index order. Each tile has a **128 × 128** core with a **2-pixel extruded gutter** on all sides; the pitch is **132 pixels**. The manifest records coordinates, asset mapping and hashes.

For zero-based `shapeIndex` and `cellIndex`:

```text
sourceX = shapeIndex * 132 + 2
sourceY = cellIndex  * 132 + 2
sourceWidth = sourceHeight = 128
```

The gutter protects filtering at atlas boundaries. Exclude it from the source rectangle; do not render it as additional cell padding. Decode and draw the supplied colors directly, without tint, palette recoloring, contrast overlays or random cell variants. Continue using the existing palette colors for semantic UI and outlines.

The 84 transparent whole-figure PNGs are normalized to each canonical occupied bounding box. Their file mapping is in the manifest. They are useful for previews and inspection; the cell atlases support the runtime board after partial line removal. I has preview-local y=0 but engine-canonical y=1. Do not change its spawn geometry to match the PNG bounds.

## Canonical identity and drawing

`app/src/main/java/ru/itoltec/swypetris/GameEngine.kt` owns `Tetromino.shape`, `Piece.cells()` and `GameState`. Use the existing shape order exactly:

| Shape | Cell 0 | Cell 1 | Cell 2 | Cell 3 |
| --- | --- | --- | --- | --- |
| I | (0,1) | (1,1) | (2,1) | (3,1) |
| O | (0,0) | (1,0) | (0,1) | (1,1) |
| T | (1,0) | (0,1) | (1,1) | (2,1) |
| S | (1,0) | (2,0) | (0,1) | (1,1) |
| Z | (0,0) | (1,0) | (1,1) | (2,1) |
| J | (0,0) | (0,1) | (1,1) | (2,1) |
| L | (2,0) | (0,1) | (1,1) | (2,1) |

Extend `BlockMaterial.draw()` in `app/src/main/java/ru/itoltec/swypetris/BlockMaterial.kt` to select artwork explicitly by shape, canonical cell index and clockwise quarter-turn count. Its current `image.height`-based, color-only source calculation is incompatible with these atlases. Update `materialResource()` to map the existing twelve `BlockTexture` values to the new resources. Remove superseded `material_<theme>.webp` resources after confirming no remaining references; retain the separate field backgrounds. Preserve `blockMaterial()`/`LocalBlockMaterial` caching and the shared provider in `GameContent.kt`; never decode, allocate replacement bitmaps or generate artwork inside a draw callback.

For active pieces, `Piece.cells()` preserves canonical list order while transforming cell positions. Assign indices **before** filtering offscreen cells. Rotate each source tile by the piece's quarter-turn count so its connectors rotate with the shape. **O uses effective artwork rotation 0**, matching its geometry, which ignores rotation. Do not rotate its individual tiles in place without also permuting their identities.

Cell width and height can differ. Apply the quarter-turn in square source coordinates, then scale to the destination rectangle. Rotating an already stretched screen-space rectangle by 90 degrees can swap dimensions and spill outside its intended cell.

`GameBoard.kt` owns `fallingPiece()`, `drawSettledCells()` and `block()`. Pass the new identity through these functions and `GamePalette.kt`'s `bevelBlock()`. Retain the existing 7% inset on each side of a cell: matching connector endpoints continue logically across this gutter. The atlas core must receive no additional baked presentation padding. Literal strokes across empty gutters would be a separate geometry change.

## Settled cells, clearing and persistence

The current board stores only `Tetromino?`. In `GameEngine.kt`, `PlacementApplication.lock()` discards source-cell identity and orientation. Extend the immutable board's owned cell data to retain at least **type, canonicalCellIndex (0–3), and effectiveQuarterTurns (0–3)**. Active-piece identity is derivable from its shape and rotation; no random seed or three-variant state is required. A separate piece-instance identifier is unnecessary for selecting these fixed assets.

In `finishClear()`, remove and shift visual metadata with the same occupied cells. Surviving fragments keep their original artwork; do not recalculate their cell index from their new board coordinates or connect independently placed figures because they become neighbors. Preserve collision, scoring, spawn, rotation and loss behavior.

`LineClearAnimation.kt`'s `ClearShard` and `shards()` must retain the source cell identity/orientation. `LineClearEffects.kt`'s `lineClearEffects()` should sample the corresponding cell fragment rather than repeat a full generic tile for each shard. Resolve each quadrant in the cell's displayed orientation before applying the existing shard animation. Preserve existing fragment count, timing, alpha and motion. Every explosion must use the currently selected theme with the exploded cell's exact artwork; never use flat particles or a stale previous-theme atlas. Do not paste the whole cell image onto every shard. For four quadrants, each source fragment is 64 x 64 within the 128 x 128 core. Include visual checks after switching themes.

`app/src/main/java/ru/itoltec/swypetris/GameSession.kt` currently uses saved-session format **4** and encodes board cells as type names. Extend the serializer, decoder and validation for the new metadata. Preserve the board-encoding cache; if metadata is stored separately, its changes must also invalidate that cache.

Implement an explicit format-4 migration while retaining saved gameplay: board occupancy/types, active position/rotation, held/next pieces, bag, score, progression, clocks and session identity. Older boards contain no original cell indices or settled orientations, so exact historical artwork identity cannot be recovered. Assign a documented deterministic legacy visual fallback, such as cell 0/rotation 0, while leaving the saved game playable. New placements receive full metadata. Do not merely bump the format and let `SessionStore.read()` discard the previous save; do not change the gameplay rules version for this cosmetic change. Continue validating malformed new metadata.

## Other consumers and scope

- `MainActivity.kt`: `HoldPreview()` draws `held.shape` with canonical indices and rotation 0. Hold retrieval already resets to spawn orientation. Preserve its current availability alpha and geometry.
- `GamePalette.kt`: `PalettePicker()` currently shows seven separate color samples. Use a declared representative cell per shape for that existing arrangement; preserve the cards, titles, order and selection behavior.
- Keep the outline-only landing ghost, faint Next preview, menu decoration and `field_<theme>.webp` backgrounds unchanged unless separately requested. The new atlas mapping must not accidentally replace the field-background mapping.

At ARGB8888, one atlas represents **1,951,488 bytes (about 1.86 MiB)** of decoded pixel storage; twelve represent about **22.33 MiB**, excluding decoder, allocator and GPU overhead. These are dimension-derived estimates, not measured process memory. Preserve the existing shared decoding strategy.

## Acceptance checks for implementation

Verify all twelve atlases and 84 figures against the manifest, including dimensions, transparency, shape masks, cell ordering and hashes. Reconstruct each figure from its four atlas cores and inspect its internal connectors and quarter-turns without recoloring. Check non-square destination cells and O behavior.

Add focused coverage for artwork identity through rotation, clipping, locking, line removal and new-session save/restore; verify format-4 migration preserves playable state. Cover Hold reset and source selection for clear fragments. Inspect actual portrait gameplay and Settings across all twelve themes at relevant sizes/font scales, confirming brightness and cell readability. Existing starting points include `MaterialRenderingTest`, `HoldTest`, `HoldIntegrationTest`, `LineClearAnimationTest`, `LineClearUiTest`, `SessionWriteTest` and `SessionLifecycleTest`; select and run checks through the repository's normal risk-based local workflow.
