---
name: security-review
description: Investigate a bounded Swypetris Android security concern or supplied security finding before a targeted fix.
---

# Security review

Define the asset, untrusted input, trust boundary, and reachable code path for the assigned concern. Inspect the relevant manifest declaration, exported component, permission, backup rule, local data flow, network use, or signing path only when it can affect that boundary.

Confirm whether the path is reachable and what an attacker could actually do. Distinguish an exploitable defect, a defense-in-depth opportunity, and an unverified hypothesis. Use a supplied scanner report as evidence to check, not as proof by itself; do not upload code or secrets to a service.

For a confirmed assigned fix, preserve existing behavior and verify the specific failure mode. Report the evidence, impact, changed files, completed checks, and residual uncertainty.
