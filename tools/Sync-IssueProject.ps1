<#
.SYNOPSIS
Ensures open issues belong to the project and do not retain a completed status.
.EXAMPLE
./tools/Sync-IssueProject.ps1 -Inspect
.EXAMPLE
./tools/Sync-IssueProject.ps1 -WhatIf
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Repository = 'RoyMatus/Swypetris',
    [string]$ProjectOwner = 'RoyMatus',
    [ValidateRange(1, 1000000)][int]$ProjectNumber = 7,
    [string]$OpenStatus = 'Backlog',
    [string]$CompletedStatus = 'Done',
    [switch]$Inspect
)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'GitHub-Api.psm1') -Force
$client = New-GitHubClient -Repository $Repository

function Get-ProjectSnapshot {
    $query = @'
query($owner: String!, $number: Int!, $after: String) {
  user(login: $owner) {
    projectV2(number: $number) {
      id
      title
      fields(first: 100) {
        nodes { ... on ProjectV2SingleSelectField { id name options { id name } } }
        pageInfo { hasNextPage }
      }
      views(first: 100) { nodes { number name filter } pageInfo { hasNextPage } }
      items(first: 100, after: $after, archivedStates: [ARCHIVED, NOT_ARCHIVED]) {
        pageInfo { hasNextPage endCursor }
        nodes {
          id
          isArchived
          content { ... on Issue { id number repository { nameWithOwner } } }
          fieldValues(first: 100) {
            nodes { ... on ProjectV2ItemFieldSingleSelectValue { name field { ... on ProjectV2FieldCommon { name } } } }
            pageInfo { hasNextPage }
          }
        }
      }
    }
  }
}
'@
    $items = @()
    $after = $null
    do {
        try {
            $data = Invoke-GitHubGraphQL -Client $client -Query $query -Variables @{ owner = $ProjectOwner; number = $ProjectNumber; after = $after }
        } catch {
            if ($_.Exception.Message -notmatch 'INSUFFICIENT_SCOPES') { throw }
            throw 'GitHub Project access is unavailable. Use read:project for inspection and project for synchronization.'
        }
        $project = $data.user.projectV2
        if (!$project.id) { throw "User project $ProjectOwner/$ProjectNumber was not found." }
        if ($project.views.pageInfo.hasNextPage) { throw 'More than 100 project views exist; refusing to report incomplete filters.' }
        if ($project.fields.pageInfo.hasNextPage) { throw 'More than 100 project fields exist; refusing to inspect an incomplete schema.' }
        if (@($project.items.nodes | Where-Object { $_.fieldValues.pageInfo.hasNextPage }).Count) { throw 'More than 100 item field values exist; refusing to assess incomplete statuses.' }
        $items += @($project.items.nodes | Where-Object { $_ })
        $next = $project.items.pageInfo.endCursor
        if ($project.items.pageInfo.hasNextPage -and (!$next -or $next -eq $after)) { throw 'Project pagination did not advance.' }
        $after = $next
    } while ($project.items.pageInfo.hasNextPage)
    [pscustomobject]@{ Id = $project.id; Title = $project.title; Items = $items; Views = @($project.views.nodes); Fields = @($project.fields.nodes) }
}

function Get-ItemStatus($Item) {
    ($Item.fieldValues.nodes | Where-Object { $_.field.name -eq 'Status' } | Select-Object -First 1).name
}

$snapshot = Get-ProjectSnapshot
$statusFields = @($snapshot.Fields | Where-Object name -eq 'Status')
if ($statusFields.Count -ne 1) { throw 'Expected a single-select Status field.' }
$openOptions = @($statusFields[0].options | Where-Object name -eq $OpenStatus)
if ($openOptions.Count -ne 1 -or $OpenStatus -eq $CompletedStatus) { throw 'OpenStatus must name an existing non-completed Status option.' }
$issues = @(Invoke-GitHubRestPaged -Client $client -Path 'issues?state=open' | Where-Object { !$_.pull_request })
$missing = @()
$archived = @()
$staleStatuses = @()
$missingStatuses = @()
$addMutation = @'
mutation($project: ID!, $content: ID!) {
  addProjectV2ItemById(input: { projectId: $project, contentId: $content }) { item { id } }
}
'@
$unarchiveMutation = @'
mutation($project: ID!, $item: ID!) {
  unarchiveProjectV2Item(input: { projectId: $project, itemId: $item }) { item { id } }
}
'@
$statusMutation = @'
mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
  updateProjectV2ItemFieldValue(input: {
    projectId: $project, itemId: $item, fieldId: $field,
    value: { singleSelectOptionId: $option }
  }) { projectV2Item { id } }
}
'@
foreach ($issue in $issues) {
    $matches = @($snapshot.Items | Where-Object { $_.content.id -eq $issue.node_id })
    if ($matches.Count -gt 1) { throw "Duplicate project items for issue #$($issue.number)." }
    if ($matches.Count -eq 0 -or $matches[0].isArchived) {
        $isMissing = $matches.Count -eq 0
        if ($isMissing) { $missing += $issue.number } else { $archived += $issue.number }
        $action = if ($isMissing) { 'Add issue to project' } else { 'Unarchive open issue in project' }
        if (!$Inspect -and $PSCmdlet.ShouldProcess("$Repository#$($issue.number) -> $ProjectOwner/$ProjectNumber", $action)) {
            $mutation = if ($isMissing) { $addMutation } else { $unarchiveMutation }
            $variables = if ($isMissing) { @{ project = $snapshot.Id; content = $issue.node_id } } else { @{ project = $snapshot.Id; item = $matches[0].id } }
            $null = Invoke-GitHubGraphQL -Client $client -Query $mutation -Variables $variables
            $snapshot = Get-ProjectSnapshot
            $matches = @($snapshot.Items | Where-Object { $_.content.id -eq $issue.node_id -and !$_.isArchived })
            if ($matches.Count -ne 1) { throw "Project membership was not confirmed for issue #$($issue.number)." }
        }
    }
    if ($matches.Count -ne 1) { continue }
    $status = Get-ItemStatus $matches[0]
    if ($status -and $status -ne $CompletedStatus) { continue }
    if ($status -eq $CompletedStatus) { $staleStatuses += $issue.number } else { $missingStatuses += $issue.number }
    if ($Inspect -or !$PSCmdlet.ShouldProcess("$Repository#$($issue.number)", "Set open issue Status to $OpenStatus")) { continue }
    # Recheck the issue before changing a completed status: it may have closed
    # since the initial repository snapshot was read.
    $current = Invoke-GitHubRest -Client $client -Method Get -Path "issues/$($issue.number)"
    if ($current.state -ne 'open') { continue }
    $null = Invoke-GitHubGraphQL -Client $client -Query $statusMutation -Variables @{ project = $snapshot.Id; item = $matches[0].id; field = $statusFields[0].id; option = $openOptions[0].id }
    $snapshot = Get-ProjectSnapshot
    $confirmed = @($snapshot.Items | Where-Object { $_.content.id -eq $issue.node_id })
    if ($confirmed.Count -ne 1 -or (Get-ItemStatus $confirmed[0]) -ne $OpenStatus) { throw "Project Status was not confirmed for issue #$($issue.number)." }
}
$remaining = @($issues | Where-Object {
    $id = $_.node_id
    @($snapshot.Items | Where-Object { $_.content.id -eq $id -and !$_.isArchived }).Count -ne 1
} | ForEach-Object { $_.number })
$remainingStatuses = @($issues | Where-Object {
    $id = $_.node_id
    $item = @($snapshot.Items | Where-Object { $_.content.id -eq $id })
    if ($item.Count -eq 1) { $status = Get-ItemStatus $item[0]; !$status -or $status -eq $CompletedStatus }
} | ForEach-Object { $_.number })
[pscustomobject]@{
    Project = "$ProjectOwner/$ProjectNumber"
    OpenIssueCount = $issues.Count
    MissingIssues = $missing
    ArchivedOpenIssues = $archived
    RemainingIssues = $remaining
    StaleCompletedIssues = $staleStatuses
    MissingStatusIssues = $missingStatuses
    RemainingStatusIssues = $remainingStatuses
    Views = $snapshot.Views
}
if (!$Inspect -and !$WhatIfPreference -and $remaining.Count -gt 0) { throw "Open issues remain outside the project: $($remaining -join ', ')." }
if (!$Inspect -and !$WhatIfPreference -and $remainingStatuses.Count -gt 0) { throw "Open issues still have missing or completed statuses: $($remainingStatuses -join ', ')." }
