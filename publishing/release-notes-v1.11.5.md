# Swypetris 1.11.5

- Paused the menu sky animation while another window has focus, reducing unnecessary rendering behind system panels and dialogs.
- Returning to the menu continues the animation from its previous phase.
- Reduced temporary allocations in collision and landing-position checks while preserving piece coordinates and game rules.
