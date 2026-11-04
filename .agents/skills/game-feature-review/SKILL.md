---
name: game-feature-review
description: Explore or review a proposed Swypetris gameplay feature for its fit with the one-finger puzzle loop, including tradeoffs and viable alternatives. Use for feature concepts before product decisions, not for routine implementation or code review.
---

# Swypetris gameplay feature review

Read the relevant rules in [README.md](../../../README.md) and affected gameplay paths. Treat existing behavior, the user's goal and inferred intent as separate inputs.

Generate a small set of concrete variants when the request calls for ideation. For each serious option, evaluate:

- Whether its action and feedback remain understandable with one finger and the existing gesture vocabulary.
- Effects on a short puzzle decision, pacing, difficulty across Easy / Medium / Hard, and learning cost.
- Interactions with scoring, spawning, progression, victory, loss, saved sessions, and accessibility only where the proposal touches them.
- Implementation and verification scope relative to the benefit.

Label untested player-experience predictions as hypotheses. Explain concrete balance, usability or cost concerns and offer alternatives preserving the user's intent; unresolved product rules belong to the user.

Return a concise recommendation, alternatives worth considering, assumptions, and the few decisions needed before an implementation issue can be specified.
