---
name: android-test-selection
description: Choose risk-based local checks for Swypetris changes and distinguish development checks from final PR and release gates.
---

# Android test selection

Map each changed behavior to a plausible failure mode and the smallest useful check:

- Game logic/geometry: affected JVM classes and coupled state rules.
- Compose/platform/persistence: relevant instrumentation, explicit device serial and affected JVM rules.
- Visuals: relevant portrait sizes/aspect ratios/font scales and appearance evidence; semantics alone cannot prove rendering.
- Instructions: diff, links and consistency, no Android build. Local tools: focused tool/contract tests.
- Cross-system changes or unresolved regression: broaden for the actual risk. Release candidates retain full regression and independent signing/runtime acceptance.

Use [CI.md](../../../publishing/CI.md) for mandatory final scope/commands, `tools/ci/select_checks.py` for conservative path mappings, and [TESTING.md](../../../TESTING.md) for inventory, TDD exceptions and supplemental release scenarios. Development checkpoints do not replace the exact-head PR or exact-main release gate.

Run `tools/Verify-Tests.ps1 -Suite FocusedJvm|FocusedAndroid -TestClass <classes>` with `-JavaHome`, explicit `-Serial` for device work and `-MaxWorkers` from the [host profile](../../../publishing/EXECUTION-PROFILES.md). Direct narrower Gradle/adb checks are allowed. Preserve RED logs/XML before GREEN overwrites them; full regression/Sonar/signing is not required for each TDD cycle.

Reuse applicable passing results and coordinate with the single heavy-check owner under [AGENTS.md](../../../AGENTS.md). Record executed versus cached results and remaining coverage; build/install success is not exercised behavior.
