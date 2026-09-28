<#[
.SYNOPSIS
Updates a Swypetris issue and its GitHub Project fields through the GitHub API.
.EXAMPLE
./tools/Update-Issue.ps1 -IssueNumber 70 -Status 'In Progress' -WorkType Design -Priority Medium
.EXAMPLE
./tools/Update-Issue.ps1 -IssueNumber 70 -Status Testing -Comment 'Verified on emulator-5554.' -WhatIf
.EXAMPLE
./tools/Update-Issue.ps1 -IssueNumber 39 -Inspect -IncludeContent
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][ValidateRange(1, 1000000)][int]$IssueNumber,
    [string]$Repository = 'RoyMatus/Swypetris',
    [ValidateRange(1, 1000000)][int]$ProjectNumber = 7,
    [string]$Status,
    [string]$WorkType,
    [string]$Priority,
    [string[]]$AddLabels,
    [string[]]$AddAssignees,
    [ValidateRange(1, 1000000)][int]$MilestoneNumber,
    [string]$MilestoneTitle,
    [int]$ParentIssueNumber,
    [int[]]$BlockedByIssueNumbers,
    [int[]]$BlocksIssueNumbers,
    [string]$CreateLinkedBranch,
    [ValidateSet('open', 'closed')][string]$State,
    [string]$Comment,
    [string]$PullRequestUrl,
    [switch]$Inspect,
    [switch]$IncludeContent,
    [switch]$ListOptions
)

$ErrorActionPreference = 'Stop'
if (($IncludeContent -or $ListOptions) -and -not $Inspect) {
    throw '-IncludeContent and -ListOptions require -Inspect.'
}
$parts = $Repository.Split('/')
if ($parts.Count -ne 2 -or !$parts[0] -or !$parts[1]) { throw 'Repository must have the form owner/name.' }
Import-Module (Join-Path $PSScriptRoot 'GitHub-Api.psm1') -Force
$client = New-GitHubClient -Repository $Repository

function Get-ProjectItem {
    $query = @'
query($owner: String!, $repo: String!, $number: Int!) {
  repository(owner: $owner, name: $repo) {
    issue(number: $number) {
      projectItems(first: 100) {
        nodes {
          id
          project { id number }
          fieldValues(first: 100) {
            nodes {
              ... on ProjectV2ItemFieldSingleSelectValue {
                name
                field { ... on ProjectV2FieldCommon { name } }
              }
            }
          }
        }
      }
    }
  }
}
'@
    try {
        $data = Invoke-GitHubGraphQL -Client $client -Query $query -Variables @{ owner = $parts[0]; repo = $parts[1]; number = $IssueNumber }
    } catch {
        if ($_.Exception.Message -notmatch 'INSUFFICIENT_SCOPES') { throw }
        throw 'GitHub Project access is unavailable. Set GH_TOKEN with read:project for inspection and project for updates.'
    }
    if (!$data.repository.issue) { throw "Issue $Repository#$IssueNumber was not found." }
    $items = @($data.repository.issue.projectItems.nodes | Where-Object { $_.project.number -eq $ProjectNumber })
    if ($items.Count -ne 1) { throw "Expected one project item for issue #$IssueNumber in project #$ProjectNumber; found $($items.Count)." }
    return $items[0]
}

function Get-ProjectValues {
    $item = Get-ProjectItem
    $values = @{}
    foreach ($value in $item.fieldValues.nodes) {
        if ($value.field.name -in @('Status', 'Work Type', 'Priority')) { $values[$value.field.name] = $value.name }
    }
    return [pscustomobject]@{
        Item = $item
        Status = $values['Status']
        WorkType = $values['Work Type']
        Priority = $values['Priority']
    }
}

$issue = Invoke-GitHubRest -Client $client -Method Get -Path "issues/$IssueNumber"
if (!$issue.id) { throw "Issue $Repository#$IssueNumber was not found." }
$milestones = @()
if ($ListOptions -or $MilestoneTitle) {
    $milestones = @(Invoke-GitHubRestPaged -Client $client -Path 'milestones?state=all')
}
if ($Inspect) {
    $blockedBy = @(Invoke-GitHubRestPaged -Client $client -Path "issues/$IssueNumber/dependencies/blocked_by")
    $blocking = @(Invoke-GitHubRestPaged -Client $client -Path "issues/$IssueNumber/dependencies/blocking")
    $availableLabels = if ($ListOptions) { @(Invoke-GitHubRestPaged -Client $client -Path 'labels') } else { @() }
    $comments = if ($IncludeContent) { @(Invoke-GitHubRestPaged -Client $client -Path "issues/$IssueNumber/comments") } else { @() }
    $linksQuery = @'
query($owner: String!, $repo: String!, $number: Int!) {
  repository(owner: $owner, name: $repo) {
    issue(number: $number) {
      parent { number }
      linkedBranches(first: 100) { nodes { ref { name } } }
    }
  }
}
'@
    $links = (Invoke-GitHubGraphQL -Client $client -Query $linksQuery -Variables @{ owner = $parts[0]; repo = $parts[1]; number = $IssueNumber }).repository.issue
    $project = $null
    $projectAccessError = $null
    try { $project = Get-ProjectValues } catch {
        if ($_.Exception.Message -notmatch 'GitHub Project access is unavailable') { throw }
        $projectAccessError = $_.Exception.Message
    }
    $inspection = [ordered]@{
        Issue = "$Repository#$IssueNumber"
        Title = $issue.title
        State = $issue.state
        Labels = @($issue.labels | ForEach-Object { $_.name })
        Assignees = @($issue.assignees | ForEach-Object { $_.login })
        Milestone = $issue.milestone.title
        BlockedBy = @($blockedBy | Where-Object { $_ } | ForEach-Object { $_.number })
        Blocking = @($blocking | Where-Object { $_ } | ForEach-Object { $_.number })
        ParentIssue = $links.parent.number
        LinkedBranches = @($links.linkedBranches.nodes | ForEach-Object { $_.ref.name })
        ProjectStatus = $project.Status
        ProjectWorkType = $project.WorkType
        ProjectPriority = $project.Priority
        ProjectAccessError = $projectAccessError
        AvailableMilestones = @($milestones | ForEach-Object { "#$($_.number) $($_.title) [$($_.state); $($_.description)]" })
        AvailableLabels = @($availableLabels | ForEach-Object { foreach ($label in @($_)) { $label.name } })
    }
    if ($IncludeContent) {
        $inspection.Body = $issue.body
        $inspection.Comments = @($comments | ForEach-Object {
            [pscustomobject]@{ Author = $_.user.login; Body = $_.body; CreatedAt = $_.created_at; Url = $_.html_url }
        })
    }
    [pscustomobject]$inspection
}
if ($MilestoneTitle -and $PSBoundParameters.ContainsKey('MilestoneNumber')) {
    throw 'Specify MilestoneTitle or MilestoneNumber, not both.'
}
if ($MilestoneTitle) {
    $match = @($milestones | Where-Object title -eq $MilestoneTitle)
    if ($match.Count -ne 1) { throw "Expected one milestone titled '$MilestoneTitle'; found $($match.Count)." }
    $MilestoneNumber = $match[0].number
}
if ($State -eq 'closed' -and !$PullRequestUrl) {
    throw 'Closing an issue requires -PullRequestUrl for the closing comment.'
}

$projectUpdates = @{ Status = $Status; 'Work Type' = $WorkType; Priority = $Priority }
if (@($projectUpdates.Values | Where-Object { $_ }).Count -gt 0) {
    $item = Get-ProjectItem
    $projectId = $item.project.id
    $itemId = $item.id
    $fieldQuery = @'
query($id: ID!) {
  node(id: $id) {
    ... on ProjectV2 {
      fields(first: 100) {
        nodes { ... on ProjectV2SingleSelectField { id name options { id name } } }
      }
    }
  }
}
'@
    $fields = (Invoke-GitHubGraphQL -Client $client -Query $fieldQuery -Variables @{ id = $projectId }).node.fields.nodes
    $updateMutation = @'
mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
  updateProjectV2ItemFieldValue(input: {
    projectId: $project, itemId: $item, fieldId: $field,
    value: { singleSelectOptionId: $option }
  }) { projectV2Item { id } }
}
'@
    $requestedFields = @()
    foreach ($fieldName in @('Status', 'Work Type', 'Priority')) {
        $wanted = $projectUpdates[$fieldName]
        if (!$wanted) { continue }
        $field = @($fields | Where-Object name -eq $fieldName)
        if ($field.Count -ne 1) { throw "Project field '$fieldName' was not found or is not a single-select field." }
        $option = @($field[0].options | Where-Object name -eq $wanted)
        if ($option.Count -ne 1) {
            $available = ($field[0].options | ForEach-Object name) -join ', '
            throw "Invalid $fieldName '$wanted'. Available values: $available"
        }
        $requestedFields += [pscustomobject]@{ Name = $fieldName; Value = $wanted; FieldId = $field[0].id; OptionId = $option[0].id }
    }
    foreach ($requested in $requestedFields) {
        if (!$PSCmdlet.ShouldProcess("$Repository#$IssueNumber", "Set project $($requested.Name) to $($requested.Value)")) { continue }
        try {
            $null = Invoke-GitHubGraphQL -Client $client -Query $updateMutation -Variables @{ project = $projectId; item = $itemId; field = $requested.FieldId; option = $requested.OptionId }
        } catch {
            if ($_.Exception.Message -notmatch 'INSUFFICIENT_SCOPES') { throw }
            throw 'GitHub Project update requires GH_TOKEN with the project scope.'
        }
        $actual = Get-ProjectValues
        $actualValue = switch ($requested.Name) { 'Status' { $actual.Status } 'Work Type' { $actual.WorkType } 'Priority' { $actual.Priority } }
        if ($actualValue -ne $requested.Value) { throw "Project $($requested.Name) update was not confirmed: expected '$($requested.Value)', got '$actualValue'." }
        Write-Output "Project $($requested.Name) = $($requested.Value)"
    }
}

$issuePatch = @{}
if ($AddLabels) { $issuePatch.labels = @( (@($issue.labels | ForEach-Object { $_.name }) + $AddLabels) | Select-Object -Unique ) }
if ($AddAssignees) { $issuePatch.assignees = @( (@($issue.assignees | ForEach-Object { $_.login }) + $AddAssignees) | Select-Object -Unique ) }
if ($PSBoundParameters.ContainsKey('MilestoneNumber') -or $MilestoneTitle) { $issuePatch.milestone = $MilestoneNumber }
if ($State) { $issuePatch.state = $State }
if ($issuePatch.Count -gt 0 -and $PSCmdlet.ShouldProcess("$Repository#$IssueNumber", 'Update issue fields')) {
    $null = Invoke-GitHubRest -Client $client -Method Patch -Path "issues/$IssueNumber" -Body $issuePatch
    Write-Output 'Issue fields updated'
}
if ($ParentIssueNumber) {
    if ($ParentIssueNumber -eq $IssueNumber) { throw 'An issue cannot be its own parent.' }
    if ($PSCmdlet.ShouldProcess("$Repository#$IssueNumber", "Set parent issue #$ParentIssueNumber")) {
        $null = Invoke-GitHubRest -Client $client -Method Post -Path "issues/$ParentIssueNumber/sub_issues" -Body @{ sub_issue_id = $issue.id }
        Write-Output "Parent issue = #$ParentIssueNumber"
    }
}
foreach ($number in $BlockedByIssueNumbers) {
    if ($number -eq $IssueNumber) { throw 'An issue cannot block itself.' }
    $other = Invoke-GitHubRest -Client $client -Method Get -Path "issues/$number"
    if ($PSCmdlet.ShouldProcess("$Repository#$IssueNumber", "Add blocked-by relationship to #$number")) {
        $null = Invoke-GitHubRest -Client $client -Method Post -Path "issues/$IssueNumber/dependencies/blocked_by" -Body @{ issue_id = $other.id }
        Write-Output "Blocked by #$number"
    }
}
foreach ($number in $BlocksIssueNumbers) {
    if ($number -eq $IssueNumber) { throw 'An issue cannot block itself.' }
    if ($PSCmdlet.ShouldProcess("$Repository#$IssueNumber", "Add blocking relationship to #$number")) {
        $null = Invoke-GitHubRest -Client $client -Method Post -Path "issues/$number/dependencies/blocked_by" -Body @{ issue_id = $issue.id }
        Write-Output "Blocks #$number"
    }
}
if ($CreateLinkedBranch) {
    $base = Invoke-GitHubRest -Client $client -Method Get -Path 'git/ref/heads/main'
    $mutation = @'
mutation($issue: ID!, $name: String!, $oid: GitObjectID!) {
  createLinkedBranch(input: { issueId: $issue, name: $name, oid: $oid }) {
    linkedBranch { id }
  }
}
'@
    if ($PSCmdlet.ShouldProcess("$Repository#$IssueNumber", "Create linked branch $CreateLinkedBranch")) {
        $null = Invoke-GitHubGraphQL -Client $client -Query $mutation -Variables @{ issue = $issue.node_id; name = $CreateLinkedBranch; oid = $base.object.sha }
        Write-Output "Linked branch = $CreateLinkedBranch"
    }
}
if ($PullRequestUrl) {
    if ($PullRequestUrl -notmatch '^https://github\.com/[^/]+/[^/]+/pull/\d+$') { throw 'PullRequestUrl must be a GitHub pull request URL.' }
    $Comment = if ($Comment) { "$Comment`n`nPR: $PullRequestUrl" } else { "PR: $PullRequestUrl" }
}
if ($Comment -and $PSCmdlet.ShouldProcess("$Repository#$IssueNumber", 'Post issue comment')) {
    $null = Invoke-GitHubRest -Client $client -Method Post -Path "issues/$IssueNumber/comments" -Body @{ body = $Comment }
    Write-Output 'Issue comment posted'
}
