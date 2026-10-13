# Results record-history design proposal

Prepared on 2026-10-13 from the owner's annotated screenshot and source commit `7b8e1bfa5a4e6669750d53457156f565160802a8`.

These files are issue evidence only. They do not implement the Android screen or approve a final layout.

- `reference.png`: the owner's unchanged annotation; the crossed-out standalone history/best-score summary is the removal target.
- `mockup-right.png`: recommended compact composition, with score/name/date on the left and the existing Victory trophy on the right.
- `mockup-center.png`: alternative composition with a centered trophy, at the cost of more vertical space.

The page header, palette and card treatment follow the existing screen. Metrics use a consistent value/label row. The illustrations use the repository's existing header and Victory artwork. Quantity badges are circular as requested. Both proposals show the two results visible in the source screenshot; no additional player records were invented.

For the current-rules sample score 788,267, 78 awards imply `[10,10,10,10,10,10,9,9]` in the established fruit order. Thus all eight fruits are earned in this sample. The 2,466-point result has no fruit. Implementation must split/composite the trophy and fruits so partial collections do not reveal baked-in unearned fruit; these full-collection previews do not implement that behavior.

The local interactive preview was rendered in a headless browser in both layouts at host viewport widths 736, 360 and 320 CSS pixels. Six inspected layouts loaded their images and had no horizontal/text overflow or intersecting quantity badges. Narrow compact layouts move the artwork below the text. The two full-size previews and narrow compact preview were visually inspected. These checks concern the proposal only; no Android build, instrumentation, gameplay or large-font acceptance result is claimed.

Keep the original artwork's appearance, truthful current/archived record semantics, existing storage and gameplay rules. See the issue for the full behavior and verification requirements. The design evidence branch should remain available while the ticket uses its commit-pinned image links.
