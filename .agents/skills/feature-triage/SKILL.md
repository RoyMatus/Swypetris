---
name: feature-triage
description: Prepare scope, order, acceptance criteria, and metadata for a Swypetris feature issue through the repository API workflow.
---

# Feature triage

Inspect the requested issue with `tools/Update-Issue.ps1 -Inspect -IncludeContent`; use `-ListOptions` only when choosing a label or milestone. Compare the request with current product behavior and relevant dependencies. Separate confirmed behavior, user intent, proposed decisions, and open questions.

Return a small implementation order, acceptance criteria, and justified Work Type, Priority, Status, label, assignee, milestone, and dependency recommendations. Leave On Hold issues out until resumed. Do not decide unresolved gameplay rules for the user.

When an issue update is assigned, preview with `-WhatIf`, apply only the intended fields through the script, and inspect the result. After `ProjectAccessError`, report the missing scope once and skip further Project reads until credentials change. Keep GitHub-facing text in English and never expose tokens. The main agent owns branch, PR, merge, and closure sequencing under `AGENTS.md`.
