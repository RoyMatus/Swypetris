# Swypetris connected block textures — issue 269

Prepared from the three latest figure sheets selected by the owner in this chat. The older six-sheet set is not part of this package and is not an implementation authority for this ticket.

## Ready-to-copy artwork

- `android/app/src/main/res/drawable-nodpi/`: **12 lossless RGBA WebP atlases**, one per theme. Copy this directory into the corresponding application directory.
- `sprites/<theme>/`: **84 transparent PNG figures**, seven shapes per theme, ready for previews or reuse. Their filenames are Android resource compatible if individual figure resources are needed; the game board should use the atlases so partially cleared figures retain their artwork.
- `manifest.json`: versioned mapping for all **336 canonical cell images**, resource names, source rectangles, canonical coordinates and SHA-256 hashes.
- `INTEGRATION.md`: concrete renderer/state/persistence changes needed to use the resources. This is an asset delivery, not an already integrated Android change.
- `previews/runtime-figures-1.png` through `runtime-figures-3.png`: all figures reconstructed from the packaged cells.
- `previews/explosion-fragments.png`: original cell and its four corresponding textured quadrant fragments for every theme. This illustrates sampling; it is not a screenshot of a running animation.
- `VALIDATION.json`: actual asset checks and their limits.
- `references/`: the exact three selected final catalogues, retained without alteration as the new visual target.
- `sources/`: transparent production cutout sheets prepared with the built-in image tool, used by the packaging script.

The atlases are **924 × 528**, comprising 7 shape columns × 4 canonical cell rows. A tile is 128 × 128 plus a two-pixel extruded gutter on each side. A source rectangle starts at `(shapeIndex * 132 + 2, cellIndex * 132 + 2)` and is 128 × 128. See the manifest rather than inferring tile size from atlas height.

Artwork is normalized to equal square cells and saved without runtime palette tint. Source cells contain roughly 55–80 pixels of drawn detail per axis; the 128-pixel assets resample that detail and do not imply new high-resolution detail. Thin existing material borders are retained. The package does not authorize changing board spacing, scene layout or background textures.

## Explosion fragments are mandatory

Every fragment must show the corresponding part of the exploded cube in the **currently selected theme**, including that cube's piece color, canonical cell image and displayed orientation. Do not use flat colored particles, a previous theme's cached texture, or the entire cell image repeated on every fragment.

No separate generated explosion textures are needed. For the existing four-quadrant effect, crop the 128 × 128 cell into four 64 × 64 regions. Select the fragment in the displayed cell coordinate system, map back through the cube's quarter-turn, and then apply the existing shard motion/spin. Preserve existing count, timing and opacity. Verify explosions for every theme and after changing themes.

## Checks and reproduction

Runtime integration, Android builds and device tests have not been performed by this asset task. `VALIDATION.json` records actual decoding, dimensions, geometry/mapping, transparent empty cells, padding, pixel equality and quadrant reconstruction checks. These are asset-integrity checks, not proof of runtime behavior or an automated aesthetic judgment.

Production background removal used the built-in image generation tool, with the prompt in `PROVENANCE.md`. The exported cutouts are fixed inputs: reproducing the package does not require calling an AI service again.

Mechanical slicing/packing uses Node.js and `sharp` 0.35.5 (libvips 8.18.7, libwebp 1.6.0). From this package directory, with `sharp` available:

```text
node tools/package-textures.cjs ./rebuilt .
node tools/validate-textures.cjs ./rebuilt
```

The first command preserves the selected art, slices the four canonical regions per figure, normalizes their geometry, and packs the PNG/WebP files; it does not recolor or synthesize material details. `SHA256SUMS.txt` covers every distributed package file except itself. The ZIP has a separate published SHA-256.
