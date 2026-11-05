# Swypetris 1.11.5

- Reduced temporary allocations in collision, grounding and landing-position checks while preserving game rules.
- Reused static field, grid, fruit and celebration drawing, score digit paths and unchanged HUD data.
- Paused menu, intro and celebration animation clocks while another window has focus; returning continues the previous phase.
