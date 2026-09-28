<#
.SYNOPSIS
Creates, inspects, updates, or merges a Swypetris pull request through the GitHub API.
.EXAMPLE
./tools/Manage-PullRequest.ps1 -Action Create -IssueNumber 70 -Head feat/issue-70-launcher-icon -Title 'Launcher icon' -BodyFile .\pr-body.md -WhatIf
.EXAMPLE
./tools/Manage-PullRequest.ps1 -Action Create -Head chore/agent-skills-delegation -Title 'Update agent guidance' -BodyFile .\pr-body.md -WhatIf
.EXAMPLE
./tools/Manage-PullRequest.ps1 -Action Inspect -PullRequestNumber 72
.EXAMPLE
./tools/Manage-PullRequest.ps1 -Action Update -PullRequestNumber 72 -Title 'Updated title' -BodyFile .\pr-body.md -WhatIf
.EXAMPLE
./tools/Manage-PullRequest.ps1 -Action Merge -PullRequestNumber 72 -WhatIf
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][ValidateSet('Create', 'Inspect', 'Update', 'Merge')][string]$Action,
    [string]$Repository = 'RoyMatus/Swypetris',
    [ValidateRange(1, 1000000)][int]$IssueNumber,
    [string]$Head,
    [string]$Base = 'main',
    [string]$Title,
    [string]$BodyFile,
    [ValidateRange(1, 1000000)][int]$PullRequestNumber
)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'GitHub-Api.psm1') -Force
$client = New-GitHubClient -Repository $Repository

function Get-PullRequestChecks([string]$Sha) {
    $response = Invoke-GitHubRest -Client $client -Method Get -Path "commits/$Sha/check-runs?per_page=100"
    $checks = @($response.check_runs | Where-Object { $_ })
    if ($response.total_count -gt $checks.Count) { throw 'More than 100 check runs exist; refusing to assess an incomplete list.' }
    return $checks
}

function Get-PullRequestStatuses([string]$Sha) {
    $response = Invoke-GitHubRest -Client $client -Method Get -Path "commits/$Sha/status?per_page=100"
    $statuses = @($response.statuses | Where-Object { $_ })
    if ($response.total_count -gt $statuses.Count) { throw 'More than 100 commit statuses exist; refusing to assess an incomplete list.' }
    return $statuses
}

function Get-RequiredChecks([string]$Branch) {
    $encodedBranch = [uri]::EscapeDataString($Branch)
    $branchInfo = Invoke-GitHubRest -Client $client -Method Get -Path "branches/$encodedBranch"
    if (!$branchInfo.protected) { return @() }
    try {
        $protection = Invoke-GitHubRest -Client $client -Method Get -Path "branches/$encodedBranch/protection"
    } catch {
        throw "Cannot verify required checks for protected branch '$Branch': $($_.Exception.Message)"
    }
    if (!$protection.required_status_checks) { return @() }
    $checks = @($protection.required_status_checks.checks | Where-Object { $_ })
    foreach ($context in @($protection.required_status_checks.contexts | Where-Object { $_ })) {
        if (@($checks | Where-Object { $_.context -eq $context }).Count -eq 0) {
            $checks += [pscustomobject]@{ context = $context; app_id = $null }
        }
    }
    return $checks
}

if ($Action -eq 'Create') {
    if (!$Head -or !$Title -or !$BodyFile) {
        throw 'Create requires -Head, -Title, and -BodyFile.'
    }
    if ($Head -eq $Base) { throw 'Head and base branches must differ.' }
    $path = (Resolve-Path -LiteralPath $BodyFile -ErrorAction Stop).Path
    $body = [System.IO.File]::ReadAllText($path).Trim()
    if (!$body) { throw 'BodyFile must contain a pull request description.' }
    if ($IssueNumber) {
        $issue = Invoke-GitHubRest -Client $client -Method Get -Path "issues/$IssueNumber"
        if ($issue.pull_request) { throw "#$IssueNumber is a pull request, not an issue." }
    }
    $null = Invoke-GitHubRest -Client $client -Method Get -Path "git/ref/heads/$Head"
    if ($IssueNumber -and $body -notmatch "(?im)\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s+#$IssueNumber\b") {
        $body = "$body`n`nCloses #$IssueNumber"
    }
    if ($PSCmdlet.ShouldProcess("$Repository $Head -> $Base", 'Create pull request')) {
        $pr = Invoke-GitHubRest -Client $client -Method Post -Path 'pulls' -Body @{ title = $Title; head = $Head; base = $Base; body = $body }
        [pscustomobject]@{ Number = $pr.number; Url = $pr.html_url; State = $pr.state; Draft = $pr.draft }
    }
    return
}

if (!$PullRequestNumber) { throw "$Action requires -PullRequestNumber." }
$pr = Invoke-GitHubRest -Client $client -Method Get -Path "pulls/$PullRequestNumber"

if ($Action -eq 'Update') {
    if ($pr.state -ne 'open' -or $pr.merged) { throw "PR #$PullRequestNumber must be open and unmerged to update it." }
    $update = @{}
    if ($PSBoundParameters.ContainsKey('Title')) {
        if ([string]::IsNullOrWhiteSpace($Title)) { throw 'Update -Title cannot be empty.' }
        $update.title = $Title
    }
    if ($PSBoundParameters.ContainsKey('BodyFile')) {
        $path = (Resolve-Path -LiteralPath $BodyFile -ErrorAction Stop).Path
        $body = [System.IO.File]::ReadAllText($path).Trim()
        if (!$body) { throw 'BodyFile must contain a pull request description.' }
        $update.body = $body
    }
    if ($update.Count -eq 0) { throw 'Update requires -Title, -BodyFile, or both.' }
    if ($PSCmdlet.ShouldProcess("$Repository PR #$PullRequestNumber", 'Update pull request')) {
        $null = Invoke-GitHubRest -Client $client -Method Patch -Path "pulls/$PullRequestNumber" -Body $update
        $updated = Invoke-GitHubRest -Client $client -Method Get -Path "pulls/$PullRequestNumber"
        foreach ($field in $update.Keys) {
            if ($updated.$field -cne $update[$field]) { throw "GitHub did not confirm the updated PR $field for #$PullRequestNumber." }
        }
        [pscustomobject]@{ Number = $updated.number; Url = $updated.html_url; State = $updated.state; Title = $updated.title; BodyUpdated = $update.ContainsKey('body') }
    }
    return
}

$checks = @(Get-PullRequestChecks $pr.head.sha)
$statuses = @(Get-PullRequestStatuses $pr.head.sha)
$requiredChecks = @(Get-RequiredChecks $pr.base.ref)
$checkSummary = @($checks | ForEach-Object { "$($_.name): $($_.status)/$($_.conclusion)" })
$statusSummary = @($statuses | ForEach-Object { "$($_.context): $($_.state)" })
$requiredSummary = @($requiredChecks | ForEach-Object { if ($null -ne $_.app_id) { "$($_.context) (app $($_.app_id))" } else { $_.context } })

if ($Action -eq 'Inspect') {
    [pscustomobject]@{
        Number = $pr.number
        Url = $pr.html_url
        State = $pr.state
        Draft = $pr.draft
        Merged = $pr.merged
        Head = $pr.head.sha
        HeadRef = $pr.head.ref
        BaseRef = $pr.base.ref
        Mergeable = $pr.mergeable
        MergeableState = $pr.mergeable_state
        Checks = $checkSummary
        Statuses = $statusSummary
        RequiredChecks = $requiredSummary
    }
    return
}

Assert-GitHubPullRequestReady -PullRequest $pr -Checks $checks -Statuses $statuses -RequiredChecks $requiredChecks

if ($PSCmdlet.ShouldProcess("$Repository PR #$PullRequestNumber", 'Merge via squash')) {
    $result = Invoke-GitHubRest -Client $client -Method Put -Path "pulls/$PullRequestNumber/merge" -Body @{ merge_method = 'squash'; sha = $pr.head.sha }
    if ($result.merged -ne $true) { throw "GitHub did not confirm the merge of PR #$PullRequestNumber." }
    [pscustomobject]@{ Number = $PullRequestNumber; Merged = $result.merged; Sha = $result.sha; Message = $result.message }
}
