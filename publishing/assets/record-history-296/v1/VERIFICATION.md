# Recorded package evidence

This is a fixed artwork handoff for issue #296, prepared on 2026-10-13. It does not implement the Results screen.

The following work had completed before the owner instructed that no further tests be run:

- Inspected all nine PNG resources: RGBA images with transparent outer edges. The trophy layer has no fruits; the fruit sprites are independently composable. Detailed sizes, source hashes and crop bounds are in `asset-validation.json`.
- Compiled nine PNGs and the badge VectorDrawable with Android SDK Build Tools 36.1.0 `aapt2 compile`. Exit code 0, ten compiled resource entries. This is resource compilation only, not a linked or built application. See `android-resource-validation.json`.
- Rendered the supplied resources in six browser-only score fixtures: 2,466; 42,500; 80,000; 90,000; 788,267; 999,999. Fruit counts were 0, 4, 8, 8, 8, 8 and badge counts were 0, 0, 0, 1, 8, 8. No JavaScript errors or overlapping badge circles were reported in these fixtures and the two card previews. See `preview-validation.json` and `previews/`.
- Visually inspected the normal and enlarged-text card previews. These are compositions of the exact packaged assets, not native Android screenshots.

After the owner's instruction, no further tests were run. Archive creation and checksum generation are packaging operations. No Android app build, gameplay test, instrumentation, device installation, implementation PR, merge or release was performed for this handoff. Native implementation checks remain future work under the ticket's delivery policy.
