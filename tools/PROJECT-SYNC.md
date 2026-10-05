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

## GitHub Actions setup

Add an Actions repository secret named `PROJECT_SYNC_TOKEN` containing a token
with access to this repository and the user project. A classic personal access
token requires `repo` and `project` scopes. The ordinary `GITHUB_TOKEN` cannot
access user projects. Never commit or print credentials.

After `project-sync.yml` is on `main`, run **Issue project sync** manually and
confirm `RemainingIssues` is empty. The workflow then reconciles all open issues
when an issue is opened, reopened, or transferred into this repository, and daily.
The daily pass also catches issues created by another workflow using
`GITHUB_TOKEN`, whose events do not trigger another workflow.

If the secret expires or loses project access, the run fails visibly; renew the
secret and rerun the workflow. A view with a restrictive filter can still show
fewer items than the repository; inspect the reported `Views` before changing it.

Run the offline membership/status regression checks with
`pwsh -NoProfile -File tools/tests/Sync-IssueProject.Checks.ps1`.

References: [GitHub project automation with Actions](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/automating-projects-using-actions)
and [automatic project addition](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/adding-items-automatically).
