<#[
.SYNOPSIS
Updates a Swypetris issue and its GitHub Project fields through the GitHub API.
.EXAMPLE
./tools/Update-Issue.ps1 -IssueNumber 70 -Status 'In Progress' -WorkType Design -Priority Medium
.EXAMPLE
./tools/Update-Issue.ps1 -IssueNumber 70 -Status Testing -Comment 'Verified on emulator-5554.' -WhatIf
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
    [switch]$Inspect
)

$ErrorActionPreference = 'Stop'
$parts = $Repository.Split('/')
if ($parts.Count -ne 2 -or !$parts[0] -or !$parts[1]) { throw 'Repository must have the form owner/name.' }
$token = if ($env:GH_TOKEN) { $env:GH_TOKEN } elseif ($env:GITHUB_TOKEN) { $env:GITHUB_TOKEN } else {
    $credential = "protocol=https`nhost=github.com`n`n" | & git credential fill
    if ($LASTEXITCODE -ne 0) { throw 'Git Credential Manager did not return a GitHub credential.' }
    ($credential | Where-Object { $_ -like 'password=*' } | Select-Object -First 1) -replace '^password=', ''
}
if (!$token) { throw 'Set GH_TOKEN/GITHUB_TOKEN or sign in through Git Credential Manager.' }
$headers = @{ Authorization = "Bearer $token"; Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28' }

function Invoke-GithubGraphQL([string]$Query, [hashtable]$Variables) {
    $body = @{ query = $Query; variables = $Variables } | ConvertTo-Json -Depth 12 -Compress
    $result = Invoke-RestMethod -Uri 'https://api.github.com/graphql' -Method Post -Headers $headers -ContentType 'application/json' -Body $body
    if ($result.errors) { throw (($result.errors | ForEach-Object { $_.message }) -join '; ') }
    return $result.data
}

function Invoke-GithubRest([string]$Method, [string]$Path, [hashtable]$Body = @{}) {
    $uri = "https://api.github.com/repos/$Repository/$Path"
    if ($Method -eq 'Get') { return Invoke-RestMethod -Uri $uri -Method Get -Headers $headers }
    $json = $Body | ConvertTo-Json -Depth 10 -Compress
    return Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -ContentType 'application/json' -Body $json
}

$issue = Invoke-GithubRest Get "issues/$IssueNumber"
if (!$issue.id) { throw "Issue $Repository#$IssueNumber was not found." }
$milestones = @()
if ($Inspect -or $MilestoneTitle) {
    $milestones = @(Invoke-GithubRest Get 'milestones?state=all&per_page=100')
    $milestones = @(foreach ($entry in $milestones) { foreach ($milestone in @($entry)) { $milestone } })
}
if ($Inspect) {
    $blockedBy = @(Invoke-GithubRest Get "issues/$IssueNumber/dependencies/blocked_by?per_page=100")
    $blocking = @(Invoke-GithubRest Get "issues/$IssueNumber/dependencies/blocking?per_page=100")
    $availableLabels = @(Invoke-GithubRest Get 'labels?per_page=100')
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
    $links = (Invoke-GithubGraphQL $linksQuery @{ owner = $parts[0]; repo = $parts[1]; number = $IssueNumber }).repository.issue
    [pscustomobject]@{
        Issue = "$Repository#$IssueNumber"
        State = $issue.state
        Labels = @($issue.labels | ForEach-Object { $_.name })
        Assignees = @($issue.assignees | ForEach-Object { $_.login })
        Milestone = $issue.milestone.title
        BlockedBy = @($blockedBy | Where-Object { $_ } | ForEach-Object { $_.number })
        Blocking = @($blocking | Where-Object { $_ } | ForEach-Object { $_.number })
        ParentIssue = $links.parent.number
        LinkedBranches = @($links.linkedBranches.nodes | ForEach-Object { $_.ref.name })
        AvailableMilestones = @($milestones | ForEach-Object { "#$($_.number) $($_.title) [$($_.state); $($_.description)]" })
        AvailableLabels = @($availableLabels | ForEach-Object { foreach ($label in @($_)) { $label.name } })
    }
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
    $issueQuery = @'
query($owner: String!, $repo: String!, $number: Int!) {
  repository(owner: $owner, name: $repo) {
    issue(number: $number) {
      projectItems(first: 100) { nodes { id project { id number } } }
    }
  }
}
'@
    $data = Invoke-GithubGraphQL $issueQuery @{ owner = $parts[0]; repo = $parts[1]; number = $IssueNumber }
    if (!$data.repository.issue) { throw "Issue $Repository#$IssueNumber was not found." }
    $item = @($data.repository.issue.projectItems.nodes | Where-Object { $_.project.number -eq $ProjectNumber })
    if ($item.Count -ne 1) { throw "Expected one project item for issue #$IssueNumber in project #$ProjectNumber; found $($item.Count)." }
    $projectId = $item[0].project.id
    $itemId = $item[0].id
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
    $fields = (Invoke-GithubGraphQL $fieldQuery @{ id = $projectId }).node.fields.nodes
    $updateMutation = @'
mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
  updateProjectV2ItemFieldValue(input: {
    projectId: $project, itemId: $item, fieldId: $field,
    value: { singleSelectOptionId: $option }
  }) { projectV2Item { id } }
}
'@
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
        if ($PSCmdlet.ShouldProcess("$Repository#$IssueNumber", "Set project $fieldName to $wanted")) {
            $null = Invoke-GithubGraphQL $updateMutation @{ project = $projectId; item = $itemId; field = $field[0].id; option = $option[0].id }
            Write-Output "Project $fieldName = $wanted"
        }
    }
}

$issuePatch = @{}
if ($AddLabels) { $issuePatch.labels = @( (@($issue.labels | ForEach-Object { $_.name }) + $AddLabels) | Select-Object -Unique ) }
if ($AddAssignees) { $issuePatch.assignees = @( (@($issue.assignees | ForEach-Object { $_.login }) + $AddAssignees) | Select-Object -Unique ) }
if ($PSBoundParameters.ContainsKey('MilestoneNumber') -or $MilestoneTitle) { $issuePatch.milestone = $MilestoneNumber }
if ($State) { $issuePatch.state = $State }
if ($issuePatch.Count -gt 0 -and $PSCmdlet.ShouldProcess("$Repository#$IssueNumber", 'Update issue fields')) {
    $null = Invoke-GithubRest Patch "issues/$IssueNumber" $issuePatch
    Write-Output 'Issue fields updated'
}
if ($ParentIssueNumber) {
    if ($ParentIssueNumber -eq $IssueNumber) { throw 'An issue cannot be its own parent.' }
    if ($PSCmdlet.ShouldProcess("$Repository#$IssueNumber", "Set parent issue #$ParentIssueNumber")) {
        $null = Invoke-GithubRest Post "issues/$ParentIssueNumber/sub_issues" @{ sub_issue_id = $issue.id }
        Write-Output "Parent issue = #$ParentIssueNumber"
    }
}
foreach ($number in $BlockedByIssueNumbers) {
    if ($number -eq $IssueNumber) { throw 'An issue cannot block itself.' }
    $other = Invoke-GithubRest Get "issues/$number"
    if ($PSCmdlet.ShouldProcess("$Repository#$IssueNumber", "Add blocked-by relationship to #$number")) {
        $null = Invoke-GithubRest Post "issues/$IssueNumber/dependencies/blocked_by" @{ issue_id = $other.id }
        Write-Output "Blocked by #$number"
    }
}
foreach ($number in $BlocksIssueNumbers) {
    if ($number -eq $IssueNumber) { throw 'An issue cannot block itself.' }
    if ($PSCmdlet.ShouldProcess("$Repository#$IssueNumber", "Add blocking relationship to #$number")) {
        $null = Invoke-GithubRest Post "issues/$number/dependencies/blocked_by" @{ issue_id = $issue.id }
        Write-Output "Blocks #$number"
    }
}
if ($CreateLinkedBranch) {
    $base = Invoke-GithubRest Get 'git/ref/heads/main'
    $mutation = @'
mutation($issue: ID!, $name: String!, $oid: GitObjectID!) {
  createLinkedBranch(input: { issueId: $issue, name: $name, oid: $oid }) {
    linkedBranch { id }
  }
}
'@
    if ($PSCmdlet.ShouldProcess("$Repository#$IssueNumber", "Create linked branch $CreateLinkedBranch")) {
        $null = Invoke-GithubGraphQL $mutation @{ issue = $issue.node_id; name = $CreateLinkedBranch; oid = $base.object.sha }
        Write-Output "Linked branch = $CreateLinkedBranch"
    }
}
if ($PullRequestUrl) {
    if ($PullRequestUrl -notmatch '^https://github\.com/[^/]+/[^/]+/pull/\d+$') { throw 'PullRequestUrl must be a GitHub pull request URL.' }
    $Comment = if ($Comment) { "$Comment`n`nPR: $PullRequestUrl" } else { "PR: $PullRequestUrl" }
}
if ($Comment -and $PSCmdlet.ShouldProcess("$Repository#$IssueNumber", 'Post issue comment')) {
    $null = Invoke-GithubRest Post "issues/$IssueNumber/comments" @{ body = $Comment }
    Write-Output 'Issue comment posted'
}
