---
name: feature-triage
description: Prepare Swypetris issue scope, acceptance criteria, ordering and evidence-based GitHub metadata.
---

# Feature triage

Inspect with `tools/Update-Issue.ps1 -IssueNumber N -Inspect -IncludeContent`; use `-ListOptions` when choosing metadata options. Compare the request with current behavior and real technical dependencies, separating evidence, user intent, proposed decisions and unknowns.

Use [DEVELOPMENT-WORKFLOW.md](../../../publishing/DEVELOPMENT-WORKFLOW.md) and its issue/epic templates for metadata, native child order and completion contracts. Return a small implementation queue, measurable acceptance criteria and justified Work Type, Priority, Status, labels, assignee, milestone and dependencies. Preparation alone leaves work Backlog; preserve On Hold until resumed. Scheduling order is not a technical blocker. Do not choose unresolved gameplay rules for the user.

For assigned writes, preview `Update-Issue.ps1` with `-WhatIf`, apply intended fields and inspect results. Follow [AGENTS.md](../../../AGENTS.md) for English GitHub text, ProjectAccessError handling and PR/closure sequencing. Triage alone needs no builds.
