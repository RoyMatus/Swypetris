---
name: code-quality-review
description: Review a concrete Swypetris correctness or performance concern, including a supplied SonarQube finding.
---

# Code quality review

Trace the affected call and state path, then test whether the concern can cause an observable regression. For a SonarQube finding, inspect its rule, location, and surrounding code before accepting its severity or proposing suppression.

Prioritize correctness, lifecycle, duplicated game rules, and material performance. Report a confirmed defect with its failure path and the smallest viable correction; mark an unverified risk as a hypothesis. Recommend a focused test only when it can detect the failure. Avoid style-only findings and unrelated refactoring.

For an assigned fix, use the repository's `android-test-selection` skill to choose verification and report only checks that actually completed.
