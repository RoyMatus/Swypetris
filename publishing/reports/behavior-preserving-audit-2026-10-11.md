# Behavior-preserving audit implementation — 2026-10-11

The owner approved sequential PRs for tool reliability, redundant calculations,
dependency verification, menu-audio preparation and updater disk writes. Gameplay,
layout, graphics, audio content, storage formats and application versions remain
protected. Existing Android runners and broad R8 rules are outside these changes.
The final verification is a manual signed run on main without publication.

Execution profile: Local, four Gradle workers, one Gradle process and one headless
emulator at a time. Focused local checks address each change's risk; required PR
CI remains mandatory. Successful unchanged checks are reused.

## Tool reliability

- Explicitly selected test classes must each contain a passing case. Unfiltered
  optional skips remain supported. Focused Pester: 16 passed, one platform skip,
  3.317 seconds; fixtures include a passing class alongside an all-skipped class.
- Signing backups restore into a protected sibling staging directory, then move
  only a complete key/credential set into an absent destination. Synthetic-key
  tests cover malformed input, partial failure, retry and a concurrent destination.
  Focused Pester: five passed, one platform skip, 4.03 seconds. No production signing credentials are used by these tests.
- Release smoke records observations independently of logcat availability and
  preserves the original UI failure when diagnostics fail. Incomplete diagnostics
  still fail an otherwise passing run. Six focused Python tests passed.
- Complete Python contract suite: 104 discovered, one platform skip, no failures,
  20.050 seconds. No local Android rebuild was needed for these tool changes;
  required CI covers the affected execution scripts on the final PR commit.

Context7 supplied current PowerShell cleanup/CLIXML documentation and Python
exception/context-manager semantics. These changes use existing supported APIs;
no framework or library upgrade is part of the implementation.
