# Issue project synchronization

Repository issues and GitHub Project items are separate lists. Project views may
also hide existing items through filters, or items may be archived.

Inspect membership and view filters without changing anything:

```powershell
./tools/Sync-IssueProject.ps1 -Inspect
```

Preview and then repair missing or archived open issues:

```powershell
./tools/Sync-IssueProject.ps1 -WhatIf
./tools/Sync-IssueProject.ps1
```

The script handles pagination, excludes pull requests, avoids duplicate items,
and verifies membership and status after each write. Open issues with no Status
or a stale `Done` Status receive `Backlog`; existing active statuses, including
`On Hold`, are preserved. This catches reopened issues whose project Status was
not reset by the built-in closed-issue workflow.
It does not change view filters or add closed issues. Use `Update-Issue.ps1`
for Status, Work Type, and Priority changes after membership is established.

## Local credentials and reconciliation

Run the script locally after issue creation/reopening or when reconciling an epic; there is no daily GitHub Actions schedule. Use the repository API client's existing authenticated local credential flow with access to the repository and user project. A classic personal access token needs `repo` and `project`; never print or commit credentials or expand access implicitly.

Confirm `RemainingIssues` is empty after a repair. A restrictive view can still hide existing items, so inspect reported `Views` before changing it. If Project access fails, report it once and stop Project operations until credentials change. Do not dispatch the archived `project-sync.yml` workflow.

Run offline membership/status checks with `pwsh -NoProfile -File tools/tests/Sync-IssueProject.Checks.ps1`.
